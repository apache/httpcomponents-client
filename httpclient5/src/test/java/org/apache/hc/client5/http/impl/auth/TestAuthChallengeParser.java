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

import org.apache.hc.client5.http.NameValuePairMatcher;
import org.apache.hc.client5.http.auth.AuthChallenge;
import org.apache.hc.client5.http.auth.ChallengeType;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.message.ParserCursor;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestAuthChallengeParser {

    private AuthChallengeParser parser;

    @Before
    public void setUp() throws Exception {
        this.parser = new AuthChallengeParser();
    }

    @Test
    public void testParseTokenTerminatedByBlank() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("aaabbbbccc ");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        MatcherAssert.assertThat(parser.parseToken(buffer, cursor), CoreMatchers.equalTo("aaabbbbccc"));
    }

    @Test
    public void testParseTokenTerminatedByComma() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("aaabbbbccc, ");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        MatcherAssert.assertThat(parser.parseToken(buffer, cursor), CoreMatchers.equalTo("aaabbbbccc"));
    }

    @Test
    public void testParseTokenTerminatedByEndOfStream() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("aaabbbbccc");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        MatcherAssert.assertThat(parser.parseToken(buffer, cursor), CoreMatchers.equalTo("aaabbbbccc"));
    }

    @Test
    public void testParsePaddedToken68() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("aaabbbbccc==== ");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        MatcherAssert.assertThat(parser.parseToken(buffer, cursor), CoreMatchers.equalTo("aaabbbbccc===="));
        MatcherAssert.assertThat(cursor.atEnd(), CoreMatchers.equalTo(false));
        MatcherAssert.assertThat(buffer.charAt(cursor.getPos()), CoreMatchers.equalTo(' '));
    }

    @Test
    public void testParsePaddedToken68SingleEqual() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("aaabbbbccc=");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        MatcherAssert.assertThat(parser.parseToken(buffer, cursor), CoreMatchers.equalTo("aaabbbbccc="));
        MatcherAssert.assertThat(cursor.atEnd(), CoreMatchers.equalTo(true));
    }

    @Test
    public void testParsePaddedToken68MultipleEquals() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(16);
        buffer.append("aaabbbbccc======");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        MatcherAssert.assertThat(parser.parseToken(buffer, cursor), CoreMatchers.equalTo("aaabbbbccc======"));
        MatcherAssert.assertThat(cursor.atEnd(), CoreMatchers.equalTo(true));
    }

    @Test
    public void testParsePaddedToken68TerminatedByComma() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("aaabbbbccc====,");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        MatcherAssert.assertThat(parser.parseToken(buffer, cursor), CoreMatchers.equalTo("aaabbbbccc===="));
        MatcherAssert.assertThat(cursor.atEnd(), CoreMatchers.equalTo(false));
        MatcherAssert.assertThat(buffer.charAt(cursor.getPos()), CoreMatchers.equalTo(','));
    }

    @Test
    public void testParseTokenTerminatedByParameter() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("aaabbbbccc=blah");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        MatcherAssert.assertThat(parser.parseToken(buffer, cursor), CoreMatchers.equalTo("aaabbbbccc"));
        MatcherAssert.assertThat(cursor.atEnd(), CoreMatchers.equalTo(false));
        MatcherAssert.assertThat(buffer.charAt(cursor.getPos()), CoreMatchers.equalTo('='));
    }

    @Test
    public void testParseBasicAuthChallenge() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append(StandardAuthScheme.BASIC + " realm=blah");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        final List<AuthChallenge> challenges = parser.parse(ChallengeType.TARGET, buffer, cursor);
        Assert.assertNotNull(challenges);
        Assert.assertEquals(1, challenges.size());
        final AuthChallenge challenge1 = challenges.get(0);
        Assert.assertEquals(StandardAuthScheme.BASIC, challenge1.getSchemeName());
        Assert.assertNull(challenge1.getValue());
        final List<NameValuePair> params = challenge1.getParams();
        Assert.assertNotNull(params);
        Assert.assertEquals(1, params.size());
        MatcherAssert.assertThat(params.get(0), NameValuePairMatcher.equals("realm", "blah"));
    }

    @Test
    public void testParseAuthChallengeWithBlanks() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("   " + StandardAuthScheme.BASIC + "  realm = blah   ");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        final List<AuthChallenge> challenges = parser.parse(ChallengeType.TARGET, buffer, cursor);
        Assert.assertNotNull(challenges);
        Assert.assertEquals(1, challenges.size());
        final AuthChallenge challenge1 = challenges.get(0);
        Assert.assertEquals(StandardAuthScheme.BASIC, challenge1.getSchemeName());
        Assert.assertNull(challenge1.getValue());
        final List<NameValuePair> params = challenge1.getParams();
        Assert.assertNotNull(params);
        Assert.assertEquals(1, params.size());
        MatcherAssert.assertThat(params.get(0), NameValuePairMatcher.equals("realm", "blah"));
    }

    @Test
    public void testParseMultipleAuthChallenge() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("This  xxxxxxxxxxxxxxxxxxxxxx, " +
                "That yyyyyyyyyyyyyyyyyyyyyy  ");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        final List<AuthChallenge> challenges = parser.parse(ChallengeType.TARGET, buffer, cursor);
        Assert.assertNotNull(challenges);
        Assert.assertEquals(2, challenges.size());

        final AuthChallenge challenge1 = challenges.get(0);
        Assert.assertEquals("This", challenge1.getSchemeName());
        Assert.assertEquals("xxxxxxxxxxxxxxxxxxxxxx", challenge1.getValue());
        Assert.assertNull(challenge1.getParams());

        final AuthChallenge challenge2 = challenges.get(1);
        Assert.assertEquals("That", challenge2.getSchemeName());
        Assert.assertEquals("yyyyyyyyyyyyyyyyyyyyyy", challenge2.getValue());
        Assert.assertNull(challenge2.getParams());
    }

    @Test
    public void testParseMultipleAuthChallengeWithParams() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append(StandardAuthScheme.BASIC + " realm=blah, param1 = this, param2=that, " +
                StandardAuthScheme.BASIC + " realm=\"\\\"yada\\\"\", this, that=,this-and-that  ");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        final List<AuthChallenge> challenges = parser.parse(ChallengeType.TARGET, buffer, cursor);
        Assert.assertNotNull(challenges);
        Assert.assertEquals(2, challenges.size());

        final AuthChallenge challenge1 = challenges.get(0);
        Assert.assertEquals(StandardAuthScheme.BASIC, challenge1.getSchemeName());
        Assert.assertNull(challenge1.getValue());
        final List<NameValuePair> params1 = challenge1.getParams();
        Assert.assertNotNull(params1);
        Assert.assertEquals(3, params1.size());
        MatcherAssert.assertThat(params1.get(0), NameValuePairMatcher.equals("realm", "blah"));
        MatcherAssert.assertThat(params1.get(1), NameValuePairMatcher.equals("param1", "this"));
        MatcherAssert.assertThat(params1.get(2), NameValuePairMatcher.equals("param2", "that"));

        final AuthChallenge challenge2 = challenges.get(1);
        Assert.assertEquals(StandardAuthScheme.BASIC, challenge2.getSchemeName());
        Assert.assertNull(challenge2.getValue());
        final List<NameValuePair> params2 = challenge2.getParams();
        Assert.assertNotNull(params2);
        Assert.assertEquals(4, params2.size());
        MatcherAssert.assertThat(params2.get(0), NameValuePairMatcher.equals("realm", "\"yada\""));
        MatcherAssert.assertThat(params2.get(1), NameValuePairMatcher.equals("this", null));
        MatcherAssert.assertThat(params2.get(2), NameValuePairMatcher.equals("that", ""));
        MatcherAssert.assertThat(params2.get(3), NameValuePairMatcher.equals("this-and-that", null));
    }

    @Test
    public void testParseMultipleAuthChallengeWithParamsContainingComma() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append(StandardAuthScheme.BASIC + " realm=blah, param1 = \"this, param2=that\", " +
                StandardAuthScheme.BASIC + " realm=\"\\\"yada,,,,\\\"\"");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        final List<AuthChallenge> challenges = parser.parse(ChallengeType.TARGET, buffer, cursor);
        Assert.assertNotNull(challenges);
        Assert.assertEquals(2, challenges.size());

        final AuthChallenge challenge1 = challenges.get(0);
        Assert.assertEquals(StandardAuthScheme.BASIC, challenge1.getSchemeName());
        Assert.assertNull(challenge1.getValue());
        final List<NameValuePair> params1 = challenge1.getParams();
        Assert.assertNotNull(params1);
        Assert.assertEquals(2, params1.size());
        MatcherAssert.assertThat(params1.get(0), NameValuePairMatcher.equals("realm", "blah"));
        MatcherAssert.assertThat(params1.get(1), NameValuePairMatcher.equals("param1", "this, param2=that"));

        final AuthChallenge challenge2 = challenges.get(1);
        Assert.assertEquals(StandardAuthScheme.BASIC, challenge2.getSchemeName());
        Assert.assertNull(challenge2.getValue());
        final List<NameValuePair> params2 = challenge2.getParams();
        Assert.assertNotNull(params2);
        Assert.assertEquals(1, params2.size());
        MatcherAssert.assertThat(params2.get(0), NameValuePairMatcher.equals("realm", "\"yada,,,,\""));
    }

    @Test
    public void testParseEmptyAuthChallenge1() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("This");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        final List<AuthChallenge> challenges = parser.parse(ChallengeType.TARGET, buffer, cursor);
        Assert.assertNotNull(challenges);
        Assert.assertEquals(1, challenges.size());

        final AuthChallenge challenge1 = challenges.get(0);
        Assert.assertEquals("This", challenge1.getSchemeName());
        Assert.assertNull(challenge1.getValue());
        Assert.assertNull(challenge1.getParams());
    }

    @Test
    public void testParseMalformedAuthChallenge1() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("This , ");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        Assert.assertThrows(ParseException.class, () ->
                parser.parse(ChallengeType.TARGET, buffer, cursor));
    }

    @Test
    public void testParseMalformedAuthChallenge2() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("This = that");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        Assert.assertThrows(ParseException.class, () ->
                parser.parse(ChallengeType.TARGET, buffer, cursor));
    }

    @Test
    public void testParseMalformedAuthChallenge3() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("blah blah blah");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        Assert.assertThrows(ParseException.class, () ->
                parser.parse(ChallengeType.TARGET, buffer, cursor));
    }

    @Test
    public void testParseValidAuthChallenge1() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("blah blah");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        final List<AuthChallenge> challenges = parser.parse(ChallengeType.TARGET, buffer, cursor);
        Assert.assertNotNull(challenges);
        Assert.assertEquals(1, challenges.size());

        final AuthChallenge challenge1 = challenges.get(0);
        Assert.assertEquals("blah", challenge1.getSchemeName());
        Assert.assertEquals("blah", challenge1.getValue());
        Assert.assertNull(challenge1.getParams());
    }

    @Test
    public void testParseValidAuthChallenge2() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("blah blah, blah");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        final List<AuthChallenge> challenges = parser.parse(ChallengeType.TARGET, buffer, cursor);
        Assert.assertNotNull(challenges);
        Assert.assertEquals(1, challenges.size());

        final AuthChallenge challenge1 = challenges.get(0);
        Assert.assertEquals("blah", challenge1.getSchemeName());
        Assert.assertNull(challenge1.getValue());
        final List<NameValuePair> params1 = challenge1.getParams();
        Assert.assertNotNull(params1);
        Assert.assertEquals(2, params1.size());
        MatcherAssert.assertThat(params1.get(0), NameValuePairMatcher.equals("blah", null));
        MatcherAssert.assertThat(params1.get(1), NameValuePairMatcher.equals("blah", null));
    }

    @Test
    public void testParseEmptyNTLMAuthChallenge() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append(StandardAuthScheme.NTLM);
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        final List<AuthChallenge> challenges = parser.parse(ChallengeType.TARGET, buffer, cursor);
        Assert.assertNotNull(challenges);
        Assert.assertEquals(1, challenges.size());
        final AuthChallenge challenge1 = challenges.get(0);
        Assert.assertEquals(StandardAuthScheme.NTLM, challenge1.getSchemeName());
        Assert.assertNull(challenge1.getValue());
    }

    @Test
    public void testParseParameterAndToken68AuthChallengeMix() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("scheme1 aaaa  , scheme2 aaaa==,  scheme3 aaaa=aaaa, scheme4 aaaa=");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        final List<AuthChallenge> challenges = parser.parse(ChallengeType.TARGET, buffer, cursor);
        Assert.assertNotNull(challenges);
        Assert.assertEquals(4, challenges.size());
        final AuthChallenge challenge1 = challenges.get(0);
        MatcherAssert.assertThat(challenge1.getSchemeName(), CoreMatchers.equalTo("scheme1"));
        MatcherAssert.assertThat(challenge1.getValue(), CoreMatchers.equalTo("aaaa"));
        MatcherAssert.assertThat(challenge1.getParams(), CoreMatchers.nullValue());
        final AuthChallenge challenge2 = challenges.get(1);
        MatcherAssert.assertThat(challenge2.getSchemeName(), CoreMatchers.equalTo("scheme2"));
        MatcherAssert.assertThat(challenge2.getValue(), CoreMatchers.equalTo("aaaa=="));
        MatcherAssert.assertThat(challenge2.getParams(), CoreMatchers.nullValue());
        final AuthChallenge challenge3 = challenges.get(2);
        MatcherAssert.assertThat(challenge3.getSchemeName(), CoreMatchers.equalTo("scheme3"));
        MatcherAssert.assertThat(challenge3.getValue(), CoreMatchers.nullValue());
        MatcherAssert.assertThat(challenge3.getParams(), CoreMatchers.notNullValue());
        MatcherAssert.assertThat(challenge3.getParams().size(), CoreMatchers.equalTo(1));
        MatcherAssert.assertThat(challenge3.getParams().get(0), NameValuePairMatcher.equals("aaaa", "aaaa"));
        final AuthChallenge challenge4 = challenges.get(3);
        MatcherAssert.assertThat(challenge4.getSchemeName(), CoreMatchers.equalTo("scheme4"));
        MatcherAssert.assertThat(challenge4.getValue(), CoreMatchers.equalTo("aaaa="));
        MatcherAssert.assertThat(challenge4.getParams(), CoreMatchers.nullValue());
    }

}
