/*
 * $HeadURL:$
 * $Revision:$
 * $Date:$
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

import java.io.IOException;

import org.apache.http.HttpVersion;
import org.apache.http.params.HttpProtocolParams;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class TestHttpRequestBase extends TestCase {

    // ------------------------------------------------------------ Constructor
    public TestHttpRequestBase(final String testName) throws IOException {
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
    
}
