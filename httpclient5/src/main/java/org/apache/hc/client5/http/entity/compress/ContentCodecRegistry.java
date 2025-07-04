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
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.apache.hc.client5.http.entity.DeflateInputStream;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.HttpEntity;
import org.brotli.dec.BrotliInputStream;


/**
 * Run-time catalogue of built-in and Commons-Compress
 * {@linkplain Encoder encoders} / {@linkplain Decoder decoders}.
 *
 * <p>Entries are wired once at class-load time and published through an
 * unmodifiable map, so lookups are lock-free and thread-safe.</p>
 *
 * @since 5.6
 */
@Internal
@Contract(threading = ThreadingBehavior.STATELESS)
public final class ContentCodecRegistry {

    private static final Map<ContentCoding, Codec> REGISTRY = build();

    private static Map<ContentCoding, Codec> build() {
        final Map<ContentCoding, Codec> m = new EnumMap<>(ContentCoding.class);

        m.put(ContentCoding.GZIP,
                new Codec(
                        // encoder
                        org.apache.hc.client5.http.entity.GzipCompressingEntity::new,
                        ent -> new DecompressingEntity(ent, GZIPInputStream::new)));
        m.put(ContentCoding.DEFLATE,
                new Codec(
                        org.apache.hc.client5.http.entity.DeflateCompressingEntity::new,
                        ent -> new DecompressingEntity(ent, DeflateInputStream::new)));

        /* 2. Commons-Compress extras ---------------------------------- */
        if (CommonsCompressSupport.isPresent()) {
            for (final ContentCoding c : Arrays.asList(
                    ContentCoding.BROTLI,
                    ContentCoding.ZSTD,
                    ContentCoding.XZ,
                    ContentCoding.LZMA,
                    ContentCoding.LZ4_FRAMED,
                    ContentCoding.LZ4_BLOCK,
                    ContentCoding.BZIP2,
                    ContentCoding.PACK200,
                    ContentCoding.DEFLATE64)) {

                if (CommonsCompressDecoderFactory.runtimeAvailable(c.token())) {
                    m.put(c, new Codec(
                            e -> new CommonsCompressingEntity(e, c.token()),
                            ent -> new DecompressingEntity(ent,
                                    CommonsCompressDecoderFactory.decoder(c.token()))));
                }
            }
        }

        /* 3. Native Brotli fallback (decode-only) ---------------------- */
        if (!m.containsKey(ContentCoding.BROTLI)
                && CommonsCompressDecoderFactory.runtimeAvailable(ContentCoding.BROTLI.token())) {
            m.put(ContentCoding.BROTLI,
                    Codec.decodeOnly(ent ->
                            new DecompressingEntity(ent, BrotliInputStream::new)));
        }

        return Collections.unmodifiableMap(m);
    }

    public static HttpEntity wrap(final ContentCoding coding, final HttpEntity src) {
        final Codec c = REGISTRY.get(coding);
        return c != null && c.encoder != null ? c.encoder.wrap(src) : null;
    }

    public static HttpEntity unwrap(final ContentCoding coding, final HttpEntity src) throws IOException {
        final Codec c = REGISTRY.get(coding);
        return c != null && c.decoder != null ? c.decoder.wrap(src) : null;
    }

    private ContentCodecRegistry() {
    }

    /**
     * Returns the {@link Decoder} for the given coding, or {@code null}.
     */
    public static Decoder decoder(final ContentCoding coding) {
        final Codec c = REGISTRY.get(coding);
        return c != null ? c.decoder : null;
    }

    /**
     * Returns the {@link Encoder} for the given coding, or {@code null}.
     */
    public static Encoder encoder(final ContentCoding coding) {
        final Codec c = REGISTRY.get(coding);
        return c != null ? c.encoder : null;
    }


    static final class Codec {
        final Encoder encoder;
        final Decoder decoder;

        Codec(final Encoder enc, final Decoder dec) {
            this.encoder = enc;
            this.decoder = dec;
        }

        static Codec encodeOnly(final Encoder e) {
            return new Codec(e, null);
        }

        static Codec decodeOnly(final Decoder d) {
            return new Codec(null, d);
        }
    }

}