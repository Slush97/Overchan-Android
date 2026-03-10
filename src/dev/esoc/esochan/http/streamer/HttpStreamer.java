/*
 * esochan (Meta Imageboard Client)
 * Copyright (C) 2014-2016  miku-nyan <https://github.com/miku-nyan>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package dev.esoc.esochan.http.streamer;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import dev.esoc.esochan.api.interfaces.CancellableTask;
import dev.esoc.esochan.api.interfaces.ProgressListener;
import dev.esoc.esochan.common.IOUtils;
import dev.esoc.esochan.common.Logger;
import dev.esoc.esochan.http.HttpConstants;
import dev.esoc.esochan.http.HttpHeader;
import dev.esoc.esochan.http.client.ExtendedHttpClient;
import dev.esoc.esochan.lib.org_json.JSONArray;
import dev.esoc.esochan.lib.org_json.JSONException;
import dev.esoc.esochan.lib.org_json.JSONObject;
import dev.esoc.esochan.lib.org_json.JSONTokener;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class HttpStreamer {
    private static final String TAG = "HttpStreamer";

    private HttpStreamer() {}

    private static HttpStreamer instance = null;

    public static void initInstance() {
        if (instance == null) instance = new HttpStreamer();
    }

    public static HttpStreamer getInstance() {
        if (instance == null) {
            Logger.e(TAG, "HttpStreamer is not initialized");
            initInstance();
        }
        return instance;
    }

    private final HashMap<String, String> ifModifiedMap = new HashMap<String, String>();

    public String removeFromModifiedMap(String url) {
        String value = null;
        synchronized (ifModifiedMap) {
            value = ifModifiedMap.remove(url);
        }
        return value;
    }

    public HttpResponseModel getFromUrl(String url, HttpRequestModel requestModel, ExtendedHttpClient httpClient, ProgressListener listener,
            CancellableTask task) throws HttpRequestException {
        if (requestModel == null) requestModel = HttpRequestModel.DEFAULT_GET;

        Response response = null;
        try {
            OkHttpClient client = httpClient.newCallClient(requestModel.timeoutValue, !requestModel.noRedirect);

            Request.Builder requestBuilder = new Request.Builder().url(url);
            requestBuilder.header("User-Agent", HttpConstants.getUserAgentString());

            switch (requestModel.method) {
                case HttpRequestModel.METHOD_GET:
                    requestBuilder.get();
                    break;
                case HttpRequestModel.METHOD_POST:
                    requestBuilder.post(requestModel.postEntity);
                    break;
                case HttpRequestModel.METHOD_DELETE:
                    requestBuilder.delete(requestModel.postEntity);
                    break;
                default:
                    throw new IllegalArgumentException("Incorrect type of HTTP Request");
            }
            if (requestModel.customHeaders != null) {
                for (HttpHeader header : requestModel.customHeaders) {
                    requestBuilder.header(header.getName(), header.getValue());
                }
            }
            if (requestModel.checkIfModified && requestModel.method == HttpRequestModel.METHOD_GET) {
                synchronized (ifModifiedMap) {
                    if (ifModifiedMap.containsKey(url)) {
                        requestBuilder.header("If-Modified-Since", ifModifiedMap.get(url));
                    }
                }
            }

            Request request = requestBuilder.build();

            IOException responseException = null;
            for (int i = 0; i < 5; ++i) {
                try {
                    if (task != null && task.isCancelled()) throw new InterruptedException();
                    response = client.newCall(request).execute();
                    responseException = null;
                    break;
                } catch (IOException e) {
                    Logger.e(TAG, e);
                    responseException = e;
                    if (e.getMessage() == null) break;
                    String message = e.getMessage();
                    if (message.indexOf("Connection reset by peer") != -1 ||
                            message.indexOf("I/O error during system call, Broken pipe") != -1 ||
                            (message.indexOf("Write error: ssl") != -1 && message.indexOf("I/O error during system call") != -1)) {
                        continue;
                    } else {
                        break;
                    }
                }
            }
            if (responseException != null) {
                throw responseException;
            }
            if (task != null && task.isCancelled()) throw new InterruptedException();

            HttpResponseModel responseModel = new HttpResponseModel();
            responseModel.statusCode = response.code();
            responseModel.statusReason = response.message();

            String lastModifiedValue = null;
            if (responseModel.statusCode == 200) {
                String lastModified = response.header("Last-Modified");
                if (lastModified != null) lastModifiedValue = lastModified;
            }
            String location = response.header("Location");
            if (location != null) responseModel.locationHeader = location;

            // Convert headers
            List<HttpHeader> headerList = new ArrayList<>();
            for (String name : response.headers().names()) {
                for (String value : response.headers(name)) {
                    headerList.add(new HttpHeader(name, value));
                }
            }
            responseModel.headers = headerList.toArray(new HttpHeader[0]);

            ResponseBody body = response.body();
            if (body != null) {
                responseModel.contentLength = body.contentLength();
                if (listener != null) listener.setMaxValue(responseModel.contentLength);
                InputStream stream = body.byteStream();
                responseModel.stream = IOUtils.modifyInputStream(stream, listener, task);
            }
            responseModel.response = response;
            if (lastModifiedValue != null) {
                synchronized (ifModifiedMap) {
                    ifModifiedMap.put(url, lastModifiedValue);
                }
            }
            return responseModel;
        } catch (Exception e) {
            Logger.e(TAG, e);
            HttpResponseModel.release(response);
            throw new HttpRequestException(e);
        }
    }

    public byte[] getBytesFromUrl(String url, HttpRequestModel requestModel, ExtendedHttpClient httpClient, ProgressListener listener,
            CancellableTask task, boolean anyCode) throws IOException, HttpRequestException, HttpWrongStatusCodeException {
        HttpResponseModel responseModel = null;
        try {
            responseModel = getFromUrl(url, requestModel, httpClient, listener, task);
            if (responseModel.statusCode == 200) {
                if (responseModel.stream == null) throw new HttpRequestException(new NullPointerException());
                ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
                IOUtils.copyStream(responseModel.stream, output);
                return output.toByteArray();
            } else {
                if (responseModel.notModified()) return null;
                if (anyCode) {
                    byte[] html = null;
                    try {
                        ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
                        IOUtils.copyStream(responseModel.stream, output);
                        html = output.toByteArray();
                    } catch (Exception e) {
                        Logger.e(TAG, e);
                    }
                    throw new HttpWrongStatusCodeException(responseModel.statusCode, responseModel.statusCode+" - "+responseModel.statusReason, html);
                } else {
                    throw new HttpWrongStatusCodeException(responseModel.statusCode, responseModel.statusCode+" - "+responseModel.statusReason);
                }
            }
        } catch (Exception e) {
            if (responseModel != null) removeFromModifiedMap(url);
            throw e;
        } finally {
            if (responseModel != null) responseModel.release();
        }
    }

    public String getStringFromUrl(String url, HttpRequestModel requestModel, ExtendedHttpClient httpClient, ProgressListener listener,
            CancellableTask task, boolean anyCode) throws IOException, HttpRequestException, HttpWrongStatusCodeException {
        byte[] bytes = getBytesFromUrl(url, requestModel, httpClient, listener, task, anyCode);
        if (bytes == null) return null;
        return new String(bytes);
    }

    public JSONObject getJSONObjectFromUrl(String url, HttpRequestModel requestModel, ExtendedHttpClient httpClient, ProgressListener listener,
            CancellableTask task, boolean anyCode) throws IOException, HttpRequestException, HttpWrongStatusCodeException, JSONException {
        return (JSONObject) getJSONFromUrl(url, requestModel, httpClient, listener, task, anyCode, false);
    }

    public JSONArray getJSONArrayFromUrl(String url, HttpRequestModel requestModel, ExtendedHttpClient httpClient, ProgressListener listener,
            CancellableTask task, boolean anyCode) throws IOException, HttpRequestException, HttpWrongStatusCodeException, JSONException {
        return (JSONArray) getJSONFromUrl(url, requestModel, httpClient, listener, task, anyCode, true);
    }

    private Object getJSONFromUrl(String url, HttpRequestModel requestModel, ExtendedHttpClient httpClient, ProgressListener listener,
            CancellableTask task, boolean anyCode, boolean isArray)
            throws IOException, HttpRequestException, HttpWrongStatusCodeException, JSONException {
        HttpResponseModel responseModel = null;
        BufferedReader in = null;
        try {
            responseModel = getFromUrl(url, requestModel, httpClient, listener, task);
            if (responseModel.statusCode == 200) {
                if (responseModel.stream == null) throw new HttpRequestException(new NullPointerException());
                in = new BufferedReader(new InputStreamReader(responseModel.stream));
                return isArray ? new JSONArray(new JSONTokener(in)) : new JSONObject(new JSONTokener(in));
            } else {
                if (responseModel.notModified()) return null;
                if (anyCode) {
                    byte[] html = null;
                    try {
                        ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
                        IOUtils.copyStream(responseModel.stream, output);
                        html = output.toByteArray();
                    } catch (Exception e) {
                        Logger.e(TAG, e);
                    }
                    throw new HttpWrongStatusCodeException(responseModel.statusCode, responseModel.statusCode+" - "+responseModel.statusReason, html);
                } else {
                    throw new HttpWrongStatusCodeException(responseModel.statusCode, responseModel.statusCode+" - "+responseModel.statusReason);
                }
            }
        } catch (Exception e) {
            if (responseModel != null) removeFromModifiedMap(url);
            throw e;
        } finally {
            IOUtils.closeQuietly(in);
            if (responseModel != null) responseModel.release();
        }
    }

    public void downloadFileFromUrl(String url, OutputStream out, HttpRequestModel requestModel, ExtendedHttpClient httpClient,
            ProgressListener listener, CancellableTask task, boolean anyCode)
            throws IOException, HttpRequestException, HttpWrongStatusCodeException {
        HttpResponseModel responseModel = null;
        try {
            responseModel = getFromUrl(url, requestModel, httpClient, listener, task);
            if (responseModel.statusCode == 200) {
                IOUtils.copyStream(responseModel.stream, out);
            } else {
                if (anyCode) {
                    byte[] html = null;
                    try {
                        ByteArrayOutputStream byteStream = new ByteArrayOutputStream(1024);
                        IOUtils.copyStream(responseModel.stream, byteStream);
                        html = byteStream.toByteArray();
                    } catch (Exception e) {
                        Logger.e(TAG, e);
                    }
                    throw new HttpWrongStatusCodeException(responseModel.statusCode, responseModel.statusCode+" - "+responseModel.statusReason, html);
                } else {
                    throw new HttpWrongStatusCodeException(responseModel.statusCode, responseModel.statusCode+" - "+responseModel.statusReason);
                }
            }
        } catch (Exception e) {
            if (responseModel != null) removeFromModifiedMap(url);
            throw e;
        } finally {
            if (responseModel != null) responseModel.release();
        }
    }
}
