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

package org.apache.http.mockup;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

import org.apache.http.impl.io.AbstractSessionInputBuffer;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;

/**
 * {@link org.apache.http.io.SessionInputBuffer} mockup implementation.
 */
public class SessionInputBufferMockup extends AbstractSessionInputBuffer {

    public static final int BUFFER_SIZE = 16;

    public SessionInputBufferMockup(
            final InputStream instream,
            int buffersize,
            final HttpParams params) {
        super();
        init(instream, buffersize, params);
    }

    public SessionInputBufferMockup(
            final InputStream instream,
            int buffersize) {
        this(instream, buffersize, new BasicHttpParams());
    }

    public SessionInputBufferMockup(
            final byte[] bytes,
            final HttpParams params) {
        this(bytes, BUFFER_SIZE, params);
    }

    public SessionInputBufferMockup(
            final byte[] bytes) {
        this(bytes, BUFFER_SIZE, new BasicHttpParams());
    }

    public SessionInputBufferMockup(
            final byte[] bytes,
            int buffersize,
            final HttpParams params) {
        this(new ByteArrayInputStream(bytes), buffersize, params);
    }

    public SessionInputBufferMockup(
            final byte[] bytes,
            int buffersize) {
        this(new ByteArrayInputStream(bytes), buffersize, new BasicHttpParams());
    }

    public SessionInputBufferMockup(
            final String s,
            final String charset,
            int buffersize,
            final HttpParams params)
        throws UnsupportedEncodingException {
        this(s.getBytes(charset), buffersize, params);
    }

    public SessionInputBufferMockup(
            final String s,
            final String charset,
            int buffersize)
        throws UnsupportedEncodingException {
        this(s.getBytes(charset), buffersize, new BasicHttpParams());
    }

    public SessionInputBufferMockup(
            final String s,
            final String charset,
            final HttpParams params)
        throws UnsupportedEncodingException {
        this(s.getBytes(charset), params);
    }

    public SessionInputBufferMockup(
            final String s,
            final String charset)
        throws UnsupportedEncodingException {
        this(s.getBytes(charset), new BasicHttpParams());

    }

    public boolean isDataAvailable(int timeout) throws IOException {
        return true;
    }

}
