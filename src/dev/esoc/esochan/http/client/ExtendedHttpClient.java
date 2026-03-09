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

package dev.esoc.esochan.http.client;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import dev.esoc.esochan.http.HttpConstants;
import dev.esoc.esochan.http.HttpCookie;
import dev.esoc.esochan.common.Logger;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

public class ExtendedHttpClient implements Closeable {
    private static final String TAG = "ExtendedHttpClient";

    private final CookieStore cookieStore;
    private final String proxyHost;
    private final int proxyPort;
    private volatile OkHttpClient httpClient;

    public CookieStore getCookieStore() {
        return cookieStore;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public boolean hasProxy() {
        return proxyHost != null;
    }

    public OkHttpClient getClient() {
        if (httpClient == null) {
            synchronized (this) {
                if (httpClient == null) {
                    httpClient = build(proxyHost, proxyPort, cookieStore);
                }
            }
        }
        return httpClient;
    }

    public ExtendedHttpClient(String proxyHost, int proxyPort) {
        super();
        this.cookieStore = new CookieStore();
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
    }

    public ExtendedHttpClient() {
        this(null, 0);
    }

    public OkHttpClient newCallClient(int timeout, boolean followRedirects) {
        return getClient().newBuilder()
                .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                .readTimeout(timeout, TimeUnit.MILLISECONDS)
                .writeTimeout(timeout, TimeUnit.MILLISECONDS)
                .followRedirects(followRedirects)
                .followSslRedirects(followRedirects)
                .build();
    }

    @Override
    public void close() throws IOException {
        OkHttpClient client = httpClient;
        if (client != null) {
            client.dispatcher().executorService().shutdown();
            client.connectionPool().evictAll();
        }
    }

    private static OkHttpClient build(String proxyHost, int proxyPort, CookieStore cookieStore) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(HttpConstants.DEFAULT_HTTP_TIMEOUT, TimeUnit.MILLISECONDS)
                .readTimeout(HttpConstants.DEFAULT_HTTP_TIMEOUT, TimeUnit.MILLISECONDS)
                .writeTimeout(HttpConstants.DEFAULT_HTTP_TIMEOUT, TimeUnit.MILLISECONDS)
                .cookieJar(cookieStore);

        if (proxyHost != null) {
            builder.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort)));
        }

        try {
            SSLSocketFactory sslSocketFactory = ExtendedSSLSocketFactory.getSSLSocketFactory();
            X509TrustManager trustManager = ExtendedSSLSocketFactory.getTrustManager();
            HostnameVerifier hostnameVerifier = ExtendedSSLSocketFactory.getHostnameVerifier();
            if (sslSocketFactory != null && trustManager != null) {
                builder.sslSocketFactory(sslSocketFactory, trustManager);
            }
            if (hostnameVerifier != null) {
                builder.hostnameVerifier(hostnameVerifier);
            }
        } catch (Exception e) {
            Logger.e(TAG, "SSL configuration failed", e);
        }

        return builder.build();
    }

    public static class CookieStore implements CookieJar {
        private final List<HttpCookie> cookies = Collections.synchronizedList(new ArrayList<HttpCookie>());

        public void addCookie(HttpCookie cookie) {
            synchronized (cookies) {
                // Remove existing cookie with same name and domain
                Iterator<HttpCookie> it = cookies.iterator();
                while (it.hasNext()) {
                    HttpCookie existing = it.next();
                    if (existing.getName().equals(cookie.getName()) && domainMatch(existing.getDomain(), cookie.getDomain())) {
                        it.remove();
                    }
                }
                cookies.add(cookie);
            }
        }

        public List<HttpCookie> getCookies() {
            return new ArrayList<>(cookies);
        }

        public void removeCookie(String name) {
            synchronized (cookies) {
                Iterator<HttpCookie> it = cookies.iterator();
                while (it.hasNext()) {
                    if (it.next().getName().equals(name)) {
                        it.remove();
                    }
                }
            }
        }

        public void clearExpired(Date date) {
            synchronized (cookies) {
                Iterator<HttpCookie> it = cookies.iterator();
                while (it.hasNext()) {
                    if (it.next().isExpired(date)) {
                        it.remove();
                    }
                }
            }
        }

        @Override
        public void saveFromResponse(HttpUrl url, List<Cookie> responseCookies) {
            for (Cookie okhttpCookie : responseCookies) {
                HttpCookie cookie = new HttpCookie(okhttpCookie.name(), okhttpCookie.value());
                cookie.setDomain(okhttpCookie.domain());
                cookie.setPath(okhttpCookie.path());
                if (okhttpCookie.expiresAt() != Long.MAX_VALUE) {
                    cookie.setExpiryDate(new Date(okhttpCookie.expiresAt()));
                }
                addCookie(cookie);
            }
        }

        @Override
        public List<Cookie> loadForRequest(HttpUrl url) {
            List<Cookie> result = new ArrayList<>();
            synchronized (cookies) {
                Iterator<HttpCookie> it = cookies.iterator();
                while (it.hasNext()) {
                    HttpCookie cookie = it.next();
                    if (cookie.isExpired(new Date())) {
                        it.remove();
                        continue;
                    }
                    if (matchesDomain(url.host(), cookie.getDomain())) {
                        Cookie.Builder b = new Cookie.Builder()
                                .name(cookie.getName())
                                .value(cookie.getValue())
                                .path(cookie.getPath() != null ? cookie.getPath() : "/");
                        String domain = cookie.getDomain();
                        if (domain != null) {
                            if (domain.startsWith(".")) {
                                b.domain(domain.substring(1));
                            } else {
                                b.domain(domain);
                            }
                        } else {
                            b.domain(url.host());
                        }
                        if (cookie.getExpiryDate() != null) {
                            b.expiresAt(cookie.getExpiryDate().getTime());
                        }
                        try {
                            result.add(b.build());
                        } catch (Exception e) {
                            // skip malformed cookies
                        }
                    }
                }
            }
            return result;
        }

        private static boolean matchesDomain(String host, String cookieDomain) {
            if (cookieDomain == null) return true;
            String domain = cookieDomain.startsWith(".") ? cookieDomain.substring(1) : cookieDomain;
            return host.equalsIgnoreCase(domain) || host.endsWith("." + domain);
        }

        private static boolean domainMatch(String domain1, String domain2) {
            if (domain1 == null || domain2 == null) return domain1 == domain2;
            String d1 = domain1.startsWith(".") ? domain1.substring(1) : domain1;
            String d2 = domain2.startsWith(".") ? domain2.substring(1) : domain2;
            return d1.equalsIgnoreCase(d2);
        }
    }
}
