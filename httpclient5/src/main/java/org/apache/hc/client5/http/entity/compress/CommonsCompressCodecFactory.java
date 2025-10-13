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
import org.apache.hc.core5.io.IOFunction;

/**
 * Codecs factory for Apache Commons Compress.
 * <p>
 * Use {@link #runtimeAvailable(ContentCoding)} to probe whether a given coding
 * can be provided by the current classpath configuration. Callers can then
 * register codecs conditionally without hard dependencies.
 * </p>
 *
 * @since 5.6
 */
@Internal
@Contract(threading = ThreadingBehavior.STATELESS)
final class CommonsCompressCodecFactory {

    private static final String FACTORY_CLASS =
            "org.apache.commons.compress.compressors.CompressorStreamFactory";

    // CC stream classes
    private static final String CC_BROTLI = "org.apache.commons.compress.compressors.brotli.BrotliCompressorInputStream";
    private static final String CC_ZSTD = "org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream";
    private static final String CC_XZ = "org.apache.commons.compress.compressors.xz.XZCompressorInputStream";
    private static final String CC_LZMA = "org.apache.commons.compress.compressors.lzma.LZMACompressorInputStream";
    private static final String CC_LZ4_F = "org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorInputStream";
    private static final String CC_LZ4_B = "org.apache.commons.compress.compressors.lz4.BlockLZ4CompressorInputStream";
    private static final String CC_BZIP2 = "org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream";
    private static final String CC_PACK200 = "org.apache.commons.compress.compressors.pack200.Pack200CompressorInputStream";
    private static final String CC_DEFLATE64 = "org.apache.commons.compress.compressors.deflate64.Deflate64CompressorInputStream";

    // Helper libs
    private static final String H_BROTLI = "org.brotli.dec.BrotliInputStream";
    private static final String H_ZSTD = "com.github.luben.zstd.ZstdInputStream";
    private static final String H_XZ = "org.tukaani.xz.XZInputStream";

    private CommonsCompressCodecFactory() {
    }

    private static boolean isPresent(final String className) {
        try {
            Class.forName(className, false, CommonsCompressCodecFactory.class.getClassLoader());
            return true;
        } catch (final ClassNotFoundException | LinkageError ex) {
            return false;
        }
    }

    /**
     * Creates a lazy decoder that instantiates the Commons Compress stream.
     */
    static IOFunction<InputStream, InputStream> decoder(final String token) {
        final String enc = token.toLowerCase(Locale.ROOT);
        final CompressorStreamFactory factory = new CompressorStreamFactory();
        return in -> {
            try {
                return factory.createCompressorInputStream(enc, in);
            } catch (final CompressorException | LinkageError ex) {
                throw new IOException("Unable to decode Content-Encoding '" + enc + '\'', ex);
            }
        };
    }

    /**
     * Creates a lazy encoder that instantiates the Commons Compress stream.
     */
    static IOFunction<OutputStream, OutputStream> encoder(final String token) {
        final String enc = token.toLowerCase(Locale.ROOT);
        final CompressorStreamFactory factory = new CompressorStreamFactory();
        return in -> {
            try {
                return factory.createCompressorOutputStream(enc, in);
            } catch (final CompressorException | LinkageError ex) {
                throw new IOException("Unable to decode Content-Encoding '" + enc + '\'', ex);
            }
        };
    }

    /**
     * Best-effort availability probe for optional Commons-Compress codecs.
     * <p>
     * Returns {@code true} only if the CC factory and the codec-specific
     * implementation (and helper classes if required) are present on the
     * classpath. Built-in gzip/deflate are handled elsewhere and are not
     * probed here.
     * </p>
     */
    static boolean runtimeAvailable(final ContentCoding coding) {
        if (coding == null) {
            return false;
        }
        if (!isPresent(FACTORY_CLASS)) {
            return false;
        }
        switch (coding) {
            case BROTLI:
                return isPresent(CC_BROTLI) && isPresent(H_BROTLI);
            case ZSTD:
                return isPresent(CC_ZSTD) && isPresent(H_ZSTD);
            case XZ:
                return isPresent(CC_XZ) && isPresent(H_XZ);
            case LZMA:
                return isPresent(CC_LZMA) && isPresent(H_XZ);
            case LZ4_FRAMED:
                return isPresent(CC_LZ4_F);
            case LZ4_BLOCK:
                return isPresent(CC_LZ4_B);
            case BZIP2:
                return isPresent(CC_BZIP2);
            case PACK200:
                return isPresent(CC_PACK200) || isPresent("java.util.jar.Pack200");
            case DEFLATE64:
                return isPresent(CC_DEFLATE64);
            default:
                return false;
        }
    }
}
