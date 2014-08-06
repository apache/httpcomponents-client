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

package org.apache.http.conn.ssl;

import java.util.Arrays;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.message.ParserCursor;
import org.apache.http.util.CharArrayBuffer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestDistinguishedNameParser {

    private DistinguishedNameParser parser;

    @Before
    public void setUp() throws Exception {
        parser = new DistinguishedNameParser();
    }

    private static CharArrayBuffer createBuffer(final String value) {
        if (value == null) {
            return null;
        }
        final CharArrayBuffer buffer = new CharArrayBuffer(value.length());
        buffer.append(value);
        return buffer;
    }

    @Test
    public void testTokenParsingMixedValuesAndQuotedValues() throws Exception {
        final String s = "  stuff and    \" some more \"   \"stuff  ;";
        final CharArrayBuffer raw = createBuffer(s);
        final ParserCursor cursor = new ParserCursor(0, s.length());
        final String result = parser.parseValue(raw, cursor, TokenParser.INIT_BITSET(';'));
        Assert.assertEquals("stuff and  some more  stuff  ;", result);
    }

    @Test
    public void testTokenParsingMixedValuesAndQuotedValues2() throws Exception {
        final String s = "stuff\"more\"stuff;";
        final CharArrayBuffer raw = createBuffer(s);
        final ParserCursor cursor = new ParserCursor(0, s.length());
        final String result = parser.parseValue(raw, cursor, TokenParser.INIT_BITSET(';'));
        Assert.assertEquals("stuffmorestuff", result);
    }

    @Test
    public void testTokenParsingEscapedQuotes() throws Exception {
        final String s = "stuff\"\\\"more\\\"\"stuff;";
        final CharArrayBuffer raw = createBuffer(s);
        final ParserCursor cursor = new ParserCursor(0, s.length());
        final String result = parser.parseValue(raw, cursor, TokenParser.INIT_BITSET(';'));
        Assert.assertEquals("stuff\"more\"stuff", result);
    }

    @Test
    public void testTokenParsingEscapedDelimiter() throws Exception {
        final String s = "stuff\"\\\"more\\\";\"stuff;";
        final CharArrayBuffer raw = createBuffer(s);
        final ParserCursor cursor = new ParserCursor(0, s.length());
        final String result = parser.parseValue(raw, cursor, TokenParser.INIT_BITSET(';'));
        Assert.assertEquals("stuff\"more\";stuff", result);
    }

    @Test
    public void testTokenParsingEscapedSlash() throws Exception {
        final String s = "stuff\"\\\"more\\\";\\\\\"stuff;";
        final CharArrayBuffer raw = createBuffer(s);
        final ParserCursor cursor = new ParserCursor(0, s.length());
        final String result = parser.parseValue(raw, cursor, TokenParser.INIT_BITSET(';'));
        Assert.assertEquals("stuff\"more\";\\stuff", result);
    }

    @Test
    public void testTokenParsingSlashOutsideQuotes() throws Exception {
        final String s = "stuff\\; more stuff;";
        final CharArrayBuffer raw = createBuffer(s);
        final ParserCursor cursor = new ParserCursor(0, s.length());
        final String result = parser.parseValue(raw, cursor, TokenParser.INIT_BITSET(';'));
        Assert.assertEquals("stuff; more stuff", result);
    }

    @Test
    public void testBasicParameterParsing() throws Exception {
        final String s = "cn=blah,";
        final CharArrayBuffer raw = createBuffer(s);
        final ParserCursor cursor = new ParserCursor(0, s.length());
        final NameValuePair result = parser.parseParameter(raw, cursor);
        Assert.assertNotNull("cn", result.getName());
        Assert.assertEquals("blah", result.getValue());
    }

    @Test
    public void testParameterParsingBlanks() throws Exception {
        final String s = "  cn  =     blah    ,stuff";
        final CharArrayBuffer raw = createBuffer(s);
        final ParserCursor cursor = new ParserCursor(0, s.length());
        final NameValuePair result = parser.parseParameter(raw, cursor);
        Assert.assertNotNull("cn", result.getName());
        Assert.assertEquals("blah", result.getValue());
        Assert.assertEquals('s', raw.charAt(cursor.getPos()));
    }

    @Test
    public void testParameterParsingEmptyValue() throws Exception {
        final String s = "  cn  =    ,stuff ";
        final CharArrayBuffer raw = createBuffer(s);
        final ParserCursor cursor = new ParserCursor(0, s.length());
        final NameValuePair result = parser.parseParameter(raw, cursor);
        Assert.assertNotNull("cn", result.getName());
        Assert.assertEquals("", result.getValue());
        Assert.assertEquals('s', raw.charAt(cursor.getPos()));
    }

    @Test
    public void testParameterParsingNullValue() throws Exception {
        final String s = "  cn     ";
        final CharArrayBuffer raw = createBuffer(s);
        final ParserCursor cursor = new ParserCursor(0, s.length());
        final NameValuePair result = parser.parseParameter(raw, cursor);
        Assert.assertNotNull("cn", result.getName());
        Assert.assertEquals(null, result.getValue());
        Assert.assertTrue(cursor.atEnd());
    }

    @Test
    public void testParameterParsingQuotedValue() throws Exception {
        final String s = "cn = \"blah, blah\"  ,stuff";
        final CharArrayBuffer raw = createBuffer(s);
        final ParserCursor cursor = new ParserCursor(0, s.length());
        final NameValuePair result = parser.parseParameter(raw, cursor);
        Assert.assertNotNull("cn", result.getName());
        Assert.assertEquals("blah, blah", result.getValue());
        Assert.assertEquals('s', raw.charAt(cursor.getPos()));
    }

    @Test
    public void testParameterParsingQuotedValueWithEscapedQuotes() throws Exception {
        final String s = "cn = \"blah, blah, \\\"yada, yada\\\"\"  ,stuff";
        final CharArrayBuffer raw = createBuffer(s);
        final ParserCursor cursor = new ParserCursor(0, s.length());
        final NameValuePair result = parser.parseParameter(raw, cursor);
        Assert.assertNotNull("cn", result.getName());
        Assert.assertEquals("blah, blah, \"yada, yada\"", result.getValue());
        Assert.assertEquals('s', raw.charAt(cursor.getPos()));
    }

    @Test
    public void testParameterParsingValueWithEscapedDelimiter() throws Exception {
        final String s = "cn = blah\\, blah\\, blah  ,stuff";
        final CharArrayBuffer raw = createBuffer(s);
        final ParserCursor cursor = new ParserCursor(0, s.length());
        final NameValuePair result = parser.parseParameter(raw, cursor);
        Assert.assertNotNull("cn", result.getName());
        Assert.assertEquals("blah, blah, blah", result.getValue());
        Assert.assertEquals('s', raw.charAt(cursor.getPos()));
    }

    @Test
    public void testDNParsing() throws Exception {
        Assert.assertEquals(Arrays.asList(
                        new BasicNameValuePair("cn", "blah"),
                        new BasicNameValuePair("cn", "yada"),
                        new BasicNameValuePair("cn", "booh")),
                parser.parse("cn=blah, cn=yada, cn=booh"));
        Assert.assertEquals(Arrays.asList(
                        new BasicNameValuePair("c", "pampa"),
                        new BasicNameValuePair("cn", "blah"),
                        new BasicNameValuePair("ou", "blah"),
                        new BasicNameValuePair("o", "blah")),
                parser.parse("c = pampa ,  cn  =    blah    , ou = blah , o = blah"));
        Assert.assertEquals(Arrays.asList(
                        new BasicNameValuePair("cn", "blah"),
                        new BasicNameValuePair("ou", "blah"),
                        new BasicNameValuePair("o", "blah")),
                parser.parse("cn=\"blah\", ou=blah, o=blah"));
        Assert.assertEquals(Arrays.asList(
                        new BasicNameValuePair("cn", "blah  blah"),
                        new BasicNameValuePair("ou", "blah"),
                        new BasicNameValuePair("o", "blah")),
                parser.parse("cn=\"blah  blah\", ou=blah, o=blah"));
        Assert.assertEquals(Arrays.asList(
                        new BasicNameValuePair("cn", "blah, blah"),
                        new BasicNameValuePair("ou", "blah"),
                        new BasicNameValuePair("o", "blah")),
                parser.parse("cn=\"blah, blah\", ou=blah, o=blah"));
        Assert.assertEquals(Arrays.asList(
                        new BasicNameValuePair("cn", "blah, blah"),
                        new BasicNameValuePair("ou", "blah"),
                        new BasicNameValuePair("o", "blah")),
                parser.parse("cn=blah\\, blah, ou=blah, o=blah"));
        Assert.assertEquals(Arrays.asList(
                        new BasicNameValuePair("c", "cn=uuh"),
                        new BasicNameValuePair("cn", "blah"),
                        new BasicNameValuePair("ou", "blah"),
                        new BasicNameValuePair("o", "blah")),
                parser.parse("c = cn=uuh, cn=blah, ou=blah, o=blah"));
        Assert.assertEquals(Arrays.asList(
                        new BasicNameValuePair("cn", ""),
                        new BasicNameValuePair("ou", "blah"),
                        new BasicNameValuePair("o", "blah")),
                parser.parse("cn=   , ou=blah, o=blah"));
    }

}
