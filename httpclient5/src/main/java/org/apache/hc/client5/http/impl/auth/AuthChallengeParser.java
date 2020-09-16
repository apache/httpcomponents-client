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

package org.apache.hc.client5.http.impl.auth;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import org.apache.hc.client5.http.auth.AuthChallenge;
import org.apache.hc.client5.http.auth.ChallengeType;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.http.message.ParserCursor;
import org.apache.hc.core5.util.TextUtils;
import org.apache.hc.core5.util.Tokenizer;

/**
 * Authentication challenge parser.
 *
 * @since 5.0
 */
public class AuthChallengeParser {

    public static final AuthChallengeParser INSTANCE = new AuthChallengeParser();

    private final Tokenizer tokenParser = Tokenizer.INSTANCE;

    private final static char BLANK            = ' ';
    private final static char COMMA_CHAR       = ',';
    private final static char EQUAL_CHAR       = '=';

    // IMPORTANT!
    // These private static variables must be treated as immutable and never exposed outside this class
    private static final BitSet TERMINATORS = Tokenizer.INIT_BITSET(BLANK, EQUAL_CHAR, COMMA_CHAR);
    private static final BitSet DELIMITER = Tokenizer.INIT_BITSET(COMMA_CHAR);
    private static final BitSet SPACE = Tokenizer.INIT_BITSET(BLANK);

    static class ChallengeInt {

        final String schemeName;
        final List<NameValuePair> params;

        ChallengeInt(final String schemeName) {
            this.schemeName = schemeName;
            this.params = new ArrayList<>();
        }

        @Override
        public String toString() {
            return "ChallengeInternal{" +
                    "schemeName='" + schemeName + '\'' +
                    ", params=" + params +
                    '}';
        }

    }

    /**
     * Parses the given sequence of characters into a list of {@link AuthChallenge} elements.
     *
     * @param challengeType the type of challenge (target or proxy).
     * @param buffer the sequence of characters to be parsed.
     * @param cursor the parser cursor.
     * @return a list of auth challenge elements.
     */
    public List<AuthChallenge> parse(
            final ChallengeType challengeType, final CharSequence buffer, final ParserCursor cursor) throws ParseException {
        tokenParser.skipWhiteSpace(buffer, cursor);
        if (cursor.atEnd()) {
            throw new ParseException("Malformed auth challenge");
        }
        final List<ChallengeInt> internalChallenges = new ArrayList<>();
        final String schemeName = tokenParser.parseToken(buffer, cursor, SPACE);
        if (TextUtils.isBlank(schemeName)) {
            throw new ParseException("Malformed auth challenge");
        }
        ChallengeInt current = new ChallengeInt(schemeName);
        while (current != null) {
            internalChallenges.add(current);
            current = parseChallenge(buffer, cursor, current);
        }
        final List<AuthChallenge> challenges = new ArrayList<>(internalChallenges.size());
        for (final ChallengeInt internal : internalChallenges) {
            final List<NameValuePair> params = internal.params;
            String token68 = null;
            if (params.size() == 1) {
                final NameValuePair param = params.get(0);
                if (param.getValue() == null) {
                    token68 = param.getName();
                    params.clear();
                }
            }
            challenges.add(
                    new AuthChallenge(challengeType, internal.schemeName, token68, !params.isEmpty() ? params : null));
        }
        return challenges;
    }

    ChallengeInt parseChallenge(
            final CharSequence buffer,
            final ParserCursor cursor,
            final ChallengeInt currentChallenge) throws ParseException {
        for (;;) {
            tokenParser.skipWhiteSpace(buffer, cursor);
            if (cursor.atEnd()) {
                return null;
            }
            final String token = parseToken(buffer, cursor);
            if (TextUtils.isBlank(token)) {
                throw new ParseException("Malformed auth challenge");
            }
            tokenParser.skipWhiteSpace(buffer, cursor);

            // it gets really messy here
            if (cursor.atEnd()) {
                // at the end of the header
                currentChallenge.params.add(new BasicNameValuePair(token, null));
            } else {
                char ch = buffer.charAt(cursor.getPos());
                if (ch == EQUAL_CHAR) {
                    cursor.updatePos(cursor.getPos() + 1);
                    final String value = tokenParser.parseValue(buffer, cursor, DELIMITER);
                    tokenParser.skipWhiteSpace(buffer, cursor);
                    if (!cursor.atEnd()) {
                        ch = buffer.charAt(cursor.getPos());
                        if (ch == COMMA_CHAR) {
                            cursor.updatePos(cursor.getPos() + 1);
                        }
                    }
                    currentChallenge.params.add(new BasicNameValuePair(token, value));
                } else if (ch == COMMA_CHAR) {
                    cursor.updatePos(cursor.getPos() + 1);
                    currentChallenge.params.add(new BasicNameValuePair(token, null));
                } else {
                    // the token represents new challenge
                    if (currentChallenge.params.isEmpty()) {
                        throw new ParseException("Malformed auth challenge");
                    }
                    return new ChallengeInt(token);
                }
            }
        }
    }

    String parseToken(final CharSequence buf, final ParserCursor cursor) {
        final StringBuilder dst = new StringBuilder();
        while (!cursor.atEnd()) {
            int pos = cursor.getPos();
            char current = buf.charAt(pos);
            if (TERMINATORS.get(current)) {
                // Here it gets really ugly
                if (current == EQUAL_CHAR) {
                    // it can be a start of a parameter value or token68 padding
                    // Look ahead and see if there are more '=' or at end of buffer
                    if (pos + 1 < cursor.getUpperBound() && buf.charAt(pos + 1) != EQUAL_CHAR) {
                        break;
                    }
                    do {
                        dst.append(current);
                        pos++;
                        cursor.updatePos(pos);
                        if (cursor.atEnd()) {
                            break;
                        }
                        current = buf.charAt(pos);
                    } while (current == EQUAL_CHAR);
                } else {
                    break;
                }
            } else {
                dst.append(current);
                cursor.updatePos(pos + 1);
            }
        }
        return dst.toString();
    }

}
