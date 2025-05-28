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
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.hc.client5.http.entity.DeflateInputStream;
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
 * commons-compress library if available.
 * </p>
 *
 * <p>
 * This class is thread-safe. The provider caches are lazily initialized and effectively immutable after creation.
 * </p>
 *
 * @since 5.5
 */
public class CompressingFactory {

    /**
     * Singleton instance of the factory.
     */
    public static final CompressingFactory INSTANCE = new CompressingFactory();

    private Set<String> inputProvidersCache;
    private Set<String> outputProvidersCache;
    private final Map<String, String> formattedNameCache = new ConcurrentHashMap<>();
    private final boolean commonsCompressAvailable;
    private final Object compressorStreamFactory;

    public CompressingFactory() {
        boolean available = true;
        Object factory = null;
        try {
            final Class<?> factoryClass = Class.forName("org.apache.commons.compress.compressors.CompressorStreamFactory");
            factory = factoryClass.getDeclaredConstructor().newInstance();
        } catch (final ReflectiveOperationException e) {
            available = false;
        }
        this.commonsCompressAvailable = available;
        this.compressorStreamFactory = factory;
    }

    /**
     * Returns a set of available input stream compression providers.
     *
     * @return a set of available input stream compression providers in lowercase.
     */
    public Set<String> getAvailableInputProviders() {
        if (inputProvidersCache == null) {
            final Set<String> inputNames = new HashSet<>();
            // Always add built-in compression formats with correct names
            inputNames.add("gz");
            inputNames.add("deflate");
            if (commonsCompressAvailable) {
                try {
                    // Add commons-compress formats if available
                    final Set<?> names = (Set<?>) compressorStreamFactory.getClass()
                            .getMethod("getInputStreamCompressorNames")
                            .invoke(compressorStreamFactory);
                    names.stream()
                            .map(name -> ((String) name).toLowerCase(Locale.ROOT))
                            .forEach(inputNames::add);
                } catch (final ReflectiveOperationException e) {
                    // Ignore reflection errors
                }
            }
            inputProvidersCache = inputNames;
        }
        return inputProvidersCache;
    }

