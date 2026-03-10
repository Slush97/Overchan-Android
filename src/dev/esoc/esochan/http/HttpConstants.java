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

package dev.esoc.esochan.http;

import android.webkit.WebSettings;

public class HttpConstants {

    private static String userAgentString;

    /**
     * Returns a User-Agent string matching the device's WebView.
     * This is critical — Cloudflare ties cf_clearance cookies to the UA that solved the challenge,
     * so OkHttp and WebView must use the same UA.
     */
    public static String getUserAgentString() {
        if (userAgentString == null) {
            try {
                userAgentString = WebSettings.getDefaultUserAgent(
                        dev.esoc.esochan.common.MainApplication.getInstance());
            } catch (Exception e) {
                // Fallback if WebView is not available
                userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36";
            }
        }
        return userAgentString;
    }

    /** @deprecated Use {@link #getUserAgentString()} instead */
    @Deprecated
    public static final String USER_AGENT_STRING = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36";

    public static final int DEFAULT_HTTP_TIMEOUT = 30 * 1000;

}
