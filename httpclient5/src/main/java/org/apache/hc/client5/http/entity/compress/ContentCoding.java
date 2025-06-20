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

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Enumeration of the canonical IANA content-coding tokens supported by HttpClient for
 * HTTP request and response bodies.
 * <p>
 * Each constant corresponds to the standard token used in the {@code Content-Encoding}
 * and {@code Accept-Encoding} headers.  Some codings (e.g. Brotli, Zstandard, XZ/LZMA)
 * may require additional helper libraries at runtime.
 *
 * @since 5.6
 */
public enum ContentCoding {

    /**
     * GZIP compression format.
     */
    GZIP("gzip"),
    /**
     * "deflate" compression format (zlib or raw).
     */
    DEFLATE("deflate"),
    /**
     * Legacy alias for GZIP.
     */
    X_GZIP("x-gzip"),

    // Optional codecs requiring Commons-Compress or native helpers
    /**
     * Brotli compression format.
     */
    BROTLI("br"),
    /**
     * Zstandard compression format.
     */
    ZSTD("zstd"),
    /**
     * XZ compression format.
     */
    XZ("xz"),
    /**
     * LZMA compression format.
     */
    LZMA("lzma"),
    /**
     * Framed LZ4 compression format.
     */
    LZ4_FRAMED("lz4-framed"),
    /**
     * Block LZ4 compression format.
     */
    LZ4_BLOCK("lz4-block"),
    /**
     * BZIP2 compression format.
     */
    BZIP2("bzip2"),
    /**
     * Pack200 compression format.
     */
    PACK200("pack200"),
    /**
     * Deflate64 compression format.
     */
    DEFLATE64("deflate64");

    private static final Map<String, ContentCoding> TOKEN_LOOKUP;
    static {
        final Map<String, ContentCoding> map = new HashMap<>(values().length, 1f);
        for (final ContentCoding contentCoding : values()) {
            map.put(contentCoding.token, contentCoding);
        }
        TOKEN_LOOKUP = Collections.unmodifiableMap(map);
    }

    private final String token;

    ContentCoding(final String token) {
        this.token = token;
    }

    /**
     * Returns the standard IANA token string for this content-coding.
     *
     * @return the lowercase token used in HTTP headers
     */
    public String token() {
        return token;
    }

    /**
     * Lookup an enum by its token (case‐insensitive), or {@code null} if none matches.
     * <p>
     * This method is backed by a static, pre‐populated map so the lookup is O(1)
     * instead of O(n).</p>
     *
     * @param token the content‐coding token to look up
     * @return the matching enum constant, or {@code null} if none
     */
    public static ContentCoding fromToken(final String token) {
        return token != null ? TOKEN_LOOKUP.get(token.toLowerCase(Locale.ROOT)) : null;
    }
}
