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
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.testing.auth.Authenticator;
import org.apache.hc.client5.testing.auth.BasicAuthTokenExtractor;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncResponseProducer;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.ResponseChannel;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseProducer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.util.Args;

public class AuthenticatingAsyncDecorator implements AsyncServerExchangeHandler {

    private final AsyncServerExchangeHandler exchangeHandler;
    private final Authenticator authenticator;
    private final AtomicReference<AsyncResponseProducer> responseProducerRef;
    private final BasicAuthTokenExtractor authTokenExtractor;

    public AuthenticatingAsyncDecorator(final AsyncServerExchangeHandler exchangeHandler, final Authenticator authenticator) {
        this.exchangeHandler = Args.notNull(exchangeHandler, "Request handler");
        this.authenticator = Args.notNull(authenticator, "Authenticator");
        this.responseProducerRef = new AtomicReference<>(null);
        this.authTokenExtractor = new BasicAuthTokenExtractor();
    }

    protected void customizeUnauthorizedResponse(final HttpResponse unauthorized) {
    }

    @Override
    public void handleRequest(
            final HttpRequest request,
            final EntityDetails entityDetails,
            final ResponseChannel responseChannel,
            final HttpContext context) throws HttpException, IOException {
        final Header h = request.getFirstHeader(HttpHeaders.AUTHORIZATION);
        final String challengeResponse = h != null ? authTokenExtractor.extract(h.getValue()) : null;

        final URIAuthority authority = request.getAuthority();
        final String requestUri = request.getRequestUri();

        final boolean authenticated = authenticator.authenticate(authority, requestUri, challengeResponse);
        final Header expect = request.getFirstHeader(HttpHeaders.EXPECT);
        final boolean expectContinue = expect != null && "100-continue".equalsIgnoreCase(expect.getValue());

        if (authenticated) {
            if (expectContinue) {
                responseChannel.sendInformation(new BasicClassicHttpResponse(HttpStatus.SC_CONTINUE), context);
            }
            exchangeHandler.handleRequest(request, entityDetails, responseChannel, context);
        } else {
            final HttpResponse unauthorized = new BasicHttpResponse(HttpStatus.SC_UNAUTHORIZED);
            final String realm = authenticator.getRealm(authority, requestUri);
            unauthorized.addHeader(HttpHeaders.WWW_AUTHENTICATE, StandardAuthScheme.BASIC + " realm=\"" + realm + "\"");

            customizeUnauthorizedResponse(unauthorized);

            final AsyncResponseProducer responseProducer = new BasicResponseProducer(
                    unauthorized,
                    new BasicAsyncEntityProducer("Unauthorized", ContentType.TEXT_PLAIN));
            responseProducerRef.set(responseProducer);
            responseProducer.sendResponse(responseChannel, context);
        }

    }

    @Override
    public final void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        final AsyncResponseProducer responseProducer = responseProducerRef.get();
        if (responseProducer == null) {
            exchangeHandler.updateCapacity(capacityChannel);
        } else {
            capacityChannel.update(Integer.MAX_VALUE);
        }
    }

    @Override
    public final void consume(final ByteBuffer src) throws IOException {
        final AsyncResponseProducer responseProducer = responseProducerRef.get();
        if (responseProducer == null) {
            exchangeHandler.consume(src);
        }
    }

    @Override
    public final void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        final AsyncResponseProducer responseProducer = responseProducerRef.get();
        if (responseProducer == null) {
            exchangeHandler.streamEnd(trailers);
        }
    }

    @Override
    public final int available() {
        final AsyncResponseProducer responseProducer = responseProducerRef.get();
        if (responseProducer == null) {
            return exchangeHandler.available();
        } else {
            return responseProducer.available();
        }
    }

    @Override
    public final void produce(final DataStreamChannel channel) throws IOException {
        final AsyncResponseProducer responseProducer = responseProducerRef.get();
        if (responseProducer == null) {
            exchangeHandler.produce(channel);
        } else {
            responseProducer.produce(channel);
        }
    }

    @Override
    public final void failed(final Exception cause) {
        try {
            exchangeHandler.failed(cause);
            final AsyncResponseProducer dataProducer = responseProducerRef.getAndSet(null);
            if (dataProducer != null) {
                dataProducer.failed(cause);
            }
        } finally {
            releaseResources();
        }
    }

    @Override
    public final void releaseResources() {
        exchangeHandler.releaseResources();
        final AsyncResponseProducer dataProducer = responseProducerRef.getAndSet(null);
        if (dataProducer != null) {
            dataProducer.releaseResources();
        }
    }

}
