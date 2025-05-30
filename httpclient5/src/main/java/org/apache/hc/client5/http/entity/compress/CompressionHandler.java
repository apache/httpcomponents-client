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
import java.util.Set;

/**
 * Interface for handling compression and decompression operations.
 *
 * @since 5.5
 */
public interface CompressionHandler {
    /**
     * Gets the set of supported compression formats for input streams.
     *
     * @return Set of supported input formats
     */
    Set<String> getSupportedInputFormats();

    /**
     * Gets the set of supported compression formats for output streams.
     *
     * @return Set of supported output formats
     */
    Set<String> getSupportedOutputFormats();

    /**
     * Creates a decompressor input stream for the specified format.
     *
     * @param name        The compression format name
     * @param inputStream The input stream to decompress
     * @param noWrap      Whether to use nowrap mode (for formats that support it)
     * @return The decompressing input stream, or null if format not supported
     * @throws IOException If an error occurs creating the stream
     */
    InputStream createDecompressorStream(String name, InputStream inputStream, boolean noWrap) throws IOException;

    /**
     * Creates a compressor output stream for the specified format.
     *
     * @param name         The compression format name
     * @param outputStream The output stream to compress to
     * @return The compressing output stream, or null if format not supported
     * @throws IOException If an error occurs creating the stream
     */
    OutputStream createCompressorStream(String name, OutputStream outputStream) throws IOException;
}