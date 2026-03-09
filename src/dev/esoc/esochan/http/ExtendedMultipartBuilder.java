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

import java.io.File;
import java.io.IOException;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.Random;

import dev.esoc.esochan.api.interfaces.CancellableTask;
import dev.esoc.esochan.api.interfaces.ProgressListener;
import dev.esoc.esochan.common.IOUtils;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okio.Buffer;
import okio.BufferedSink;
import okio.ForwardingSink;
import okio.Okio;
import okio.Sink;

public class ExtendedMultipartBuilder {
    private static final int RANDOMHASH_TAIL_SIZE = 6;

    private static Random random = new Random();
    private static Random getRandom() {
        if (random == null) random = new Random();
        return random;
    }

    private final MultipartBody.Builder builder;
    private ProgressListener listener = null;
    private CancellableTask task = null;

    public ExtendedMultipartBuilder() {
        builder = new MultipartBody.Builder(generateBoundary()).setType(MultipartBody.FORM);
    }

    public static ExtendedMultipartBuilder create() {
        return new ExtendedMultipartBuilder();
    }

    /**
     * Sets the charset for the multipart entity. OkHttp always uses UTF-8,
     * so this is a no-op kept for API compatibility.
     */
    public ExtendedMultipartBuilder setCharset(Charset charset) {
        // OkHttp MultipartBody always uses UTF-8; kept for API compatibility
        return this;
    }

    public ExtendedMultipartBuilder addString(String key, String value) {
        builder.addFormDataPart(key, value);
        return this;
    }

    public ExtendedMultipartBuilder addFile(String key, File file, boolean uniqueHash) {
        String filename = file.getName();
        String mimeType = URLConnection.guessContentTypeFromName(filename);
        if (mimeType == null) mimeType = "application/octet-stream";
        MediaType mediaType = MediaType.parse(mimeType);

        RequestBody fileBody;
        if (uniqueHash) {
            final int randomTail = RANDOMHASH_TAIL_SIZE;
            fileBody = new RequestBody() {
                @Override
                public MediaType contentType() { return mediaType; }
                @Override
                public long contentLength() { return file.length() + randomTail; }
                @Override
                public void writeTo(BufferedSink sink) throws IOException {
                    okio.Source source = Okio.source(file);
                    try {
                        Buffer buf = new Buffer();
                        long remaining = file.length();
                        while (remaining > 0) {
                            long read = source.read(buf, Math.min(8192, remaining));
                            if (read == -1) break;
                            sink.write(buf, read);
                            remaining -= read;
                        }
                    } finally {
                        source.close();
                    }
                    byte[] tail = new byte[randomTail];
                    getRandom().nextBytes(tail);
                    sink.write(tail);
                }
            };
        } else {
            fileBody = RequestBody.create(file, mediaType);
        }

        builder.addFormDataPart(key, filename, fileBody);
        return this;
    }

    public ExtendedMultipartBuilder addFile(String key, File file) {
        return addFile(key, file, false);
    }

    public ExtendedMultipartBuilder addPart(String key, RequestBody body) {
        builder.addFormDataPart(key, null, body);
        return this;
    }

    public ExtendedMultipartBuilder addByteArray(String key, String filename, byte[] data, String mimeType) {
        MediaType mediaType = mimeType != null ? MediaType.parse(mimeType) : MediaType.parse("application/octet-stream");
        builder.addFormDataPart(key, filename, RequestBody.create(data, mediaType));
        return this;
    }

    public ExtendedMultipartBuilder setDelegates(ProgressListener listener, CancellableTask task) {
        this.listener = listener;
        this.task = task;
        return this;
    }

    public RequestBody build() {
        RequestBody body = builder.build();
        if (listener != null || task != null) {
            return new ProgressRequestBody(body, listener, task);
        }
        return body;
    }

    protected String generateBoundary() {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < 27; ++i) stringBuilder.append("-");
        int length = 26 + getRandom().nextInt(4);
        for (int i = 0; i < length; ++i) stringBuilder.append(Integer.toString(getRandom().nextInt(10)));
        return stringBuilder.toString();
    }

    private static class ProgressRequestBody extends RequestBody {
        private final RequestBody delegate;
        private final ProgressListener listener;
        private final CancellableTask task;

        ProgressRequestBody(RequestBody delegate, ProgressListener listener, CancellableTask task) {
            this.delegate = delegate;
            this.listener = listener;
            this.task = task;
        }

        @Override
        public MediaType contentType() { return delegate.contentType(); }

        @Override
        public long contentLength() throws IOException { return delegate.contentLength(); }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {
            if (listener != null) listener.setMaxValue(contentLength());
            CountingSink countingSink = new CountingSink(sink, listener, task);
            BufferedSink bufferedSink = Okio.buffer(countingSink);
            delegate.writeTo(bufferedSink);
            bufferedSink.flush();
            if (listener != null) listener.setIndeterminate();
        }
    }

    private static class CountingSink extends ForwardingSink {
        private long bytesWritten = 0;
        private final ProgressListener listener;
        private final CancellableTask task;

        CountingSink(Sink delegate, ProgressListener listener, CancellableTask task) {
            super(delegate);
            this.listener = listener;
            this.task = task;
        }

        @Override
        public void write(Buffer source, long byteCount) throws IOException {
            if (task != null && task.isCancelled()) {
                throw new IOUtils.InterruptedStreamException();
            }
            super.write(source, byteCount);
            bytesWritten += byteCount;
            if (listener != null) {
                listener.setProgress(bytesWritten);
            }
        }
    }
}
