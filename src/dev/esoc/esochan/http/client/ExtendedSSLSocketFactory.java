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

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import dev.esoc.esochan.common.Logger;

public class ExtendedSSLSocketFactory {
    private static final String TAG = "ExtendedSSLSocketFactory";

    private static volatile boolean initialized = false;
    private static SSLSocketFactory sslSocketFactory;
    private static X509TrustManager trustManager;
    private static HostnameVerifier hostnameVerifier;

    private static void init() {
        if (initialized) return;
        synchronized (ExtendedSSLSocketFactory.class) {
            if (initialized) return;
            try {
                ExtendedTrustManager mtm = new ExtendedTrustManager();
                SSLContext context = SSLContext.getInstance("TLS");
                context.init(null, new X509TrustManager[]{mtm}, null);
                sslSocketFactory = context.getSocketFactory();
                trustManager = mtm;
                hostnameVerifier = mtm.wrapHostnameVerifier();
            } catch (Exception e) {
                Logger.e(TAG, "SSL initialization failed", e);
            }
            initialized = true;
        }
    }

    public static SSLSocketFactory getSSLSocketFactory() {
        init();
        return sslSocketFactory;
    }

    public static X509TrustManager getTrustManager() {
        init();
        return trustManager;
    }

    public static HostnameVerifier getHostnameVerifier() {
        init();
        return hostnameVerifier;
    }

    public static ExtendedTrustManager getExtendedTrustManager() throws Exception {
        init();
        if (trustManager instanceof ExtendedTrustManager) {
            return (ExtendedTrustManager) trustManager;
        }
        throw new Exception("ExtendedTrustManager not available");
    }
}
