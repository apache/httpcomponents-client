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

package org.apache.http.client.utils;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.Consts;
import org.apache.http.NameValuePair;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.junit.Assert;
import org.junit.Test;

public class TestURLEncodedUtils {

    @Test
    public void testParseURLCodedContent() throws Exception {
        List <NameValuePair> result;

        result = parse("");
        Assert.assertTrue(result.isEmpty());

        result = parse("Name0");
        Assert.assertEquals(1, result.size());
        assertNameValuePair(result.get(0), "Name0", null);

        result = parse("Name1=Value1");
        Assert.assertEquals(1, result.size());
        assertNameValuePair(result.get(0), "Name1", "Value1");

        result = parse("Name2=");
        Assert.assertEquals(1, result.size());
        assertNameValuePair(result.get(0), "Name2", "");

        result = parse("Name3");
        Assert.assertEquals(1, result.size());
        assertNameValuePair(result.get(0), "Name3", null);

        result = parse("Name4=Value%204%21");
        Assert.assertEquals(1, result.size());
        assertNameValuePair(result.get(0), "Name4", "Value 4!");

        result = parse("Name4=Value%2B4%21");
        Assert.assertEquals(1, result.size());
        assertNameValuePair(result.get(0), "Name4", "Value+4!");

        result = parse("Name4=Value%204%21%20%214");
        Assert.assertEquals(1, result.size());
        assertNameValuePair(result.get(0), "Name4", "Value 4! !4");

        result = parse("Name5=aaa&Name6=bbb");
        Assert.assertEquals(2, result.size());
        assertNameValuePair(result.get(0), "Name5", "aaa");
        assertNameValuePair(result.get(1), "Name6", "bbb");

        result = parse("Name7=aaa&Name7=b%2Cb&Name7=ccc");
        Assert.assertEquals(3, result.size());
        assertNameValuePair(result.get(0), "Name7", "aaa");
        assertNameValuePair(result.get(1), "Name7", "b,b");
        assertNameValuePair(result.get(2), "Name7", "ccc");

        result = parse("Name8=xx%2C%20%20yy%20%20%2Czz");
        Assert.assertEquals(1, result.size());
        assertNameValuePair(result.get(0), "Name8", "xx,  yy  ,zz");

        result = parse("price=10%20%E2%82%AC");
        Assert.assertEquals(1, result.size());
        assertNameValuePair(result.get(0), "price", "10 \u20AC");
    }

    @Test
    public void testParseURLCodedContentString() throws Exception {
        List <NameValuePair> result;

        result = parseString("");
        Assert.assertTrue(result.isEmpty());

        result = parseString("Name0");
        Assert.assertEquals(1, result.size());
        assertNameValuePair(result.get(0), "Name0", null);

        result = parseString("Name1=Value1");
        Assert.assertEquals(1, result.size());
        assertNameValuePair(result.get(0), "Name1", "Value1");

        result = parseString("Name2=");
        Assert.assertEquals(1, result.size());
        assertNameValuePair(result.get(0), "Name2", "");

        result = parseString("Name3");
        Assert.assertEquals(1, result.size());
        assertNameValuePair(result.get(0), "Name3", null);

        result = parseString("Name4=Value%204%21");
        Assert.assertEquals(1, result.size());
        assertNameValuePair(result.get(0), "Name4", "Value 4!");

        result = parseString("Name4=Value%2B4%21");
        Assert.assertEquals(1, result.size());
        assertNameValuePair(result.get(0), "Name4", "Value+4!");

        result = parseString("Name4=Value%204%21%20%214");
        Assert.assertEquals(1, result.size());
        assertNameValuePair(result.get(0), "Name4", "Value 4! !4");

        result = parseString("Name5=aaa&Name6=bbb");
        Assert.assertEquals(2, result.size());
        assertNameValuePair(result.get(0), "Name5", "aaa");
        assertNameValuePair(result.get(1), "Name6", "bbb");

        result = parseString("Name7=aaa&Name7=b%2Cb&Name7=ccc");
        Assert.assertEquals(3, result.size());
        assertNameValuePair(result.get(0), "Name7", "aaa");
        assertNameValuePair(result.get(1), "Name7", "b,b");
        assertNameValuePair(result.get(2), "Name7", "ccc");

        result = parseString("Name8=xx%2C%20%20yy%20%20%2Czz");
        Assert.assertEquals(1, result.size());
        assertNameValuePair(result.get(0), "Name8", "xx,  yy  ,zz");

        result = parseString("price=10%20%E2%82%AC");
        Assert.assertEquals(1, result.size());
        assertNameValuePair(result.get(0), "price", "10 \u20AC");
    }

