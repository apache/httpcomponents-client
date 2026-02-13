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
package org.apache.hc.core5.websocket.server;

import java.io.IOException;
import java.net.Socket;

import javax.net.ssl.SSLSocket;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentLengthStrategy;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.CharCodingSupport;
import org.apache.hc.core5.http.impl.io.DefaultHttpRequestParserFactory;
import org.apache.hc.core5.http.impl.io.DefaultHttpResponseWriterFactory;
import org.apache.hc.core5.http.io.HttpConnectionFactory;
import org.apache.hc.core5.http.io.HttpMessageParserFactory;
import org.apache.hc.core5.http.io.HttpMessageWriterFactory;

class WebSocketServerConnectionFactory implements HttpConnectionFactory<WebSocketServerConnection> {

    private final String scheme;
    private final Http1Config http1Config;
    private final CharCodingConfig charCodingConfig;
    private final ContentLengthStrategy incomingContentStrategy;
    private final ContentLengthStrategy outgoingContentStrategy;
    private final HttpMessageParserFactory<ClassicHttpRequest> requestParserFactory;
    private final HttpMessageWriterFactory<ClassicHttpResponse> responseWriterFactory;

    WebSocketServerConnectionFactory(
            final String scheme,
            final Http1Config http1Config,
            final CharCodingConfig charCodingConfig,
            final ContentLengthStrategy incomingContentStrategy,
            final ContentLengthStrategy outgoingContentStrategy,
            final HttpMessageParserFactory<ClassicHttpRequest> requestParserFactory,
            final HttpMessageWriterFactory<ClassicHttpResponse> responseWriterFactory) {
        this.scheme = scheme;
        this.http1Config = http1Config != null ? http1Config : Http1Config.DEFAULT;
        this.charCodingConfig = charCodingConfig != null ? charCodingConfig : CharCodingConfig.DEFAULT;
        this.incomingContentStrategy = incomingContentStrategy;
        this.outgoingContentStrategy = outgoingContentStrategy;
        this.requestParserFactory = requestParserFactory != null ? requestParserFactory :
                new DefaultHttpRequestParserFactory(this.http1Config);
        this.responseWriterFactory = responseWriterFactory != null ? responseWriterFactory :
                new DefaultHttpResponseWriterFactory(this.http1Config);
    }

    WebSocketServerConnectionFactory(final String scheme, final Http1Config http1Config, final CharCodingConfig charCodingConfig) {
        this(scheme, http1Config, charCodingConfig, null, null, null, null);
    }

    private WebSocketServerConnection createDetached(final Socket socket) {
        return new WebSocketServerConnection(
                scheme != null ? scheme : (socket instanceof SSLSocket ? URIScheme.HTTPS.id : URIScheme.HTTP.id),
                this.http1Config,
                CharCodingSupport.createDecoder(this.charCodingConfig),
                CharCodingSupport.createEncoder(this.charCodingConfig),
                this.incomingContentStrategy,
                this.outgoingContentStrategy,
                this.requestParserFactory,
                this.responseWriterFactory);
    }

    @Override
    public WebSocketServerConnection createConnection(final Socket socket) throws IOException {
        final WebSocketServerConnection conn = createDetached(socket);
        conn.bind(socket);
        return conn;
    }

    @Override
    public WebSocketServerConnection createConnection(final SSLSocket sslSocket, final Socket socket) throws IOException {
        final WebSocketServerConnection conn = createDetached(sslSocket);
        conn.bind(sslSocket, socket);
        return conn;
    }
}
