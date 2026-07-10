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
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import dev.esoc.esochan.http.HttpConstants;
import dev.esoc.esochan.http.HttpCookie;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

public class ExtendedHttpClient implements Closeable {
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

        return builder.build();
    }

    public static class CookieStore implements CookieJar {
        private final List<Cookie> cookies = new ArrayList<>();

        public void addCookie(HttpCookie cookie) {
            addLegacyCookie(cookie, false);
        }

        public void addSecureCookie(HttpCookie cookie) {
            addLegacyCookie(cookie, true);
        }

        private void addLegacyCookie(HttpCookie cookie, boolean secure) {
            if (cookie == null || cookie.getName() == null || cookie.getValue() == null
                    || cookie.getDomain() == null) {
                return;
            }

            String domain = cookie.getDomain();
            boolean domainCookie = domain.startsWith(".");
            if (domain.startsWith(".")) {
                domain = domain.substring(1);
            }

            try {
                Cookie.Builder builder = new Cookie.Builder()
                        .name(cookie.getName())
                        .value(cookie.getValue())
                        .path(cookie.getPath() != null ? cookie.getPath() : "/");
                if (domainCookie) {
                    builder.domain(domain);
                } else {
                    builder.hostOnlyDomain(domain);
                }
                if (cookie.getExpiryDate() != null) {
                    builder.expiresAt(cookie.getExpiryDate().getTime());
                }
                if (secure) {
                    builder.secure();
                }
                addCookie(builder.build());
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed legacy cookies, as OkHttp does for malformed response cookies.
            }
        }

        private void addCookie(Cookie cookie) {
            synchronized (cookies) {
                Iterator<Cookie> it = cookies.iterator();
                while (it.hasNext()) {
                    Cookie existing = it.next();
                    if (hasSameIdentity(existing, cookie)) {
                        it.remove();
                    }
                }
                if (cookie.expiresAt() > System.currentTimeMillis()) {
                    cookies.add(cookie);
                }
            }
        }

        public List<HttpCookie> getCookies() {
            List<HttpCookie> result = new ArrayList<>();
            synchronized (cookies) {
                long now = System.currentTimeMillis();
                Iterator<Cookie> it = cookies.iterator();
                while (it.hasNext()) {
                    Cookie cookie = it.next();
                    if (cookie.expiresAt() <= now) {
                        it.remove();
                        continue;
                    }
                    HttpCookie legacyCookie = new HttpCookie(cookie.name(), cookie.value());
                    legacyCookie.setDomain(cookie.hostOnly() ? cookie.domain() : "." + cookie.domain());
                    legacyCookie.setPath(cookie.path());
                    if (cookie.persistent()) {
                        legacyCookie.setExpiryDate(new Date(cookie.expiresAt()));
                    }
                    result.add(legacyCookie);
                }
            }
            return result;
        }

        public void removeCookie(String name) {
            synchronized (cookies) {
                Iterator<Cookie> it = cookies.iterator();
                while (it.hasNext()) {
                    if (it.next().name().equals(name)) {
                        it.remove();
                    }
                }
            }
        }

        public void clearExpired(Date date) {
            synchronized (cookies) {
                Iterator<Cookie> it = cookies.iterator();
                while (it.hasNext()) {
                    if (it.next().expiresAt() <= date.getTime()) {
                        it.remove();
                    }
                }
            }
        }

        @Override
        public void saveFromResponse(HttpUrl url, List<Cookie> responseCookies) {
            for (Cookie cookie : responseCookies) {
                addCookie(cookie);
            }
        }

        @Override
        public List<Cookie> loadForRequest(HttpUrl url) {
            List<Cookie> result = new ArrayList<>();
            synchronized (cookies) {
                long now = System.currentTimeMillis();
                Iterator<Cookie> it = cookies.iterator();
                while (it.hasNext()) {
                    Cookie cookie = it.next();
                    if (cookie.expiresAt() <= now) {
                        it.remove();
                        continue;
                    }
                    if (cookie.matches(url)) {
                        result.add(cookie);
                    }
                }
            }
            return result;
        }

        private static boolean hasSameIdentity(Cookie first, Cookie second) {
            return first.name().equals(second.name())
                    && first.domain().equalsIgnoreCase(second.domain())
                    && first.path().equals(second.path());
        }
    }
}
