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

import java.util.HashMap;
import java.util.Map;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpRequest;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.AUTH;
import org.apache.http.auth.MalformedChallengeException;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHeaderValueParser;
import org.apache.http.message.BasicHttpRequest;

/**
 * Test Methods for DigestScheme Authentication.
 *
 */
public class TestDigestScheme extends TestCase {

    // ------------------------------------------------------------ Constructor
    public TestDigestScheme(String testName) {
        super(testName);
    }

    // ------------------------------------------------------------------- Main
    public static void main(String args[]) {
        String[] testCaseName = { TestDigestScheme.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        return new TestSuite(TestDigestScheme.class);
    }

    public void testDigestAuthenticationWithNoRealm() throws Exception {
        Header authChallenge = new BasicHeader(AUTH.WWW_AUTH, "Digest");
        try {
            AuthScheme authscheme = new DigestScheme();
            authscheme.processChallenge(authChallenge);
            fail("Should have thrown MalformedChallengeException");
        } catch(MalformedChallengeException e) {
            // expected
        }
    }

    public void testDigestAuthenticationWithNoRealm2() throws Exception {
        Header authChallenge = new BasicHeader(AUTH.WWW_AUTH, "Digest ");
        try {
            AuthScheme authscheme = new DigestScheme();
            authscheme.processChallenge(authChallenge);
            fail("Should have thrown MalformedChallengeException");
        } catch(MalformedChallengeException e) {
            // expected
        }
    }

    public void testDigestAuthenticationWithDefaultCreds() throws Exception {
        String challenge = "Digest realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\"";
        Header authChallenge = new BasicHeader(AUTH.WWW_AUTH, challenge);
        HttpRequest request = new BasicHttpRequest("Simple", "/");
        Credentials cred = new UsernamePasswordCredentials("username","password");
        AuthScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge);
        Header authResponse = authscheme.authenticate(cred, request);

        Map<String, String> table = parseAuthResponse(authResponse);
        assertEquals("username", table.get("username"));
        assertEquals("realm1", table.get("realm"));
        assertEquals("/", table.get("uri"));
        assertEquals("f2a3f18799759d4f1a1c068b92b573cb", table.get("nonce"));
        assertEquals("e95a7ddf37c2eab009568b1ed134f89a", table.get("response"));
    }

    public void testDigestAuthentication() throws Exception {
        String challenge = "Digest realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\"";
        Header authChallenge = new BasicHeader(AUTH.WWW_AUTH, challenge);
        HttpRequest request = new BasicHttpRequest("Simple", "/");
        Credentials cred = new UsernamePasswordCredentials("username","password");
        AuthScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge);
        Header authResponse = authscheme.authenticate(cred, request);

