package dev.esoc.esochan.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import dev.esoc.esochan.http.client.ExtendedHttpClient;
import kotlin.Pair;
import okhttp3.HttpUrl;

public class WebViewCookieBridgeTest {

    @Test
    public void parseCookieHeaderSplitsNameValuePairs() {
        List<Pair<String, String>> pairs =
                WebViewCookieBridge.parseCookieHeader("cf_clearance=abc; other=1; bare");
        assertEquals(2, pairs.size());
        assertEquals("cf_clearance", pairs.get(0).getFirst());
        assertEquals("abc", pairs.get(0).getSecond());
        assertEquals("other", pairs.get(1).getFirst());
        assertEquals("1", pairs.get(1).getSecond());
    }

    @Test
    public void parseCookieHeaderHandlesNullAndEmpty() {
        assertTrue(WebViewCookieBridge.parseCookieHeader(null).isEmpty());
        assertTrue(WebViewCookieBridge.parseCookieHeader("").isEmpty());
        assertTrue(WebViewCookieBridge.parseCookieHeader("   ").isEmpty());
    }

    @Test
    public void cookieDomainForUrlMaps4chanAnd4channel() {
        assertEquals(".4chan.org", WebViewCookieBridge.cookieDomainForUrl("https://sys.4chan.org/"));
        assertEquals(".4chan.org", WebViewCookieBridge.cookieDomainForUrl("https://boards.4chan.org/g/"));
        assertEquals(".4chan.org", WebViewCookieBridge.cookieDomainForUrl("https://www.4chan.org/"));
        assertEquals(".4channel.org", WebViewCookieBridge.cookieDomainForUrl("https://sys.4channel.org/"));
        assertEquals(".4channel.org", WebViewCookieBridge.cookieDomainForUrl("https://boards.4channel.org/"));
        assertNull(WebViewCookieBridge.cookieDomainForUrl("https://example.com/"));
        assertNull(WebViewCookieBridge.cookieDomainForUrl("not a url"));
    }

    @Test
    public void mergeAllowlistedImportsOnlyAllowlistedNames() {
        ExtendedHttpClient.CookieStore store = new ExtendedHttpClient.CookieStore();
        Map<String, String> headers = new HashMap<>();
        headers.put(
                "https://sys.4chan.org/",
                "cf_clearance=clear-value; session=nope; pass_id=should-not-import");

        List<String> names = WebViewCookieBridge.mergeAllowlisted(store, headers);

        assertEquals(Collections.singletonList("cf_clearance"), names);
        List<HttpCookie> cookies = store.getCookies();
        assertEquals(1, cookies.size());
        assertEquals("cf_clearance", cookies.get(0).getName());
        assertEquals("clear-value", cookies.get(0).getValue());
        assertEquals(".4chan.org", cookies.get(0).getDomain());
        assertEquals("/", cookies.get(0).getPath());
    }

    @Test
    public void mergeAllowlistedDoesNotClearExistingJarOrPassCookies() {
        ExtendedHttpClient.CookieStore store = new ExtendedHttpClient.CookieStore();
        HttpCookie pass = new HttpCookie("pass_id", "secret");
        pass.setDomain(".4chan.org");
        pass.setPath("/");
        store.addSecureCookie(pass);

        Map<String, String> headers = new HashMap<>();
        headers.put("https://boards.4chan.org/", "cf_clearance=xyz; pass_id=0");

        WebViewCookieBridge.mergeAllowlisted(store, headers);

        boolean hasPass = false;
        boolean hasClearance = false;
        for (HttpCookie cookie : store.getCookies()) {
            if ("pass_id".equals(cookie.getName())) {
                hasPass = true;
                assertEquals("secret", cookie.getValue());
            }
            if ("cf_clearance".equals(cookie.getName())) {
                hasClearance = true;
            }
        }
        assertTrue(hasPass);
        assertTrue(hasClearance);

        HttpUrl url = HttpUrl.get("https://sys.4chan.org/captcha");
        assertFalse(store.loadForRequest(url).isEmpty());
    }

    @Test
    public void mergeAllowlistedWritesBothParentDomainsFromSeparateUrls() {
        ExtendedHttpClient.CookieStore store = new ExtendedHttpClient.CookieStore();
        Map<String, String> headers = new HashMap<>();
        headers.put("https://sys.4chan.org/", "cf_clearance=a");
        headers.put("https://sys.4channel.org/", "cf_clearance=b");

        List<String> names = WebViewCookieBridge.mergeAllowlisted(store, headers);
        assertEquals(1, names.size());
        assertEquals("cf_clearance", names.get(0));
        assertEquals(2, store.getCookies().size());
    }

    @Test
    public void mergeAllowlistedRespectsCustomAllowlist() {
        ExtendedHttpClient.CookieStore store = new ExtendedHttpClient.CookieStore();
        Map<String, String> headers = Collections.singletonMap(
                "https://sys.4chan.org/", "cf_clearance=a; session=b");

        List<String> names = WebViewCookieBridge.mergeAllowlisted(
                store, headers, Set.of("session"));

        assertEquals(Collections.singletonList("session"), names);
        assertEquals("session", store.getCookies().get(0).getName());
    }

    @Test
    public void mergeAllowlistedNoopsOnEmptyHeaders() {
        ExtendedHttpClient.CookieStore store = new ExtendedHttpClient.CookieStore();
        List<String> names = WebViewCookieBridge.mergeAllowlisted(
                store, Collections.singletonMap("https://sys.4chan.org/", null));
        assertTrue(names.isEmpty());
        assertTrue(store.getCookies().isEmpty());
    }
}
