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

import org.apache.http.NameValuePair;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.junit.Assert;
import org.junit.Test;

public class TestURLEncodedUtils {

    @Test
    public void testParseURI () throws Exception {
        List <NameValuePair> result;

        result = parse("", null);
        Assert.assertTrue(result.isEmpty());

        result = parse("Name1=Value1", null);
        Assert.assertEquals(1, result.size());
        assertNameValuePair(result.get(0), "Name1", "Value1");

        result = parse("Name2=", null);
        Assert.assertEquals(1, result.size());
        assertNameValuePair(result.get(0), "Name2", null);

        result = parse("Name3", null);
        Assert.assertEquals(1, result.size());
        assertNameValuePair(result.get(0), "Name3", null);

        result = parse("Name4=Value+4%21", null);
        Assert.assertEquals(1, result.size());
        assertNameValuePair(result.get(0), "Name4", "Value 4!");

        result = parse("Name4=Value%2B4%21", null);
        Assert.assertEquals(1, result.size());
        assertNameValuePair(result.get(0), "Name4", "Value+4!");

        result = parse("Name4=Value+4%21+%214", null);
        Assert.assertEquals(1, result.size());
        assertNameValuePair(result.get(0), "Name4", "Value 4! !4");

        result = parse("Name5=aaa&Name6=bbb", null);
        Assert.assertEquals(2, result.size());
        assertNameValuePair(result.get(0), "Name5", "aaa");
        assertNameValuePair(result.get(1), "Name6", "bbb");

        result = parse("Name7=aaa&Name7=b%2Cb&Name7=ccc", null);
        Assert.assertEquals(3, result.size());
        assertNameValuePair(result.get(0), "Name7", "aaa");
        assertNameValuePair(result.get(1), "Name7", "b,b");
        assertNameValuePair(result.get(2), "Name7", "ccc");

        result = parse("Name8=xx%2C++yy++%2Czz", null);
        Assert.assertEquals(1, result.size());
        assertNameValuePair(result.get(0), "Name8", "xx,  yy  ,zz");
    }

    @Test
    public void testParseEntity () throws Exception {
        final StringEntity entity = new StringEntity("Name1=Value1", null);

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

    private static String constructString(int [] unicodeChars) {
        StringBuffer buffer = new StringBuffer();
        if (unicodeChars != null) {
            for (int i = 0; i < unicodeChars.length; i++) {
                buffer.append((char)unicodeChars[i]);
            }
        }
        return buffer.toString();
    }

    @Test
    public void testParseUTF8Entity () throws Exception {
        String ru_hello = constructString(RUSSIAN_HELLO);
        String ch_hello = constructString(SWISS_GERMAN_HELLO);
        List <NameValuePair> parameters = new ArrayList<NameValuePair>();
        parameters.add(new BasicNameValuePair("russian", ru_hello));
        parameters.add(new BasicNameValuePair("swiss", ch_hello));

        String s = URLEncodedUtils.format(parameters, HTTP.UTF_8);

        Assert.assertEquals("russian=%D0%92%D1%81%D0%B5%D0%BC_%D0%BF%D1%80%D0%B8%D0%B2%D0%B5%D1%82" +
                "&swiss=Gr%C3%BCezi_z%C3%A4m%C3%A4", s);

        StringEntity entity = new StringEntity(s, HTTP.UTF_8);
        entity.setContentType(URLEncodedUtils.CONTENT_TYPE + HTTP.CHARSET_PARAM + HTTP.UTF_8);
        List <NameValuePair> result = URLEncodedUtils.parse(entity);
        Assert.assertEquals(2, result.size());
        assertNameValuePair(result.get(0), "russian", ru_hello);
        assertNameValuePair(result.get(1), "swiss", ch_hello);
    }

    @Test
    public void testIsEncoded () throws Exception {
        final StringEntity entity = new StringEntity("...", null);

        entity.setContentType(URLEncodedUtils.CONTENT_TYPE);
        Assert.assertTrue(URLEncodedUtils.isEncoded(entity));

        entity.setContentType(URLEncodedUtils.CONTENT_TYPE + "; charset=US-ASCII");
        Assert.assertTrue(URLEncodedUtils.isEncoded(entity));

        entity.setContentType("text/test");
        Assert.assertFalse(URLEncodedUtils.isEncoded(entity));
    }

    @Test
    public void testFormat () throws Exception {
        final List <NameValuePair> params = new ArrayList <NameValuePair>();
        Assert.assertEquals(0, URLEncodedUtils.format(params, null).length());

        params.clear();
        params.add(new BasicNameValuePair("Name1", "Value1"));
        Assert.assertEquals("Name1=Value1", URLEncodedUtils.format(params, null));

        params.clear();
        params.add(new BasicNameValuePair("Name2", null));
        Assert.assertEquals("Name2=", URLEncodedUtils.format(params, null));

        params.clear();
        params.add(new BasicNameValuePair("Name4", "Value 4!"));
        Assert.assertEquals("Name4=Value+4%21", URLEncodedUtils.format(params, null));

        params.clear();
        params.add(new BasicNameValuePair("Name4", "Value+4!"));
        Assert.assertEquals("Name4=Value%2B4%21", URLEncodedUtils.format(params, null));

        params.clear();
        params.add(new BasicNameValuePair("Name4", "Value 4! !4"));
        Assert.assertEquals("Name4=Value+4%21+%214", URLEncodedUtils.format(params, null));

        params.clear();
        params.add(new BasicNameValuePair("Name5", "aaa"));
        params.add(new BasicNameValuePair("Name6", "bbb"));
        Assert.assertEquals("Name5=aaa&Name6=bbb", URLEncodedUtils.format(params, null));

        params.clear();
        params.add(new BasicNameValuePair("Name7", "aaa"));
        params.add(new BasicNameValuePair("Name7", "b,b"));
        params.add(new BasicNameValuePair("Name7", "ccc"));
        Assert.assertEquals("Name7=aaa&Name7=b%2Cb&Name7=ccc", URLEncodedUtils.format(params, null));

        params.clear();
        params.add(new BasicNameValuePair("Name8", "xx,  yy  ,zz"));
        Assert.assertEquals("Name8=xx%2C++yy++%2Czz", URLEncodedUtils.format(params, null));
    }

    private List <NameValuePair> parse (final String params, final String encoding) {
        return URLEncodedUtils.parse(URI.create("http://hc.apache.org/params?" + params), encoding);
    }

    private static void assertNameValuePair (
            final NameValuePair parameter,
            final String expectedName,
            final String expectedValue) {
        Assert.assertEquals(parameter.getName(), expectedName);
        Assert.assertEquals(parameter.getValue(), expectedValue);
    }

}