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
package org.apache.hc.client5.http.impl.cache;

import java.util.BitSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.BiConsumer;

import org.apache.hc.client5.http.cache.HeaderConstants;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.RequestCacheControl;
import org.apache.hc.client5.http.cache.ResponseCacheControl;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.FormattedHeader;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.message.ParserCursor;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.apache.hc.core5.util.TextUtils;
import org.apache.hc.core5.util.Tokenizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A parser for the HTTP Cache-Control header that can be used to extract information about caching directives.
 * <p>
 * This class is thread-safe and has a singleton instance ({@link #INSTANCE}).
 * </p>
 * <p>
 * The {@link #parseResponse(Iterator)} method takes an HTTP header and returns a {@link ResponseCacheControl} object containing
 * the relevant caching directives. The header can be either a {@link FormattedHeader} object, which contains a
 * pre-parsed {@link CharArrayBuffer}, or a plain {@link Header} object, in which case the value will be parsed and
 * stored in a new {@link CharArrayBuffer}.
 * </p>
 * <p>
 * This parser only supports two directives: "max-age" and "s-maxage". If either of these directives are present in the
 * header, their values will be parsed and stored in the {@link ResponseCacheControl} object. If both directives are
 * present, the value of "s-maxage" takes precedence.
 * </p>
 */
@Internal
@Contract(threading = ThreadingBehavior.IMMUTABLE)
class CacheControlHeaderParser {

    /**
     * The singleton instance of this parser.
     */
    public static final CacheControlHeaderParser INSTANCE = new CacheControlHeaderParser();

    /**
     * The logger for this class.
     */
    private static final Logger LOG = LoggerFactory.getLogger(CacheControlHeaderParser.class);


    private final static char EQUAL_CHAR = '=';

    /**
     * The set of characters that can delimit a token in the header.
     */
    private static final BitSet TOKEN_DELIMS = Tokenizer.INIT_BITSET(EQUAL_CHAR, ',');

    /**
     * The set of characters that can delimit a value in the header.
     */
    private static final BitSet VALUE_DELIMS = Tokenizer.INIT_BITSET(EQUAL_CHAR, ',');

    /**
     * The token parser used to extract values from the header.
     */
    private final Tokenizer tokenParser;

    /**
     * Constructs a new instance of this parser.
     */
    protected CacheControlHeaderParser() {
        super();
        this.tokenParser = Tokenizer.INSTANCE;
    }

    public void parse(final Iterator<Header> headerIterator, final BiConsumer<String, String> consumer) {
        while (headerIterator.hasNext()) {
            final Header header = headerIterator.next();
            final CharArrayBuffer buffer;
            final Tokenizer.Cursor cursor;
            if (header instanceof FormattedHeader) {
                buffer = ((FormattedHeader) header).getBuffer();
                cursor = new Tokenizer.Cursor(((FormattedHeader) header).getValuePos(), buffer.length());
            } else {
                final String s = header.getValue();
                if (s == null) {
                    continue;
                }
                buffer = new CharArrayBuffer(s.length());
                buffer.append(s);
                cursor = new Tokenizer.Cursor(0, buffer.length());
            }

            // Parse the header
            while (!cursor.atEnd()) {
                final String name = tokenParser.parseToken(buffer, cursor, TOKEN_DELIMS);
                String value = null;
                if (!cursor.atEnd()) {
                    final int valueDelim = buffer.charAt(cursor.getPos());
                    cursor.updatePos(cursor.getPos() + 1);
                    if (valueDelim == EQUAL_CHAR) {
                        value = tokenParser.parseValue(buffer, cursor, VALUE_DELIMS);
                        if (!cursor.atEnd()) {
                            cursor.updatePos(cursor.getPos() + 1);
                        }
                    }
                }
                consumer.accept(name, value);
            }
        }
    }

    /**
     * Parses the specified response header and returns a new {@link ResponseCacheControl} instance containing
     * the relevant caching directives.
     *
     * <p>The returned {@link ResponseCacheControl} instance will contain the values for "max-age" and "s-maxage"
     * caching directives parsed from the input header. If the input header does not contain any caching directives
     * or if the directives are malformed, the returned {@link ResponseCacheControl} instance will have default values
     * for "max-age" and "s-maxage" (-1).</p>
     *
     * @param headerIterator the header to parse, cannot be {@code null}
     * @return a new {@link ResponseCacheControl} instance containing the relevant caching directives parsed
     * from the response header
     * @throws IllegalArgumentException if the input header is {@code null}
     */
    public final ResponseCacheControl parseResponse(final Iterator<Header> headerIterator) {
        Args.notNull(headerIterator, "headerIterator");
        final ResponseCacheControl.Builder builder = ResponseCacheControl.builder();
        parse(headerIterator, (name, value) -> {
            if (name.equalsIgnoreCase(HeaderConstants.CACHE_CONTROL_S_MAX_AGE)) {
                builder.setSharedMaxAge(parseSeconds(name, value));
            } else if (name.equalsIgnoreCase(HeaderConstants.CACHE_CONTROL_MAX_AGE)) {
                builder.setMaxAge(parseSeconds(name, value));
            } else if (name.equalsIgnoreCase(HeaderConstants.CACHE_CONTROL_MUST_REVALIDATE)) {
                builder.setMustRevalidate(true);
            } else if (name.equalsIgnoreCase(HeaderConstants.CACHE_CONTROL_NO_CACHE)) {
                builder.setNoCache(true);
                if (value != null) {
                    final Tokenizer.Cursor valCursor = new ParserCursor(0, value.length());
                    final Set<String> noCacheFields = new HashSet<>();
                    while (!valCursor.atEnd()) {
                        final String token = tokenParser.parseToken(value, valCursor, VALUE_DELIMS);
                        if (!TextUtils.isBlank(token)) {
                            noCacheFields.add(token);
                        }
                        if (!valCursor.atEnd()) {
                            valCursor.updatePos(valCursor.getPos() + 1);
                        }
                    }
                    builder.setNoCacheFields(noCacheFields);
                }
            } else if (name.equalsIgnoreCase(HeaderConstants.CACHE_CONTROL_NO_STORE)) {
                builder.setNoStore(true);
            } else if (name.equalsIgnoreCase(HeaderConstants.CACHE_CONTROL_PRIVATE)) {
                builder.setCachePrivate(true);
            } else if (name.equalsIgnoreCase(HeaderConstants.CACHE_CONTROL_PROXY_REVALIDATE)) {
                builder.setProxyRevalidate(true);
            } else if (name.equalsIgnoreCase(HeaderConstants.CACHE_CONTROL_PUBLIC)) {
                builder.setCachePublic(true);
            } else if (name.equalsIgnoreCase(HeaderConstants.CACHE_CONTROL_STALE_WHILE_REVALIDATE)) {
                builder.setStaleWhileRevalidate(parseSeconds(name, value));
            } else if (name.equalsIgnoreCase(HeaderConstants.CACHE_CONTROL_STALE_IF_ERROR)) {
                builder.setStaleIfError(parseSeconds(name, value));
            } else if (name.equalsIgnoreCase(HeaderConstants.CACHE_CONTROL_MUST_UNDERSTAND)) {
                builder.setMustUnderstand(true);
            } else if (name.equalsIgnoreCase(HeaderConstants.CACHE_CONTROL_IMMUTABLE)) {
                builder.setImmutable(true);
            }
        });
        return builder.build();
    }

    public final ResponseCacheControl parse(final HttpResponse response) {
        return parseResponse(response.headerIterator(HttpHeaders.CACHE_CONTROL));
    }

    public final ResponseCacheControl parse(final HttpCacheEntry cacheEntry) {
        return parseResponse(cacheEntry.headerIterator(HttpHeaders.CACHE_CONTROL));
    }

    /**
     * Parses the specified request header and returns a new {@link RequestCacheControl} instance containing
     * the relevant caching directives.
     *
     * @param headerIterator the header to parse, cannot be {@code null}
     * @return a new {@link RequestCacheControl} instance containing the relevant caching directives parsed
     * from the request header
     * @throws IllegalArgumentException if the input header is {@code null}
     */
    public final RequestCacheControl parseRequest(final Iterator<Header> headerIterator) {
        Args.notNull(headerIterator, "headerIterator");
        final RequestCacheControl.Builder builder = RequestCacheControl.builder();
        parse(headerIterator, (name, value) -> {
            if (name.equalsIgnoreCase(HeaderConstants.CACHE_CONTROL_MAX_AGE)) {
                builder.setMaxAge(parseSeconds(name, value));
            } else if (name.equalsIgnoreCase(HeaderConstants.CACHE_CONTROL_MAX_STALE)) {
                builder.setMaxStale(parseSeconds(name, value));
            } else if (name.equalsIgnoreCase(HeaderConstants.CACHE_CONTROL_MIN_FRESH)) {
                builder.setMinFresh(parseSeconds(name, value));
            } else if (name.equalsIgnoreCase(HeaderConstants.CACHE_CONTROL_NO_STORE)) {
                builder.setNoStore(true);
            } else if (name.equalsIgnoreCase(HeaderConstants.CACHE_CONTROL_NO_CACHE)) {
                builder.setNoCache(true);
            } else if (name.equalsIgnoreCase(HeaderConstants.CACHE_CONTROL_ONLY_IF_CACHED)) {
                builder.setOnlyIfCached(true);
            } else if (name.equalsIgnoreCase(HeaderConstants.CACHE_CONTROL_STALE_IF_ERROR)) {
                builder.setStaleIfError(parseSeconds(name, value));
            }
        });
        return builder.build();
    }

    public final RequestCacheControl parse(final HttpRequest request) {
        return parseRequest(request.headerIterator(HttpHeaders.CACHE_CONTROL));
    }

    private static long parseSeconds(final String name, final String value) {
        final long delta = CacheSupport.deltaSeconds(value);
        if (delta == -1 && LOG.isDebugEnabled()) {
            LOG.debug("Directive {} is malformed: {}", name, value);
        }
        return delta;
    }

}


