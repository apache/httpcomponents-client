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
package org.apache.hc.client5.http.async.methods;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.UnsupportedCharsetException;
import java.util.List;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.util.Args;

public abstract class AbstractAsyncResponseConsumer<T, E> implements AsyncResponseConsumer<T> {

    private final AsyncEntityConsumer<E> entityConsumer;

    public AbstractAsyncResponseConsumer(final AsyncEntityConsumer<E> entityConsumer) {
        Args.notNull(entityConsumer, "Entity consumer");
        this.entityConsumer = entityConsumer;
    }

    protected abstract T buildResult(HttpResponse response, E entity, ContentType contentType);

    @Override
    public final void consumeResponse(
            final HttpResponse response,
            final EntityDetails entityDetails,
            final FutureCallback<T> resultCallback) throws HttpException, IOException {
        if (entityDetails != null) {
            entityConsumer.streamStart(entityDetails, new FutureCallback<E>() {

                @Override
                public void completed(final E result) {
                    final ContentType contentType;
                    try {
                        contentType = ContentType.parse(entityDetails.getContentType());
                        resultCallback.completed(buildResult(response, result, contentType));
                    } catch (UnsupportedCharsetException ex) {
                        resultCallback.failed(ex);
                    }
                }

                @Override
                public void failed(final Exception ex) {
                    resultCallback.failed(ex);
                }

                @Override
                public void cancelled() {
                    resultCallback.cancelled();
                }

            });
        } else {
            resultCallback.completed(buildResult(response, null, null));
            entityConsumer.releaseResources();
        }

    }

    @Override
    public final void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        entityConsumer.updateCapacity(capacityChannel);
    }

    @Override
    public final int consume(final ByteBuffer src) throws IOException {
        return entityConsumer.consume(src);
    }

    @Override
    public final void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        entityConsumer.streamEnd(trailers);
    }

    @Override
    public final void failed(final Exception cause) {
        releaseResources();
    }

    @Override
    public final void releaseResources() {
        entityConsumer.releaseResources();
    }

}