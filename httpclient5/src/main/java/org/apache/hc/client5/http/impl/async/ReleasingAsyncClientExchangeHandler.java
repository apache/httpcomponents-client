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
package org.apache.hc.client5.http.impl.async;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.RequestChannel;
import org.apache.hc.core5.http.protocol.HttpContext;

@Internal
final class ReleasingAsyncClientExchangeHandler implements AsyncClientExchangeHandler {

    private final AsyncClientExchangeHandler handler;
    private final Runnable onRelease;
    private final AtomicBoolean released;

    ReleasingAsyncClientExchangeHandler(final AsyncClientExchangeHandler handler, final Runnable onRelease) {
        this.handler = handler;
        this.onRelease = onRelease;
        this.released = new AtomicBoolean(false);
    }

    @Override
    public void produceRequest(final RequestChannel channel, final HttpContext context) throws HttpException, IOException {
        handler.produceRequest(channel, context);
    }

    @Override
    public void consumeResponse(final HttpResponse response, final EntityDetails entityDetails, final HttpContext context)
            throws HttpException, IOException {
        handler.consumeResponse(response, entityDetails, context);
    }

    @Override
    public void consumeInformation(final HttpResponse response, final HttpContext context) throws HttpException, IOException {
        handler.consumeInformation(response, context);
    }

    @Override
    public int available() {
        return handler.available();
    }

    @Override
    public void produce(final DataStreamChannel channel) throws IOException {
        handler.produce(channel);
    }

    @Override
    public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        handler.updateCapacity(capacityChannel);
    }

    @Override
    public void consume(final ByteBuffer src) throws IOException {
        handler.consume(src);
    }

    @Override
    public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        handler.streamEnd(trailers);
    }

    @Override
    public void failed(final Exception cause) {
        handler.failed(cause);
    }

    @Override
    public void cancel() {
        handler.cancel();
    }

    @Override
    public void releaseResources() {
        try {
            handler.releaseResources();
        } finally {
            if (released.compareAndSet(false, true)) {
                onRelease.run();
            }
        }
    }
}
