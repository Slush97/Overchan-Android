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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import dev.esoc.esochan.common.Logger;
import dev.esoc.esochan.http.HttpHeader;
import okhttp3.Response;

public class HttpResponseModel {
    private static final String TAG = "HttpResponseModel";

    /*package*/ HttpResponseModel() {}

    public InputStream stream = null;
    public long contentLength = 0;

    public int statusCode;
    public String statusReason;
    public boolean notModified() {
        return statusCode == 304;
    }

    public String locationHeader;
    public HttpHeader[] headers;

    Response response;

    public void release() {
        release(response);
    }

    static void release(Response response) {
        try {
            if (response != null) {
                response.close();
            }
        } catch (Exception e) {
            Logger.e(TAG, e);
        }
    }
}
