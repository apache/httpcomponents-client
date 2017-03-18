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
import org.apache.hc.core5.http.message.TokenParser;

public class AuthChallengeParser {

    public static final AuthChallengeParser INSTANCE = new AuthChallengeParser();

    private final TokenParser tokenParser = TokenParser.INSTANCE;

    private final static char BLANK            = ' ';
    private final static char COMMA_CHAR       = ',';
    private final static char EQUAL_CHAR       = '=';

    // IMPORTANT!
    // These private static variables must be treated as immutable and never exposed outside this class
    private static final BitSet TERMINATORS = TokenParser.INIT_BITSET(BLANK, EQUAL_CHAR, COMMA_CHAR);
    private static final BitSet DELIMITER = TokenParser.INIT_BITSET(COMMA_CHAR);

    NameValuePair parseTokenOrParameter(final CharSequence buffer, final ParserCursor cursor) {

        tokenParser.skipWhiteSpace(buffer, cursor);
        final String token = tokenParser.parseToken(buffer, cursor, TERMINATORS);
        if (!cursor.atEnd()) {
            if (buffer.charAt(cursor.getPos()) == BLANK) {
                tokenParser.skipWhiteSpace(buffer, cursor);
            }
            if (!cursor.atEnd() && buffer.charAt(cursor.getPos()) == EQUAL_CHAR) {
                cursor.updatePos(cursor.getPos() + 1);
                final String value = tokenParser.parseValue(buffer, cursor, DELIMITER);
                return new BasicNameValuePair(token, value);
            }
        }
        return new BasicNameValuePair(token, null);
    }

    public List<AuthChallenge> parse(final ChallengeType challengeType, final CharSequence buffer, final ParserCursor cursor) throws ParseException {

        final List<AuthChallenge> list = new ArrayList<>();
        String scheme = null;
        final List<NameValuePair> params = new ArrayList<>();
        while (!cursor.atEnd()) {
            final NameValuePair tokenOrParameter = parseTokenOrParameter(buffer, cursor);
            if (tokenOrParameter.getValue() == null && !cursor.atEnd() && buffer.charAt(cursor.getPos()) != COMMA_CHAR) {
                if (scheme != null) {
                    if (params.isEmpty()) {
                        throw new ParseException("Malformed auth challenge");
                    }
                    list.add(createAuthChallenge(challengeType, scheme, params));
                    params.clear();
                }
                scheme = tokenOrParameter.getName();
            } else {
                params.add(tokenOrParameter);
                if (!cursor.atEnd() && buffer.charAt(cursor.getPos()) != COMMA_CHAR) {
                    scheme = null;
                }
            }
            if (!cursor.atEnd() && buffer.charAt(cursor.getPos()) == COMMA_CHAR) {
                cursor.updatePos(cursor.getPos() + 1);
            }
        }
        list.add(createAuthChallenge(challengeType, scheme, params));
        return list;
    }

    private static AuthChallenge createAuthChallenge(final ChallengeType challengeType, final String scheme, final List<NameValuePair> params) throws ParseException {
        if (scheme != null) {
            if (params.size() == 1) {
                final NameValuePair nvp = params.get(0);
                if (nvp.getValue() == null) {
                    return new AuthChallenge(challengeType, scheme, nvp.getName(), null);
                }
            }
            return new AuthChallenge(challengeType, scheme, null, params.size() > 0 ? params : null);
        }
        if (params.size() == 1) {
            final NameValuePair nvp = params.get(0);
            if (nvp.getValue() == null) {
                return new AuthChallenge(challengeType, nvp.getName(), null, null);
            }
        }
        throw new ParseException("Malformed auth challenge");
    }

}
