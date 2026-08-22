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
package org.apache.hc.client5.http.impl.async;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.util.Args;

/**
 * Parsed form of the {@code Use-As-Dictionary} response header used by Compression Dictionary
 * Transport. The header advertises that a response may serve as a compression dictionary for
 * subsequent requests and is an HTTP Structured Field Dictionary carrying a required {@code match}
 * URL pattern, an optional {@code match-dest} list of request destinations, an optional opaque
 * {@code id} echoed back in {@code Dictionary-ID}, and a {@code type} token naming the dictionary
 * format.
 * <p>
 * Only the {@link #RAW raw} type is understood. An offer of any other type parses successfully but
 * reports {@link #isSupported()} as {@code false}, so an unrecognised format renders the dictionary
 * unusable rather than aborting the exchange.
 */
final class UseAsDictionary {

    /**
     * The single dictionary {@code type} this implementation honours, and the default when the
     * {@code type} member is absent.
     */
    static final String RAW = "raw";

    private final String match;
    private final List<String> matchDest;
    private final String id;
    private final String type;

    /**
     * Creates a parsed dictionary offer. {@code matchDest} is defensively copied and exposed as an
     * unmodifiable list; a {@code null} list becomes empty, a {@code null} {@code id} becomes the
     * empty string, and a {@code null} {@code type} defaults to {@link #RAW}.
     *
     * @param match the URL pattern the dictionary applies to.
     * @param matchDest the request destinations the offer is restricted to, or {@code null} for none.
     * @param id the opaque identifier echoed in {@code Dictionary-ID}, or {@code null} for none.
     * @param type the dictionary format token, or {@code null} for {@link #RAW}.
     */
    UseAsDictionary(
            final String match,
            final List<String> matchDest,
            final String id,
            final String type) {
        this.match = match;
        this.matchDest = matchDest != null
                ? Collections.unmodifiableList(new ArrayList<>(matchDest))
                : Collections.<String>emptyList();
        this.id = id != null ? id : "";
        this.type = type != null ? type : RAW;
    }

