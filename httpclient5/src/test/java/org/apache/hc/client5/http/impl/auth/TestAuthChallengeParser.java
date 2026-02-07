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

import java.util.List;

import org.apache.hc.client5.http.auth.AuthChallenge;
import org.apache.hc.client5.http.auth.ChallengeType;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.message.ParserCursor;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TestAuthChallengeParser {

    private AuthChallengeParser parser;

    @BeforeEach
    void setUp() {
        this.parser = new AuthChallengeParser();
    }

    @Test
    void testParseTokenTerminatedByBlank() {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("aaabbbbccc ");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        Assertions.assertEquals("aaabbbbccc", parser.parseToken(buffer, cursor));
    }

    @Test
    void testParseTokenTerminatedByComma() {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("aaabbbbccc, ");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        Assertions.assertEquals("aaabbbbccc", parser.parseToken(buffer, cursor));
    }

    @Test
    void testParseTokenTerminatedByEndOfStream() {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("aaabbbbccc");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        Assertions.assertEquals("aaabbbbccc", parser.parseToken(buffer, cursor));
    }

    @Test
    void testParsePaddedToken68() {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("aaabbbbccc==== ");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        Assertions.assertEquals("aaabbbbccc====", parser.parseToken(buffer, cursor));
        Assertions.assertFalse(cursor.atEnd());
        Assertions.assertEquals(' ', buffer.charAt(cursor.getPos()));
    }

    @Test
    void testParsePaddedToken68SingleEqual() {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("aaabbbbccc=");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        Assertions.assertEquals("aaabbbbccc=", parser.parseToken(buffer, cursor));
        Assertions.assertTrue(cursor.atEnd());
    }

    @Test
    void testParsePaddedToken68MultipleEquals() {
        final CharArrayBuffer buffer = new CharArrayBuffer(16);
        buffer.append("aaabbbbccc======");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        Assertions.assertEquals("aaabbbbccc======", parser.parseToken(buffer, cursor));
        Assertions.assertTrue(cursor.atEnd());
    }

    @Test
    void testParsePaddedToken68TerminatedByComma() {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("aaabbbbccc====,");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        Assertions.assertEquals("aaabbbbccc====", parser.parseToken(buffer, cursor));
        Assertions.assertFalse(cursor.atEnd());
        Assertions.assertEquals(',', buffer.charAt(cursor.getPos()));
    }

    @Test
    void testParseTokenTerminatedByParameter() {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("aaabbbbccc=blah");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        Assertions.assertEquals("aaabbbbccc", parser.parseToken(buffer, cursor));
        Assertions.assertFalse(cursor.atEnd());
        Assertions.assertEquals('=', buffer.charAt(cursor.getPos()));
    }

    @Test
    void testParseBasicAuthChallenge() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append(StandardAuthScheme.BASIC + " realm=blah");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        final List<AuthChallenge> challenges = parser.parse(ChallengeType.TARGET, buffer, cursor);
        Assertions.assertNotNull(challenges);
        Assertions.assertEquals(1, challenges.size());
        final AuthChallenge challenge1 = challenges.get(0);
        Assertions.assertEquals(StandardAuthScheme.BASIC, challenge1.getSchemeName());
        Assertions.assertNull(challenge1.getValue());
        final List<NameValuePair> params = challenge1.getParams();
        Assertions.assertNotNull(params);
        Assertions.assertEquals(1, params.size());
        assertNameValuePair(params.get(0), "realm", "blah");
    }

    @Test
    void testParseAuthChallengeWithBlanks() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("   " + StandardAuthScheme.BASIC + "  realm = blah   ");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        final List<AuthChallenge> challenges = parser.parse(ChallengeType.TARGET, buffer, cursor);
        Assertions.assertNotNull(challenges);
        Assertions.assertEquals(1, challenges.size());
        final AuthChallenge challenge1 = challenges.get(0);
        Assertions.assertEquals(StandardAuthScheme.BASIC, challenge1.getSchemeName());
        Assertions.assertNull(challenge1.getValue());
        final List<NameValuePair> params = challenge1.getParams();
        Assertions.assertNotNull(params);
        Assertions.assertEquals(1, params.size());
        assertNameValuePair(params.get(0), "realm", "blah");
    }

    @Test
    void testParseMultipleAuthChallenge() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("This  xxxxxxxxxxxxxxxxxxxxxx, " +
                "That yyyyyyyyyyyyyyyyyyyyyy  ");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        final List<AuthChallenge> challenges = parser.parse(ChallengeType.TARGET, buffer, cursor);
        Assertions.assertNotNull(challenges);
        Assertions.assertEquals(2, challenges.size());

        final AuthChallenge challenge1 = challenges.get(0);
        Assertions.assertEquals("This", challenge1.getSchemeName());
        Assertions.assertEquals("xxxxxxxxxxxxxxxxxxxxxx", challenge1.getValue());
        Assertions.assertNull(challenge1.getParams());

        final AuthChallenge challenge2 = challenges.get(1);
        Assertions.assertEquals("That", challenge2.getSchemeName());
        Assertions.assertEquals("yyyyyyyyyyyyyyyyyyyyyy", challenge2.getValue());
        Assertions.assertNull(challenge2.getParams());
    }

    @Test
    void testParseMultipleAuthChallengeWithParams() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append(StandardAuthScheme.BASIC + " realm=blah, param1 = this, param2=that, " +
                StandardAuthScheme.BASIC + " realm=\"\\\"yada\\\"\", this, that=,this-and-that  ");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        final List<AuthChallenge> challenges = parser.parse(ChallengeType.TARGET, buffer, cursor);
        Assertions.assertNotNull(challenges);
        Assertions.assertEquals(2, challenges.size());

        final AuthChallenge challenge1 = challenges.get(0);
        Assertions.assertEquals(StandardAuthScheme.BASIC, challenge1.getSchemeName());
        Assertions.assertNull(challenge1.getValue());
        final List<NameValuePair> params1 = challenge1.getParams();
        Assertions.assertNotNull(params1);
        Assertions.assertEquals(3, params1.size());
        assertNameValuePair(params1.get(0), "realm", "blah");
        assertNameValuePair(params1.get(1), "param1", "this");
        assertNameValuePair(params1.get(2), "param2", "that");

        final AuthChallenge challenge2 = challenges.get(1);
        Assertions.assertEquals(StandardAuthScheme.BASIC, challenge2.getSchemeName());
        Assertions.assertNull(challenge2.getValue());
        final List<NameValuePair> params2 = challenge2.getParams();
        Assertions.assertNotNull(params2);
        Assertions.assertEquals(4, params2.size());
        assertNameValuePair(params2.get(0), "realm", "\"yada\"");
        assertNameValuePair(params2.get(1), "this", null);
        assertNameValuePair(params2.get(2), "that", "");
        assertNameValuePair(params2.get(3), "this-and-that", null);
    }

    @Test
    void testParseMultipleAuthChallengeWithParamsContainingComma() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append(StandardAuthScheme.BASIC + " realm=blah, param1 = \"this, param2=that\", " +
                StandardAuthScheme.BASIC + " realm=\"\\\"yada,,,,\\\"\"");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        final List<AuthChallenge> challenges = parser.parse(ChallengeType.TARGET, buffer, cursor);
        Assertions.assertNotNull(challenges);
        Assertions.assertEquals(2, challenges.size());

        final AuthChallenge challenge1 = challenges.get(0);
        Assertions.assertEquals(StandardAuthScheme.BASIC, challenge1.getSchemeName());
        Assertions.assertNull(challenge1.getValue());
        final List<NameValuePair> params1 = challenge1.getParams();
        Assertions.assertNotNull(params1);
        Assertions.assertEquals(2, params1.size());
        assertNameValuePair(params1.get(0), "realm", "blah");
        assertNameValuePair(params1.get(1), "param1", "this, param2=that");

        final AuthChallenge challenge2 = challenges.get(1);
        Assertions.assertEquals(StandardAuthScheme.BASIC, challenge2.getSchemeName());
        Assertions.assertNull(challenge2.getValue());
        final List<NameValuePair> params2 = challenge2.getParams();
        Assertions.assertNotNull(params2);
        Assertions.assertEquals(1, params2.size());
        assertNameValuePair(params2.get(0), "realm", "\"yada,,,,\"");
    }

    @Test
    void testParseEmptyAuthChallenge1() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("This");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        final List<AuthChallenge> challenges = parser.parse(ChallengeType.TARGET, buffer, cursor);
        Assertions.assertNotNull(challenges);
        Assertions.assertEquals(1, challenges.size());

        final AuthChallenge challenge1 = challenges.get(0);
        Assertions.assertEquals("This", challenge1.getSchemeName());
        Assertions.assertNull(challenge1.getValue());
        Assertions.assertNull(challenge1.getParams());
    }

    @Test
    void testParseMalformedAuthChallenge1() {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("This , ");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        Assertions.assertThrows(ParseException.class, () ->
                parser.parse(ChallengeType.TARGET, buffer, cursor));
    }

    @Test
    void testParseMalformedAuthChallenge2() {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("This = that");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        Assertions.assertThrows(ParseException.class, () ->
                parser.parse(ChallengeType.TARGET, buffer, cursor));
    }

    @Test
    void testParseMalformedAuthChallenge3() {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("blah blah blah");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        Assertions.assertThrows(ParseException.class, () ->
                parser.parse(ChallengeType.TARGET, buffer, cursor));
    }

    @Test
    void testParseValidAuthChallenge1() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("blah blah");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        final List<AuthChallenge> challenges = parser.parse(ChallengeType.TARGET, buffer, cursor);
        Assertions.assertNotNull(challenges);
        Assertions.assertEquals(1, challenges.size());

        final AuthChallenge challenge1 = challenges.get(0);
        Assertions.assertEquals("blah", challenge1.getSchemeName());
        Assertions.assertEquals("blah", challenge1.getValue());
        Assertions.assertNull(challenge1.getParams());
    }

    @Test
    void testParseValidAuthChallenge2() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("blah blah, blah");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        final List<AuthChallenge> challenges = parser.parse(ChallengeType.TARGET, buffer, cursor);
        Assertions.assertNotNull(challenges);
        Assertions.assertEquals(1, challenges.size());

        final AuthChallenge challenge1 = challenges.get(0);
        Assertions.assertEquals("blah", challenge1.getSchemeName());
        Assertions.assertNull(challenge1.getValue());
        final List<NameValuePair> params1 = challenge1.getParams();
        Assertions.assertNotNull(params1);
        Assertions.assertEquals(2, params1.size());
        assertNameValuePair(params1.get(0), "blah", null);
        assertNameValuePair(params1.get(1), "blah", null);
    }

    @Test
    void testParseParameterAndToken68AuthChallengeMix() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("scheme1 aaaa  , scheme2 aaaa==,  scheme3 aaaa=aaaa, scheme4 aaaa=");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        final List<AuthChallenge> challenges = parser.parse(ChallengeType.TARGET, buffer, cursor);
        Assertions.assertNotNull(challenges);
        Assertions.assertEquals(4, challenges.size());
        final AuthChallenge challenge1 = challenges.get(0);
        Assertions.assertEquals("scheme1", challenge1.getSchemeName());
        Assertions.assertEquals("aaaa", challenge1.getValue());
        Assertions.assertNull(challenge1.getParams());
        final AuthChallenge challenge2 = challenges.get(1);
        Assertions.assertEquals("scheme2", challenge2.getSchemeName());
        Assertions.assertEquals("aaaa==", challenge2.getValue());
        Assertions.assertNull(challenge2.getParams());
        final AuthChallenge challenge3 = challenges.get(2);
        Assertions.assertEquals("scheme3", challenge3.getSchemeName());
        Assertions.assertNull(challenge3.getValue());
        Assertions.assertNotNull(challenge3.getParams());
        Assertions.assertEquals(1, challenge3.getParams().size());
        assertNameValuePair(challenge3.getParams().get(0), "aaaa", "aaaa");
        final AuthChallenge challenge4 = challenges.get(3);
        Assertions.assertEquals("scheme4", challenge4.getSchemeName());
        Assertions.assertEquals("aaaa=", challenge4.getValue());
        Assertions.assertNull(challenge4.getParams());
    }

    private static void assertNameValuePair(final NameValuePair pair, final String name, final String value) {
        Assertions.assertNotNull(pair);
        Assertions.assertEquals(name, pair.getName());
        Assertions.assertEquals(value, pair.getValue());
    }

}
