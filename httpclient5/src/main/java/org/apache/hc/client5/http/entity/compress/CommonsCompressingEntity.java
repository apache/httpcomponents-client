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
import java.util.Locale;

import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.HttpEntityWrapper;
import org.apache.hc.core5.util.Args;

/**
 * Compresses the wrapped entity on-the-fly using Apache&nbsp;Commons Compress.
 *
 * <p>The codec is chosen by its IANA token (for example {@code "br"} or
 * {@code "zstd"}).  The helper JAR must be present at run-time; otherwise
 * {@link #writeTo(OutputStream)} will throw {@link IOException}.</p>
 *
 * @since 5.6
 */
@Internal
@Contract(threading = ThreadingBehavior.STATELESS)
public final class CommonsCompressingEntity extends HttpEntityWrapper {

    private final String coding;                     // lower-case
    private final CompressorStreamFactory factory = new CompressorStreamFactory();

    CommonsCompressingEntity(final HttpEntity src, final String coding) {
        super(src);
        this.coding = coding.toLowerCase(Locale.ROOT);
    }

    @Override
    public String getContentEncoding() {
        return coding;
    }

    @Override
    public long getContentLength() {
        return -1;
    }   // streaming

    @Override
    public boolean isChunked() {
        return true;
    }

    @Override
    public InputStream getContent() {          // Pull-mode is not supported
        throw new UnsupportedOperationException("Compressed entity is write-only");
    }

    @Override
    public void writeTo(final OutputStream out) throws IOException {
        Args.notNull(out, "Output stream");
        try (OutputStream cos = factory.createCompressorOutputStream(coding, out)) {
            super.writeTo(cos);
        } catch (final CompressorException | LinkageError ex) {
            throw new IOException("Unable to compress using coding '" + coding + '\'', ex);
        }
    }
}