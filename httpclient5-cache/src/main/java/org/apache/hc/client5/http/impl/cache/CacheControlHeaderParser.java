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

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.FormattedHeader;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.apache.hc.core5.util.Tokenizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A parser for the HTTP Cache-Control header that can be used to extract information about caching directives.
 * <p>
 * This class is thread-safe and has a singleton instance ({@link #INSTANCE}).
 * </p>
 * <p>
 * The {@link #parse(Header)} method takes an HTTP header and returns a {@link CacheControl} object containing
 * the relevant caching directives. The header can be either a {@link FormattedHeader} object, which contains a
 * pre-parsed {@link CharArrayBuffer}, or a plain {@link Header} object, in which case the value will be parsed and
 * stored in a new {@link CharArrayBuffer}.
 * </p>
 * <p>
 * This parser only supports two directives: "max-age" and "s-maxage". If either of these directives are present in the
 * header, their values will be parsed and stored in the {@link CacheControl} object. If both directives are
 * present, the value of "s-maxage" takes precedence.
 * </p>
 */
@Internal
@Contract(threading = ThreadingBehavior.SAFE)
class CacheControlHeaderParser {

    /**
     * The singleton instance of this parser.
     */
    public static final CacheControlHeaderParser INSTANCE = new CacheControlHeaderParser();

    /**
     * The logger for this class.
     */
    private static final Logger LOG = LoggerFactory.getLogger(CacheControlHeaderParser.class);


    /**
     * The character used to indicate a parameter's value in the Cache-Control header.
     */
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

    /**
     * Parses the specified header and returns a new {@link CacheControl} instance containing the relevant caching
     * <p>
     * directives.
     *
     * <p>The returned {@link CacheControl} instance will contain the values for "max-age" and "s-maxage" caching
     * directives parsed from the input header. If the input header does not contain any caching directives or if the
     * <p>
     * directives are malformed, the returned {@link CacheControl} instance will have default values for "max-age" and
     * <p>
     * "s-maxage" (-1).</p>
     *
     * @param header the header to parse, cannot be {@code null}
     * @return a new {@link CacheControl} instance containing the relevant caching directives parsed from the header
     * @throws IllegalArgumentException if the input header is {@code null}
     */
    public final CacheControl parse(final Header header) {
        Args.notNull(header, "Header");

        long maxAge = -1;
        long sharedMaxAge = -1;

        final CharArrayBuffer buffer;
        final Tokenizer.Cursor cursor;
        if (header instanceof FormattedHeader) {
            buffer = ((FormattedHeader) header).getBuffer();
            cursor = new Tokenizer.Cursor(((FormattedHeader) header).getValuePos(), buffer.length());
        } else {
            final String s = header.getValue();
            if (s == null) {
                return new CacheControl(maxAge, sharedMaxAge);
            }
            buffer = new CharArrayBuffer(s.length());
            buffer.append(s);
            cursor = new Tokenizer.Cursor(0, buffer.length());
        }

        while (!cursor.atEnd()) {
            final String name = tokenParser.parseToken(buffer, cursor, TOKEN_DELIMS);
            if (cursor.atEnd()) {
                return new CacheControl(maxAge, sharedMaxAge);
            }
            final int valueDelim = buffer.charAt(cursor.getPos());
            cursor.updatePos(cursor.getPos() + 1);
            if (valueDelim != EQUAL_CHAR) {
                continue;
            }
            final String value = tokenParser.parseValue(buffer, cursor, VALUE_DELIMS);

            if (!cursor.atEnd()) {
                cursor.updatePos(cursor.getPos() + 1);
            }

            try {
                if (name.equalsIgnoreCase("s-maxage")) {
                    sharedMaxAge = Long.parseLong(value);
                } else if (name.equalsIgnoreCase("max-age")) {
                    maxAge = Long.parseLong(value);
                }
            } catch (final NumberFormatException e) {
                // skip malformed directive
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Header {} was malformed: {}", name, value);
                }
            }
        }
        return new CacheControl(maxAge, sharedMaxAge);
    }
}


