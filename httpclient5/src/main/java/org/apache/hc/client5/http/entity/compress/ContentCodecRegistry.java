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
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.zip.GZIPInputStream;

import org.apache.hc.client5.http.entity.DeflateCompressingEntity;
import org.apache.hc.client5.http.entity.DeflateInputStream;
import org.apache.hc.client5.http.entity.GzipCompressingEntity;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.io.IOFunction;


/**
 * Registry of encode/decode transformations for HTTP content codings.
 * <p>
 * Entries are wired once at class-load time and exposed via an unmodifiable map.
 * Built-in gzip/deflate are always available. Additional codecs are discovered
 * reflectively:
 * </p>
 * <ul>
 *   <li>Commons Compress codecs, when the library and (if required) its helper JARs
 *   are present.</li>
 *   <li>Decode-only Brotli via brotli4j, when present on the classpath. This does
 *   not affect the advertised {@code Accept-Encoding} unless an encoder is also
 *   registered.</li>
 * </ul>
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
                new Codec(GzipCompressingEntity::new, ent -> new DecompressingEntity(ent, GZIPInputStream::new)));
        m.put(ContentCoding.DEFLATE, new Codec(DeflateCompressingEntity::new, ent -> new DecompressingEntity(ent, DeflateInputStream::new)));

        // 2) Commons-Compress (optional) — reflectively wired
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

                if (CommonsCompressCodecFactory.runtimeAvailable(c)) {
                    m.put(c, new Codec(
                            e -> new CompressingEntity(e, c.token(),
                                    CommonsCompressCodecFactory.encoder(c.token())),
                            ent -> new DecompressingEntity(ent,
                                    CommonsCompressCodecFactory.decoder(c.token()))));
                }
            }

        }
        // 3) Native Brotli fallback (decode-only), no compile-time dep
        if (isPresent("com.aayushatharva.brotli4j.decoder.BrotliInputStream")
                && isPresent("com.aayushatharva.brotli4j.Brotli4jLoader")) {
            m.put(ContentCoding.BROTLI, Codec.decodeOnly(ent ->
                    new DecompressingEntity(ent, brotli4jDecoder())));
        }

        return Collections.unmodifiableMap(m);
    }

    public static HttpEntity wrap(final ContentCoding c, final HttpEntity src) {
        final Codec k = REGISTRY.get(c);
        return k != null && k.encoder != null ? k.encoder.apply(src) : null;
    }

    public static HttpEntity unwrap(final ContentCoding c, final HttpEntity src) {
        final Codec k = REGISTRY.get(c);
        return k != null && k.decoder != null ? k.decoder.apply(src) : null;
    }

    public static UnaryOperator<HttpEntity> decoder(final ContentCoding coding) {
        final Codec c = REGISTRY.get(coding);
        return c != null ? c.decoder : null;
    }

    public static UnaryOperator<HttpEntity> encoder(final ContentCoding coding) {
        final Codec c = REGISTRY.get(coding);
        return c != null ? c.encoder : null;
    }

    static final class Codec {
        final UnaryOperator<HttpEntity> encoder;
        final UnaryOperator<HttpEntity> decoder;

        Codec(final UnaryOperator<HttpEntity> enc, final UnaryOperator<HttpEntity> dec) {
            this.encoder = enc;
            this.decoder = dec;
        }

        static Codec encodeOnly(final UnaryOperator<HttpEntity> e) {
            return new Codec(e, null);
        }

        static Codec decodeOnly(final UnaryOperator<HttpEntity> d) {
            return new Codec(null, d);
        }
    }

    private static boolean isPresent(final String className) {
        try {
            Class.forName(className, false, ContentCodecRegistry.class.getClassLoader());
            return true;
        } catch (final ClassNotFoundException | LinkageError ex) {
            return false;
        }
    }

    private static IOFunction<InputStream, InputStream> brotli4jDecoder() {
        return in -> {
            try {
                final ClassLoader cl = ContentCodecRegistry.class.getClassLoader();
                final Class<?> loader = Class.forName("com.aayushatharva.brotli4j.Brotli4jLoader", false, cl);
                loader.getMethod("ensureAvailability").invoke(null);
                final Class<?> cls = Class.forName("com.aayushatharva.brotli4j.decoder.BrotliInputStream", false, cl);
                final Constructor<?> ctor = cls.getConstructor(InputStream.class);
                return (InputStream) ctor.newInstance(in);
            } catch (final ReflectiveOperationException | LinkageError e) {
                throw new IOException("Unable to decode brotli (brotli4j)", e);
            }
        };
    }

    private ContentCodecRegistry() {
    }
}