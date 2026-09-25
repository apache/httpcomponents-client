/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package org.apache.hc.client5.http.impl.classic;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.entity.compress.CompressionDictionary;
import org.apache.hc.client5.http.entity.compress.CompressionDictionaryStore;
import org.apache.hc.client5.http.impl.UseAsDictionary;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.HttpEntityWrapper;
import org.apache.hc.core5.util.Args;

/**
 * Entity decorator that captures the decoded representation of a successful
 * {@code Use-As-Dictionary} response while it is consumed by the caller.
 */
final class DictionaryCapturingEntity extends HttpEntityWrapper {

    private static final int BUFFER_SIZE = 8 * 1024;

    private final CompressionDictionaryStore store;
    private final CookieStore partition;
    private final URI source;
    private final UseAsDictionary directive;
    private final Instant storedAt;
    private final Instant validUntil;
    private final int maxSize;
    private final AtomicBoolean stored;

    private InputStream content;

    DictionaryCapturingEntity(
            final HttpEntity wrapped,
            final CompressionDictionaryStore store,
            final CookieStore partition,
            final URI source,
            final UseAsDictionary directive,
            final Instant storedAt,
            final Instant validUntil,
            final int maxSize) {
        super(wrapped);
        this.store = Args.notNull(store, "Dictionary store");
        this.partition = Args.notNull(partition, "Cookie partition");
        this.source = Args.notNull(source, "Source");
        this.directive = Args.notNull(directive, "Directive");
        this.storedAt = Args.notNull(storedAt, "Stored at");
        this.validUntil = Args.notNull(validUntil, "Valid until");
        this.maxSize = Args.positive(maxSize, "Maximum dictionary size");
        this.stored = new AtomicBoolean(false);
    }

    private InputStream newCapturingStream() throws IOException {
        return new CapturingInputStream(super.getContent());
    }

    @Override
    public InputStream getContent() throws IOException {
        if (!isStreaming()) {
            return newCapturingStream();
        }
        if (content == null) {
            content = newCapturingStream();
        }
        return content;
    }

    @Override
    public void writeTo(final OutputStream outStream) throws IOException {
        Args.notNull(outStream, "Output stream");
        try (InputStream inStream = getContent()) {
            final byte[] buffer = new byte[BUFFER_SIZE];
            int count;
            while ((count = inStream.read(buffer)) != -1) {
                outStream.write(buffer, 0, count);
            }
        }
    }

    private void store(final byte[] bytes) {
        if (stored.compareAndSet(false, true) && directive.isSupported()) {
            store.add(partition, new CompressionDictionary(
                    bytes,
                    source,
                    directive.getMatch(),
                    directive.getMatchDest(),
                    directive.getId(),
                    directive.getType(),
                    storedAt,
                    validUntil));
        }
    }

    private final class CapturingInputStream extends InputStream {

        private final InputStream delegate;
        private final ByteArrayOutputStream buffer;
        private boolean discarded;
        private boolean complete;
        private boolean closed;

        private CapturingInputStream(final InputStream delegate) {
            this.delegate = delegate;
            this.buffer = new ByteArrayOutputStream(Math.min(maxSize, BUFFER_SIZE));
        }

        @Override
        public int read() throws IOException {
            final int value;
            try {
                value = delegate.read();
            } catch (final IOException ex) {
                discard();
                throw ex;
            }
            if (value == -1) {
                complete();
            } else {
                captureByte(value);
            }
            return value;
        }

        @Override
        public int read(final byte[] b, final int off, final int len) throws IOException {
            final int count;
            try {
                count = delegate.read(b, off, len);
            } catch (final IOException ex) {
                discard();
                throw ex;
            }
            if (count == -1) {
                complete();
            } else if (count > 0) {
                capture(b, off, count);
            }
            return count;
        }

        @Override
        public long skip(final long n) throws IOException {
            if (n <= 0) {
                return 0;
            }
            long remaining = n;
            final byte[] tmp = new byte[(int) Math.min(BUFFER_SIZE, n)];
            while (remaining > 0) {
                final int count = read(tmp, 0, (int) Math.min(tmp.length, remaining));
                if (count == -1) {
                    break;
                }
                remaining -= count;
            }
            return n - remaining;
        }

        @Override
        public int available() throws IOException {
            return delegate.available();
        }

        private void captureByte(final int value) {
            if (!discarded) {
                if (buffer.size() == maxSize) {
                    discard();
                } else {
                    buffer.write(value);
                }
            }
        }

        private void capture(final byte[] b, final int off, final int len) {
            if (!discarded) {
                if (buffer.size() + len > maxSize) {
                    discard();
                } else {
                    buffer.write(b, off, len);
                }
            }
        }

        private void complete() {
            if (!complete) {
                complete = true;
                if (!discarded) {
                    DictionaryCapturingEntity.this.store(buffer.toByteArray());
                }
            }
        }

        private void discard() {
            discarded = true;
            buffer.reset();
        }

        @Override
        public void close() throws IOException {
            if (!closed) {
                closed = true;
                if (!complete) {
                    discard();
                }
                delegate.close();
            }
        }
    }
}