    @Test
    public void testParseInvalidURLCodedContent() throws Exception {
        List <NameValuePair> result;

        result = parse("name=%");
        Assert.assertEquals(1, result.size());
        assertNameValuePair(result.get(0), "name", "%");

        result = parse("name=%a");
        Assert.assertEquals(1, result.size());
        assertNameValuePair(result.get(0), "name", "%a");

        result = parse("name=%wa%20");
        Assert.assertEquals(1, result.size());
        assertNameValuePair(result.get(0), "name", "%wa ");
    }

    @Test
    public void testParseEntity() throws Exception {
        final StringEntity entity = new StringEntity("Name1=Value1");

        entity.setContentType(URLEncodedUtils.CONTENT_TYPE);
        final List <NameValuePair> result = URLEncodedUtils.parse(entity);
        Assert.assertEquals(1, result.size());
        assertNameValuePair(result.get(0), "Name1", "Value1");

        entity.setContentType("text/test");
        Assert.assertTrue(URLEncodedUtils.parse(entity).isEmpty());
    }

    private static final int SWISS_GERMAN_HELLO [] = {
        0x47, 0x72, 0xFC, 0x65, 0x7A, 0x69, 0x5F, 0x7A, 0xE4, 0x6D, 0xE4
    };

    private static final int RUSSIAN_HELLO [] = {
        0x412, 0x441, 0x435, 0x43C, 0x5F, 0x43F, 0x440, 0x438,
        0x432, 0x435, 0x442
    };

    private static String constructString(final int [] unicodeChars) {
        final StringBuilder buffer = new StringBuilder();
        if (unicodeChars != null) {
            for (final int unicodeChar : unicodeChars) {
                buffer.append((char)unicodeChar);
            }
        }
        return buffer.toString();
    }

    @Test
    public void testParseUTF8Entity() throws Exception {
        final String ru_hello = constructString(RUSSIAN_HELLO);
        final String ch_hello = constructString(SWISS_GERMAN_HELLO);
        final List <NameValuePair> parameters = new ArrayList<NameValuePair>();
        parameters.add(new BasicNameValuePair("russian", ru_hello));
        parameters.add(new BasicNameValuePair("swiss", ch_hello));

        final String s = URLEncodedUtils.format(parameters, Consts.UTF_8);

        Assert.assertEquals("russian=%D0%92%D1%81%D0%B5%D0%BC_%D0%BF%D1%80%D0%B8%D0%B2%D0%B5%D1%82" +
                "&swiss=Gr%C3%BCezi_z%C3%A4m%C3%A4", s);

        final StringEntity entity = new StringEntity(s, ContentType.create(
                URLEncodedUtils.CONTENT_TYPE, Consts.UTF_8));
        final List <NameValuePair> result = URLEncodedUtils.parse(entity);
        Assert.assertEquals(2, result.size());
        assertNameValuePair(result.get(0), "russian", ru_hello);
        assertNameValuePair(result.get(1), "swiss", ch_hello);
    }

    @Test
    public void testParseUTF8Ampersand1String() throws Exception {
        final String ru_hello = constructString(RUSSIAN_HELLO);
        final String ch_hello = constructString(SWISS_GERMAN_HELLO);
        final List <NameValuePair> parameters = new ArrayList<NameValuePair>();
        parameters.add(new BasicNameValuePair("russian", ru_hello));
        parameters.add(new BasicNameValuePair("swiss", ch_hello));

        final String s = URLEncodedUtils.format(parameters, Consts.UTF_8);

        final List <NameValuePair> result = URLEncodedUtils.parse(s, Consts.UTF_8);
        Assert.assertEquals(2, result.size());
        assertNameValuePair(result.get(0), "russian", ru_hello);
        assertNameValuePair(result.get(1), "swiss", ch_hello);
    }

