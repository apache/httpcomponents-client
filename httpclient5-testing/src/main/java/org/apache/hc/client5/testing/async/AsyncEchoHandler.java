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

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.MethodNotSupportedException;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.ResponseChannel;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityProducer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Asserts;

/**
 * A handler that echos the incoming request entity.
 */
public class AsyncEchoHandler implements AsyncServerExchangeHandler {

    private final BasicAsyncEntityConsumer entityConsumer;
    private final AtomicReference<AsyncEntityProducer> entityProducerRef;

    public AsyncEchoHandler() {
        this.entityConsumer = new BasicAsyncEntityConsumer();
        this.entityProducerRef = new AtomicReference<>(null);
    }

    @Override
    public void releaseResources() {
        entityConsumer.releaseResources();
        final AsyncEntityProducer producer = entityProducerRef.getAndSet(null);
        if (producer != null) {
            producer.releaseResources();
        }
    }

    @Override
    public void handleRequest(
            final HttpRequest request,
            final EntityDetails entityDetails,
            final ResponseChannel responseChannel,
            final HttpContext context) throws HttpException, IOException {
        final String method = request.getMethod();
        if (!"GET".equalsIgnoreCase(method) &&
                !"HEAD".equalsIgnoreCase(method) &&
                !"POST".equalsIgnoreCase(method) &&
                !"PUT".equalsIgnoreCase(method)) {
            throw new MethodNotSupportedException(method + " not supported by " + getClass().getName());
        }
        if (entityDetails != null) {

            final ContentType contentType = ContentType.parseLenient(entityDetails.getContentType());
            entityConsumer.streamStart(entityDetails, new FutureCallback<byte[]>() {

                @Override
                public void completed(final byte[] content) {
                    final BasicAsyncEntityProducer entityProducer = new BasicAsyncEntityProducer(content, contentType);
                    entityProducerRef.set(entityProducer);
                    try {
                        responseChannel.sendResponse(new BasicHttpResponse(HttpStatus.SC_OK), entityProducer, context);
                    } catch (final IOException | HttpException ex) {
                        failed(ex);
                    }
                }

                @Override
                public void failed(final Exception ex) {
                    releaseResources();
                }

                @Override
                public void cancelled() {
                    releaseResources();
                }

            });
        } else {
            responseChannel.sendResponse(new BasicHttpResponse(HttpStatus.SC_OK), null, context);
            entityConsumer.releaseResources();
        }
    }

    @Override
    public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        entityConsumer.updateCapacity(capacityChannel);
    }

    @Override
    public void consume(final ByteBuffer src) throws IOException {
        entityConsumer.consume(src);
    }

    @Override
    public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        entityConsumer.streamEnd(trailers);
    }

    @Override
    public int available() {
        final AsyncEntityProducer producer = entityProducerRef.get();
        Asserts.notNull(producer, "Entity producer");
        return producer.available();
    }

    @Override
    public void produce(final DataStreamChannel channel) throws IOException {
        final AsyncEntityProducer producer = entityProducerRef.get();
        Asserts.notNull(producer, "Entity producer");
        producer.produce(channel);
    }

    @Override
    public void failed(final Exception cause) {
        releaseResources();
    }

}
