/*
 * esochan (Meta Imageboard Client)
 * Copyright (C) 2024-2026  esoc <https://github.com/esoc-dev>
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

package dev.esoc.esochan.http

import android.webkit.CookieManager
import dev.esoc.esochan.http.client.ExtendedHttpClient
import java.net.URI

/**
 * Merges allowlisted cookies from Android [CookieManager] into the OkHttp jar.
 *
 * CookieManager only exposes `name=value` pairs (no Domain/Path/Secure/Expires).
 * Domain is derived from the request URL host; path is always `/`; allowlisted
 * cookies on HTTPS are marked secure.
 */
object WebViewCookieBridge {

    val DEFAULT_ALLOWLIST: Set<String> = setOf("cf_clearance")

    val DEFAULT_4CHAN_URLS: List<String> = listOf(
        "https://sys.4chan.org/",
        "https://sys.4channel.org/",
        "https://boards.4chan.org/",
        "https://boards.4channel.org/",
        "https://4chan.org/",
        "https://www.4chan.org/",
    )

    private val PASS_COOKIE_NAMES: Set<String> = setOf("pass_id", "pass_enabled")

    /**
     * Read [CookieManager] for each URL and merge allowlisted cookies into [cookieStore].
     * Never clear-all. Returns distinct cookie names that were written (for debug logs only).
     */
    @JvmStatic
    @JvmOverloads
    fun syncAllowlisted(
        cookieStore: ExtendedHttpClient.CookieStore,
        urls: Collection<String> = DEFAULT_4CHAN_URLS,
        allowlist: Set<String> = DEFAULT_ALLOWLIST,
    ): List<String> {
        val headers = LinkedHashMap<String, String?>()
        val manager = CookieManager.getInstance()
        for (url in urls) {
            headers[url] = try {
                manager.getCookie(url)
            } catch (_: Exception) {
                null
            }
        }
        return mergeAllowlisted(cookieStore, headers, allowlist)
    }

    /**
     * Pure merge path for unit tests: [urlToHeader] maps full HTTPS URLs to CookieManager headers.
     */
    @JvmStatic
    @JvmOverloads
    fun mergeAllowlisted(
        cookieStore: ExtendedHttpClient.CookieStore,
        urlToHeader: Map<String, String?>,
        allowlist: Set<String> = DEFAULT_ALLOWLIST,
    ): List<String> {
        val synced = LinkedHashSet<String>()
        for ((url, header) in urlToHeader) {
            val domain = cookieDomainForUrl(url) ?: continue
            for ((name, value) in parseCookieHeader(header)) {
                if (name !in allowlist) continue
                if (name in PASS_COOKIE_NAMES) continue
                if (value.isEmpty()) continue
                val cookie = HttpCookie(name, value)
                cookie.domain = domain
                cookie.path = "/"
                cookieStore.addSecureCookie(cookie)
                synced.add(name)
            }
        }
        return synced.toList()
    }

    /** Pure/testable: parse `"a=b; c=d"` → list of (name, value). */
    @JvmStatic
    fun parseCookieHeader(header: String?): List<Pair<String, String>> {
        if (header.isNullOrBlank()) return emptyList()
        val out = ArrayList<Pair<String, String>>()
        for (part in header.split(';')) {
            val trimmed = part.trim()
            if (trimmed.isEmpty()) continue
            val eq = trimmed.indexOf('=')
            if (eq <= 0) continue
            val name = trimmed.substring(0, eq).trim()
            val value = trimmed.substring(eq + 1).trim()
            if (name.isEmpty()) continue
            out.add(name to value)
        }
        return out
    }

    /**
     * Map URL host to cookie domain with leading dot for parent domains.
     * `*.4chan.org` → `.4chan.org`; `*.4channel.org` → `.4channel.org`.
     */
    @JvmStatic
    fun cookieDomainForUrl(url: String): String? {
        val host = try {
            URI(url).host?.lowercase()
        } catch (_: Exception) {
            null
        } ?: return null
        return when {
            host == "4chan.org" || host.endsWith(".4chan.org") -> ".4chan.org"
            host == "4channel.org" || host.endsWith(".4channel.org") -> ".4channel.org"
            else -> null
        }
    }
}
