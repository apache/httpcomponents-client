/*
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.impl.auth;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AUTH;
import org.apache.http.auth.MalformedChallengeException;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.util.EncodingUtils;

/**
 * Basic authentication test cases.
 *
 * @version $Id$
 */
public class TestBasicScheme extends TestCase {

    // ------------------------------------------------------------ Constructor
    public TestBasicScheme(final String testName) {
        super(testName);
    }

    // ------------------------------------------------------------------- Main
    public static void main(String args[]) {
        String[] testCaseName = { TestBasicScheme.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        TestSuite suite = new TestSuite(TestBasicScheme.class);
        return suite;
    }

    public void testBasicAuthenticationWithNoRealm() {
        String challenge = "Basic";
        Header header = new BasicHeader(AUTH.WWW_AUTH, challenge);
        try {
            AuthScheme authscheme = new BasicScheme();
            authscheme.processChallenge(header);
            fail("Should have thrown MalformedChallengeException");
        } catch(MalformedChallengeException e) {
            // expected
        }
    }

    public void testBasicAuthenticationWith88591Chars() throws Exception {
        int[] germanChars = { 0xE4, 0x2D, 0xF6, 0x2D, 0xFc };
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < germanChars.length; i++) {
            buffer.append((char)germanChars[i]); 
        }
        
        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials("dh", buffer.toString());
        Header header = BasicScheme.authenticate(credentials, "ISO-8859-1", false);
        assertEquals("Basic ZGg65C32Lfw=", header.getValue());
    }
    
    public void testBasicAuthentication() throws Exception {
        UsernamePasswordCredentials creds = 
            new UsernamePasswordCredentials("testuser", "testpass");
        
        Header challenge = new BasicHeader(AUTH.WWW_AUTH, "Basic realm=\"test\"");
        
        BasicScheme authscheme = new BasicScheme();
        authscheme.processChallenge(challenge);
        
        HttpRequest request = new BasicHttpRequest("GET", "/");
        Header authResponse = authscheme.authenticate(creds, request);
        
        String expected = "Basic " + EncodingUtils.getAsciiString(
            Base64.encodeBase64(EncodingUtils.getAsciiBytes("testuser:testpass")));
        assertEquals(AUTH.WWW_AUTH_RESP, authResponse.getName());
        assertEquals(expected, authResponse.getValue());
        assertEquals("test", authscheme.getRealm());
        assertTrue(authscheme.isComplete());
        assertFalse(authscheme.isConnectionBased());
    }

    public void testBasicProxyAuthentication() throws Exception {
        UsernamePasswordCredentials creds = 
            new UsernamePasswordCredentials("testuser", "testpass");
        
        Header challenge = new BasicHeader(AUTH.PROXY_AUTH, "Basic realm=\"test\"");
        
        BasicScheme authscheme = new BasicScheme();
        authscheme.processChallenge(challenge);
        
        HttpRequest request = new BasicHttpRequest("GET", "/");
        Header authResponse = authscheme.authenticate(creds, request);
        
        String expected = "Basic " + EncodingUtils.getAsciiString(
            Base64.encodeBase64(EncodingUtils.getAsciiBytes("testuser:testpass")));
        assertEquals(AUTH.PROXY_AUTH_RESP, authResponse.getName());
        assertEquals(expected, authResponse.getValue());
        assertEquals("test", authscheme.getRealm());
        assertTrue(authscheme.isComplete());
        assertFalse(authscheme.isConnectionBased());
    }

}
