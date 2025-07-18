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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import org.apache.hc.client5.http.entity.compress.ContentCodecRegistry;
import org.apache.hc.client5.http.entity.compress.ContentCoding;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;

/**
 * An {@link AsyncEntityConsumer} implementation that consumes and decompresses
 * an HTTP response body to produce a {@link String}, based on the value of the
 * {@code Content-Encoding} header. Supports all built-in and Commons Compress
 * codecs registered in {@link ContentCodecRegistry}.
 *
 * <p>
 * This class buffers the entire response entity content into memory before
 * applying decompression. Not suitable for large payloads.
 * </p>
 *
 * @since 5.6
 */
public class DecompressingStringAsyncEntityConsumer implements AsyncEntityConsumer<String> {

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private FutureCallback<String> callback;
    private List<Header> trailers;
    private String result;
    private String encoding;
    private ContentType contentType;

    @Override
    public void streamStart(final EntityDetails entityDetails, final FutureCallback<String> resultCallback) {
        this.callback = resultCallback;
        this.trailers = new ArrayList<>();
        this.encoding = entityDetails.getContentEncoding();
        try {
            this.contentType = ContentType.parse(entityDetails.getContentType());
        } catch (final Exception ex) {
            this.contentType = ContentType.APPLICATION_OCTET_STREAM; // Fallback
        }
    }


    @Override
    public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {

    }

    @Override
    public void consume(final ByteBuffer src) throws IOException {
        final byte[] bytes = new byte[src.remaining()];
        src.get(bytes);
        buffer.write(bytes);
    }

    @Override
    public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        if (trailers != null) {
            this.trailers.addAll(trailers);
        }
        final byte[] rawBytes = buffer.toByteArray();

        try {
            final ContentCoding coding = ContentCoding.fromToken(encoding);
            final UnaryOperator<HttpEntity> decoderOp = ContentCodecRegistry.decoder(coding);

            if (decoderOp == null) {
                throw new HttpException("Unsupported content coding: " + encoding);
            }

            final ByteArrayEntity entity = new ByteArrayEntity(rawBytes, contentType);
            final HttpEntity decodedEntity = decoderOp.apply(entity);

            try (final InputStream inputStream = decodedEntity.getContent();
                 final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

                final StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }

                result = sb.toString();
            }

            if (callback != null) {
                callback.completed(result);
            }

        } catch (final Exception ex) {
            if (callback != null) {
                callback.failed(ex);
            } else {
                throw new HttpException("Failed to decompress response", ex);
            }
        }
    }

    @Override
    public void failed(final Exception cause) {
        if (callback != null) {
            callback.failed(cause);
        }
    }

    @Override
    public void releaseResources() {
        buffer.reset();
    }

    @Override
    public String getContent() {
        return result;
    }
}