    @Test
    public void testParseUTF8Ampersand2String() throws Exception {
        testParseUTF8String('&');
    }

    @Test
    public void testParseUTF8SemicolonString() throws Exception {
        testParseUTF8String(';');
    }

    private void testParseUTF8String(final char parameterSeparator) throws Exception {
        final String ru_hello = constructString(RUSSIAN_HELLO);
        final String ch_hello = constructString(SWISS_GERMAN_HELLO);
        final List <NameValuePair> parameters = new ArrayList<NameValuePair>();
        parameters.add(new BasicNameValuePair("russian", ru_hello));
        parameters.add(new BasicNameValuePair("swiss", ch_hello));

        final String s = URLEncodedUtils.format(parameters, parameterSeparator, Consts.UTF_8);

        final List <NameValuePair> result1 = URLEncodedUtils.parse(s, Consts.UTF_8);
        Assert.assertEquals(2, result1.size());
        assertNameValuePair(result1.get(0), "russian", ru_hello);
        assertNameValuePair(result1.get(1), "swiss", ch_hello);

        final List <NameValuePair> result2 = URLEncodedUtils.parse(s, Consts.UTF_8, parameterSeparator);
        Assert.assertEquals(result1, result2);
    }

    @Test
    public void testParseEntityDefaultContentType() throws Exception {
        final String ch_hello = constructString(SWISS_GERMAN_HELLO);
        final String us_hello = "hi there";
        final List <NameValuePair> parameters = new ArrayList<NameValuePair>();
        parameters.add(new BasicNameValuePair("english", us_hello));
        parameters.add(new BasicNameValuePair("swiss", ch_hello));

        final String s = URLEncodedUtils.format(parameters, HTTP.DEF_CONTENT_CHARSET);

        Assert.assertEquals("english=hi+there&swiss=Gr%FCezi_z%E4m%E4", s);

        final StringEntity entity = new StringEntity(s, ContentType.create(
                URLEncodedUtils.CONTENT_TYPE, HTTP.DEF_CONTENT_CHARSET));
        final List <NameValuePair> result = URLEncodedUtils.parse(entity);
        Assert.assertEquals(2, result.size());
        assertNameValuePair(result.get(0), "english", us_hello);
        assertNameValuePair(result.get(1), "swiss", ch_hello);
    }

    @Test
    public void testIsEncoded() throws Exception {
        final StringEntity entity = new StringEntity("...");

        entity.setContentType(URLEncodedUtils.CONTENT_TYPE);
        Assert.assertTrue(URLEncodedUtils.isEncoded(entity));

        entity.setContentType(URLEncodedUtils.CONTENT_TYPE + "; charset=US-ASCII");
        Assert.assertTrue(URLEncodedUtils.isEncoded(entity));

        entity.setContentType("text/test");
        Assert.assertFalse(URLEncodedUtils.isEncoded(entity));
    }

