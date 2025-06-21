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
import java.util.EnumMap;
import java.util.Map;

import org.apache.hc.client5.http.entity.DeflateCompressingEntity;
import org.apache.hc.client5.http.entity.GzipCompressingEntity;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.HttpEntity;

@Internal
@Contract(threading = ThreadingBehavior.STATELESS)
public final class ContentEncoderRegistry {

    /**
     * Map token → factory (immutable, thread-safe).
     */
    private static final Map<ContentCoding, EncoderFactory> REGISTRY = build();

    public static EncoderFactory lookup(final ContentCoding coding) {
        return REGISTRY.get(coding);
    }


    @FunctionalInterface
    public interface EncoderFactory {
        /**
         * Wraps the source entity in its compressing counterpart.
         */
        HttpEntity wrap(HttpEntity src);
    }

    private static Map<ContentCoding, EncoderFactory> build() {
        final Map<ContentCoding, EncoderFactory> m =
                new EnumMap<>(ContentCoding.class);

        /* 1. Built-ins – gzip + deflate use the existing wrappers */
        m.put(ContentCoding.GZIP, GzipCompressingEntity::new);
        m.put(ContentCoding.DEFLATE, DeflateCompressingEntity::new);

        /* 2. Commons-Compress – only if the helper class is present */
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
                    m.put(c, e -> new CommonsCompressingEntity(e, c.token()));
                }
            }
        }
        return Collections.unmodifiableMap(m);
    }

    private ContentEncoderRegistry() {
    } // no-instantiation
}
