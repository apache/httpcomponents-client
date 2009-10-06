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

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.http.NameValuePair;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicNameValuePair;

public class TestURLEncodedUtils extends TestCase {
  
    public TestURLEncodedUtils(final String testName) {
        super(testName);
    }

    public static void main(String args[]) {
        String[] testCaseName = { TestURLEncodedUtils.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    public static Test suite() {
        return new TestSuite(TestURLEncodedUtils.class);
    }
    
    public void testParseURI () throws Exception {
        List <NameValuePair> result;

        result = parse("", null);
        assertTrue(result.isEmpty());

        result = parse("Name1=Value1", null);
        assertEquals(1, result.size());
        assertNameValuePair(result.get(0), "Name1", "Value1");

        result = parse("Name2=", null);
        assertEquals(1, result.size());
        assertNameValuePair(result.get(0), "Name2", null);

        result = parse("Name3", null);
        assertEquals(1, result.size());
        assertNameValuePair(result.get(0), "Name3", null);

        result = parse("Name4=Value+4%21", null);
        assertEquals(1, result.size());
        assertNameValuePair(result.get(0), "Name4", "Value 4!");

        result = parse("Name4=Value%2B4%21", null);
        assertEquals(1, result.size());
        assertNameValuePair(result.get(0), "Name4", "Value+4!");

        result = parse("Name4=Value+4%21+%214", null);
        assertEquals(1, result.size());
        assertNameValuePair(result.get(0), "Name4", "Value 4! !4");

        result = parse("Name5=aaa&Name6=bbb", null);
        assertEquals(2, result.size());
        assertNameValuePair(result.get(0), "Name5", "aaa");
        assertNameValuePair(result.get(1), "Name6", "bbb");

        result = parse("Name7=aaa&Name7=b%2Cb&Name7=ccc", null);
        assertEquals(3, result.size());
        assertNameValuePair(result.get(0), "Name7", "aaa");
        assertNameValuePair(result.get(1), "Name7", "b,b");
        assertNameValuePair(result.get(2), "Name7", "ccc");

        result = parse("Name8=xx%2C++yy++%2Czz", null);
        assertEquals(1, result.size());
        assertNameValuePair(result.get(0), "Name8", "xx,  yy  ,zz");
    }

    public void testParseEntity () throws Exception {
        final StringEntity entity = new StringEntity("Name1=Value1", null);

        entity.setContentType(URLEncodedUtils.CONTENT_TYPE);
        final List <NameValuePair> result = URLEncodedUtils.parse(entity);
        assertEquals(1, result.size());
        assertNameValuePair(result.get(0), "Name1", "Value1");

        entity.setContentType("text/test");
        assertTrue(URLEncodedUtils.parse(entity).isEmpty());
    }

    public void testIsEncoded () throws Exception {
        final StringEntity entity = new StringEntity("...", null);

        entity.setContentType(URLEncodedUtils.CONTENT_TYPE);
        assertTrue(URLEncodedUtils.isEncoded(entity));

        entity.setContentType(URLEncodedUtils.CONTENT_TYPE + "; charset=US-ASCII");
        assertTrue(URLEncodedUtils.isEncoded(entity));

        entity.setContentType("text/test");
        assertFalse(URLEncodedUtils.isEncoded(entity));
    }

    public void testFormat () throws Exception {
        final List <NameValuePair> params = new ArrayList <NameValuePair>();
        assertEquals(0, URLEncodedUtils.format(params, null).length());

        params.clear();
        params.add(new BasicNameValuePair("Name1", "Value1"));
        assertEquals("Name1=Value1", URLEncodedUtils.format(params, null));

        params.clear();
        params.add(new BasicNameValuePair("Name2", null));
        assertEquals("Name2=", URLEncodedUtils.format(params, null));

        params.clear();
        params.add(new BasicNameValuePair("Name4", "Value 4!"));
        assertEquals("Name4=Value+4%21", URLEncodedUtils.format(params, null));

        params.clear();
        params.add(new BasicNameValuePair("Name4", "Value+4!"));
        assertEquals("Name4=Value%2B4%21", URLEncodedUtils.format(params, null));

        params.clear();
        params.add(new BasicNameValuePair("Name4", "Value 4! !4"));
        assertEquals("Name4=Value+4%21+%214", URLEncodedUtils.format(params, null));

        params.clear();
        params.add(new BasicNameValuePair("Name5", "aaa"));
        params.add(new BasicNameValuePair("Name6", "bbb"));
        assertEquals("Name5=aaa&Name6=bbb", URLEncodedUtils.format(params, null));

        params.clear();
        params.add(new BasicNameValuePair("Name7", "aaa"));
        params.add(new BasicNameValuePair("Name7", "b,b"));
        params.add(new BasicNameValuePair("Name7", "ccc"));
        assertEquals("Name7=aaa&Name7=b%2Cb&Name7=ccc", URLEncodedUtils.format(params, null));

        params.clear();
        params.add(new BasicNameValuePair("Name8", "xx,  yy  ,zz"));
        assertEquals("Name8=xx%2C++yy++%2Czz", URLEncodedUtils.format(params, null));
    }

    private List <NameValuePair> parse (final String params, final String encoding) {
        return URLEncodedUtils.parse(URI.create("http://hc.apache.org/params?" + params), encoding);
    }

    private static void assertNameValuePair (
            final NameValuePair parameter, 
            final String expectedName, 
            final String expectedValue) {
        assertEquals(parameter.getName(), expectedName);
        assertEquals(parameter.getValue(), expectedValue);
    }
}