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
package org.apache.hc.client5.http.rest;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.hc.core5.concurrent.CallbackContribution;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * Response consumer that routes success and error responses to different entity consumers.
 * Message content of successful responses gets routed to the {@link AsyncEntityConsumer}
 * supplied by the caller. Error responses are consumed as RestContent via
 * {@link RestContentConsumer} and stored in {@link Message#error()}.
 */
final class RestResponseConsumer<T> implements AsyncResponseConsumer<Message<HttpResponse, T>> {

    private final ObjectMapper objectMapper;
    private final Supplier<AsyncEntityConsumer<T>> dataConsumerSupplier;

    private AsyncEntityConsumer<?> entityConsumer;

    RestResponseConsumer(final ObjectMapper objectMapper,
                         final Supplier<AsyncEntityConsumer<T>> dataConsumerSupplier) {
        this.dataConsumerSupplier = dataConsumerSupplier;
        this.objectMapper = objectMapper;
    }

    @Override
    public void consumeResponse(final HttpResponse response,
                                final EntityDetails entityDetails,
                                final HttpContext context,
                                final FutureCallback<Message<HttpResponse, T>> resultCallback)
            throws HttpException, IOException {
        if (entityDetails == null) {
            if (response.getCode() >= HttpStatus.SC_REDIRECTION) {
                resultCallback.completed(Message.error(response, null));
            } else {
                resultCallback.completed(Message.of(response, null));
            }
            return;
        }
        if (response.getCode() >= HttpStatus.SC_REDIRECTION) {
            final RestContentConsumer consumer = new RestContentConsumer(objectMapper);
            this.entityConsumer = consumer;
            consumer.streamStart(entityDetails,
                    new CallbackContribution<>(resultCallback) {

                        @Override
                        public void completed(final RestContent content) {
                            resultCallback.completed(Message.error(response, content));
                        }

                    });
        } else {
            final AsyncEntityConsumer<T> consumer = dataConsumerSupplier.get();
            this.entityConsumer = consumer;
            consumer.streamStart(entityDetails, new CallbackContribution<T>(resultCallback) {

                        @Override
                        public void completed(final T body) {
                            resultCallback.completed(Message.of(response, body));
                        }

                    });
        }
    }

    @Override
    public void informationResponse(final HttpResponse response,
                                    final HttpContext context)
            throws HttpException, IOException {
    }

    @Override
    public void updateCapacity(final CapacityChannel capacityChannel)
            throws IOException {
        if (entityConsumer != null) {
            entityConsumer.updateCapacity(capacityChannel);
        }
    }

    @Override
    public void consume(final ByteBuffer data) throws IOException {
        if (entityConsumer != null) {
            entityConsumer.consume(data);
        }
    }

    @Override
    public void streamEnd(final List<? extends Header> trailers)
            throws HttpException, IOException {
        if (entityConsumer != null) {
            entityConsumer.streamEnd(trailers);
        }
    }

    @Override
    public void failed(final Exception cause) {
        if (entityConsumer != null) {
            entityConsumer.failed(cause);
        }
        releaseResources();
    }

    @Override
    public void releaseResources() {
        if (entityConsumer != null) {
            entityConsumer.releaseResources();
            entityConsumer = null;
        }
    }

}