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

package nya.miku.wishmaster.http.streamer;

import nya.miku.wishmaster.http.HttpConstants;
import nya.miku.wishmaster.http.HttpHeader;
import okhttp3.RequestBody;

public class HttpRequestModel {
    private static final int METHOD_UNDEFINED = -1;
    static final int METHOD_GET = 0;
    static final int METHOD_POST = 1;
    static final int METHOD_DELETE = 2;

    public static final HttpRequestModel DEFAULT_GET = builder().setGET().build();

    final int method;
    final boolean checkIfModified;
    final boolean noRedirect;
    final HttpHeader[] customHeaders;
    final RequestBody postEntity;
    final int timeoutValue;

    private HttpRequestModel(
            int method,
            boolean checkIfModified,
            boolean noRedirect,
            HttpHeader[] customHeaders,
            RequestBody postEntity,
            int timeoutValue) {
        this.method = method;
        this.checkIfModified = checkIfModified;
        this.noRedirect = noRedirect;
        this.customHeaders = customHeaders;
        this.postEntity = postEntity;
        this.timeoutValue = timeoutValue;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int method = METHOD_UNDEFINED;
        private boolean checkIfModified = false;
        private boolean noRedirect = false;
        private HttpHeader[] customHeaders = null;
        private RequestBody postEntity = null;
        private int timeoutValue = HttpConstants.DEFAULT_HTTP_TIMEOUT;

        private Builder() {}

        public Builder setGET() {
            this.method = METHOD_GET;
            this.postEntity = null;
            return this;
        }

        public Builder setPOST(RequestBody postEntity) {
            this.method = METHOD_POST;
            this.postEntity = postEntity;
            return this;
        }

        public Builder setDELETE(RequestBody postEntity) {
            this.method = METHOD_DELETE;
            this.postEntity = postEntity;
            return this;
        }

        public Builder setNoRedirect(boolean noRedirect) {
            this.noRedirect = noRedirect;
            return this;
        }

        public Builder setCheckIfModified(boolean checkIfModified) {
            this.checkIfModified = checkIfModified;
            return this;
        }

        public Builder setCustomHeaders(HttpHeader[] customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public Builder setTimeout(int timeoutValue) {
            this.timeoutValue = timeoutValue;
            return this;
        }

        public HttpRequestModel build() {
            if (method == METHOD_UNDEFINED) throw new IllegalStateException("method not set");
            if (method != METHOD_GET && checkIfModified) throw new IllegalStateException("check if-modified is available only for GET method");
            return new HttpRequestModel(method, checkIfModified, noRedirect, customHeaders, postEntity, timeoutValue);
        }
    }
}