    @Test
    public void testFormat() throws Exception {
        final List <NameValuePair> params = new ArrayList <NameValuePair>();
        Assert.assertEquals(0, URLEncodedUtils.format(params, Consts.ASCII).length());

        params.clear();
        params.add(new BasicNameValuePair("Name0", null));
        Assert.assertEquals("Name0", URLEncodedUtils.format(params, Consts.ASCII));

        params.clear();
        params.add(new BasicNameValuePair("Name1", "Value1"));
        Assert.assertEquals("Name1=Value1", URLEncodedUtils.format(params, Consts.ASCII));

        params.clear();
        params.add(new BasicNameValuePair("Name2", ""));
        Assert.assertEquals("Name2=", URLEncodedUtils.format(params, Consts.ASCII));

        params.clear();
        params.add(new BasicNameValuePair("Name4", "Value 4&"));
        Assert.assertEquals("Name4=Value+4%26", URLEncodedUtils.format(params, Consts.ASCII));

        params.clear();
        params.add(new BasicNameValuePair("Name4", "Value+4&"));
        Assert.assertEquals("Name4=Value%2B4%26", URLEncodedUtils.format(params, Consts.ASCII));

        params.clear();
        params.add(new BasicNameValuePair("Name4", "Value 4& =4"));
        Assert.assertEquals("Name4=Value+4%26+%3D4", URLEncodedUtils.format(params, Consts.ASCII));

        params.clear();
        params.add(new BasicNameValuePair("Name5", "aaa"));
        params.add(new BasicNameValuePair("Name6", "bbb"));
        Assert.assertEquals("Name5=aaa&Name6=bbb", URLEncodedUtils.format(params, Consts.ASCII));

        params.clear();
        params.add(new BasicNameValuePair("Name7", "aaa"));
        params.add(new BasicNameValuePair("Name7", "b,b"));
        params.add(new BasicNameValuePair("Name7", "ccc"));
        Assert.assertEquals("Name7=aaa&Name7=b%2Cb&Name7=ccc", URLEncodedUtils.format(params, Consts.ASCII));
        Assert.assertEquals("Name7=aaa&Name7=b%2Cb&Name7=ccc", URLEncodedUtils.format(params, '&', Consts.ASCII));
        Assert.assertEquals("Name7=aaa;Name7=b%2Cb;Name7=ccc", URLEncodedUtils.format(params, ';', Consts.ASCII));

        params.clear();
        params.add(new BasicNameValuePair("Name8", "xx,  yy  ,zz"));
        Assert.assertEquals("Name8=xx%2C++yy++%2Czz", URLEncodedUtils.format(params, Consts.ASCII));
    }

    @Test
    public void testFormatString() throws Exception { // as above, using String
        final List <NameValuePair> params = new ArrayList <NameValuePair>();
        Assert.assertEquals(0, URLEncodedUtils.format(params, "US-ASCII").length());

        params.clear();
        params.add(new BasicNameValuePair("Name0", null));
        Assert.assertEquals("Name0", URLEncodedUtils.format(params, "US-ASCII"));

        params.clear();
        params.add(new BasicNameValuePair("Name1", "Value1"));
        Assert.assertEquals("Name1=Value1", URLEncodedUtils.format(params, "US-ASCII"));

        params.clear();
        params.add(new BasicNameValuePair("Name2", ""));
        Assert.assertEquals("Name2=", URLEncodedUtils.format(params, "US-ASCII"));

        params.clear();
        params.add(new BasicNameValuePair("Name4", "Value 4&"));
        Assert.assertEquals("Name4=Value+4%26", URLEncodedUtils.format(params, "US-ASCII"));

        params.clear();
        params.add(new BasicNameValuePair("Name4", "Value+4&"));
        Assert.assertEquals("Name4=Value%2B4%26", URLEncodedUtils.format(params, "US-ASCII"));

        params.clear();
        params.add(new BasicNameValuePair("Name4", "Value 4& =4"));
        Assert.assertEquals("Name4=Value+4%26+%3D4", URLEncodedUtils.format(params, "US-ASCII"));

        params.clear();
        params.add(new BasicNameValuePair("Name5", "aaa"));
        params.add(new BasicNameValuePair("Name6", "bbb"));
        Assert.assertEquals("Name5=aaa&Name6=bbb", URLEncodedUtils.format(params, "US-ASCII"));

        params.clear();
        params.add(new BasicNameValuePair("Name7", "aaa"));
        params.add(new BasicNameValuePair("Name7", "b,b"));
        params.add(new BasicNameValuePair("Name7", "ccc"));
        Assert.assertEquals("Name7=aaa&Name7=b%2Cb&Name7=ccc", URLEncodedUtils.format(params, "US-ASCII"));

        params.clear();
        params.add(new BasicNameValuePair("Name8", "xx,  yy  ,zz"));
        Assert.assertEquals("Name8=xx%2C++yy++%2Czz", URLEncodedUtils.format(params, "US-ASCII"));
    }

    private List <NameValuePair> parse (final String params) {
        return URLEncodedUtils.parse(params, Consts.UTF_8);
    }

    private List <NameValuePair> parseString (final String uri) throws Exception {
        return URLEncodedUtils.parse(new URI("?"+uri), "UTF-8");
    }

    private static void assertNameValuePair (
            final NameValuePair parameter,
            final String expectedName,
            final String expectedValue) {
        Assert.assertEquals(parameter.getName(), expectedName);
        Assert.assertEquals(parameter.getValue(), expectedValue);
    }

}
