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

public class DecompressingEntity extends HttpEntityWrapper {

    private static final int BUF_SIZE = 8 * 1024;   // 8 KiB buffer
    private final IOFunction<InputStream, InputStream> decoder;
    private volatile InputStream cached;

    public DecompressingEntity(
            final HttpEntity src,
            final IOFunction<InputStream, InputStream> decoder) {
        super(src);
        this.decoder = Args.notNull(decoder, "Stream decoder");
    }

    /**
     * Returns the cached <em>decoded</em> stream, creating it once if necessary.
     */
    @Override
    public InputStream getContent() throws IOException {
        if (!isStreaming()) {
            return decoder.apply(super.getContent());
        }

        InputStream local = cached;
        if (local == null) {
            if (cached == null) {
                cached = decoder.apply(super.getContent());
            }
            local = cached;
        }
        return local;
    }

    /**
     * Length is unknown after decompression.
     */
    @Override
    public long getContentLength() {
        return -1;
    }

    /**
     * Content is no longer encoded
     */
    @Override
    public String getContentEncoding() {
        return null;
    }

    /**
     * Streams the decoded bytes directly to {@code out}.
     */
    @Override
    public void writeTo(final OutputStream out) throws IOException {
        Args.notNull(out, "Output stream");
        try (InputStream in = getContent()) {
            final byte[] buf = new byte[BUF_SIZE];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
        }
    }
}