    /**
     * Returns a set of available output stream compression providers.
     *
     * @return a set of available output stream compression providers in lowercase.
     */
    public Set<String> getAvailableOutputProviders() {
        if (outputProvidersCache == null) {
            final Set<String> outputNames = new HashSet<>();
            // Always add built-in compression formats with correct names
            outputNames.add("gz");
            outputNames.add("deflate");
            if (commonsCompressAvailable) {
                try {
                    // Add commons-compress formats if available
                    final Set<?> names = (Set<?>) compressorStreamFactory.getClass()
                            .getMethod("getOutputStreamCompressorNames")
                            .invoke(compressorStreamFactory);
                    names.stream()
                            .map(name -> ((String) name).toLowerCase(Locale.ROOT))
                            .forEach(outputNames::add);
                } catch (final ReflectiveOperationException e) {
                    // Ignore reflection errors
                }
            }
            outputProvidersCache = outputNames;
        }
        return outputProvidersCache;
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
     * @throws IOException if an error occurs while creating the decompressor input stream.
     */
    public InputStream getDecompressorInputStream(final String name, final InputStream inputStream, final boolean noWrap) throws IOException {
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
     * @param name         the format type to use for compression.
     * @param outputStream the output stream to be compressed.
     * @return a compressed output stream if the format type is supported; otherwise, the original output stream.
     * @throws IOException if an error occurs while creating the compressor output stream.
     */
    public OutputStream getCompressorOutputStream(final String name, final OutputStream outputStream) throws IOException {
        Args.notNull(outputStream, "OutputStream");
        Args.notNull(name, "name");
        final String formattedName = getFormattedName(name);
        return isSupported(formattedName, true)
                ? createCompressorOutputStream(formattedName, outputStream)
                : outputStream;
    }

    /**
     * Decompresses an HTTP entity using the specified content encoding.
     *
     * @param entity         the HTTP entity to decompress.
     * @param contentEncoding the content encoding to use for decompression.
     * @return a decompressed HTTP entity if the content encoding is supported; otherwise, the original entity.
     */
    public HttpEntity decompressEntity(final HttpEntity entity, final String contentEncoding) {
        return decompressEntity(entity, contentEncoding, false);
    }

    /**
     * Decompresses an HTTP entity using the specified content encoding and noWrap option.
     *
     * @param entity         the HTTP entity to decompress.
     * @param contentEncoding the content encoding to use for decompression.
     * @param noWrap         whether to exclude zlib headers and trailers for deflate streams.
     * @return a decompressed HTTP entity if the content encoding is supported; otherwise, the original entity.
     */
    public HttpEntity decompressEntity(final HttpEntity entity, final String contentEncoding, final boolean noWrap) {
        Args.notNull(entity, "HttpEntity");
        if (contentEncoding == null) {
            return entity;
        }
        final String formattedName = getFormattedName(contentEncoding);
        return isSupported(formattedName, false)
                ? new DecompressingEntity(entity, formattedName, noWrap)
                : entity;
    }

    /**
     * Compresses an HTTP entity using the specified content encoding.
     *
     * @param entity         the HTTP entity to compress.
     * @param contentEncoding the content encoding to use for compression.
     * @return a compressed HTTP entity if the content encoding is supported; otherwise, the original entity.
     */
    public HttpEntity compressEntity(final HttpEntity entity, final String contentEncoding) {
        Args.notNull(entity, "HttpEntity");
        if (contentEncoding == null) {
            return entity;
        }
        final String formattedName = getFormattedName(contentEncoding);
        return isSupported(formattedName, true)
                ? new CompressingEntity(entity, formattedName)
                : entity;
    }

    private InputStream createDecompressorInputStream(final String name, final InputStream inputStream, final boolean noWrap) throws IOException {
        if ("gz".equals(name)) {
            return new GZIPInputStream(inputStream);
        } else if ("deflate".equals(name)) {
            if (commonsCompressAvailable) {
                try {
                    final Class<?> paramsClass = Class.forName("org.apache.commons.compress.compressors.deflate.DeflateParameters");
                    final Object params = paramsClass.getDeclaredConstructor().newInstance();
                    paramsClass.getMethod("setWithZlibHeader", boolean.class).invoke(params, noWrap);

                    final Class<?> inputStreamClass = Class.forName("org.apache.commons.compress.compressors.deflate.DeflateCompressorInputStream");
                    return (InputStream) inputStreamClass.getDeclaredConstructor(InputStream.class, paramsClass)
                            .newInstance(inputStream, params);
                } catch (final ReflectiveOperationException e) {
                    // Fallback to built-in implementation
                    return new DeflateInputStream(inputStream);
                }
            } else {
                return new DeflateInputStream(inputStream);
            }
        } else if (commonsCompressAvailable) {
            try {
                return (InputStream) compressorStreamFactory.getClass()
                        .getMethod("createCompressorInputStream", String.class, InputStream.class)
                        .invoke(compressorStreamFactory, name, inputStream);
            } catch (final ReflectiveOperationException e) {
                throw new IOException("Failed to create compressor input stream", e);
            }
        }
        return inputStream;
    }

    private OutputStream createCompressorOutputStream(final String name, final OutputStream outputStream) throws IOException {
        if ("gz".equals(name)) {
            return new GZIPOutputStream(outputStream);
        } else if ("deflate".equals(name)) {
            if (commonsCompressAvailable) {
                try {
                    return (OutputStream) compressorStreamFactory.getClass()
                            .getMethod("createCompressorOutputStream", String.class, OutputStream.class)
                            .invoke(compressorStreamFactory, name, outputStream);
                } catch (final ReflectiveOperationException e) {
                    // Fallback to built-in implementation
                    return new GZIPOutputStream(outputStream);
                }
            } else {
                return new GZIPOutputStream(outputStream);
            }
        } else if (commonsCompressAvailable) {
            try {
                return (OutputStream) compressorStreamFactory.getClass()
                        .getMethod("createCompressorOutputStream", String.class, OutputStream.class)
                        .invoke(compressorStreamFactory, name, outputStream);
            } catch (final ReflectiveOperationException e) {
                throw new IOException("Failed to create compressor output stream", e);
            }
        }
        return outputStream;
    }

    private boolean isSupported(final String name, final boolean isOutput) {
        final Set<String> providers = isOutput ? getAvailableOutputProviders() : getAvailableInputProviders();
        return providers.contains(name);
    }
}

