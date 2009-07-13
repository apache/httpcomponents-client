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

package org.apache.http.client.methods;

import java.io.ByteArrayInputStream;

import org.apache.http.Header;
import org.apache.http.HttpVersion;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.util.LangUtils;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class TestHttpRequestBase extends TestCase {

    // ------------------------------------------------------------ Constructor
    public TestHttpRequestBase(final String testName) {
        super(testName);
    }

    // ------------------------------------------------------------------- Main
    public static void main(String args[]) {
        String[] testCaseName = { TestHttpRequestBase.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        return new TestSuite(TestHttpRequestBase.class);
    }

    public void testBasicProperties() throws Exception {
        HttpGet httpget = new HttpGet("http://host/path");
        httpget.getParams().setParameter(
                HttpProtocolParams.PROTOCOL_VERSION, HttpVersion.HTTP_1_0);
        assertEquals("GET", httpget.getRequestLine().getMethod());
        assertEquals("http://host/path", httpget.getRequestLine().getUri());
        assertEquals(HttpVersion.HTTP_1_0, httpget.getRequestLine().getProtocolVersion());
    }
    
    public void testEmptyURI() throws Exception {
        HttpGet httpget = new HttpGet("");
        assertEquals("/", httpget.getRequestLine().getUri());
    }
    
    public void testCloneBasicRequests() throws Exception {
        HttpGet httpget = new HttpGet("http://host/path");
        httpget.addHeader("h1", "this header");
        httpget.addHeader("h2", "that header");
        httpget.addHeader("h3", "all sorts of headers");
        httpget.getParams().setParameter("p1", Integer.valueOf(1));
        httpget.getParams().setParameter("p2", "whatever");
        HttpGet clone = (HttpGet) httpget.clone();
        
        assertEquals(httpget.getMethod(), clone.getMethod());
        assertEquals(httpget.getURI(), clone.getURI());
        
        Header[] headers1 = httpget.getAllHeaders();
        Header[] headers2 = clone.getAllHeaders();
        
        assertTrue(LangUtils.equals(headers1, headers2));
        assertTrue(httpget.getParams() != clone.getParams());
        
        assertEquals(Integer.valueOf(1), clone.getParams().getParameter("p1"));
        assertEquals("whatever", clone.getParams().getParameter("p2"));
        assertEquals(null, clone.getParams().getParameter("p3"));
    }
    
    public void testCloneEntityEnclosingRequests() throws Exception {
        HttpPost httppost = new HttpPost("http://host/path");
        httppost.addHeader("h1", "this header");
        httppost.addHeader("h2", "that header");
        httppost.addHeader("h3", "all sorts of headers");
        httppost.getParams().setParameter("p1", Integer.valueOf(1));
        httppost.getParams().setParameter("p2", "whatever");
        HttpPost clone = (HttpPost) httppost.clone();
        
        assertEquals(httppost.getMethod(), clone.getMethod());
        assertEquals(httppost.getURI(), clone.getURI());
        
        Header[] headers1 = httppost.getAllHeaders();
        Header[] headers2 = clone.getAllHeaders();
        
        assertTrue(LangUtils.equals(headers1, headers2));
        assertTrue(httppost.getParams() != clone.getParams());
        
        assertEquals(Integer.valueOf(1), clone.getParams().getParameter("p1"));
        assertEquals("whatever", clone.getParams().getParameter("p2"));
        assertEquals(null, clone.getParams().getParameter("p3"));
        
        assertNull(clone.getEntity());
        
        StringEntity e1 = new StringEntity("stuff");
        httppost.setEntity(e1);
        clone = (HttpPost) httppost.clone();
        assertTrue(clone.getEntity() instanceof StringEntity);
        assertFalse(clone.getEntity().equals(e1));
        
        ByteArrayInputStream instream = new ByteArrayInputStream(new byte[] {}); 
        InputStreamEntity e2 = new InputStreamEntity(instream, -1);
        httppost.setEntity(e2);
        
        try {
            httppost.clone();
            fail("CloneNotSupportedException should have been thrown");
        } catch (CloneNotSupportedException expected) {
        }
    }
}
