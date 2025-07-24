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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import java.util.zip.ZipException;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;

/**
 * A DEFLATE-only {@link AsyncEntityConsumer} that decompresses incrementally without buffering the full compressed input.
 * Decompressed output is appended to a StringBuilder chunk-by-chunk. Suitable for modest decompressed payloads.
 *
 * @since 5.6
 */
public class DeflateDecompressingStringAsyncEntityConsumer implements AsyncEntityConsumer<String> {

    private final StringBuilder resultBuilder = new StringBuilder();
    private FutureCallback<String> callback;
    private List<Header> trailers;
    private String result;
    private Inflater inflater;
    private final byte[] decompressBuffer = new byte[8192]; // Fixed-size output buffer for inflate

    @Override
    public void streamStart(final EntityDetails entityDetails, final FutureCallback<String> resultCallback) throws HttpException, IOException {
        this.callback = resultCallback;
        this.trailers = new ArrayList<>();
        final String encoding = entityDetails.getContentEncoding();
        if (!"deflate".equalsIgnoreCase(encoding)) {
            throw new HttpException("Unsupported content coding: " + encoding + ". Only DEFLATE is supported.");
        }
        this.inflater = new Inflater(true); // nowrap: true for raw deflate
    }

    @Override
    public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        capacityChannel.update(Integer.MAX_VALUE); // Always ready for more data
    }

    @Override
    public void consume(final ByteBuffer src) throws IOException {
        final byte[] input = new byte[src.remaining()];
        src.get(input);
        inflater.setInput(input);

        while (!inflater.needsInput()) {
            final int bytesInflated;
            try {
                bytesInflated = inflater.inflate(decompressBuffer);
            } catch (final DataFormatException e) {
                throw new IOException("Decompression error", e);
            }
            if (bytesInflated == 0) {
                if (inflater.finished()) {
                    break;
                } else if (inflater.needsDictionary()) {
                    throw new ZipException("Deflate dictionary needed");
                }
            }
            resultBuilder.append(new String(decompressBuffer, 0, bytesInflated, StandardCharsets.UTF_8));
        }
    }

    @Override
    public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        if (trailers != null) {
            this.trailers.addAll(trailers);
        }
        // Ensure final inflate if any remaining
        while (!inflater.finished()) {
            final int bytesInflated;
            try {
                bytesInflated = inflater.inflate(decompressBuffer);
            } catch (final DataFormatException e) {
                throw new IOException("Decompression error", e);
            }
            if (bytesInflated == 0) {
                break;
            }
            resultBuilder.append(new String(decompressBuffer, 0, bytesInflated, StandardCharsets.UTF_8));
        }
        if (!inflater.finished()) {
            throw new IOException("Incomplete Deflate stream");
        }
        result = resultBuilder.toString();

        if (callback != null) {
            callback.completed(result);
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
        if (inflater != null) {
            inflater.end();
            inflater = null;
        }
        resultBuilder.setLength(0);
    }

    @Override
    public String getContent() {
        return result;
    }
}