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

package org.apache.hc.client5.testing.async;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.client5.testing.redirect.Redirect;
import org.apache.hc.client5.testing.redirect.RedirectResolver;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.ResponseChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;

public class RedirectingAsyncDecorator implements AsyncServerExchangeHandler {

    private final AsyncServerExchangeHandler exchangeHandler;
    private final RedirectResolver redirectResolver;
    private final AtomicBoolean redirecting;

    public RedirectingAsyncDecorator(final AsyncServerExchangeHandler exchangeHandler,
                                     final RedirectResolver redirectResolver) {
        this.exchangeHandler = Args.notNull(exchangeHandler, "Exchange handler");
        this.redirectResolver = redirectResolver;
        this.redirecting = new AtomicBoolean();
    }

    private Redirect resolveRedirect(final HttpRequest request) throws HttpException {
        try {
            final URI requestURI = request.getUri();
            return redirectResolver != null ? redirectResolver.resolve(requestURI) : null;
        } catch (final URISyntaxException ex) {
            throw new ProtocolException(ex.getMessage(), ex);
        }
    }

    private HttpResponse createRedirectResponse(final Redirect redirect) {
        final HttpResponse response = new BasicHttpResponse(redirect.status);
        if (redirect.location != null) {
            response.addHeader(new BasicHeader(HttpHeaders.LOCATION, redirect.location));
        }
        switch (redirect.connControl) {
            case KEEP_ALIVE:
                response.addHeader(new BasicHeader(HttpHeaders.CONNECTION, HeaderElements.KEEP_ALIVE));
                break;
            case CLOSE:
                response.addHeader(new BasicHeader(HttpHeaders.CONNECTION, HeaderElements.CLOSE));
        }
        return response;
    }

    @Override
    public void handleRequest(final HttpRequest request,
                              final EntityDetails entityDetails,
                              final ResponseChannel responseChannel,
                              final HttpContext context) throws HttpException, IOException {
        final Redirect redirect = resolveRedirect(request);
        if (redirect != null) {
            responseChannel.sendResponse(createRedirectResponse(redirect), null, context);
            redirecting.set(true);
        } else {
            exchangeHandler.handleRequest(request, entityDetails, responseChannel, context);
        }
    }

    @Override
    public final void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        if (!redirecting.get()) {
            exchangeHandler.updateCapacity(capacityChannel);
        } else {
            capacityChannel.update(Integer.MAX_VALUE);
        }
    }

    @Override
    public final void consume(final ByteBuffer src) throws IOException {
        if (!redirecting.get()) {
            exchangeHandler.consume(src);
        }
    }

    @Override
    public final void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        if (!redirecting.get()) {
            exchangeHandler.streamEnd(trailers);
        }
    }

    @Override
    public int available() {
        if (!redirecting.get()) {
            return exchangeHandler.available();
        } else {
            return 0;
        }
    }

    @Override
    public void produce(final DataStreamChannel channel) throws IOException {
        if (!redirecting.get()) {
            exchangeHandler.produce(channel);
        }
    }

    @Override
    public void failed(final Exception cause) {
        if (!redirecting.get()) {
            exchangeHandler.failed(cause);
        }
    }

    @Override
    public void releaseResources() {
        exchangeHandler.releaseResources();
    }

}
