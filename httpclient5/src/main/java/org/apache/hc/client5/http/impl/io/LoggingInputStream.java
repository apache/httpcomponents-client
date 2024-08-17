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

package org.apache.hc.client5.http.impl.io;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.hc.client5.http.impl.Wire;

/**
 * Delegates {@link InputStream} calls and logs to a {@link Wire}.
 */
class LoggingInputStream extends FilterInputStream {

    private final Wire wire;

    LoggingInputStream(final InputStream in, final Wire wire) {
        super(in);
        this.wire = wire;
    }

    @Override
    public int read() throws IOException {
        try {
            final int b = in.read();
            if (b == -1) {
                wire.input("end of stream");
            } else {
                wire.input(b);
            }
            return b;
        } catch (final IOException ex) {
            wire.input("[read] I/O error: " + ex.getMessage());
            throw ex;
        }
    }

    @Override
    public int read(final byte[] b) throws IOException {
        try {
            final int bytesRead = in.read(b);
            if (bytesRead == -1) {
                wire.input("end of stream");
            } else if (bytesRead > 0) {
                wire.input(b, 0, bytesRead);
            }
            return bytesRead;
        } catch (final IOException ex) {
            wire.input("[read] I/O error: " + ex.getMessage());
            throw ex;
        }
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        try {
            final int bytesRead = in.read(b, off, len);
            if (bytesRead == -1) {
                wire.input("end of stream");
            } else if (bytesRead > 0) {
                wire.input(b, off, bytesRead);
            }
            return bytesRead;
        } catch (final IOException ex) {
            wire.input("[read] I/O error: " + ex.getMessage());
            throw ex;
        }
    }

    @Override
    public long skip(final long n) throws IOException {
        try {
            return in.skip(n);
        } catch (final IOException ex) {
            wire.input("[skip] I/O error: " + ex.getMessage());
            throw ex;
        }
    }

    @Override
    public int available() throws IOException {
        try {
            return in.available();
        } catch (final IOException ex) {
            wire.input("[available] I/O error: " + ex.getMessage());
            throw ex;
        }
    }

    @Override
    public void close() throws IOException {
        try {
            in.close();
        } catch (final IOException ex) {
            wire.input("[close] I/O error: " + ex.getMessage());
            throw ex;
        }
    }

}
