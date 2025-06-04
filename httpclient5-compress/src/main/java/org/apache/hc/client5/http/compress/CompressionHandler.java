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
import java.util.Set;

/**
 * Interface for handling compression and decompression of data streams.
 * <p>
 * This interface provides a unified way to handle different compression formats
 * in HTTP client operations. Implementations can support various compression
 * algorithms and formats, such as GZIP, DEFLATE, BZIP2, etc.
 * </p>
 * <p>
 * The interface is designed to be extensible, allowing new compression formats
 * to be added without modifying existing code. Each implementation should clearly
 * document which formats it supports and any specific requirements or limitations.
 * </p>
 *
 * @since 5.6
 */
public interface CompressionHandler {

    /**
     * Gets the set of supported input formats for decompression.
     * <p>
     * The returned set contains the names of compression formats that this handler
     * can decompress. Format names should be lowercase and match the standard
     * HTTP content encoding values where applicable.
     * </p>
     *
     * @return An unmodifiable set of supported format names
     * @since 5.6
     */
    Set<String> getSupportedInputFormats();

    /**
     * Gets the set of supported output formats for compression.
     * <p>
     * The returned set contains the names of compression formats that this handler
     * can compress data into. Format names should be lowercase and match the standard
     * HTTP content encoding values where applicable.
     * </p>
     *
     * @return An unmodifiable set of supported format names
     * @since 5.6
     */
    Set<String> getSupportedOutputFormats();

    /**
     * Creates a decompressor stream for the specified compression format.
     * <p>
     * This method wraps the provided input stream with a decompressing stream
     * that handles the specified format. If the format is not supported,
     * the method returns {@code null} to allow chaining of multiple handlers.
     * </p>
     *
     * @param name        The compression format name (case-sensitive)
     * @param inputStream The input stream to decompress
     * @param noWrap     Whether to include format-specific headers and trailers
     *                   (implementation-dependent, typically used for DEFLATE)
     * @return A decompressing input stream, or {@code null} if the format is not supported
     * @since 5.6
     */
    InputStream createDecompressorStream(String name, InputStream inputStream, boolean noWrap) throws IOException;

    /**
     * Creates a compressor stream for the specified compression format.
     * <p>
     * This method wraps the provided output stream with a compressing stream
     * that handles the specified format. If the format is not supported,
     * the method returns {@code null} to allow chaining of multiple handlers.
     * </p>
     *
     * @param name         The compression format name (case-sensitive)
     * @param outputStream The output stream to compress to
     * @return A compressing output stream, or {@code null} if the format is not supported
     * @since 5.6
     */
    OutputStream createCompressorStream(String name, OutputStream outputStream) throws IOException;
}