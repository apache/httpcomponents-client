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

package org.apache.http.impl.conn;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

import org.apache.http.config.MessageConstraints;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.io.SessionInputBufferImpl;

/**
 * {@link org.apache.http.io.SessionInputBuffer} mockup implementation.
 */
public class SessionInputBufferMock extends SessionInputBufferImpl {

    public static final int BUFFER_SIZE = 16;

    public SessionInputBufferMock(
            final InputStream instream,
            final int buffersize,
            final MessageConstraints constrains,
            final CharsetDecoder decoder) {
        super(new HttpTransportMetricsImpl(), buffersize, -1, constrains, decoder);
        bind(instream);
    }

    public SessionInputBufferMock(
            final InputStream instream,
            final int buffersize) {
        this(instream, buffersize, null, null);
    }

    public SessionInputBufferMock(
            final byte[] bytes,
            final int buffersize,
            final MessageConstraints constrains,
            final CharsetDecoder decoder) {
        this(new ByteArrayInputStream(bytes), buffersize, constrains, decoder);
    }

    public SessionInputBufferMock(
            final byte[] bytes,
            final int buffersize,
            final MessageConstraints constrains) {
        this(new ByteArrayInputStream(bytes), buffersize, constrains, null);
    }

    public SessionInputBufferMock(
            final byte[] bytes,
            final int buffersize) {
        this(new ByteArrayInputStream(bytes), buffersize);
    }

    public SessionInputBufferMock(
            final byte[] bytes) {
        this(bytes, BUFFER_SIZE);
    }

    public SessionInputBufferMock(
            final byte[] bytes, final Charset charset) {
        this(bytes, BUFFER_SIZE, null, charset != null ? charset.newDecoder() : null);
    }

    public SessionInputBufferMock(
            final byte[] bytes,
            final CharsetDecoder decoder) {
        this(bytes, BUFFER_SIZE, null, decoder);
    }

    public SessionInputBufferMock(
            final String s,
            final Charset charset) {
        this(s.getBytes(charset), charset);
    }

    @Override
    public boolean isDataAvailable(final int timeout) throws IOException {
        return true;
    }

}