        Map<String, String> table = parseAuthResponse(authResponse);
        assertEquals("username", table.get("username"));
        assertEquals("realm1", table.get("realm"));
        assertEquals("/", table.get("uri"));
        assertEquals("f2a3f18799759d4f1a1c068b92b573cb", table.get("nonce"));
        assertEquals("e95a7ddf37c2eab009568b1ed134f89a", table.get("response"));
    }

    public void testDigestAuthenticationWithSHA() throws Exception {
        String challenge = "Digest realm=\"realm1\", " +
                "nonce=\"f2a3f18799759d4f1a1c068b92b573cb\", " +
                "algorithm=SHA";
        Header authChallenge = new BasicHeader(AUTH.WWW_AUTH, challenge);
        HttpRequest request = new BasicHttpRequest("Simple", "/");
        Credentials cred = new UsernamePasswordCredentials("username","password");
        AuthScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge);
        Header authResponse = authscheme.authenticate(cred, request);

        Map<String, String> table = parseAuthResponse(authResponse);
        assertEquals("username", table.get("username"));
        assertEquals("realm1", table.get("realm"));
        assertEquals("/", table.get("uri"));
        assertEquals("f2a3f18799759d4f1a1c068b92b573cb", table.get("nonce"));
        assertEquals("8769e82e4e28ecc040b969562b9050580c6d186d", table.get("response"));
    }

    public void testDigestAuthenticationWithQueryStringInDigestURI() throws Exception {
        String challenge = "Digest realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\"";
        Header authChallenge = new BasicHeader(AUTH.WWW_AUTH, challenge);
        HttpRequest request = new BasicHttpRequest("Simple", "/?param=value");
        Credentials cred = new UsernamePasswordCredentials("username","password");
        AuthScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge);
        Header authResponse = authscheme.authenticate(cred, request);

        Map<String, String> table = parseAuthResponse(authResponse);
        assertEquals("username", table.get("username"));
        assertEquals("realm1", table.get("realm"));
        assertEquals("/?param=value", table.get("uri"));
        assertEquals("f2a3f18799759d4f1a1c068b92b573cb", table.get("nonce"));
        assertEquals("a847f58f5fef0bc087bcb9c3eb30e042", table.get("response"));
    }

    public void testDigestAuthenticationWithMultipleRealms() throws Exception {
        String challenge1 = "Digest realm=\"realm1\", nonce=\"abcde\"";
        String challenge2 = "Digest realm=\"realm2\", nonce=\"123546\"";
        Credentials cred = new UsernamePasswordCredentials("username","password");
        Credentials cred2 = new UsernamePasswordCredentials("uname2","password2");

        Header authChallenge = new BasicHeader(AUTH.WWW_AUTH, challenge1);
        HttpRequest request = new BasicHttpRequest("Simple", "/");
        AuthScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge);
        Header authResponse = authscheme.authenticate(cred, request);

        Map<String, String> table = parseAuthResponse(authResponse);
        assertEquals("username", table.get("username"));
        assertEquals("realm1", table.get("realm"));
        assertEquals("/", table.get("uri"));
        assertEquals("abcde", table.get("nonce"));
        assertEquals("786f500303eac1478f3c2865e676ed68", table.get("response"));

        authChallenge = new BasicHeader(AUTH.WWW_AUTH, challenge2);
        AuthScheme authscheme2 = new DigestScheme();
        authscheme2.processChallenge(authChallenge);
        authResponse = authscheme2.authenticate(cred2, request);

        table = parseAuthResponse(authResponse);
        assertEquals("uname2", table.get("username"));
        assertEquals("realm2", table.get("realm"));
        assertEquals("/", table.get("uri"));
        assertEquals("123546", table.get("nonce"));
        assertEquals("0283edd9ef06a38b378b3b74661391e9", table.get("response"));
    }

    /** 
     * Test digest authentication using the MD5-sess algorithm.
     */
    public void testDigestAuthenticationMD5Sess() throws Exception {
        // Example using Digest auth with MD5-sess

        String realm="realm";
        String username="username";
        String password="password";
        String nonce="e273f1776275974f1a120d8b92c5b3cb";

        String challenge="Digest realm=\"" + realm + "\", "
            + "nonce=\"" + nonce + "\", "
            + "opaque=\"SomeString\", "
            + "stale=false, "
            + "algorithm=MD5-sess, "
            + "qop=\"auth,auth-int\""; // we pass both but expect auth to be used

        Header authChallenge = new BasicHeader(AUTH.WWW_AUTH, challenge);
        
        Credentials cred = new UsernamePasswordCredentials(username, password);
        HttpRequest request = new BasicHttpRequest("Simple", "/");

        AuthScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge);
        Header authResponse = authscheme.authenticate(cred, request);
        String response = authResponse.getValue();
        
        assertTrue(response.indexOf("nc=00000001") > 0); // test for quotes
        assertTrue(response.indexOf("qop=auth") > 0); // test for quotes

        Map<String, String> table = parseAuthResponse(authResponse);
        assertEquals(username, table.get("username"));
        assertEquals(realm, table.get("realm"));
        assertEquals("MD5-sess", table.get("algorithm"));
        assertEquals("/", table.get("uri"));
        assertEquals(nonce, table.get("nonce"));
        assertEquals(1, Integer.parseInt(table.get("nc"),16));
        assertTrue(null != table.get("cnonce"));
        assertEquals("SomeString", table.get("opaque"));
        assertEquals("auth", table.get("qop"));
        //@TODO: add better check
        assertTrue(null != table.get("response")); 
    }

    /** 
     * Test digest authentication using the MD5-sess algorithm.
     */
    public void testDigestAuthenticationMD5SessNoQop() throws Exception {
        // Example using Digest auth with MD5-sess

        String realm="realm";
        String username="username";
        String password="password";
        String nonce="e273f1776275974f1a120d8b92c5b3cb";

        String challenge="Digest realm=\"" + realm + "\", "
            + "nonce=\"" + nonce + "\", "
            + "opaque=\"SomeString\", "
            + "stale=false, "
            + "algorithm=MD5-sess";

        Header authChallenge = new BasicHeader(AUTH.WWW_AUTH, challenge);
        
        Credentials cred = new UsernamePasswordCredentials(username, password);

        HttpRequest request = new BasicHttpRequest("Simple", "/");

        AuthScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge);
        Header authResponse = authscheme.authenticate(cred, request);

        Map<String, String> table = parseAuthResponse(authResponse);
        assertEquals(username, table.get("username"));
        assertEquals(realm, table.get("realm"));
        assertEquals("MD5-sess", table.get("algorithm"));
        assertEquals("/", table.get("uri"));
        assertEquals(nonce, table.get("nonce"));
        assertTrue(null == table.get("nc"));
        assertEquals("SomeString", table.get("opaque"));
        assertTrue(null == table.get("qop"));
        //@TODO: add better check
        assertTrue(null != table.get("response")); 
    }

    /** 
     * Test digest authentication with invalud qop value
     */
    public void testDigestAuthenticationMD5SessInvalidQop() throws Exception {
        // Example using Digest auth with MD5-sess

        String realm="realm";
        String nonce="e273f1776275974f1a120d8b92c5b3cb";

        String challenge="Digest realm=\"" + realm + "\", "
            + "nonce=\"" + nonce + "\", "
            + "opaque=\"SomeString\", "
            + "stale=false, "
            + "algorithm=MD5-sess, "
            + "qop=\"jakarta\""; // jakarta is an invalid qop value

        Header authChallenge = new BasicHeader(AUTH.WWW_AUTH, challenge);
        
        try {
            AuthScheme authscheme = new DigestScheme();
            authscheme.processChallenge(authChallenge);
            fail("MalformedChallengeException exception expected due to invalid qop value");
        } catch(MalformedChallengeException e) {
        }
    }

    public void testDigestAuthenticationWithStaleNonce() throws Exception {
        String challenge = "Digest realm=\"realm1\", " +
                "nonce=\"f2a3f18799759d4f1a1c068b92b573cb\", stale=\"true\"";
        Header authChallenge = new BasicHeader(AUTH.WWW_AUTH, challenge);
        AuthScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge);

        assertFalse(authscheme.isComplete());
    }

    private static Map<String, String> parseAuthResponse(final Header authResponse) {
        String s = authResponse.getValue();
        if (!s.startsWith("Digest ")) {
            return null;
        }
        HeaderElement[] elements = BasicHeaderValueParser.parseElements(s.substring(7), null);
        Map<String, String> map = new HashMap<String, String>(elements.length);
        for (int i = 0; i < elements.length; i++) {
            HeaderElement element = elements[i];
            map.put(element.getName(), element.getValue());
        }
        return map;
    }
    
}
