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
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.apache.hc.core5.annotation.Internal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for compression formats provided by Apache Commons Compress.
 *
 * @since 5.6
 */
@Internal
class CommonsCompressHandler implements CompressionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(CommonsCompressHandler.class);

    private static final String FACTORY_CLASS_NAME = "org.apache.commons.compress.compressors.CompressorStreamFactory";
    private static final String DEFLATE_PARAMS_CLASS_NAME = "org.apache.commons.compress.compressors.deflate.DeflateParameters";
    private static final String DEFLATE_INPUT_STREAM_CLASS_NAME = "org.apache.commons.compress.compressors.deflate.DeflateCompressorInputStream";

    private static final String GET_INPUT_NAMES_METHOD = "getInputStreamCompressorNames";
    private static final String GET_OUTPUT_NAMES_METHOD = "getOutputStreamCompressorNames";
    private static final String CREATE_INPUT_METHOD = "createCompressorInputStream";
    private static final String CREATE_OUTPUT_METHOD = "createCompressorOutputStream";

    private final boolean commonsCompressAvailable;
    private final Object compressorStreamFactory;
    private final Class<?> deflateParamsClass;
    private final Class<?> deflateInputStreamClass;
    private final Method createCompressorInputStreamMethod;
    private final Method createCompressorOutputStreamMethod;

    private Set<String> inputFormatsCache;
    private Set<String> outputFormatsCache;

    public CommonsCompressHandler() {
        final CommonsCompressInitResult init = initializeCommonsCompress();
        this.commonsCompressAvailable = init.available;
        this.compressorStreamFactory = init.factory;
        this.deflateParamsClass = init.deflateParams;
        this.deflateInputStreamClass = init.deflateInputStream;
        this.createCompressorInputStreamMethod = init.inputStreamMethod;
        this.createCompressorOutputStreamMethod = init.outputStreamMethod;
    }

    private static class CommonsCompressInitResult {
        final boolean available;
        final Object factory;
        final Class<?> deflateParams;
        final Class<?> deflateInputStream;
        final Method inputStreamMethod;
        final Method outputStreamMethod;

        CommonsCompressInitResult(final boolean available,
                                  final Object factory,
                                  final Class<?> deflateParams,
                                  final Class<?> deflateInputStream,
                                  final Method inputStreamMethod,
                                  final Method outputStreamMethod) {
            this.available = available;
            this.factory = factory;
            this.deflateParams = deflateParams;
            this.deflateInputStream = deflateInputStream;
            this.inputStreamMethod = inputStreamMethod;
            this.outputStreamMethod = outputStreamMethod;
        }
    }

    private CommonsCompressInitResult initializeCommonsCompress() {
        Object factory = null;
        Class<?> deflateParams = null;
        Class<?> deflateInputStream = null;
        Method inputStreamMethod = null;
        Method outputStreamMethod = null;
        boolean available = false;

        try {
            final Class<?> factoryClass = Class.forName(FACTORY_CLASS_NAME);
            factory = factoryClass.getDeclaredConstructor().newInstance();

            deflateParams = Class.forName(DEFLATE_PARAMS_CLASS_NAME);
            deflateInputStream = Class.forName(DEFLATE_INPUT_STREAM_CLASS_NAME);

            inputStreamMethod = factoryClass.getMethod(
                    CREATE_INPUT_METHOD,
                    String.class,
                    InputStream.class,
                    boolean.class);
            outputStreamMethod = factoryClass.getMethod(
                    CREATE_OUTPUT_METHOD,
                    String.class,
                    OutputStream.class);

            available = true;
        } catch (final ReflectiveOperationException ignored) {
        }

        return new CommonsCompressInitResult(
                available,
                factory,
                deflateParams,
                deflateInputStream,
                inputStreamMethod,
                outputStreamMethod
        );
    }

    @Override
    public Set<String> getSupportedInputFormats() {
        if (inputFormatsCache == null) {
            final Set<String> formats = new HashSet<>();
            formats.add("deflate");
            formats.add("gzip");
            formats.add("x-gzip");
            formats.add("gz");

            if (commonsCompressAvailable) {
                try {
                    @SuppressWarnings("unchecked") final Set<?> names = (Set<?>) compressorStreamFactory.getClass()
                            .getMethod(GET_INPUT_NAMES_METHOD)
                            .invoke(compressorStreamFactory);
                    names.stream()
                            .map(name -> ((String) name).toLowerCase(Locale.ROOT))
                            .forEach(formats::add);
                } catch (final ReflectiveOperationException ignored) {
                }
            }
            inputFormatsCache = Collections.unmodifiableSet(formats);
        }
        return inputFormatsCache;
    }

    @Override
    public Set<String> getSupportedOutputFormats() {
        if (outputFormatsCache == null) {
            final Set<String> formats = new HashSet<>();
            // Always support raw deflate
            formats.add("deflate");

            if (commonsCompressAvailable) {
                try {
                    @SuppressWarnings("unchecked") final Set<?> names = (Set<?>) compressorStreamFactory.getClass()
                            .getMethod(GET_OUTPUT_NAMES_METHOD)
                            .invoke(compressorStreamFactory);
                    names.stream()
                            .map(name -> ((String) name).toLowerCase(Locale.ROOT))
                            .forEach(formats::add);
                } catch (final ReflectiveOperationException ignored) {
                }
            }
            outputFormatsCache = Collections.unmodifiableSet(formats);
        }
        return outputFormatsCache;
    }

    @Override
    public InputStream createDecompressorStream(
            final String name,
            final InputStream inputStream,
            final boolean noWrap) throws IOException {

        if ("deflate".equals(name)) {
            if (commonsCompressAvailable) {
                try {
                    final Object params = deflateParamsClass.getDeclaredConstructor().newInstance();
                    deflateParamsClass.getMethod("setWithZlibHeader", boolean.class)
                            .invoke(params, noWrap);

                    return (InputStream) deflateInputStreamClass
                            .getDeclaredConstructor(InputStream.class, deflateParamsClass)
                            .newInstance(inputStream, params);
                } catch (final Exception e) {
                    throw new IOException("Failed to create decompressor for format: " + name, e);
                }
            }
        }

        if (commonsCompressAvailable) {
            try {
                return (InputStream) createCompressorInputStreamMethod
                        .invoke(compressorStreamFactory, name, inputStream, true);
            } catch (final Exception e) {
                throw new IOException("Failed to create decompressor for format: " + name, e);
            }
        }

        return null;
    }

    @Override
    public OutputStream createCompressorStream(
            final String name,
            final OutputStream outputStream) throws IOException {

        if ("deflate".equals(name)) {
            if (commonsCompressAvailable) {
                try {
                    return (OutputStream) createCompressorOutputStreamMethod
                            .invoke(compressorStreamFactory, name, outputStream);
                } catch (final Exception e) {
                    throw new IOException("Failed to create compressor for format: " + name, e);
                }
            }
        }
        if (commonsCompressAvailable) {
            try {
                return (OutputStream) createCompressorOutputStreamMethod
                        .invoke(compressorStreamFactory, name, outputStream);
            } catch (final Exception e) {
                throw new IOException("Failed to create compressor for format: " + name, e);
            }
        }

        return null;
    }
}
