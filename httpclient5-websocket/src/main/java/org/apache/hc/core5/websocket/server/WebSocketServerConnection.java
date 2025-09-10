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
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentLengthStrategy;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.io.DefaultBHttpServerConnection;
import org.apache.hc.core5.http.impl.io.SocketHolder;
import org.apache.hc.core5.http.io.HttpMessageParserFactory;
import org.apache.hc.core5.http.io.HttpMessageWriterFactory;

class WebSocketServerConnection extends DefaultBHttpServerConnection {

    WebSocketServerConnection(
            final String scheme,
            final Http1Config http1Config,
            final CharsetDecoder charDecoder,
            final CharsetEncoder charEncoder,
            final ContentLengthStrategy incomingContentStrategy,
            final ContentLengthStrategy outgoingContentStrategy,
            final HttpMessageParserFactory<ClassicHttpRequest> requestParserFactory,
            final HttpMessageWriterFactory<ClassicHttpResponse> responseWriterFactory) {
        super(scheme, http1Config, charDecoder, charEncoder, incomingContentStrategy, outgoingContentStrategy,
                requestParserFactory, responseWriterFactory);
    }

    InputStream getSocketInputStream() throws IOException {
        final SocketHolder socketHolder = getSocketHolder();
        return socketHolder != null ? socketHolder.getInputStream() : null;
    }

    OutputStream getSocketOutputStream() throws IOException {
        final SocketHolder socketHolder = getSocketHolder();
        return socketHolder != null ? socketHolder.getOutputStream() : null;
    }
}
