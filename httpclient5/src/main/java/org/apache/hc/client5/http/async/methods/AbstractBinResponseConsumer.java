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
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.UnsupportedCharsetException;
import java.util.List;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;

public abstract class AbstractBinResponseConsumer<T> implements AsyncResponseConsumer<T> {

    private static final ByteBuffer EMPTY = ByteBuffer.wrap(new byte[0]);

    private volatile FutureCallback<T> resultCallback;

    protected abstract void start(HttpResponse response, ContentType contentType) throws HttpException, IOException;

    protected abstract int capacity();

    protected abstract void data(ByteBuffer data, boolean endOfStream) throws IOException;

    protected abstract T getResult();

    @Override
    public final void consumeResponse(
            final HttpResponse response,
            final EntityDetails entityDetails,
            final FutureCallback<T> resultCallback) throws HttpException, IOException {
        this.resultCallback = resultCallback;
        if (entityDetails != null) {
            try {
                final ContentType contentType = ContentType.parse(entityDetails.getContentType());
                start(response, contentType);
            } catch (UnsupportedCharsetException ex) {
                throw new UnsupportedEncodingException(ex.getMessage());
            }
        } else {
            start(response, null);
            resultCallback.completed(getResult());
        }

    }

    @Override
    public final void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        capacityChannel.update(capacity());
    }

    @Override
    public final int consume(final ByteBuffer src) throws IOException {
        data(src, false);
        return capacity();
    }

    @Override
    public final void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        data(EMPTY, true);
        resultCallback.completed(getResult());
    }

    @Override
    public void failed(final Exception cause) {
    }

    @Override
    public void releaseResources() {
    }

}