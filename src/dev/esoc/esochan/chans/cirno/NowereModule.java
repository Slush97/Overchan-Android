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

package dev.esoc.esochan.chans.cirno;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceGroup;
import androidx.core.content.res.ResourcesCompat;
import dev.esoc.esochan.R;
import dev.esoc.esochan.api.AbstractChanModule;
import dev.esoc.esochan.api.interfaces.CancellableTask;
import dev.esoc.esochan.api.interfaces.ProgressListener;
import dev.esoc.esochan.api.models.BoardModel;
import dev.esoc.esochan.api.models.CaptchaModel;
import dev.esoc.esochan.api.models.DeletePostModel;
import dev.esoc.esochan.api.models.PostModel;
import dev.esoc.esochan.api.models.SendPostModel;
import dev.esoc.esochan.api.models.SimpleBoardModel;
import dev.esoc.esochan.api.models.ThreadModel;
import dev.esoc.esochan.api.models.UrlPageModel;
import dev.esoc.esochan.api.util.ChanModels;
import dev.esoc.esochan.api.util.WakabaReader;
import dev.esoc.esochan.api.util.WakabaUtils;
import dev.esoc.esochan.common.IOUtils;
import dev.esoc.esochan.http.ExtendedMultipartBuilder;
import dev.esoc.esochan.http.streamer.HttpRequestModel;
import dev.esoc.esochan.http.streamer.HttpResponseModel;
import dev.esoc.esochan.http.streamer.HttpStreamer;
import dev.esoc.esochan.http.streamer.HttpWrongStatusCodeException;

public class NowereModule extends AbstractChanModule {
    
    static final String NOWERE_NAME = "nowere.net";
    static final String NOWERE_DOMAIN = "nowere.net";
    static final String NOWERE_URL_HTTP = "http://nowere.net/";
    static final String NOWERE_URL_HTTPS = "https://nowere.net/";
    
