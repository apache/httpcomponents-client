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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.MethodNotSupportedException;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.ResponseChannel;
import org.apache.hc.core5.http.nio.StreamChannel;
import org.apache.hc.core5.http.nio.entity.AbstractBinAsyncEntityProducer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Asserts;

/**
 * A handler that generates random data.
 */
public class AsyncRandomHandler implements AsyncServerExchangeHandler {

    private final AtomicReference<AsyncEntityProducer> entityProducerRef;

    public AsyncRandomHandler() {
        this.entityProducerRef = new AtomicReference<>(null);
    }

    @Override
    public void releaseResources() {
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
        final URI uri;
        try {
            uri = request.getUri();
        } catch (final URISyntaxException ex) {
            throw new ProtocolException(ex.getMessage(), ex);
        }
        final String path = uri.getPath();
        final int slash = path.lastIndexOf('/');
        if (slash != -1) {
            final String payload = path.substring(slash + 1, path.length());
            final long n;
            if (!payload.isEmpty()) {
                try {
                    n = Long.parseLong(payload);
                } catch (final NumberFormatException ex) {
                    throw new ProtocolException("Invalid request path: " + path);
                }
            } else {
                // random length, but make sure at least something is sent
                n = 1 + (int)(Math.random() * 79.0);
            }
            final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_OK);
            final AsyncEntityProducer entityProducer = new RandomBinAsyncEntityProducer(n);
            entityProducerRef.set(entityProducer);
            responseChannel.sendResponse(response, entityProducer, context);
        } else {
            throw new ProtocolException("Invalid request path: " + path);
        }
    }

    @Override
    public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        capacityChannel.update(Integer.MAX_VALUE);
    }

    @Override
    public void consume(final ByteBuffer src) throws IOException {
    }

    @Override
    public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
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

    /**
     * An entity that generates random data.
     */
    public static class RandomBinAsyncEntityProducer extends AbstractBinAsyncEntityProducer {

        /** The range from which to generate random data. */
        private final static byte[] RANGE = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
                .getBytes(StandardCharsets.US_ASCII);

        /** The length of the random data to generate. */
        private final long length;
        private long remaining;
        private final ByteBuffer buffer;

        public RandomBinAsyncEntityProducer(final long len) {
            super(512, ContentType.DEFAULT_TEXT);
            length = len;
            remaining = len;
            buffer = ByteBuffer.allocate(1024);
        }

        @Override
        public void releaseResources() {
            remaining = length;
        }

        @Override
        public boolean isRepeatable() {
            return true;
        }

        @Override
        public long getContentLength() {
            return length;
        }

        @Override
        public int availableData() {
            return Integer.MAX_VALUE;
        }

        @Override
        protected void produceData(final StreamChannel<ByteBuffer> channel) throws IOException {
            final int chunk = Math.min((int) (remaining < Integer.MAX_VALUE ? remaining : Integer.MAX_VALUE), buffer.remaining());
            for (int i = 0; i < chunk; i++) {
                final byte b = RANGE[(int) (Math.random() * RANGE.length)];
                buffer.put(b);
            }
            remaining -= chunk;

            buffer.flip();
            channel.write(buffer);
            buffer.compact();

            if (remaining <= 0 && buffer.position() == 0) {
                channel.endStream();
            }
        }

        @Override
        public void failed(final  Exception cause) {
        }

    }

}
