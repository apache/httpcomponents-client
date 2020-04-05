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
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.http.message.ParserCursor;
import org.apache.hc.core5.util.CharArrayBuffer;
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
    public void testParseBasicToken() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("blah");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        final NameValuePair nvp = parser.parseTokenOrParameter(buffer, cursor);
        Assert.assertNotNull(nvp);
        Assert.assertEquals("blah", nvp.getName());
        Assert.assertEquals(null, nvp.getValue());
    }

    @Test
    public void testParseTokenWithBlank() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("blah ");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        final NameValuePair nvp = parser.parseTokenOrParameter(buffer, cursor);
        Assert.assertNotNull(nvp);
        Assert.assertEquals("blah", nvp.getName());
        Assert.assertEquals(null, nvp.getValue());
    }

    @Test
    public void testParseTokenWithBlanks() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("  blah  blah ");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        final NameValuePair nvp = parser.parseTokenOrParameter(buffer, cursor);
        Assert.assertNotNull(nvp);
        Assert.assertEquals("blah", nvp.getName());
        Assert.assertEquals(null, nvp.getValue());
    }

    @Test
    public void testParseTokenDelimited() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("blah,blah");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        final NameValuePair nvp = parser.parseTokenOrParameter(buffer, cursor);
        Assert.assertNotNull(nvp);
        Assert.assertEquals("blah", nvp.getName());
        Assert.assertEquals(null, nvp.getValue());
    }

    @Test
    public void testParseParameterSimple() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("param=blah");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        final NameValuePair nvp = parser.parseTokenOrParameter(buffer, cursor);
        Assert.assertNotNull(nvp);
        Assert.assertEquals("param", nvp.getName());
        Assert.assertEquals("blah", nvp.getValue());
    }

    @Test
    public void testParseParameterDelimited() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("param   =  blah  ,  ");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        final NameValuePair nvp = parser.parseTokenOrParameter(buffer, cursor);
        Assert.assertNotNull(nvp);
        Assert.assertEquals("param", nvp.getName());
        Assert.assertEquals("blah", nvp.getValue());
    }

    @Test
    public void testParseParameterQuoted() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append(" param   =  \" blah  blah \"");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        final NameValuePair nvp = parser.parseTokenOrParameter(buffer, cursor);
        Assert.assertNotNull(nvp);
        Assert.assertEquals("param", nvp.getName());
        Assert.assertEquals(" blah  blah ", nvp.getValue());
    }

    @Test
    public void testParseParameterEscaped() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append(" param   =  \" blah  \\\"blah\\\" \"");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        final NameValuePair nvp = parser.parseTokenOrParameter(buffer, cursor);
        Assert.assertNotNull(nvp);
        Assert.assertEquals("param", nvp.getName());
        Assert.assertEquals(" blah  \"blah\" ", nvp.getValue());
    }

    @Test
    public void testParseParameterNoValue() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("param   =  ,  ");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        final NameValuePair nvp = parser.parseTokenOrParameter(buffer, cursor);
        Assert.assertNotNull(nvp);
        Assert.assertEquals("param", nvp.getName());
        Assert.assertEquals("", nvp.getValue());
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
        Assert.assertEquals(null, challenge1.getValue());
        final List<NameValuePair> params = challenge1.getParams();
        Assert.assertNotNull(params);
        Assert.assertEquals(1, params.size());
        assertNameValuePair(new BasicNameValuePair("realm", "blah"), params.get(0));
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
        Assert.assertEquals(null, challenge1.getValue());
        final List<NameValuePair> params = challenge1.getParams();
        Assert.assertNotNull(params);
        Assert.assertEquals(1, params.size());
        assertNameValuePair(new BasicNameValuePair("realm", "blah"), params.get(0));
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
        Assert.assertEquals(null, challenge1.getValue());
        final List<NameValuePair> params1 = challenge1.getParams();
        Assert.assertNotNull(params1);
        Assert.assertEquals(3, params1.size());
        assertNameValuePair(new BasicNameValuePair("realm", "blah"), params1.get(0));
        assertNameValuePair(new BasicNameValuePair("param1", "this"), params1.get(1));
        assertNameValuePair(new BasicNameValuePair("param2", "that"), params1.get(2));

        final AuthChallenge challenge2 = challenges.get(1);
        Assert.assertEquals(StandardAuthScheme.BASIC, challenge2.getSchemeName());
        Assert.assertEquals(null, challenge2.getValue());
        final List<NameValuePair> params2 = challenge2.getParams();
        Assert.assertNotNull(params2);
        Assert.assertEquals(4, params2.size());
        assertNameValuePair(new BasicNameValuePair("realm", "\"yada\""), params2.get(0));
        assertNameValuePair(new BasicNameValuePair("this", null), params2.get(1));
        assertNameValuePair(new BasicNameValuePair("that", ""), params2.get(2));
        assertNameValuePair(new BasicNameValuePair("this-and-that", null), params2.get(3));
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
        Assert.assertEquals(null, challenge1.getValue());
        Assert.assertNull(challenge1.getParams());
    }

    @Test(expected = ParseException.class)
    public void testParseMalformedAuthChallenge1() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("This , ");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        parser.parse(ChallengeType.TARGET, buffer, cursor);
    }

    @Test(expected = ParseException.class)
    public void testParseMalformedAuthChallenge2() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("This = that");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        parser.parse(ChallengeType.TARGET, buffer, cursor);
    }

    @Test(expected = ParseException.class)
    public void testParseMalformedAuthChallenge3() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append("blah blah blah");
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        parser.parse(ChallengeType.TARGET, buffer, cursor);
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
        Assert.assertEquals(null, challenge1.getValue());
        final List<NameValuePair> params1 = challenge1.getParams();
        Assert.assertNotNull(params1);
        Assert.assertEquals(2, params1.size());
        assertNameValuePair(new BasicNameValuePair("blah", null), params1.get(0));
        assertNameValuePair(new BasicNameValuePair("blah", null), params1.get(1));
    }

    @Test
    public void testParseNTLMAuthChallenge() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append(StandardAuthScheme.NTLM);
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        final List<AuthChallenge> challenges = parser.parse(ChallengeType.TARGET, buffer, cursor);
        Assert.assertNotNull(challenges);
        Assert.assertEquals(1, challenges.size());
        final AuthChallenge challenge1 = challenges.get(0);
        Assert.assertEquals(StandardAuthScheme.NTLM, challenge1.getSchemeName());
        Assert.assertEquals(null, challenge1.getValue());
    }

    private static void assertNameValuePair (
            final NameValuePair expected,
            final NameValuePair result) {
        Assert.assertNotNull(result);
        Assert.assertEquals(expected.getName(), result.getName());
        Assert.assertEquals(expected.getValue(), result.getValue());
    }

}
