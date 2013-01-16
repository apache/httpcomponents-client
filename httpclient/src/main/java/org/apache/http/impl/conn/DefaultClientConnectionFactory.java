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

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.annotation.Immutable;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.conn.HttpConnectionFactory;
import org.apache.http.conn.SocketClientConnection;
import org.apache.http.impl.io.DefaultHttpRequestWriterFactory;
import org.apache.http.io.HttpMessageParserFactory;
import org.apache.http.io.HttpMessageWriterFactory;
import org.apache.http.util.Args;

/**
 * @since 4.3
 */
@Immutable
public class DefaultClientConnectionFactory implements HttpConnectionFactory<SocketClientConnection> {

    private static final int DEFAULT_BUFSIZE = 8 * 1024;

    public static final DefaultClientConnectionFactory INSTANCE = new DefaultClientConnectionFactory();

    private final int bufferSize;
    private final HttpMessageWriterFactory<HttpRequest> requestWriterFactory;
    private final HttpMessageParserFactory<HttpResponse> responseParserFactory;

    public DefaultClientConnectionFactory(
            final int bufferSize,
            final HttpMessageWriterFactory<HttpRequest> requestWriterFactory,
            final HttpMessageParserFactory<HttpResponse> responseParserFactory) {
        super();
        this.bufferSize = Args.notNegative(bufferSize, "Buffer size");
        this.requestWriterFactory = requestWriterFactory != null ? requestWriterFactory :
            DefaultHttpRequestWriterFactory.INSTANCE;
        this.responseParserFactory = responseParserFactory != null ? responseParserFactory :
            DefaultHttpResponseParserFactory.INSTANCE;
    }

    public DefaultClientConnectionFactory(
            final HttpMessageWriterFactory<HttpRequest> requestWriterFactory,
            final HttpMessageParserFactory<HttpResponse> responseParserFactory) {
        this(DEFAULT_BUFSIZE, requestWriterFactory, responseParserFactory);
    }

    public DefaultClientConnectionFactory(
            final HttpMessageParserFactory<HttpResponse> responseParserFactory) {
        this(null, responseParserFactory);
    }

    public DefaultClientConnectionFactory(final int bufferSize) {
        this(bufferSize, null, null);
    }

    public DefaultClientConnectionFactory() {
        this(DEFAULT_BUFSIZE, null, null);
    }

    public SocketClientConnection create(final ConnectionConfig config) {
        final ConnectionConfig cconfig = config != null ? config : ConnectionConfig.DEFAULT;
        CharsetDecoder chardecoder = null;
        CharsetEncoder charencoder = null;
        final Charset charset = cconfig.getCharset();
        final CodingErrorAction malformedInputAction = cconfig.getMalformedInputAction() != null ?
                cconfig.getMalformedInputAction() : CodingErrorAction.REPORT;
        final CodingErrorAction unmappableInputAction = cconfig.getUnmappableInputAction() != null ?
                cconfig.getUnmappableInputAction() : CodingErrorAction.REPORT;
        if (charset != null) {
            chardecoder = charset.newDecoder();
            chardecoder.onMalformedInput(malformedInputAction);
            chardecoder.onUnmappableCharacter(unmappableInputAction);
            charencoder = charset.newEncoder();
            charencoder.onMalformedInput(malformedInputAction);
            charencoder.onUnmappableCharacter(unmappableInputAction);
        }
        return new SocketClientConnectionImpl(bufferSize,
                chardecoder, charencoder,
                cconfig.getMessageConstraints(),
                null, null,
                requestWriterFactory,
                responseParserFactory);
    }

}
