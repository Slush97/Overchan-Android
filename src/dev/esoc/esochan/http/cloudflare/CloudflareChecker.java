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

package dev.esoc.esochan.http.cloudflare;

import java.util.Date;
import dev.esoc.esochan.api.interfaces.CancellableTask;
import dev.esoc.esochan.common.Logger;
import dev.esoc.esochan.http.HttpConstants;
import dev.esoc.esochan.http.HttpCookie;
import dev.esoc.esochan.http.client.ExtendedHttpClient;
import dev.esoc.esochan.http.streamer.HttpRequestModel;
import dev.esoc.esochan.http.streamer.HttpResponseModel;
import dev.esoc.esochan.http.streamer.HttpStreamer;
import dev.esoc.esochan.lib.WebViewProxy;
import dev.esoc.esochan.ui.CompatibilityImpl;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;

@SuppressWarnings("deprecation")
public class CloudflareChecker {
    private static final String TAG = "CloudflareChecker";

    public static final long TIMEOUT = 35 * 1000;

    private CloudflareChecker() {}
    private static CloudflareChecker instance;

    public static synchronized CloudflareChecker getInstance() {
        if (instance == null) instance = new CloudflareChecker();
        return instance;
    }

    public boolean isAvaibleAntiDDOS() {
        return !(processing || InterceptingAntiDDOS.getInstance().isProcessing());
    }

    public HttpCookie checkAntiDDOS(CloudflareException exception, ExtendedHttpClient httpClient, CancellableTask task, Activity activity) {
        if (exception.isRecaptcha()) throw new IllegalArgumentException();

        if (httpClient.hasProxy()) {
            return InterceptingAntiDDOS.getInstance().check(exception, httpClient, task, activity);
        } else {
            return checkAntiDDOS(exception, httpClient, task, activity, false);
        }
    }

    private volatile boolean processing = false;
    private volatile boolean processing2 = false;
    private volatile HttpCookie currentCookie;
    private volatile WebView webView;
    private volatile Context webViewContext;
    private Object lock = new Object();

    private HttpCookie checkAntiDDOS(final CloudflareException exception, final ExtendedHttpClient httpClient,
            CancellableTask task, final Activity activity, boolean useProxy) {
        synchronized (lock) {
            if (processing) return null;
            processing = true;
        }
        processing2 = true;
        currentCookie = null;

        CompatibilityImpl.clearCookies(CookieManager.getInstance());

        final ViewGroup layout = (ViewGroup) activity.getWindow().getDecorView().getRootView();
        final WebViewClient client = new WebViewClient() {
            @Override
            public void onPageFinished(WebView webView, String url) {
                super.onPageFinished(webView, url);
                Logger.d(TAG, "Got Page: "+url);
                String value = null;
                try {
                    String[] cookies = CookieManager.getInstance().getCookie(url).split("[;]");
                    for (String cookie : cookies) {
                        if ((cookie != null) && (!cookie.trim().equals("")) && (cookie.startsWith(" " + exception.getRequiredCookieName() + "="))) {
                            value = cookie.substring(exception.getRequiredCookieName().length() + 2);
                        }
                    }
                } catch (NullPointerException e) {
                    Logger.e(TAG, e);
                }
                if (value != null) {
                    HttpCookie cf_cookie = new HttpCookie(exception.getRequiredCookieName(), value);
                    cf_cookie.setDomain("." + Uri.parse(url).getHost());
                    cf_cookie.setPath("/");
                    currentCookie = cf_cookie;
                    Logger.d(TAG, "Cookie found: "+value);
                    processing2 = false;
                } else {
                    Logger.d(TAG, "Cookie is not found");
                }
            }
        };

        activity.runOnUiThread(new Runnable() {
            @SuppressLint("SetJavaScriptEnabled")
            @Override
            public void run() {
                webView = new WebView(activity);
                webView.setVisibility(View.GONE);
                layout.addView(webView);
                webView.setWebViewClient(client);
                webView.getSettings().setUserAgentString(HttpConstants.USER_AGENT_STRING);
                webView.getSettings().setJavaScriptEnabled(true);
                webViewContext = webView.getContext();
                if (httpClient.hasProxy()) WebViewProxy.setProxy(webViewContext, httpClient.getProxyHost(), httpClient.getProxyPort());
                webView.loadUrl(exception.getCheckUrl());
            }
        });

        long startTime = System.currentTimeMillis();
        while (processing2) {
            long time = System.currentTimeMillis() - startTime;
            if ((task != null && task.isCancelled()) || time > TIMEOUT) {
                processing2 = false;
            }
        }

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    layout.removeView(webView);
                    webView.stopLoading();
                    webView.clearCache(true);
                    webView.destroy();
                    webView = null;
                } finally {
                    if (httpClient.hasProxy()) WebViewProxy.setProxy(webViewContext, null, 0);
                    processing = false;
                }
            }
        });

        return currentCookie;
    }

    public HttpCookie checkRecaptcha(CloudflareException exception, ExtendedHttpClient httpClient, CancellableTask task, String url) {
        if (!exception.isRecaptcha()) throw new IllegalArgumentException("wrong type of CloudflareException");
        HttpResponseModel responseModel = null;
        try {
            HttpRequestModel rqModel = HttpRequestModel.builder().setGET().setNoRedirect(false).build();
            ExtendedHttpClient.CookieStore cookieStore = httpClient.getCookieStore();
            cookieStore.removeCookie(exception.getRequiredCookieName());
            responseModel = HttpStreamer.getInstance().getFromUrl(url, rqModel, httpClient, null, task);
            for (int i = 0; i < 3  && responseModel.statusCode == 400; ++i) {
                Logger.d(TAG, "HTTP 400");
                responseModel.release();
                responseModel = HttpStreamer.getInstance().getFromUrl(url, rqModel, httpClient, null, task);
            }
            for (HttpCookie cookie : cookieStore.getCookies()) {
                if (isClearanceCookie(cookie, url, exception.getRequiredCookieName())) {
                    Logger.d(TAG, "Cookie found: " + cookie.getValue());
                    return cookie;
                }
            }
            Logger.d(TAG, "Cookie is not found");
        } catch (Exception e) {
            Logger.e(TAG, e);
        } finally {
            if (responseModel != null) {
                responseModel.release();
            }
        }
        return null;
    }

    static boolean isClearanceCookie(HttpCookie cookie, String url, String requiredCookieName) {
        try {
            String cookieName = cookie.getName();
            String cookieDomain = cookie.getDomain();
            if (cookieDomain != null && !cookieDomain.startsWith(".")) {
                cookieDomain = "." + cookieDomain;
            }

            String urlCookie = "." + Uri.parse(url).getHost();
            if (cookieName.equals(requiredCookieName) && cookieDomain != null && cookieDomain.equalsIgnoreCase(urlCookie)) {
                return true;
            }
        } catch (Exception e) {
            Logger.e(TAG, e);
        }
        return false;
    }
}
