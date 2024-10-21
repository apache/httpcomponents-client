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

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.compress.compressors.deflate.DeflateCompressorInputStream;
import org.apache.commons.compress.compressors.deflate.DeflateParameters;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.util.Args;

/**
 * A factory class for managing compression and decompression of HTTP entities using different compression formats.
 * <p>
 * This factory uses a cache to optimize access to available input and output stream providers for compression formats.
 * It also allows the use of aliases (e.g., "gzip" and "x-gzip") and automatically formats the compression names
 * to ensure consistency.
 * </p>
 *
 * <p>
 * Supported compression formats include gzip, deflate, and other available formats provided by the
 * {@link CompressorStreamFactory}.
 * </p>
 *
 * <p>
 * This class is thread-safe and uses {@link AtomicReference} to cache the available input and output stream providers.
 * </p>
 *
 * @since 5.5
 */
public class CompressingFactory {

    /**
     * Singleton instance of the factory.
     */
    public static final CompressingFactory INSTANCE = new CompressingFactory();

    private final CompressorStreamFactory compressorStreamFactory = new CompressorStreamFactory();
    private final AtomicReference<Set<String>> inputProvidersCache = new AtomicReference<>();
    private final AtomicReference<Set<String>> outputProvidersCache = new AtomicReference<>();
    private final Map<String, String> formattedNameCache = new ConcurrentHashMap<>();

    /**
     * Returns a set of available input stream compression providers.
     *
     * @return a set of available input stream compression providers in lowercase.
     */
    public Set<String> getAvailableInputProviders() {
        return inputProvidersCache.updateAndGet(existing -> {
            if (existing != null) {
                return existing;
            }
            final Set<String> inputNames = compressorStreamFactory.getInputStreamCompressorNames();
            return inputNames.stream()
                    .map(name -> name.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());
        });
    }

    /**
     * Returns a set of available output stream compression providers.
     *
     * @return a set of available output stream compression providers in lowercase.
     */
    public Set<String> getAvailableOutputProviders() {
        return outputProvidersCache.updateAndGet(existing -> {
            if (existing != null) {
                return existing;
            }
            final Set<String> outputNames = compressorStreamFactory.getOutputStreamCompressorNames();
            return outputNames.stream()
                    .map(name -> name.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());
        });
    }

    /**
     * Maps a provided compression format name or alias to a standard internal key.
     * <p>
     * If the provided name matches a known alias (e.g., "gzip" or "x-gzip"),
     * the method returns the corresponding standard key (e.g., "gz").
     * If no match is found, it returns the original name as-is.
     * </p>
     *
     * @param name the compression format name or alias.
     * @return the corresponding standard key or the original name if no alias is found.
     * @throws IllegalArgumentException if the name is null or empty.
     */
    public String getFormattedName(final String name) {
        Args.notEmpty(name, "name");
        final String lowerCaseName = name.toLowerCase(Locale.ROOT);
        return formattedNameCache.computeIfAbsent(lowerCaseName, key -> {
            if ("gzip".equals(key) || "x-gzip".equals(key)) {
                return "gz";
            } else if ("compress".equals(key)) {
                return "z";
            }
            return key;
        });
    }

    /**
     * Creates a decompressor input stream for the specified format type and decompresses the provided input stream.
     * <p>
     * If the format type is supported, this method returns a new input stream that decompresses data from the original input stream.
     * For "deflate" format, the "noWrap" option controls the inclusion of zlib headers:
     * - If {@code noWrap} is {@code true}, zlib headers and trailers are omitted, resulting in raw deflate data.
     * - If {@code noWrap} is {@code false}, the deflate stream includes standard zlib headers.
     * </p>
     *
     * @param name        the format type to use for decompression.
     * @param inputStream the input stream to be decompressed.
     * @param noWrap      whether to exclude zlib headers and trailers for deflate streams (applicable to "deflate" only).
     * @return a decompressed input stream if the format type is supported; otherwise, the original input stream.
     * @throws CompressorException if an error occurs while creating the decompressor input stream.
     */
    public InputStream getDecompressorInputStream(final String name, final InputStream inputStream, final boolean noWrap) throws CompressorException {
        Args.notNull(inputStream, "InputStream");
        Args.notNull(name, "name");
        final String formattedName = getFormattedName(name);
        return isSupported(formattedName, false)
                ? createDecompressorInputStream(formattedName, inputStream, noWrap)
                : inputStream;
    }

