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

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.hc.client5.http.entity.BrotliDecompressingEntity;
import org.apache.hc.client5.http.entity.BrotliInputStreamFactory;
import org.apache.hc.client5.http.entity.DeflateInputStreamFactory;
import org.apache.hc.client5.http.entity.GZIPInputStreamFactory;
import org.apache.hc.client5.http.entity.InputStreamFactory;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;

/**
 * Immutable run-time catalogue of {@link InputStreamFactory} instances
 * capable of <em>decoding</em> HTTP entity bodies.
 *
 * <p>The map is populated once during class initialisation:</p>
 * <ol>
 *     <li>Built-ins: {@code gzip} and {@code deflate} are always present.</li>
 *     <li>If Commons-Compress is on the class-path we register a configurable
 *         list of codecs (br, zstd, xz, …) via
 *         {@link CommonsCompressDecoderFactory} – guarded by a cheap
 *         presence check.</li>
 *     <li>If Commons was absent or could not supply <code>br</code>,
 *         we fall back to the pure native singleton
 *         {@link BrotliInputStreamFactory} (when the <code>org.brotli</code>
 *         decoder JAR is available).</li>
 * </ol>
 *
 * <p>The resulting {@code Map} is wrapped in
 * {@link Collections#unmodifiableMap(Map)} and published through
 * {@link #getRegistry()} for safe, lock-free concurrent reads.</p>
 *
 * @since 5.6
 */
@Internal
@Contract(threading = ThreadingBehavior.STATELESS)
public final class ContentDecoderRegistry {


    private static final Map<ContentCoding, InputStreamFactory> REGISTRY = buildRegistry();


    /**
     * Returns the unmodifiable codec map (key = canonical token, value = factory).
     */
    public static Map<ContentCoding, InputStreamFactory> getRegistry() {
        return REGISTRY;
    }


    private static Map<ContentCoding, InputStreamFactory> buildRegistry() {
        final LinkedHashMap<ContentCoding, InputStreamFactory> m = new LinkedHashMap<>();

        // 1. Built-ins
        register(m, ContentCoding.GZIP, new GZIPInputStreamFactory());
        register(m, ContentCoding.DEFLATE, new DeflateInputStreamFactory());

        // 2. Commons-Compress (optional)
        if (CommonsCompressSupport.isPresent()) {
            for (final ContentCoding coding : Arrays.asList(
                    ContentCoding.BROTLI,     // note: will be skipped until CC ships an encoder
                    ContentCoding.ZSTD,
                    ContentCoding.XZ,
                    ContentCoding.LZMA,
                    ContentCoding.LZ4_FRAMED,
                    ContentCoding.LZ4_BLOCK,
                    ContentCoding.BZIP2,
                    ContentCoding.PACK200,
                    ContentCoding.DEFLATE64)) {
                addCommons(m, coding);
            }
        }

        // 3. Native Brotli fallback if Commons did not register it
        if (!m.containsKey(ContentCoding.BROTLI)
                && BrotliDecompressingEntity.isAvailable()) {
            register(m, ContentCoding.BROTLI, new BrotliInputStreamFactory());
        }

        return Collections.unmodifiableMap(m);
    }

    private static void register(final Map<ContentCoding, InputStreamFactory> map,
                                 final ContentCoding coding,
                                 final InputStreamFactory factory) {
        map.put(coding, factory);
    }

    private static void addCommons(final Map<ContentCoding, InputStreamFactory> map,
                                   final ContentCoding coding) {
        if (CommonsCompressDecoderFactory.runtimeAvailable(coding.token())) {
            register(map, coding, new CommonsCompressDecoderFactory(coding.token()));
        }
    }

    private ContentDecoderRegistry() {
    }
}

