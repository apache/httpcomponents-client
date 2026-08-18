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

package org.apache.hc.client5.http.support;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.client5.http.ProxyStatus;
import org.apache.hc.core5.http.message.ParserCursor;
import org.apache.hc.core5.util.Args;

/**
 * Parser for the {@code Proxy-Status} response field defined by RFC 9209. The field is a
 * Structured Fields (RFC 8941) List whose members are Items: an intermediary identity followed
 * by parameters.
 * <p>
 * Parsing is strict. Input that does not conform to the grammar is rejected with a
 * {@link ParseException}, since RFC 8941 requires a field that fails to parse to be treated as
 * absent rather than partially applied. The parser only decodes the field; it does not interpret
 * or act on the reported values. Parameter values are returned by their structured-field types:
 * {@link String} for tokens and strings, {@link Long} for integers, {@link BigDecimal} for
 * decimals, {@link Boolean} for booleans and {@code byte[]} for byte sequences.
 *
 * @since 5.7
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public final class ProxyStatusSupport {

    private ProxyStatusSupport() {
        // no instances
    }

    /**
     * Parses a {@code Proxy-Status} field value into its ordered list of members.
     *
     * @param value the field value; must not be {@code null}.
     * @return the parsed members, empty when the value contains no members.
     * @throws ParseException if the value does not conform to RFC 9209 / RFC 8941.
     */
    public static List<ProxyStatus> parse(final CharSequence value) throws ParseException {
        Args.notNull(value, "Proxy-Status value");
        return parse(value, new ParserCursor(0, value.length()));
    }

    /**
     * Parses a {@code Proxy-Status} field value into its ordered list of members, starting at the
     * current position of the given cursor. This allows the field to be parsed directly from a
     * {@link org.apache.hc.core5.http.FormattedHeader} buffer without creating an intermediate
     * {@link String}.
     *
     * @param value the buffer holding the field value; must not be {@code null}.
     * @param cursor the cursor positioned at the start of the field value; must not be {@code null}.
     * @return the parsed members, empty when the value contains no members.
     * @throws ParseException if the value does not conform to RFC 9209 / RFC 8941.
     */
    public static List<ProxyStatus> parse(final CharSequence value, final ParserCursor cursor) throws ParseException {
        Args.notNull(value, "Proxy-Status value");
        Args.notNull(cursor, "Parser cursor");
        final List<ProxyStatus> members = new ArrayList<>();
        skipOws(value, cursor);
        if (cursor.atEnd()) {
            return members;
        }
        for (;;) {
            members.add(parseMember(value, cursor));
            skipOws(value, cursor);
            if (cursor.atEnd()) {
                break;
            }
            if (value.charAt(cursor.getPos()) != ',') {
                throw malformed("Malformed Proxy-Status: expected ','", value, cursor.getPos());
            }
            cursor.updatePos(cursor.getPos() + 1);
            skipOws(value, cursor);
            if (cursor.atEnd()) {
                throw malformed("Malformed Proxy-Status: trailing comma", value, cursor.getPos());
            }
        }
        return members;
    }

    private static ProxyStatus parseMember(final CharSequence value, final ParserCursor cursor) throws ParseException {
        final int start = cursor.getPos();
        final Object identity = parseBareItem(value, cursor);
        final String name;
        if (identity instanceof ProxyStatus.Token) {
            name = ((ProxyStatus.Token) identity).getValue();
        } else if (identity instanceof String) {
            name = (String) identity;
        } else {
            throw malformed("Malformed Proxy-Status: intermediary identity must be a token or string", value, start);
        }
        return new ProxyStatus(name, parseParameters(value, cursor));
    }

    private static Map<String, Object> parseParameters(final CharSequence value, final ParserCursor cursor)
            throws ParseException {
        final Map<String, Object> params = new LinkedHashMap<>();
        while (!cursor.atEnd() && value.charAt(cursor.getPos()) == ';') {
            final int paramStart = cursor.getPos();
            cursor.updatePos(cursor.getPos() + 1);
            skipSp(value, cursor);
            final String key = parseKey(value, cursor);
            final Object paramValue;
            if (!cursor.atEnd() && value.charAt(cursor.getPos()) == '=') {
                cursor.updatePos(cursor.getPos() + 1);
                paramValue = parseBareItem(value, cursor);
            } else {
                paramValue = Boolean.TRUE;
            }
            enforceParameterType(key, paramValue, value, paramStart);
            params.put(key, paramValue);
        }
        return params;
    }

    private static void enforceParameterType(final String key, final Object value, final CharSequence text,
            final int errorOffset) throws ParseException {
        switch (key) {
            case "error":
            case "coding":
                requireType(value instanceof ProxyStatus.Token, key, "a Token", text, errorOffset);
                break;
            case "details":
            case "rcode":
            case "status-phrase":
            case "header-name":
            case "trailer-name":
                requireType(value instanceof String, key, "a String", text, errorOffset);
                break;
            case "info-code":
            case "alert-id":
            case "status-code":
            case "header-section-size":
            case "header-size":
            case "body-size":
            case "trailer-section-size":
            case "trailer-size":
                requireType(value instanceof Long, key, "an Integer", text, errorOffset);
                break;
            case "next-hop":
            case "alert-message":
                requireType(value instanceof String || value instanceof ProxyStatus.Token, key,
                        "a String or Token", text, errorOffset);
                break;
            case "next-protocol":
                requireType(value instanceof ProxyStatus.Token || value instanceof byte[], key,
                        "a Token or Byte Sequence", text, errorOffset);
                break;
            case "received-status": {
                requireType(value instanceof Long, key, "an Integer", text, errorOffset);
                final long status = (Long) value;
                if (status < 100 || status > 599) {
                    throw malformed("Malformed Proxy-Status: 'received-status' is not a valid HTTP status code",
                            text, errorOffset);
                }
                break;
            }
            default:
                break;
        }
    }

    private static void requireType(final boolean satisfied, final String key, final String expected,
            final CharSequence text, final int errorOffset) throws ParseException {
        if (!satisfied) {
            throw malformed("Malformed Proxy-Status: '" + key + "' must be " + expected, text, errorOffset);
        }
    }

    private static Object parseBareItem(final CharSequence value, final ParserCursor cursor) throws ParseException {
        if (cursor.atEnd()) {
            throw malformed("Malformed Proxy-Status: expected a value", value, cursor.getPos());
        }
        final char c = value.charAt(cursor.getPos());
        if (c == '"') {
            return parseString(value, cursor);
        }
        if (c == '?') {
            return parseBoolean(value, cursor);
        }
        if (c == ':') {
            return parseByteSequence(value, cursor);
        }
        if (c == '-' || isDigit(c)) {
            return parseNumber(value, cursor);
        }
        if (c == '*' || isAlpha(c)) {
            return parseToken(value, cursor);
        }
        throw malformed("Malformed Proxy-Status: unexpected character", value, cursor.getPos());
    }

    private static String parseString(final CharSequence value, final ParserCursor cursor) throws ParseException {
        cursor.updatePos(cursor.getPos() + 1);
        final StringBuilder sb = new StringBuilder();
        while (!cursor.atEnd()) {
            final char c = value.charAt(cursor.getPos());
            cursor.updatePos(cursor.getPos() + 1);
            if (c == '\\') {
                if (cursor.atEnd()) {
                    throw malformed("Malformed Proxy-Status: truncated escape in string", value, cursor.getPos());
                }
                final char esc = value.charAt(cursor.getPos());
                cursor.updatePos(cursor.getPos() + 1);
                if (esc != '"' && esc != '\\') {
                    throw malformed("Malformed Proxy-Status: invalid escape in string", value, cursor.getPos() - 1);
                }
                sb.append(esc);
            } else if (c == '"') {
                return sb.toString();
            } else if (c < ' ' || c > '~') {
                throw malformed("Malformed Proxy-Status: invalid character in string", value, cursor.getPos() - 1);
            } else {
                sb.append(c);
            }
        }
        throw malformed("Malformed Proxy-Status: unterminated string", value, cursor.getPos());
    }

    private static ProxyStatus.Token parseToken(final CharSequence value, final ParserCursor cursor) {
        final int start = cursor.getPos();
        cursor.updatePos(cursor.getPos() + 1);
        while (!cursor.atEnd() && isTokenChar(value.charAt(cursor.getPos()))) {
            cursor.updatePos(cursor.getPos() + 1);
        }
        return new ProxyStatus.Token(value.subSequence(start, cursor.getPos()).toString());
    }

    private static String parseKey(final CharSequence value, final ParserCursor cursor) throws ParseException {
        if (cursor.atEnd()) {
            throw malformed("Malformed Proxy-Status: expected a parameter name", value, cursor.getPos());
        }
        final char first = value.charAt(cursor.getPos());
        if (first != '*' && !isLcAlpha(first)) {
            throw malformed("Malformed Proxy-Status: invalid parameter name", value, cursor.getPos());
        }
        final int start = cursor.getPos();
        cursor.updatePos(cursor.getPos() + 1);
        while (!cursor.atEnd() && isKeyChar(value.charAt(cursor.getPos()))) {
            cursor.updatePos(cursor.getPos() + 1);
        }
        return value.subSequence(start, cursor.getPos()).toString();
    }

    private static Object parseNumber(final CharSequence value, final ParserCursor cursor) throws ParseException {
        final int start = cursor.getPos();
        if (value.charAt(cursor.getPos()) == '-') {
            cursor.updatePos(cursor.getPos() + 1);
        }
        if (cursor.atEnd() || !isDigit(value.charAt(cursor.getPos()))) {
            throw malformed("Malformed Proxy-Status: invalid number", value, start);
        }
        boolean decimal = false;
        int intDigits = 0;
        int fracDigits = 0;
        while (!cursor.atEnd()) {
            final char c = value.charAt(cursor.getPos());
            if (isDigit(c)) {
                if (decimal) {
                    if (++fracDigits > 3) {
                        throw malformed("Malformed Proxy-Status: too many fractional digits", value, cursor.getPos());
                    }
                } else if (++intDigits > 15) {
                    throw malformed("Malformed Proxy-Status: integer too long", value, cursor.getPos());
                }
                cursor.updatePos(cursor.getPos() + 1);
            } else if (c == '.' && !decimal) {
                if (intDigits > 12) {
                    throw malformed("Malformed Proxy-Status: too many integer digits in decimal", value, cursor.getPos());
                }
                decimal = true;
                cursor.updatePos(cursor.getPos() + 1);
            } else {
                break;
            }
        }
        final String text = value.subSequence(start, cursor.getPos()).toString();
        if (decimal) {
            if (fracDigits == 0) {
                throw malformed("Malformed Proxy-Status: decimal requires a fractional part", value, cursor.getPos());
            }
            return new BigDecimal(text);
        }
        return Long.valueOf(Long.parseLong(text));
    }

    private static Boolean parseBoolean(final CharSequence value, final ParserCursor cursor) throws ParseException {
        cursor.updatePos(cursor.getPos() + 1);
        if (cursor.atEnd()) {
            throw malformed("Malformed Proxy-Status: truncated boolean", value, cursor.getPos());
        }
        final char c = value.charAt(cursor.getPos());
        cursor.updatePos(cursor.getPos() + 1);
        if (c == '1') {
            return Boolean.TRUE;
        }
        if (c == '0') {
            return Boolean.FALSE;
        }
        throw malformed("Malformed Proxy-Status: invalid boolean", value, cursor.getPos() - 1);
    }

    private static byte[] parseByteSequence(final CharSequence value, final ParserCursor cursor) throws ParseException {
        cursor.updatePos(cursor.getPos() + 1);
        final int start = cursor.getPos();
        while (!cursor.atEnd() && value.charAt(cursor.getPos()) != ':') {
            cursor.updatePos(cursor.getPos() + 1);
        }
        if (cursor.atEnd()) {
            throw malformed("Malformed Proxy-Status: unterminated byte sequence", value, start);
        }
        final String encoded = value.subSequence(start, cursor.getPos()).toString();
        cursor.updatePos(cursor.getPos() + 1);
        try {
            return Base64.getDecoder().decode(encoded);
        } catch (final IllegalArgumentException ex) {
            throw malformed("Malformed Proxy-Status: invalid byte sequence", value, start);
        }
    }

    private static void skipOws(final CharSequence value, final ParserCursor cursor) {
        while (!cursor.atEnd()) {
            final char c = value.charAt(cursor.getPos());
            if (c == ' ' || c == '\t') {
                cursor.updatePos(cursor.getPos() + 1);
            } else {
                break;
            }
        }
    }

    private static void skipSp(final CharSequence value, final ParserCursor cursor) {
        while (!cursor.atEnd() && value.charAt(cursor.getPos()) == ' ') {
            cursor.updatePos(cursor.getPos() + 1);
        }
    }

    private static boolean isDigit(final char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isAlpha(final char c) {
        return c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z';
    }

    private static boolean isLcAlpha(final char c) {
        return c >= 'a' && c <= 'z';
    }

    private static boolean isTchar(final char c) {
        return isDigit(c) || isAlpha(c) || "!#$%&'*+-.^_`|~".indexOf(c) >= 0;
    }

    private static boolean isTokenChar(final char c) {
        return isTchar(c) || c == ':' || c == '/';
    }

    private static boolean isKeyChar(final char c) {
        return isLcAlpha(c) || isDigit(c) || c == '_' || c == '-' || c == '.' || c == '*';
    }

    private static ParseException malformed(final String message, final CharSequence value, final int errorOffset) {
        return new ParseException(message, value, 0, value.length(), errorOffset);
    }

}
