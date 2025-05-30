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
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;

import org.apache.hc.client5.http.entity.DeflateInputStream;

/**
 * Handler for compression formats built into the Java standard library.
 *
 * @since 5.5
 */
public class BuiltInCompressionHandler implements CompressionHandler {

    private static final Set<String> SUPPORTED_FORMATS;

    static {
        final Set<String> formats = new HashSet<>();
        formats.add("gz");
        formats.add("deflate");
        SUPPORTED_FORMATS = Collections.unmodifiableSet(formats);
    }

    @Override
    public Set<String> getSupportedInputFormats() {
        return SUPPORTED_FORMATS;
    }

    @Override
    public Set<String> getSupportedOutputFormats() {
        return SUPPORTED_FORMATS;
    }

    @Override
    public InputStream createDecompressorStream(final String name, final InputStream inputStream, final boolean noWrap) throws IOException {
        if ("gzip".equals(name) || "x-gzip".equals(name) || "gz".equals(name)) {
            try {
                return new GZIPInputStream(inputStream);
            } catch (final IOException e) {
                throw new IOException("Failed to create the " + name + " compressor input stream", e);
            }
        } else if ("deflate".equals(name)) {
            return new DeflateInputStream(inputStream);
        }
        return null;
    }

    @Override
    public OutputStream createCompressorStream(final String name, final OutputStream outputStream) throws IOException {
        if ("deflate".equals(name)) {
            try {
                return new DeflaterOutputStream(outputStream);
            } catch (final Exception e) {
                throw new IOException("Failed to create the " + name + " compressor output stream", e);
            }
        }
        return null;
    }
}