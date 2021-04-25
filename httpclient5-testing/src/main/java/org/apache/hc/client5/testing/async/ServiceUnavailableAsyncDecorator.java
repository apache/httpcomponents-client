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

import org.apache.hc.core5.function.Resolver;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.ResponseChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ServiceUnavailableAsyncDecorator implements AsyncServerExchangeHandler {

    private final AsyncServerExchangeHandler exchangeHandler;
    private final Resolver<HttpRequest, TimeValue> serviceAvailabilityResolver;
    private final AtomicBoolean serviceUnavailable;

    public ServiceUnavailableAsyncDecorator(final AsyncServerExchangeHandler exchangeHandler,
                                            final Resolver<HttpRequest, TimeValue> serviceAvailabilityResolver) {
        this.exchangeHandler = Args.notNull(exchangeHandler, "Exchange handler");
        this.serviceAvailabilityResolver = Args.notNull(serviceAvailabilityResolver, "Service availability resolver");
        this.serviceUnavailable = new AtomicBoolean();
    }

    @Override
    public void handleRequest(final HttpRequest request,
                              final EntityDetails entityDetails,
                              final ResponseChannel responseChannel,
                              final HttpContext context) throws HttpException, IOException {
        final TimeValue retryAfter = serviceAvailabilityResolver.resolve(request);
        serviceUnavailable.set(TimeValue.isPositive(retryAfter));
        if (serviceUnavailable.get()) {
            final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_SERVICE_UNAVAILABLE);
            response.addHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter.toSeconds()));
            final ProtocolVersion version = request.getVersion();
            if (version != null && version.compareToVersion(HttpVersion.HTTP_2) < 0) {
                response.addHeader(HttpHeaders.CONNECTION, "Close");
            }
            responseChannel.sendResponse(response, null, context);
        } else {
            exchangeHandler.handleRequest(request, entityDetails, responseChannel, context);
        }
    }

    @Override
    public final void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        if (!serviceUnavailable.get()) {
            exchangeHandler.updateCapacity(capacityChannel);
        } else {
            capacityChannel.update(Integer.MAX_VALUE);
        }
    }

    @Override
    public final void consume(final ByteBuffer src) throws IOException {
        if (!serviceUnavailable.get()) {
            exchangeHandler.consume(src);
        }
    }

    @Override
    public final void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        if (!serviceUnavailable.get()) {
            exchangeHandler.streamEnd(trailers);
        }
    }

    @Override
    public int available() {
        if (!serviceUnavailable.get()) {
            return exchangeHandler.available();
        } else {
            return 0;
        }
    }

    @Override
    public void produce(final DataStreamChannel channel) throws IOException {
        if (!serviceUnavailable.get()) {
            exchangeHandler.produce(channel);
        }
    }

    @Override
    public void failed(final Exception cause) {
        if (!serviceUnavailable.get()) {
            exchangeHandler.failed(cause);
        }
    }

    @Override
    public void releaseResources() {
        exchangeHandler.releaseResources();
    }

}
