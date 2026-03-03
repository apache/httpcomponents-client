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
import java.util.List;
import java.util.function.Supplier;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequestMapper;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpServerRequestHandler;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.websocket.WebSocketCloseStatus;
import org.apache.hc.core5.websocket.WebSocketConfig;
import org.apache.hc.core5.websocket.WebSocketConstants;
import org.apache.hc.core5.websocket.WebSocketException;
import org.apache.hc.core5.websocket.WebSocketExtensionNegotiation;
import org.apache.hc.core5.websocket.WebSocketExtensionRegistry;
import org.apache.hc.core5.websocket.WebSocketExtensions;
import org.apache.hc.core5.websocket.WebSocketHandler;
import org.apache.hc.core5.websocket.WebSocketHandshake;
import org.apache.hc.core5.websocket.WebSocketSession;
import org.apache.hc.core5.websocket.exceptions.WebSocketProtocolException;

class WebSocketServerRequestHandler implements HttpServerRequestHandler {

    private final HttpRequestMapper<Supplier<WebSocketHandler>> requestMapper;
    private final WebSocketConfig config;
    private final WebSocketExtensionRegistry extensionRegistry;

    WebSocketServerRequestHandler(
            final HttpRequestMapper<Supplier<WebSocketHandler>> requestMapper,
            final WebSocketConfig config,
            final WebSocketExtensionRegistry extensionRegistry) {
        this.requestMapper = Args.notNull(requestMapper, "Request mapper");
        this.config = config != null ? config : WebSocketConfig.DEFAULT;
        this.extensionRegistry = extensionRegistry != null ? extensionRegistry : new WebSocketExtensionRegistry();
    }

    @Override
    public void handle(
            final ClassicHttpRequest request,
            final ResponseTrigger trigger,
            final HttpContext context) throws HttpException, IOException {
        final Supplier<WebSocketHandler> supplier = requestMapper.resolve(request, context);
        if (supplier == null) {
            trigger.submitResponse(new BasicClassicHttpResponse(HttpStatus.SC_NOT_FOUND));
            return;
        }
        if (!WebSocketHandshake.isWebSocketUpgrade(request)) {
            trigger.submitResponse(new BasicClassicHttpResponse(HttpStatus.SC_UPGRADE_REQUIRED));
            return;
        }
        final WebSocketHandler handler = supplier.get();
        final WebSocketServerConnection connection = getConnection(context);
        if (connection == null) {
            trigger.submitResponse(new BasicClassicHttpResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR));
            return;
        }
        final String key = request.getFirstHeader(WebSocketConstants.SEC_WEBSOCKET_KEY).getValue();
        final String accept = WebSocketHandshake.createAcceptKey(key);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_SWITCHING_PROTOCOLS);
        response.addHeader(HttpHeaders.CONNECTION, HeaderElements.UPGRADE);
        response.addHeader(HttpHeaders.UPGRADE, "websocket");
        response.addHeader(WebSocketConstants.SEC_WEBSOCKET_ACCEPT, accept);
        final WebSocketExtensionNegotiation negotiation = extensionRegistry.negotiate(
                WebSocketExtensions.parse(request.getFirstHeader(WebSocketConstants.SEC_WEBSOCKET_EXTENSIONS)),
                true);
        final String extensionsHeader = negotiation.formatResponseHeader();
        if (extensionsHeader != null) {
            response.addHeader(WebSocketConstants.SEC_WEBSOCKET_EXTENSIONS, extensionsHeader);
        }
        final List<String> offeredProtocols = WebSocketHandshake.parseSubprotocols(
                request.getFirstHeader(WebSocketConstants.SEC_WEBSOCKET_PROTOCOL));
        final String protocol = handler.selectSubprotocol(offeredProtocols);
        if (protocol != null) {
            response.addHeader(WebSocketConstants.SEC_WEBSOCKET_PROTOCOL, protocol);
        }
        trigger.submitResponse(response);
        final InputStream inputStream = connection.getSocketInputStream();
        final OutputStream outputStream = connection.getSocketOutputStream();
        if (inputStream == null || outputStream == null) {
            connection.close();
            return;
        }
        final WebSocketSession session = new WebSocketSession(config, inputStream, outputStream,
                connection.getRemoteAddress(), connection.getLocalAddress(), negotiation.getExtensions());
        try {
            handler.onOpen(session);
            new WebSocketServerProcessor(session, handler, config.getMaxMessageSize()).process();
        } catch (final WebSocketProtocolException ex) {
            handler.onError(session, ex);
            session.close(ex.closeCode, ex.getMessage());
        } catch (final WebSocketException ex) {
            handler.onError(session, ex);
            session.close(WebSocketCloseStatus.PROTOCOL_ERROR.getCode(), ex.getMessage());
        } catch (final Exception ex) {
            handler.onError(session, ex);
            session.close(WebSocketCloseStatus.INTERNAL_ERROR.getCode(), "WebSocket error");
        } finally {
            connection.close();
        }
    }

    private static WebSocketServerConnection getConnection(final HttpContext context) {
        if (context == null) {
            return null;
        }
        final Object conn = context.getAttribute(WebSocketContextKeys.CONNECTION);
        return conn instanceof WebSocketServerConnection ? (WebSocketServerConnection) conn : null;
    }
}
