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
import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;

/**
 * A factory for creating InputStream instances, utilizing Apache Commons Compress.
 * This class is compiled with Commons Compress as an optional dependency, loading
 * only when the library is present at runtime, avoiding mandatory inclusion in
 * downstream builds.
 * <p>
 * <p>
 * Some encodings require native helper JARs; runtime availability is checked
 * using a lightweight Class.forName probe to register codecs only when helpers
 * are present.
 *
 * @since 5.6
 */
@Internal
@Contract(threading = ThreadingBehavior.STATELESS)
final class CommonsCompressDecoderFactory implements Decoder {

    /**
     * Map of codings that need extra JARs → the fully‐qualified class we test for
     */
    private static final Map<ContentCoding, String> REQUIRED_CLASS_NAME;

    static {
        final Map<ContentCoding, String> m = new EnumMap<>(ContentCoding.class);
        m.put(ContentCoding.BROTLI, "org.brotli.dec.BrotliInputStream");
        m.put(ContentCoding.ZSTD, "com.github.luben.zstd.ZstdInputStream");
        m.put(ContentCoding.XZ, "org.tukaani.xz.XZInputStream");
        m.put(ContentCoding.LZMA, "org.tukaani.xz.XZInputStream");
        REQUIRED_CLASS_NAME = Collections.unmodifiableMap(m);
    }

    private final String encoding;          // lower-case IANA token
    private final CompressorStreamFactory factory = new CompressorStreamFactory();

    CommonsCompressDecoderFactory(final String encoding) {
        this.encoding = encoding.toLowerCase(Locale.ROOT);
    }

    public String getContentEncoding() {
        return encoding;
    }

    /**
     * Lazily wraps the source stream in a Commons-Compress decoder.
     */
    @Override
    public InputStream wrap(final InputStream source) throws IOException {
        try {
            return factory.createCompressorInputStream(encoding, source);
        } catch (final CompressorException | LinkageError ex) {
            throw new IOException("Unable to decode Content-Encoding '" + encoding + '\'', ex);
        }
    }

    /**
     * Tests that required helper classes are present for a coding token.
     */
    static boolean runtimeAvailable(final String token) {
        final ContentCoding coding = ContentCoding.fromToken(token);
        if (coding == null) {
            return true;
        }
        final String helper = REQUIRED_CLASS_NAME.get(coding);
        if (helper == null) {
            // no extra JAR needed
            return true;
        }
        try {
            Class.forName(helper, false,
                    CommonsCompressDecoderFactory.class.getClassLoader());
            return true;
        } catch (final ClassNotFoundException | LinkageError ex) {
            return false;
        }
    }
}