    public NowereModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return NOWERE_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Nowere.net";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_nowere, null);
    }
    
    @Override
    public SimpleBoardModel[] getBoardsList(ProgressListener listener, CancellableTask task, SimpleBoardModel[] oldBoardsList) throws Exception {
        return NowereBoards.getBoardsList();
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        return NowereBoards.getBoard(shortName);
    }
    
    @Override
    public void addPreferencesOnScreen(PreferenceGroup preferenceGroup) {
        addPasswordPreference(preferenceGroup);
        addHttpsPreference(preferenceGroup, false);
        addProxyPreferences(preferenceGroup);
    }
    
    private String getUsingUrl() {
        return useHttps(false) ? NOWERE_URL_HTTPS : NOWERE_URL_HTTP;
    }
    
    private ThreadModel[] readWakabaPage(String url, ProgressListener listener, CancellableTask task, boolean checkIfModified) throws Exception {
        HttpResponseModel responseModel = null;
        WakabaReader in = null;
        HttpRequestModel rqModel = HttpRequestModel.builder().setGET().setCheckIfModified(checkIfModified).build();
        try {
            responseModel = HttpStreamer.getInstance().getFromUrl(url, rqModel, httpClient, listener, task);
            if (responseModel.statusCode == 200) {
                in = new WakabaReader(responseModel.stream, DateFormats.NOWERE_DATE_FORMAT);
                if (task != null && task.isCancelled()) throw new Exception("interrupted");
                return in.readWakabaPage();
            } else {
                if (responseModel.notModified()) return null;
                throw new HttpWrongStatusCodeException(responseModel.statusCode, responseModel.statusCode + " - " + responseModel.statusReason);
            }
        } catch (Exception e) {
            if (responseModel != null) HttpStreamer.getInstance().removeFromModifiedMap(url);
            throw e;
        } finally {
            IOUtils.closeQuietly(in);
            if (responseModel != null) responseModel.release();
        }
    }
    
    @Override
    public ThreadModel[] getThreadsList(String boardName, int page, ProgressListener listener, CancellableTask task, ThreadModel[] oldList)
            throws Exception {
        UrlPageModel urlModel = new UrlPageModel();
        urlModel.chanName = NOWERE_NAME;
        urlModel.type = UrlPageModel.TYPE_BOARDPAGE;
        urlModel.boardName = boardName;
        urlModel.boardPage = page;
        String url = buildUrl(urlModel);
        
        ThreadModel[] threads = readWakabaPage(url, listener, task, oldList != null);
        if (threads == null) {
            return oldList;
        } else {
            return threads;
        }
    }
    
    @Override
    public PostModel[] getPostsList(String boardName, String threadNumber, ProgressListener listener, CancellableTask task, PostModel[] oldList)
            throws Exception {
        UrlPageModel urlModel = new UrlPageModel();
        urlModel.chanName = NOWERE_NAME;
        urlModel.type = UrlPageModel.TYPE_THREADPAGE;
        urlModel.boardName = boardName;
        urlModel.threadNumber = threadNumber;
        String url = buildUrl(urlModel);
        
        ThreadModel[] threads = readWakabaPage(url, listener, task, oldList != null);
        if (threads == null) {
            return oldList;
        } else {
            if (threads.length == 0) throw new Exception("Unable to parse response");
            return oldList == null ? threads[0].posts : ChanModels.mergePostsLists(Arrays.asList(oldList), Arrays.asList(threads[0].posts));
        }
    }
    
    @Override
    public CaptchaModel getNewCaptcha(String boardName, String threadNumber, ProgressListener listener, CancellableTask task) throws Exception {
        String captchaUrl = getUsingUrl() + boardName + "/captcha.pl?key=" + (threadNumber == null ? "mainpage" : ("res" + threadNumber));
        return downloadCaptcha(captchaUrl, listener, task);
    }
    
    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl() + model.boardName + "/wakaba.pl";
        ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().setDelegates(listener, task).
                addString("task", "post");
        if (model.threadNumber != null) postEntityBuilder.addString("parent", model.threadNumber);
        postEntityBuilder.
                addString("field1", model.name).
                addString("field2", model.sage ? "sage" : model.email).
                addString("field3", model.subject).
                addString("field4", model.comment).
                addString("captcha", model.captchaAnswer).
                addString("postredir", "1").
                addString("password", model.password);
        if (model.attachments != null && model.attachments.length > 0)
            postEntityBuilder.addFile("file", model.attachments[0], model.randomHash);
        else if (model.threadNumber == null) postEntityBuilder.addString("nofile", "on");
        
        HttpRequestModel request = HttpRequestModel.builder().setPOST(postEntityBuilder.build()).setNoRedirect(true).build();
        HttpResponseModel response = null;
        try {
            response = HttpStreamer.getInstance().getFromUrl(url, request, httpClient, null, task);
            if (response.statusCode == 303) {
                return null;
            } else if (response.statusCode == 200) {
                ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
                IOUtils.copyStream(response.stream, output);
                String htmlResponse = output.toString("UTF-8");
                if (!htmlResponse.contains("<blockquote")) {
                    int start = htmlResponse.indexOf("<h1 style=\"text-align: center\">");
                    if (start != -1) {
                        int end = htmlResponse.indexOf("<br /><br />", start + 31);
                        if (end != -1) {
                            throw new Exception(htmlResponse.substring(start + 31, end).trim());
                        }
                    }
                }
            } else throw new Exception(response.statusCode + " - " + response.statusReason);
        } finally {
            if (response != null) response.release();
        }
        return null;
    }
    
    @Override
    public String deletePost(DeletePostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl() + model.boardName + "/wakaba.pl";
        
        okhttp3.FormBody.Builder formBuilder = new okhttp3.FormBody.Builder();
        formBuilder.add("delete", model.postNumber);
        formBuilder.add("task", "delete");
        if (model.onlyFiles) formBuilder.add("fileonly", "on");
        formBuilder.add("password", model.password);

        HttpRequestModel request = HttpRequestModel.builder().setPOST(formBuilder.build()).setNoRedirect(true).build();
        HttpResponseModel response = null;
        try {
            response = HttpStreamer.getInstance().getFromUrl(url, request, httpClient, null, task);
            if (response.statusCode == 200) {
                ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
                IOUtils.copyStream(response.stream, output);
                String htmlResponse = output.toString("UTF-8");
                if (!htmlResponse.contains("<blockquote")) {
                    int start = htmlResponse.indexOf("<h1 style=\"text-align: center\">");
                    if (start != -1) {
                        int end = htmlResponse.indexOf("<br /><br />", start + 31);
                        if (end != -1) {
                            throw new Exception(htmlResponse.substring(start + 31, end).trim());
                        }
                    }
                }
            }
        } finally {
            if (response != null) response.release();
        }
        return null;
    }
    
    @Override
    public String buildUrl(UrlPageModel model) throws IllegalArgumentException {
        if (!model.chanName.equals(NOWERE_NAME)) throw new IllegalArgumentException("wrong chan");
        return WakabaUtils.buildUrl(model, getUsingUrl());
    }
    
    @Override
    public UrlPageModel parseUrl(String url) throws IllegalArgumentException {
        return WakabaUtils.parseUrl(url, NOWERE_NAME, NOWERE_DOMAIN);
    }
    
}