    /**
     * Parses a {@code Use-As-Dictionary} header value as an HTTP Structured Field Dictionary.
     * Members other than {@code match}, {@code match-dest}, {@code id} and {@code type} are accepted
     * and ignored, as are member parameters, so unknown extensions do not break parsing. A member
     * whose value has the wrong Structured Field type for its key is rejected. The {@code match}
     * member is mandatory; the others fall back to their defaults when absent.
     *
     * @param value the raw header value; must not be blank.
     * @return the parsed offer.
     * @throws ParseException if the value is malformed against the Structured Field grammar, if
     *   {@code match} is missing or not a non-empty String, if {@code match-dest} is not an inner
     *   list of Strings, if {@code id} is not a String of at most 1024 characters, or if
     *   {@code type} is not a Token.
     */
    static UseAsDictionary parse(final String value) throws ParseException {
        Args.notBlank(value, "Use-As-Dictionary");
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) > 0x7f) {
                throw new ParseException("Structured Field value is not ASCII");
            }
        }
        final Parser parser = new Parser(value);
        String match = null;
        List<String> matchDest = Collections.emptyList();
        String id = "";
        String type = RAW;

        parser.skipSp();
        while (!parser.atEnd()) {
            final String key = parser.parseKey();
            final Value member;
            if (parser.consume('=')) {
                member = parser.parseMemberValue();
            } else {
                member = Value.bool();
                parser.parseParameters();
            }

            if ("match".equals(key)) {
                if (member.kind != Value.STRING || member.stringValue.length() == 0) {
                    throw new ParseException("Invalid Use-As-Dictionary match member");
                }
                match = member.stringValue;
            } else if ("match-dest".equals(key)) {
                if (member.kind != Value.INNER_LIST || !member.stringList) {
                    throw new ParseException("Invalid Use-As-Dictionary match-dest member");
                }
                matchDest = member.listValue;
            } else if ("id".equals(key)) {
                if (member.kind != Value.STRING || member.stringValue.length() > 1024) {
                    throw new ParseException("Invalid Use-As-Dictionary id member");
                }
                id = member.stringValue;
            } else if ("type".equals(key)) {
                if (member.kind != Value.TOKEN) {
                    throw new ParseException("Invalid Use-As-Dictionary type member");
                }
                type = member.stringValue;
            }

            parser.skipOws();
            if (parser.atEnd()) {
                break;
            }
            if (!parser.consume(',')) {
                throw new ParseException("Invalid Use-As-Dictionary dictionary separator");
            }
            parser.skipOws();
            if (parser.atEnd()) {
                throw new ParseException("Trailing comma in Use-As-Dictionary");
            }
        }

        if (match == null) {
            throw new ParseException("Use-As-Dictionary requires a match member");
        }
        return new UseAsDictionary(match, matchDest, id, type);
    }

    /**
     * @return the URL pattern the dictionary applies to.
     */
    String getMatch() {
        return match;
    }

    /**
     * @return the request destinations the offer is restricted to, unmodifiable and empty when
     *   unrestricted.
     */
    List<String> getMatchDest() {
        return matchDest;
    }

    /**
     * @return the opaque identifier to echo in {@code Dictionary-ID}, or the empty string when none
     *   was offered.
     */
    String getId() {
        return id;
    }

    /**
     * @return the dictionary format token, {@link #RAW} when the {@code type} member was absent.
     */
    String getType() {
        return type;
    }

    /**
     * Whether the offered {@code type} is one this client can act on. Only {@link #RAW} is honoured;
     * any other token leaves the offer parseable but unusable.
     *
     * @return {@code true} if the dictionary can be used.
     */
    boolean isSupported() {
        return RAW.equals(type);
    }

    /**
     * A parsed Structured Field member value, reduced to only what the dictionary members care
     * about. Item types with no bearing on {@code Use-As-Dictionary} (numbers, byte sequences,
     * booleans, dates, display strings) are recognised for validation but collapsed to
     * {@link #OTHER}; only Strings, Tokens and inner lists carry their value forward. For an inner
     * list {@link #stringList} records whether every element was a String, which distinguishes a
     * valid {@code match-dest} from one contaminated by non-String items.
     */
    private static final class Value {
        static final int OTHER = 0;
        static final int STRING = 1;
        static final int TOKEN = 2;
        static final int INNER_LIST = 3;

        final int kind;
        final String stringValue;
        final List<String> listValue;
        final boolean stringList;

        Value(final int kind, final String stringValue, final List<String> listValue, final boolean stringList) {
            this.kind = kind;
            this.stringValue = stringValue;
            this.listValue = listValue;
            this.stringList = stringList;
        }

        static Value string(final String value) {
            return new Value(STRING, value, null, false);
        }

        static Value token(final String value) {
            return new Value(TOKEN, value, null, false);
        }

        static Value other() {
            return new Value(OTHER, null, null, false);
        }

        static Value bool() {
            return other();
        }

        static Value innerList(final List<String> values, final boolean stringsOnly) {
            return new Value(INNER_LIST, null,
                    Collections.unmodifiableList(new ArrayList<>(values)), stringsOnly);
        }
    }

    /**
     * A minimal recursive-descent parser for the subset of the HTTP Structured Fields grammar the
     * {@code Use-As-Dictionary} header exercises: a Dictionary of keys mapping to bare items or
     * inner lists, with parameters. It advances a cursor over the input and validates each construct
     * strictly, but only retains the String, Token and inner-list values the caller inspects; every
     * other item type is parsed for well-formedness and discarded. The parser is single-use and not
     * thread-safe.
     */
    private static final class Parser {
        private final String value;
        private int pos;

        Parser(final String value) {
            this.value = value;
        }

        boolean atEnd() {
            return pos >= value.length();
        }

        /**
         * Skips optional whitespace, the space and horizontal-tab run allowed around Dictionary
         * members.
         */
        void skipOws() {
            while (!atEnd()) {
                final char ch = value.charAt(pos);
                if (ch == ' ' || ch == '\t') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        /**
         * Skips SP characters. RFC 9651 permits OWS around top-level Dictionary
         * separators, but only SP before the field value and after a parameter
         * semicolon.
         */
        void skipSp() {
            while (!atEnd() && value.charAt(pos) == ' ') {
                pos++;
            }
        }

        /**
         * Consumes the next character if it matches, reporting whether it did without advancing on a
         * mismatch.
         *
         * @param expected the character to match.
         * @return {@code true} if the character was present and consumed.
         */
        boolean consume(final char expected) {
            if (!atEnd() && value.charAt(pos) == expected) {
                pos++;
                return true;
            }
            return false;
        }

        /**
         * Parses a Structured Field key, the lower-case identifier that names a Dictionary member or
         * a parameter.
         *
         * @return the key.
         * @throws ParseException if no valid key starts at the cursor.
         */
        String parseKey() throws ParseException {
            if (atEnd() || !isKeyStart(value.charAt(pos))) {
                throw new ParseException("Invalid Structured Field dictionary key");
            }
            final int start = pos++;
            while (!atEnd() && isKeyChar(value.charAt(pos))) {
                pos++;
            }
            return value.substring(start, pos);
        }

        /**
         * Parses the value bound to a Dictionary key, which is either an inner list or a bare item
         * followed by its parameters.
         *
         * @return the parsed value.
         * @throws ParseException if the value is malformed.
         */
        Value parseMemberValue() throws ParseException {
            final Value result;
            if (!atEnd() && value.charAt(pos) == '(') {
                result = parseInnerList();
            } else {
                result = parseBareItem();
                parseParameters();
            }
            return result;
        }

        /**
         * Parses a parenthesised inner list. Only String elements are collected; the presence of any
         * non-String element clears the strings-only flag so a {@code match-dest} carrying anything
         * other than Strings can be rejected upstream. Per-item and whole-list parameters are parsed
         * and discarded.
         *
         * @return an {@link Value#INNER_LIST} value.
         * @throws ParseException if the list is unterminated or an element or separator is invalid.
         */
        Value parseInnerList() throws ParseException {
            pos++;
            final List<String> strings = new ArrayList<>();
            boolean stringsOnly = true;
            while (true) {
                while (!atEnd() && value.charAt(pos) == ' ') {
                    pos++;
                }
                if (atEnd()) {
                    throw new ParseException("Unterminated Structured Field inner list");
                }
                if (value.charAt(pos) == ')') {
                    pos++;
                    break;
                }
                final Value item = parseBareItem();
                if (item.kind == Value.STRING) {
                    strings.add(item.stringValue);
                } else {
                    stringsOnly = false;
                }
                parseParameters();
                if (!atEnd() && value.charAt(pos) != ')' && value.charAt(pos) != ' ') {
                    throw new ParseException("Invalid Structured Field inner list separator");
                }
            }
            parseParameters();
            return Value.innerList(strings, stringsOnly);
        }

        /**
         * Parses a single bare item, dispatching on the leading character across the whole item
         * grammar: String, Token, Integer or Decimal, Byte Sequence, Boolean, Date and Display
         * String. Only Strings and Tokens retain their value; the remaining types are validated and
         * reduced to {@link Value#OTHER}.
         *
         * @return the parsed item.
         * @throws ParseException if no valid item starts at the cursor.
         */
        Value parseBareItem() throws ParseException {
            if (atEnd()) {
                throw new ParseException("Missing Structured Field item");
            }
            final char ch = value.charAt(pos);
            if (ch == '"') {
                return Value.string(parseString());
            }
            if (isTokenStart(ch)) {
                return Value.token(parseToken());
            }
            if (ch == '-' || isDigit(ch)) {
                parseNumber();
                return Value.other();
            }
            if (ch == ':') {
                parseByteSequence();
                return Value.other();
            }
            if (ch == '?') {
                parseBoolean();
                return Value.other();
            }
            if (ch == '@') {
                pos++;
                parseInteger();
                return Value.other();
            }
            if (ch == '%') {
                parseDisplayString();
                return Value.other();
            }
            throw new ParseException("Invalid Structured Field bare item");
        }

        /**
         * Parses a quoted String, unescaping the only two permitted escapes, backslash and double
         * quote, and rejecting any character outside printable ASCII.
         *
         * @return the unescaped String content.
         * @throws ParseException if an escape or character is invalid or the String is unterminated.
         */
        String parseString() throws ParseException {
            pos++;
            final StringBuilder result = new StringBuilder();
            while (!atEnd()) {
                final char ch = value.charAt(pos++);
                if (ch == '"') {
                    return result.toString();
                }
                if (ch == '\\') {
                    if (atEnd()) {
                        throw new ParseException("Invalid Structured Field String escape");
                    }
                    final char escaped = value.charAt(pos++);
                    if (escaped != '\\' && escaped != '"') {
                        throw new ParseException("Invalid Structured Field String escape");
                    }
                    result.append(escaped);
                } else {
                    if (ch < 0x20 || ch > 0x7e) {
                        throw new ParseException("Invalid Structured Field String character");
                    }
                    result.append(ch);
                }
            }
            throw new ParseException("Unterminated Structured Field String");
        }

        /**
         * Parses a Token, the unquoted identifier used by the {@code type} member.
         *
         * @return the token text.
         * @throws ParseException if no token character follows the start.
         */
        String parseToken() throws ParseException {
            final int start = pos++;
            while (!atEnd() && isTokenChar(value.charAt(pos))) {
                pos++;
            }
            if (pos == start) {
                throw new ParseException("Invalid Structured Field Token");
            }
            return value.substring(start, pos);
        }

        /**
         * Parses an Integer or Decimal, consuming an optional sign, the integer digits and, for a
         * Decimal, a fractional part of at most three digits. The value is validated but not
         * retained.
         *
         * @throws ParseException if no digits are present or the decimal form is malformed.
         */
        void parseNumber() throws ParseException {
            if (consume('-') && atEnd()) {
                throw new ParseException("Invalid Structured Field number");
            }
            final int start = pos;
            while (!atEnd() && isDigit(value.charAt(pos))) {
                pos++;
            }
            if (pos == start) {
                throw new ParseException("Invalid Structured Field number");
            }
            final int integerDigits = pos - start;
            if (consume('.')) {
                if (integerDigits > 12) {
                    throw new ParseException("Structured Field decimal is out of range");
                }
                final int fraction = pos;
                while (!atEnd() && isDigit(value.charAt(pos)) && pos - fraction < 3) {
                    pos++;
                }
                if (pos == fraction || !atEnd() && isDigit(value.charAt(pos))) {
                    throw new ParseException("Invalid Structured Field decimal");
                }
            } else if (integerDigits > 15) {
                throw new ParseException("Structured Field integer is out of range");
            }
        }

        /**
         * Parses the integer that follows the {@code @} marker of a Date item. The value is
         * validated but not retained.
         *
         * @throws ParseException if no digits are present.
         */
        void parseInteger() throws ParseException {
            if (consume('-') && atEnd()) {
                throw new ParseException("Invalid Structured Field integer");
            }
            final int start = pos;
            while (!atEnd() && isDigit(value.charAt(pos))) {
                pos++;
            }
            if (pos == start || pos - start > 15) {
                throw new ParseException("Invalid Structured Field integer");
            }
        }

        /**
         * Parses a colon-delimited Byte Sequence, verifying that the enclosed text is well-formed
         * Base64. The decoded bytes are discarded.
         *
         * @throws ParseException if the encoding is invalid or the sequence is unterminated.
         */
        void parseByteSequence() throws ParseException {
            pos++;
            final int start = pos;
            while (!atEnd() && value.charAt(pos) != ':') {
                final char ch = value.charAt(pos);
                if (!(isAlpha(ch) || isDigit(ch) || ch == '+' || ch == '/' || ch == '=')) {
                    throw new ParseException("Invalid Structured Field Byte Sequence");
                }
                pos++;
            }
            if (atEnd()) {
                throw new ParseException("Unterminated Structured Field Byte Sequence");
            }
            final String encoded = value.substring(start, pos++);
            try {
                Base64.getDecoder().decode(encoded);
            } catch (final IllegalArgumentException ex) {
                throw new ParseException("Invalid Structured Field Byte Sequence");
            }
        }

        /**
         * Parses a Boolean, the {@code ?0} or {@code ?1} form. The value is validated but not
         * retained.
         *
         * @throws ParseException if the character after {@code ?} is neither {@code 0} nor {@code 1}.
         */
        void parseBoolean() throws ParseException {
            pos++;
            if (atEnd() || value.charAt(pos) != '0' && value.charAt(pos) != '1') {
                throw new ParseException("Invalid Structured Field Boolean");
            }
            pos++;
        }

        /**
         * Parses a Display String, the {@code %"..."} form whose content is UTF-8 percent-encoded.
         * Percent escapes must be two lower-case hex digits; the decoded text is discarded.
         *
         * @throws ParseException if the opening quote, an escape or a character is invalid, or the
         *   string is unterminated.
         */
        void parseDisplayString() throws ParseException {
            pos++;
            if (!consume('"')) {
                throw new ParseException("Invalid Structured Field Display String");
            }
            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            while (!atEnd()) {
                final char ch = value.charAt(pos++);
                if (ch == '"') {
                    try {
                        StandardCharsets.UTF_8.newDecoder()
                                .onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT)
                                .decode(ByteBuffer.wrap(bytes.toByteArray()));
                        return;
                    } catch (final CharacterCodingException ex) {
                        throw new ParseException("Invalid UTF-8 in Structured Field Display String");
                    }
                }
                if (ch == '%') {
                    if (pos + 1 >= value.length()
                            || !isLowerHex(value.charAt(pos))
                            || !isLowerHex(value.charAt(pos + 1))) {
                        throw new ParseException("Invalid Structured Field Display String escape");
                    }
                    bytes.write(hexValue(value.charAt(pos)) * 16 + hexValue(value.charAt(pos + 1)));
                    pos += 2;
                } else if (ch < 0x20 || ch > 0x7e) {
                    throw new ParseException("Invalid Structured Field Display String character");
                } else {
                    bytes.write((byte) ch);
                }
            }
            throw new ParseException("Unterminated Structured Field Display String");
        }

        /**
         * Consumes any trailing {@code ;key=value} parameters attached to an item or list. This
         * implementation assigns no meaning to parameters, so they are validated and discarded.
         *
         * @throws ParseException if a parameter key or value is malformed.
         */
        void parseParameters() throws ParseException {
            while (!atEnd() && value.charAt(pos) == ';') {
                pos++;
                skipSp();
                final String ignored = parseKey();
                if (ignored.length() == 0) {
                    throw new ParseException("Invalid Structured Field parameter");
                }
                if (consume('=')) {
                    parseBareItem();
                }
            }
        }

        private static boolean isKeyStart(final char ch) {
            return ch >= 'a' && ch <= 'z' || ch == '*';
        }

        private static boolean isKeyChar(final char ch) {
            return isKeyStart(ch) || isDigit(ch) || ch == '_' || ch == '-' || ch == '.';
        }

        private static boolean isTokenStart(final char ch) {
            return isAlpha(ch) || ch == '*';
        }

        private static boolean isTokenChar(final char ch) {
            return isAlpha(ch) || isDigit(ch)
                    || "!#$%&'*+-.^_`|~:/".indexOf(ch) >= 0;
        }

        private static boolean isAlpha(final char ch) {
            return ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z';
        }

        private static boolean isDigit(final char ch) {
            return ch >= '0' && ch <= '9';
        }

        private static boolean isLowerHex(final char ch) {
            return isDigit(ch) || ch >= 'a' && ch <= 'f';
        }

        private static int hexValue(final char ch) {
            return isDigit(ch) ? ch - '0' : ch - 'a' + 10;
        }
    }
}
