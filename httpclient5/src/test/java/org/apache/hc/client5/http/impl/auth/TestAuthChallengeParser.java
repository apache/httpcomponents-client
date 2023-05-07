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

import static org.hamcrest.MatcherAssert.assertThat;

import java.util.List;

import org.apache.hc.client5.http.NameValuePairMatcher;
import org.apache.hc.client5.http.auth.AuthChallenge;
import org.apache.hc.client5.http.auth.ChallengeType;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.message.ParserCursor;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TestAuthChallengeParser {

    private AuthChallengeParser parser;

    @BeforeEach
    public void setUp() throws Exception {
        this.parser = new AuthChallengeParser();
    }

    @Test
    public void testParseTokenTerminatedByBlank() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("aaabbbbccc ");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        assertThat(parser.parseToken(buffer, cursor), CoreMatchers.equalTo("aaabbbbccc"));
    }

    @Test
    public void testParseTokenTerminatedByComma() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("aaabbbbccc, ");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        assertThat(parser.parseToken(buffer, cursor), CoreMatchers.equalTo("aaabbbbccc"));
    }

    @Test
    public void testParseTokenTerminatedByEndOfStream() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("aaabbbbccc");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        assertThat(parser.parseToken(buffer, cursor), CoreMatchers.equalTo("aaabbbbccc"));
    }

    @Test
    public void testParsePaddedToken68() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("aaabbbbccc==== ");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        assertThat(parser.parseToken(buffer, cursor), CoreMatchers.equalTo("aaabbbbccc===="));
        assertThat(cursor.atEnd(), CoreMatchers.equalTo(false));
        assertThat(buffer.charAt(cursor.getPos()), CoreMatchers.equalTo(' '));
    }

    @Test
    public void testParsePaddedToken68SingleEqual() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("aaabbbbccc=");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        assertThat(parser.parseToken(buffer, cursor), CoreMatchers.equalTo("aaabbbbccc="));
        assertThat(cursor.atEnd(), CoreMatchers.equalTo(true));
    }

    @Test
    public void testParsePaddedToken68MultipleEquals() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(16);
        buffer.append("aaabbbbccc======");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        assertThat(parser.parseToken(buffer, cursor), CoreMatchers.equalTo("aaabbbbccc======"));
        assertThat(cursor.atEnd(), CoreMatchers.equalTo(true));
    }

    @Test
    public void testParsePaddedToken68TerminatedByComma() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("aaabbbbccc====,");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        assertThat(parser.parseToken(buffer, cursor), CoreMatchers.equalTo("aaabbbbccc===="));
        assertThat(cursor.atEnd(), CoreMatchers.equalTo(false));
        assertThat(buffer.charAt(cursor.getPos()), CoreMatchers.equalTo(','));
    }

    @Test
    public void testParseTokenTerminatedByParameter() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("aaabbbbccc=blah");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        assertThat(parser.parseToken(buffer, cursor), CoreMatchers.equalTo("aaabbbbccc"));
        assertThat(cursor.atEnd(), CoreMatchers.equalTo(false));
        assertThat(buffer.charAt(cursor.getPos()), CoreMatchers.equalTo('='));
    }

    @Test
    public void testParseBasicAuthChallenge() throws Exception {
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
        assertThat(params.get(0), NameValuePairMatcher.equals("realm", "blah"));
    }

    @Test
    public void testParseAuthChallengeWithBlanks() throws Exception {
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
        assertThat(params.get(0), NameValuePairMatcher.equals("realm", "blah"));
    }

    @Test
    public void testParseMultipleAuthChallenge() throws Exception {
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
    public void testParseMultipleAuthChallengeWithParams() throws Exception {
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
        assertThat(params1.get(0), NameValuePairMatcher.equals("realm", "blah"));
        assertThat(params1.get(1), NameValuePairMatcher.equals("param1", "this"));
        assertThat(params1.get(2), NameValuePairMatcher.equals("param2", "that"));

        final AuthChallenge challenge2 = challenges.get(1);
        Assertions.assertEquals(StandardAuthScheme.BASIC, challenge2.getSchemeName());
        Assertions.assertNull(challenge2.getValue());
        final List<NameValuePair> params2 = challenge2.getParams();
        Assertions.assertNotNull(params2);
        Assertions.assertEquals(4, params2.size());
        assertThat(params2.get(0), NameValuePairMatcher.equals("realm", "\"yada\""));
        assertThat(params2.get(1), NameValuePairMatcher.equals("this", null));
        assertThat(params2.get(2), NameValuePairMatcher.equals("that", ""));
        assertThat(params2.get(3), NameValuePairMatcher.equals("this-and-that", null));
    }

    @Test
    public void testParseMultipleAuthChallengeWithParamsContainingComma() throws Exception {
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
        assertThat(params1.get(0), NameValuePairMatcher.equals("realm", "blah"));
        assertThat(params1.get(1), NameValuePairMatcher.equals("param1", "this, param2=that"));

        final AuthChallenge challenge2 = challenges.get(1);
        Assertions.assertEquals(StandardAuthScheme.BASIC, challenge2.getSchemeName());
        Assertions.assertNull(challenge2.getValue());
        final List<NameValuePair> params2 = challenge2.getParams();
        Assertions.assertNotNull(params2);
        Assertions.assertEquals(1, params2.size());
        assertThat(params2.get(0), NameValuePairMatcher.equals("realm", "\"yada,,,,\""));
    }

    @Test
    public void testParseEmptyAuthChallenge1() throws Exception {
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
    public void testParseMalformedAuthChallenge1() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("This , ");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        Assertions.assertThrows(ParseException.class, () ->
                parser.parse(ChallengeType.TARGET, buffer, cursor));
    }

    @Test
    public void testParseMalformedAuthChallenge2() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("This = that");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        Assertions.assertThrows(ParseException.class, () ->
                parser.parse(ChallengeType.TARGET, buffer, cursor));
    }

    @Test
    public void testParseMalformedAuthChallenge3() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("blah blah blah");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        Assertions.assertThrows(ParseException.class, () ->
                parser.parse(ChallengeType.TARGET, buffer, cursor));
    }

    @Test
    public void testParseValidAuthChallenge1() throws Exception {
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
    public void testParseValidAuthChallenge2() throws Exception {
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
        assertThat(params1.get(0), NameValuePairMatcher.equals("blah", null));
        assertThat(params1.get(1), NameValuePairMatcher.equals("blah", null));
    }

    @Test
    public void testParseParameterAndToken68AuthChallengeMix() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("scheme1 aaaa  , scheme2 aaaa==,  scheme3 aaaa=aaaa, scheme4 aaaa=");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        final List<AuthChallenge> challenges = parser.parse(ChallengeType.TARGET, buffer, cursor);
        Assertions.assertNotNull(challenges);
        Assertions.assertEquals(4, challenges.size());
        final AuthChallenge challenge1 = challenges.get(0);
        assertThat(challenge1.getSchemeName(), CoreMatchers.equalTo("scheme1"));
        assertThat(challenge1.getValue(), CoreMatchers.equalTo("aaaa"));
        assertThat(challenge1.getParams(), CoreMatchers.nullValue());
        final AuthChallenge challenge2 = challenges.get(1);
        assertThat(challenge2.getSchemeName(), CoreMatchers.equalTo("scheme2"));
        assertThat(challenge2.getValue(), CoreMatchers.equalTo("aaaa=="));
        assertThat(challenge2.getParams(), CoreMatchers.nullValue());
        final AuthChallenge challenge3 = challenges.get(2);
        assertThat(challenge3.getSchemeName(), CoreMatchers.equalTo("scheme3"));
        assertThat(challenge3.getValue(), CoreMatchers.nullValue());
        assertThat(challenge3.getParams(), CoreMatchers.notNullValue());
        assertThat(challenge3.getParams().size(), CoreMatchers.equalTo(1));
        assertThat(challenge3.getParams().get(0), NameValuePairMatcher.equals("aaaa", "aaaa"));
        final AuthChallenge challenge4 = challenges.get(3);
        assertThat(challenge4.getSchemeName(), CoreMatchers.equalTo("scheme4"));
        assertThat(challenge4.getValue(), CoreMatchers.equalTo("aaaa="));
        assertThat(challenge4.getParams(), CoreMatchers.nullValue());
    }

}
