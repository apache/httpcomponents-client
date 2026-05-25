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
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import jakarta.ws.rs.core.Response;
import org.apache.hc.core5.concurrent.CallbackContribution;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.jackson2.http.JsonNodeEntityConsumer;
import org.apache.hc.core5.util.Args;

final class RestResponseConsumer implements AsyncResponseConsumer<Response> {

    private final ObjectMapper objectMapper;
    private final AtomicReference<AsyncEntityConsumer<?>> entityConsumerRef;

    public RestResponseConsumer(final ObjectMapper objectMapper) {
        this.objectMapper = Args.notNull(objectMapper, "Object mapper");
        this.entityConsumerRef = new AtomicReference<>();
    }

    @Override
    public void informationResponse(final HttpResponse response, final HttpContext context) {
    }

    @Override
    public void consumeResponse(final HttpResponse httpResponse,
                                final EntityDetails entityDetails,
                                final HttpContext context,
                                final FutureCallback<Response> resultCallback) throws HttpException, IOException {
        if (entityDetails == null) {
            resultCallback.completed(new RestClientResponse(
                    objectMapper,
                    httpResponse,
                    null,
                    null,
                    -1));
            return;
        }
        final ContentType contentType = ContentType.parseLenient(entityDetails.getContentType());
        if (contentType == null || ContentType.APPLICATION_JSON.isSameMimeType(contentType)) {
            final AsyncEntityConsumer<JsonNode> entityConsumer = new JsonNodeEntityConsumer(objectMapper.getFactory());
            entityConsumerRef.set(entityConsumer);
            entityConsumer.streamStart(entityDetails, new CallbackContribution<>(resultCallback) {

                @Override
                public void completed(final JsonNode result) {
                    resultCallback.completed(new RestClientResponse(
                            objectMapper,
                            httpResponse,
                            result,
                            contentType,
                            entityDetails.getContentLength()));
                }

            });
        } else {
            final AsyncEntityConsumer<String> entityConsumer = new StringAsyncEntityConsumer();
            entityConsumerRef.set(entityConsumer);
            entityConsumer.streamStart(entityDetails, new CallbackContribution<>(resultCallback) {

                @Override
                public void completed(final String result) {
                    resultCallback.completed(new RestClientResponse(
                            objectMapper,
                            httpResponse,
                            JsonNodeFactory.instance.textNode(result),
                            contentType,
                            entityDetails.getContentLength()));
                }

            });
        }
    }

    @Override
    public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        final AsyncEntityConsumer<?> entityConsumer = entityConsumerRef.get();
        if (entityConsumer != null) {
            entityConsumer.updateCapacity(capacityChannel);
        } else {
            capacityChannel.update(Integer.MAX_VALUE);
        }
    }

    @Override
    public void consume(final ByteBuffer data) throws IOException {
        final AsyncEntityConsumer<?> entityConsumer = entityConsumerRef.get();
        if (entityConsumer != null) {
            entityConsumer.consume(data);
        }
    }

    @Override
    public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        final AsyncEntityConsumer<?> entityConsumer = entityConsumerRef.get();
        if (entityConsumer != null) {
            entityConsumer.streamEnd(trailers);
        }
    }

    public void failed(final Exception cause) {
        final AsyncEntityConsumer<?> entityConsumer = entityConsumerRef.get();
        if (entityConsumer != null) {
            entityConsumer.failed(cause);
        }
        releaseResources();
    }

    @Override
    public void releaseResources() {
        final AsyncEntityConsumer<?> entityConsumer = entityConsumerRef.getAndSet(null);
        if (entityConsumer != null) {
            entityConsumer.releaseResources();
        }
    }

}
