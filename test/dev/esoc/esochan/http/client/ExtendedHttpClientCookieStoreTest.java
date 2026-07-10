package dev.esoc.esochan.http.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.junit.Test;

import dev.esoc.esochan.http.HttpCookie;
import okhttp3.Cookie;
import okhttp3.HttpUrl;

public class ExtendedHttpClientCookieStoreTest {
    private static final HttpUrl HTTPS_BOARD = HttpUrl.get("https://boards.4chan.org/g/thread/123");

    @Test
    public void responseCookieRetainsSecureHttpOnlyHostOnlyAndPathAttributes() {
        ExtendedHttpClient.CookieStore store = new ExtendedHttpClient.CookieStore();
        Cookie cookie = new Cookie.Builder()
                .name("session")
                .value("secret")
                .hostOnlyDomain("boards.4chan.org")
                .path("/g/")
                .secure()
                .httpOnly()
                .build();

        store.saveFromResponse(HTTPS_BOARD, Collections.singletonList(cookie));

        List<Cookie> loaded = store.loadForRequest(HTTPS_BOARD);
        assertEquals(1, loaded.size());
        assertTrue(loaded.get(0).secure());
        assertTrue(loaded.get(0).httpOnly());
        assertTrue(loaded.get(0).hostOnly());
        assertEquals("boards.4chan.org", store.getCookies().get(0).getDomain());
        assertTrue(store.loadForRequest(HttpUrl.get("http://boards.4chan.org/g/thread/123")).isEmpty());
        assertTrue(store.loadForRequest(HttpUrl.get("https://boards.4chan.org/a/thread/123")).isEmpty());
        assertTrue(store.loadForRequest(HttpUrl.get("https://sys.4chan.org/g/thread/123")).isEmpty());
    }

    @Test
    public void domainCookieMatchesSubdomainsButSecureCookieRejectsHttp() {
        ExtendedHttpClient.CookieStore store = new ExtendedHttpClient.CookieStore();
        Cookie cookie = new Cookie.Builder()
                .name("pass_id")
                .value("secret")
                .domain("4chan.org")
                .path("/")
                .secure()
                .build();

        store.saveFromResponse(HTTPS_BOARD, Collections.singletonList(cookie));

        assertEquals(1, store.loadForRequest(HttpUrl.get("https://sys.4chan.org/g/post")).size());
        assertEquals(".4chan.org", store.getCookies().get(0).getDomain());
        assertTrue(store.loadForRequest(HttpUrl.get("http://sys.4chan.org/g/post")).isEmpty());
        assertTrue(store.loadForRequest(HttpUrl.get("https://example.org/g/post")).isEmpty());
    }

    @Test
    public void replacementIdentityIncludesPath() {
        ExtendedHttpClient.CookieStore store = new ExtendedHttpClient.CookieStore();
        store.saveFromResponse(HTTPS_BOARD, Collections.singletonList(cookie("first", "/")));
        store.saveFromResponse(HTTPS_BOARD, Collections.singletonList(cookie("board", "/g/")));
        store.saveFromResponse(HTTPS_BOARD, Collections.singletonList(cookie("replacement", "/g/")));

        List<Cookie> loaded = store.loadForRequest(HTTPS_BOARD);
        assertEquals(2, loaded.size());
        assertTrue(loaded.stream().anyMatch(cookie -> "first".equals(cookie.value())));
        assertTrue(loaded.stream().anyMatch(cookie -> "replacement".equals(cookie.value())));
        assertFalse(loaded.stream().anyMatch(cookie -> "board".equals(cookie.value())));
    }

    @Test
    public void secureLegacyInjectionIsNotSentOverHttp() {
        ExtendedHttpClient.CookieStore store = new ExtendedHttpClient.CookieStore();
        HttpCookie cookie = new HttpCookie("cf_clearance", "secret");
        cookie.setDomain(".4chan.org");
        cookie.setPath("/");

        store.addSecureCookie(cookie);

        assertEquals(1, store.loadForRequest(HTTPS_BOARD).size());
        assertEquals(1, store.loadForRequest(HttpUrl.get("https://sys.4chan.org/g/post")).size());
        assertTrue(store.loadForRequest(HttpUrl.get("http://boards.4chan.org/g/thread/123")).isEmpty());
    }

    @Test
    public void legacyCookieWithoutLeadingDotRemainsHostOnly() {
        ExtendedHttpClient.CookieStore store = new ExtendedHttpClient.CookieStore();
        HttpCookie cookie = new HttpCookie("host_cookie", "value");
        cookie.setDomain("boards.4chan.org");

        store.addCookie(cookie);

        assertEquals(1, store.loadForRequest(HTTPS_BOARD).size());
        assertTrue(store.loadForRequest(HttpUrl.get("https://sub.boards.4chan.org/g/thread/123")).isEmpty());
    }

    @Test
    public void expiredCookieRemovesMatchingStoredCookie() {
        ExtendedHttpClient.CookieStore store = new ExtendedHttpClient.CookieStore();
        store.saveFromResponse(HTTPS_BOARD, Collections.singletonList(cookie("active", "/")));
        Cookie expired = new Cookie.Builder()
                .name("id")
                .value("deleted")
                .domain("4chan.org")
                .path("/")
                .expiresAt(new Date(0).getTime())
                .build();

        store.saveFromResponse(HTTPS_BOARD, Collections.singletonList(expired));

        assertTrue(store.loadForRequest(HTTPS_BOARD).isEmpty());
    }

    private static Cookie cookie(String value, String path) {
        return new Cookie.Builder()
                .name("id")
                .value(value)
                .domain("4chan.org")
                .path(path)
                .build();
    }
}
