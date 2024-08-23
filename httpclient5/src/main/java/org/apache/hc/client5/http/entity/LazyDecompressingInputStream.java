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
package org.apache.hc.client5.http.entity;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import org.apache.hc.core5.io.Closer;

/**
 * Lazy initializes from an {@link InputStream} wrapper.
 */
class LazyDecompressingInputStream extends FilterInputStream {

    private final InputStreamFactory inputStreamFactory;

    private InputStream wrapperStream;

    public LazyDecompressingInputStream(
            final InputStream wrappedStream,
            final InputStreamFactory inputStreamFactory) {
        super(wrappedStream);
        this.inputStreamFactory = inputStreamFactory;
    }

    private InputStream initWrapper() throws IOException {
        if (wrapperStream == null) {
            wrapperStream = inputStreamFactory.create(in);
        }
        return wrapperStream;
    }

    @Override
    public int read() throws IOException {
        return initWrapper().read();
    }

    @Override
    public int read(final byte[] b) throws IOException {
        return initWrapper().read(b);
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        return initWrapper().read(b, off, len);
    }

    @Override
    public long skip(final long n) throws IOException {
        return initWrapper().skip(n);
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public int available() throws IOException {
        return initWrapper().available();
    }

    @Override
    public void close() throws IOException {
        try {
            Closer.close(wrapperStream);
        } finally {
            super.close();
        }
    }

    @Override
    public void mark(final int readlimit) {
        try {
            initWrapper().mark(readlimit);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void reset() throws IOException {
        initWrapper().reset();
    }
}
