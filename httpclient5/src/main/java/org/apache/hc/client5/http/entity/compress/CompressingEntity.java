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

package org.apache.hc.client5.http.entity.compress;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.HttpEntityWrapper;
import org.apache.hc.core5.io.IOFunction;
import org.apache.hc.core5.util.Args;


/**
 * Streaming wrapper that compresses the enclosed {@link HttpEntity} on write.
 * <p>
 * The actual compressor is supplied as an {@link IOFunction}&lt;OutputStream,OutputStream&gt;
 * and is resolved by the caller (for example via reflective factories). This keeps
 * compression back-ends fully optional and avoids hard classpath dependencies.
 * </p>
 * <p>
 * The entity reports the configured {@code Content-Encoding} token and streams
 * the content; length is unknown ({@code -1}), and the entity is chunked.
 * </p>
 *
 * @since 5.6
 */
public final class CompressingEntity extends HttpEntityWrapper {

    private final IOFunction<OutputStream, OutputStream> encoder;
    private final String coding; // lower-case token for header reporting

    public CompressingEntity(
            final HttpEntity src,
            final String coding,
            final IOFunction<OutputStream, OutputStream> encoder) {
        super(src);
        this.encoder = Args.notNull(encoder, "Stream encoder");
        this.coding = Args.notNull(coding, "Content coding").toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public String getContentEncoding() {
        return coding;
    }

    @Override
    public long getContentLength() {
        return -1; // streaming
    }

    @Override
    public boolean isChunked() {
        return true;
    }

    @Override
    public InputStream getContent() {
        throw new UnsupportedOperationException("Compressed entity is write-only");
    }

    @Override
    public void writeTo(final OutputStream out) throws IOException {
        Args.notNull(out, "Output stream");
        final OutputStream wrapped = encoder.apply(out);
        try {
            super.writeTo(wrapped);
        } finally {
            // Close the wrapped stream to flush trailers/footers if any.
            try {
                wrapped.close();
            } catch (final IOException ignore) {
                // best effort
            }
        }
    }
}