    /**
     * Creates an output stream to compress the provided output stream based on the specified format type.
     *
     * @param name         the format type.
     * @param outputStream the output stream to compress.
     * @return the compressed output stream, or the original output stream if the format is not supported.
     * @throws CompressorException if an error occurs while creating the compressor output stream.
     */
    public OutputStream getCompressorOutputStream(final String name, final OutputStream outputStream) throws CompressorException {
        final String formattedName = getFormattedName(name);
        return isSupported(formattedName, true)
                ? createCompressorOutputStream(formattedName, outputStream)
                : outputStream;
    }

    /**
     * Decompresses the provided HTTP entity based on the specified format type.
     *
     * @param entity          the HTTP entity to decompress.
     * @param contentEncoding the format type.
     * @return a decompressed {@link HttpEntity}, or {@code null} if the format type is unsupported.
     */
    public HttpEntity decompressEntity(final HttpEntity entity, final String contentEncoding) {
        return decompressEntity(entity, contentEncoding, false);
    }

    /**
     * Decompresses the provided HTTP entity using the specified compression format with the option for deflate streams.
     *
     * @param entity          the HTTP entity to decompress.
     * @param contentEncoding the compression format.
     * @param noWrap          if true, disables the zlib header and trailer for deflate streams.
     * @return a decompressed {@link HttpEntity}, or {@code null} if the compression format is unsupported.
     */
    public HttpEntity decompressEntity(final HttpEntity entity, final String contentEncoding, final boolean noWrap) {
        Args.notNull(entity, "Entity");
        Args.notNull(contentEncoding, "Content Encoding");
        if (!isSupported(contentEncoding, false)) {
            return null;
        }
        return new DecompressingEntity(entity, contentEncoding, noWrap);
    }

    /**
     * Compresses the provided HTTP entity based on the specified format type.
     *
     * @param entity          the HTTP entity to compress.
     * @param contentEncoding the format type.
     * @return a compressed {@link HttpEntity}, or {@code null} if the format type is unsupported.
     */
    public HttpEntity compressEntity(final HttpEntity entity, final String contentEncoding) {
        Args.notNull(entity, "Entity");
        Args.notNull(contentEncoding, "Content Encoding");
        if (!isSupported(contentEncoding, true)) {
            return null;
        }
        return new CompressingEntity(entity, contentEncoding);
    }

    /**
     * Creates a decompressor input stream for the specified format type.
     * <p>
     * If the format type is supported, this method returns an input stream that decompresses the original input data.
     * For "deflate" format, the `noWrap` parameter determines whether the stream should include standard zlib headers:
     * - If {@code noWrap} is {@code true}, the stream is created without zlib headers (raw deflate).
     * - If {@code noWrap} is {@code false}, zlib headers are included.
     * </p>
     *
     * @param name        the format type (e.g., "gzip", "deflate") for decompression.
     * @param inputStream the input stream containing compressed data; must not be {@code null}.
     * @param noWrap      only applicable to "deflate" format. If {@code true}, omits zlib headers for a raw deflate stream.
     * @return a decompressed input stream for the specified format, or throws an exception if the format is unsupported.
     * @throws CompressorException if an error occurs while creating the decompressor stream or if the format type is unsupported.
     */
    private InputStream createDecompressorInputStream(final String name, final InputStream inputStream, final boolean noWrap) throws CompressorException {
        if ("deflate".equalsIgnoreCase(name)) {
            final DeflateParameters parameters = new DeflateParameters();
            parameters.setWithZlibHeader(noWrap);
            return new DeflateCompressorInputStream(inputStream, parameters);
        }
        return compressorStreamFactory.createCompressorInputStream(name, inputStream, true);
    }

    /**
     * Creates a compressor output stream for the given format type and output stream.
     *
     * @param name         the format type.
     * @param outputStream the output stream to compress.
     * @return a compressed output stream, or null if an error occurs.
     * @throws CompressorException if an error occurs while creating the compressor output stream.
     */
    private OutputStream createCompressorOutputStream(final String name, final OutputStream outputStream) throws CompressorException {
        return compressorStreamFactory.createCompressorOutputStream(name, outputStream);
    }

    /**
     * Determines if the specified format type is supported for either input (decompression) or output (compression) streams.
     *
     * @param name     the format type.
     * @param isOutput if true, checks if the format type is supported for output; otherwise, checks for input support.
     * @return true if the format type is supported, false otherwise.
     */
    private boolean isSupported(final String name, final boolean isOutput) {
        final Set<String> availableProviders = isOutput ? getAvailableOutputProviders() : getAvailableInputProviders();
        return availableProviders.contains(name);
    }
}

