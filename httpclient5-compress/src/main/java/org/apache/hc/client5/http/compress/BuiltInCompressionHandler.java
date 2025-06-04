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

package org.apache.hc.client5.http.compress;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;

import org.apache.hc.core5.annotation.Internal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for compression formats built into the Java standard library.
 * <p>
 * This handler provides support for GZIP and DEFLATE compression formats using Java's
 * built-in compression support. These are the most commonly used HTTP compression
 * formats on the web.
 * </p>
 *
 * @since 5.6
 */
@Internal
class BuiltInCompressionHandler implements CompressionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(BuiltInCompressionHandler.class);


    private static final Set<String> SUPPORTED_FORMATS;

    static {
        final Set<String> formats = new HashSet<>();
        formats.add("gz");
        formats.add("deflate");
        SUPPORTED_FORMATS = Collections.unmodifiableSet(formats);
    }

    /**
     * Gets the set of compression formats supported for decompression.
     * <p>
     * The supported formats are:
     * </p>
     * <ul>
     *   <li>gz (GZIP format)</li>
     *   <li>deflate (DEFLATE format)</li>
     * </ul>
     *
     * @return An unmodifiable set of supported format names
     * @since 5.6
     */
    @Override
    public Set<String> getSupportedInputFormats() {
        return SUPPORTED_FORMATS;
    }

    /**
     * Gets the set of compression formats supported for compression.
     * <p>
     * The supported formats are:
     * </p>
     * <ul>
     *   <li>gz (GZIP format)</li>
     *   <li>deflate (DEFLATE format)</li>
     * </ul>
     *
     * @return An unmodifiable set of supported format names
     * @since 5.6
     */
    @Override
    public Set<String> getSupportedOutputFormats() {
        return SUPPORTED_FORMATS;
    }

    @Override
    public InputStream createDecompressorStream(final String name, final InputStream inputStream, final boolean noWrap) throws IOException {
        try {
            if ("gzip".equals(name) || "x-gzip".equals(name) || "gz".equals(name)) {
                return new GZIPInputStream(inputStream);

            } else if ("deflate".equals(name)) {
                return new DeflateInputStream(inputStream);
            }
        } catch (final Exception e) {
            throw new IOException("Failed to create decompressor for format: " + name, e);
        }
        return null;
    }

    @Override
    public OutputStream createCompressorStream(final String name, final OutputStream outputStream) throws IOException {
        try {
            if ("deflate".equals(name)) {
                return new DeflaterOutputStream(outputStream);
            }
        } catch (final Exception e) {
            throw new IOException("Failed to create compressor for format: " + name, e);
        }
        return null;
    }
}