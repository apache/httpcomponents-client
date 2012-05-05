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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.AUTH;
import org.apache.http.auth.MalformedChallengeException;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHeaderValueParser;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.junit.Assert;
import org.junit.Test;

/**
 * Test Methods for DigestScheme Authentication.
 */
public class TestDigestScheme {

    @Test(expected=MalformedChallengeException.class)
    public void testDigestAuthenticationEmptyChallenge1() throws Exception {
        Header authChallenge = new BasicHeader(AUTH.WWW_AUTH, "Digest");
        AuthScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge);
    }

    @Test(expected=MalformedChallengeException.class)
    public void testDigestAuthenticationEmptyChallenge2() throws Exception {
        Header authChallenge = new BasicHeader(AUTH.WWW_AUTH, "Digest ");
        AuthScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge);
    }

    @Test
    public void testDigestAuthenticationWithDefaultCreds() throws Exception {
        String challenge = "Digest realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\"";
        Header authChallenge = new BasicHeader(AUTH.WWW_AUTH, challenge);
        HttpRequest request = new BasicHttpRequest("Simple", "/");
        Credentials cred = new UsernamePasswordCredentials("username","password");
        DigestScheme authscheme = new DigestScheme();
        HttpContext context = new BasicHttpContext();
        authscheme.processChallenge(authChallenge);
        Header authResponse = authscheme.authenticate(cred, request, context);
        Assert.assertTrue(authscheme.isComplete());
        Assert.assertFalse(authscheme.isConnectionBased());

        Map<String, String> table = parseAuthResponse(authResponse);
        Assert.assertEquals("username", table.get("username"));
        Assert.assertEquals("realm1", table.get("realm"));
        Assert.assertEquals("/", table.get("uri"));
        Assert.assertEquals("f2a3f18799759d4f1a1c068b92b573cb", table.get("nonce"));
        Assert.assertEquals("e95a7ddf37c2eab009568b1ed134f89a", table.get("response"));
    }

    @Test
    public void testDigestAuthentication() throws Exception {
        String challenge = "Digest realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\"";
        Header authChallenge = new BasicHeader(AUTH.WWW_AUTH, challenge);
        HttpRequest request = new BasicHttpRequest("Simple", "/");
        Credentials cred = new UsernamePasswordCredentials("username","password");
        DigestScheme authscheme = new DigestScheme();
        HttpContext context = new BasicHttpContext();
        authscheme.processChallenge(authChallenge);
        Header authResponse = authscheme.authenticate(cred, request, context);

        Map<String, String> table = parseAuthResponse(authResponse);
        Assert.assertEquals("username", table.get("username"));
        Assert.assertEquals("realm1", table.get("realm"));
        Assert.assertEquals("/", table.get("uri"));
        Assert.assertEquals("f2a3f18799759d4f1a1c068b92b573cb", table.get("nonce"));
        Assert.assertEquals("e95a7ddf37c2eab009568b1ed134f89a", table.get("response"));
    }

    @Test
    public void testDigestAuthenticationInvalidInput() throws Exception {
        String challenge = "Digest realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\"";
        Header authChallenge = new BasicHeader(AUTH.WWW_AUTH, challenge);
        HttpRequest request = new BasicHttpRequest("Simple", "/");
        Credentials cred = new UsernamePasswordCredentials("username","password");
        DigestScheme authscheme = new DigestScheme();
        HttpContext context = new BasicHttpContext();
        authscheme.processChallenge(authChallenge);
        try {
            authscheme.authenticate(null, request, context);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
        }
        try {
            authscheme.authenticate(cred, null, context);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
        }
    }

    @Test
    public void testDigestAuthenticationOverrideParameter() throws Exception {
        String challenge = "Digest realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\"";
        Header authChallenge = new BasicHeader(AUTH.WWW_AUTH, challenge);
        HttpRequest request = new BasicHttpRequest("Simple", "/");
        Credentials cred = new UsernamePasswordCredentials("username","password");
        DigestScheme authscheme = new DigestScheme();
        HttpContext context = new BasicHttpContext();
        authscheme.processChallenge(authChallenge);
        authscheme.overrideParamter("realm", "other realm");
        Header authResponse = authscheme.authenticate(cred, request, context);

        Map<String, String> table = parseAuthResponse(authResponse);
        Assert.assertEquals("username", table.get("username"));
        Assert.assertEquals("other realm", table.get("realm"));
        Assert.assertEquals("/", table.get("uri"));
        Assert.assertEquals("f2a3f18799759d4f1a1c068b92b573cb", table.get("nonce"));
        Assert.assertEquals("3f211de10463cbd055ab4cd9c5158eac", table.get("response"));
    }

    @Test
    public void testDigestAuthenticationWithSHA() throws Exception {
        String challenge = "Digest realm=\"realm1\", " +
                "nonce=\"f2a3f18799759d4f1a1c068b92b573cb\", " +
                "algorithm=SHA";
        Header authChallenge = new BasicHeader(AUTH.WWW_AUTH, challenge);
        HttpRequest request = new BasicHttpRequest("Simple", "/");
        Credentials cred = new UsernamePasswordCredentials("username","password");
        HttpContext context = new BasicHttpContext();
        DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge);
        Header authResponse = authscheme.authenticate(cred, request, context);

        Map<String, String> table = parseAuthResponse(authResponse);
        Assert.assertEquals("username", table.get("username"));
        Assert.assertEquals("realm1", table.get("realm"));
        Assert.assertEquals("/", table.get("uri"));
        Assert.assertEquals("f2a3f18799759d4f1a1c068b92b573cb", table.get("nonce"));
        Assert.assertEquals("8769e82e4e28ecc040b969562b9050580c6d186d", table.get("response"));
    }

    @Test
    public void testDigestAuthenticationWithQueryStringInDigestURI() throws Exception {
        String challenge = "Digest realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\"";
        Header authChallenge = new BasicHeader(AUTH.WWW_AUTH, challenge);
        HttpRequest request = new BasicHttpRequest("Simple", "/?param=value");
        Credentials cred = new UsernamePasswordCredentials("username","password");
        HttpContext context = new BasicHttpContext();
        DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge);
        Header authResponse = authscheme.authenticate(cred, request, context);

        Map<String, String> table = parseAuthResponse(authResponse);
        Assert.assertEquals("username", table.get("username"));
        Assert.assertEquals("realm1", table.get("realm"));
        Assert.assertEquals("/?param=value", table.get("uri"));
        Assert.assertEquals("f2a3f18799759d4f1a1c068b92b573cb", table.get("nonce"));
        Assert.assertEquals("a847f58f5fef0bc087bcb9c3eb30e042", table.get("response"));
    }

    @Test
    public void testDigestAuthenticationWithMultipleRealms() throws Exception {
        String challenge1 = "Digest realm=\"realm1\", nonce=\"abcde\"";
        String challenge2 = "Digest realm=\"realm2\", nonce=\"123546\"";
        Credentials cred = new UsernamePasswordCredentials("username","password");
        Credentials cred2 = new UsernamePasswordCredentials("uname2","password2");

        Header authChallenge = new BasicHeader(AUTH.WWW_AUTH, challenge1);
        HttpRequest request = new BasicHttpRequest("Simple", "/");
        HttpContext context = new BasicHttpContext();
        DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge);
        Header authResponse = authscheme.authenticate(cred, request, context);

        Map<String, String> table = parseAuthResponse(authResponse);
        Assert.assertEquals("username", table.get("username"));
        Assert.assertEquals("realm1", table.get("realm"));
        Assert.assertEquals("/", table.get("uri"));
        Assert.assertEquals("abcde", table.get("nonce"));
        Assert.assertEquals("786f500303eac1478f3c2865e676ed68", table.get("response"));

        authChallenge = new BasicHeader(AUTH.WWW_AUTH, challenge2);
        DigestScheme authscheme2 = new DigestScheme();
        authscheme2.processChallenge(authChallenge);
        authResponse = authscheme2.authenticate(cred2, request, context);

        table = parseAuthResponse(authResponse);
        Assert.assertEquals("uname2", table.get("username"));
        Assert.assertEquals("realm2", table.get("realm"));
        Assert.assertEquals("/", table.get("uri"));
        Assert.assertEquals("123546", table.get("nonce"));
        Assert.assertEquals("0283edd9ef06a38b378b3b74661391e9", table.get("response"));
    }

    @Test(expected=AuthenticationException.class)
    public void testDigestAuthenticationNoRealm() throws Exception {
        String challenge = "Digest no-realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\"";
        Header authChallenge = new BasicHeader(AUTH.WWW_AUTH, challenge);
        HttpContext context = new BasicHttpContext();
        DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge);

        Credentials cred = new UsernamePasswordCredentials("username","password");
        HttpRequest request = new BasicHttpRequest("Simple", "/");
        authscheme.authenticate(cred, request, context);
    }

    @Test(expected=AuthenticationException.class)
    public void testDigestAuthenticationNoNonce() throws Exception {
        String challenge = "Digest realm=\"realm1\", no-nonce=\"f2a3f18799759d4f1a1c068b92b573cb\"";
        Header authChallenge = new BasicHeader(AUTH.WWW_AUTH, challenge);
        HttpContext context = new BasicHttpContext();
        DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge);

        Credentials cred = new UsernamePasswordCredentials("username","password");
        HttpRequest request = new BasicHttpRequest("Simple", "/");
        authscheme.authenticate(cred, request, context);
    }

    /**
     * Test digest authentication using the MD5-sess algorithm.
     */
    @Test
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
        HttpContext context = new BasicHttpContext();

        DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge);
        Header authResponse = authscheme.authenticate(cred, request, context);
        String response = authResponse.getValue();

        Assert.assertTrue(response.indexOf("nc=00000001") > 0); // test for quotes
        Assert.assertTrue(response.indexOf("qop=auth") > 0); // test for quotes

        Map<String, String> table = parseAuthResponse(authResponse);
        Assert.assertEquals(username, table.get("username"));
        Assert.assertEquals(realm, table.get("realm"));
        Assert.assertEquals("MD5-sess", table.get("algorithm"));
        Assert.assertEquals("/", table.get("uri"));
        Assert.assertEquals(nonce, table.get("nonce"));
        Assert.assertEquals(1, Integer.parseInt(table.get("nc"),16));
        Assert.assertTrue(null != table.get("cnonce"));
        Assert.assertEquals("SomeString", table.get("opaque"));
        Assert.assertEquals("auth", table.get("qop"));
        //@TODO: add better check
        Assert.assertTrue(null != table.get("response"));
    }

    /**
     * Test digest authentication using the MD5-sess algorithm.
     */
    @Test
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
        HttpContext context = new BasicHttpContext();

        DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge);
        Header authResponse = authscheme.authenticate(cred, request, context);

        Map<String, String> table = parseAuthResponse(authResponse);
        Assert.assertEquals(username, table.get("username"));
        Assert.assertEquals(realm, table.get("realm"));
        Assert.assertEquals("MD5-sess", table.get("algorithm"));
        Assert.assertEquals("/", table.get("uri"));
        Assert.assertEquals(nonce, table.get("nonce"));
        Assert.assertTrue(null == table.get("nc"));
        Assert.assertEquals("SomeString", table.get("opaque"));
        Assert.assertTrue(null == table.get("qop"));
        //@TODO: add better check
        Assert.assertTrue(null != table.get("response"));
    }

    /**
     * Test digest authentication with unknown qop value
     */
    @Test(expected=AuthenticationException.class)
    public void testDigestAuthenticationMD5SessUnknownQop() throws Exception {
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
            + "qop=\"stuff\"";

        Header authChallenge = new BasicHeader(AUTH.WWW_AUTH, challenge);

        DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge);

        Credentials cred = new UsernamePasswordCredentials(username, password);
        HttpRequest request = new BasicHttpRequest("Simple", "/");
        HttpContext context = new BasicHttpContext();
        authscheme.authenticate(cred, request, context);
    }

    /**
     * Test digest authentication with unknown qop value
     */
    @Test(expected=AuthenticationException.class)
    public void testDigestAuthenticationUnknownAlgo() throws Exception {
        // Example using Digest auth with MD5-sess

        String realm="realm";
        String username="username";
        String password="password";
        String nonce="e273f1776275974f1a120d8b92c5b3cb";

        String challenge="Digest realm=\"" + realm + "\", "
            + "nonce=\"" + nonce + "\", "
            + "opaque=\"SomeString\", "
            + "stale=false, "
            + "algorithm=stuff, "
            + "qop=\"auth\"";

        Header authChallenge = new BasicHeader(AUTH.WWW_AUTH, challenge);

        DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge);

        Credentials cred = new UsernamePasswordCredentials(username, password);
        HttpRequest request = new BasicHttpRequest("Simple", "/");
        HttpContext context = new BasicHttpContext();
        authscheme.authenticate(cred, request, context);
    }

    @Test
    public void testDigestAuthenticationWithStaleNonce() throws Exception {
        String challenge = "Digest realm=\"realm1\", " +
                "nonce=\"f2a3f18799759d4f1a1c068b92b573cb\", stale=\"true\"";
        Header authChallenge = new BasicHeader(AUTH.WWW_AUTH, challenge);
        AuthScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge);

        Assert.assertFalse(authscheme.isComplete());
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

    @Test
    public void testDigestNouceCount() throws Exception {
        String challenge1 = "Digest realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\", qop=auth";
        Header authChallenge1 = new BasicHeader(AUTH.WWW_AUTH, challenge1);
        HttpRequest request = new BasicHttpRequest("GET", "/");
        Credentials cred = new UsernamePasswordCredentials("username","password");
        HttpContext context = new BasicHttpContext();
        DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge1);
        Header authResponse1 = authscheme.authenticate(cred, request, context);
        Map<String, String> table1 = parseAuthResponse(authResponse1);
        Assert.assertEquals("00000001", table1.get("nc"));
        Header authResponse2 = authscheme.authenticate(cred, request, context);
        Map<String, String> table2 = parseAuthResponse(authResponse2);
        Assert.assertEquals("00000002", table2.get("nc"));
        String challenge2 = "Digest realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\", qop=auth";
        Header authChallenge2 = new BasicHeader(AUTH.WWW_AUTH, challenge2);
        authscheme.processChallenge(authChallenge2);
        Header authResponse3 = authscheme.authenticate(cred, request, context);
        Map<String, String> table3 = parseAuthResponse(authResponse3);
        Assert.assertEquals("00000003", table3.get("nc"));
        String challenge3 = "Digest realm=\"realm1\", nonce=\"e273f1776275974f1a120d8b92c5b3cb\", qop=auth";
        Header authChallenge3 = new BasicHeader(AUTH.WWW_AUTH, challenge3);
        authscheme.processChallenge(authChallenge3);
        Header authResponse4 = authscheme.authenticate(cred, request, context);
        Map<String, String> table4 = parseAuthResponse(authResponse4);
        Assert.assertEquals("00000001", table4.get("nc"));
    }

    @Test
    public void testDigestMD5SessA1AndCnonceConsistency() throws Exception {
        String challenge1 = "Digest qop=\"auth\", algorithm=MD5-sess, nonce=\"1234567890abcdef\", " +
                "charset=utf-8, realm=\"subnet.domain.com\"";
        Header authChallenge1 = new BasicHeader(AUTH.WWW_AUTH, challenge1);
        HttpRequest request = new BasicHttpRequest("GET", "/");
        Credentials cred = new UsernamePasswordCredentials("username","password");
        HttpContext context = new BasicHttpContext();
        DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge1);
        Header authResponse1 = authscheme.authenticate(cred, request, context);
        Map<String, String> table1 = parseAuthResponse(authResponse1);
        Assert.assertEquals("00000001", table1.get("nc"));
        String cnonce1 = authscheme.getCnonce();
        String sessionKey1 = authscheme.getA1();

        Header authResponse2 = authscheme.authenticate(cred, request, context);
        Map<String, String> table2 = parseAuthResponse(authResponse2);
        Assert.assertEquals("00000002", table2.get("nc"));
        String cnonce2 = authscheme.getCnonce();
        String sessionKey2 = authscheme.getA1();

        Assert.assertEquals(cnonce1, cnonce2);
        Assert.assertEquals(sessionKey1, sessionKey2);

        String challenge2 = "Digest qop=\"auth\", algorithm=MD5-sess, nonce=\"1234567890abcdef\", " +
            "charset=utf-8, realm=\"subnet.domain.com\"";
        Header authChallenge2 = new BasicHeader(AUTH.WWW_AUTH, challenge2);
        authscheme.processChallenge(authChallenge2);
        Header authResponse3 = authscheme.authenticate(cred, request, context);
        Map<String, String> table3 = parseAuthResponse(authResponse3);
        Assert.assertEquals("00000003", table3.get("nc"));

        String cnonce3 = authscheme.getCnonce();
        String sessionKey3 = authscheme.getA1();

        Assert.assertEquals(cnonce1, cnonce3);
        Assert.assertEquals(sessionKey1, sessionKey3);

        String challenge3 = "Digest qop=\"auth\", algorithm=MD5-sess, nonce=\"fedcba0987654321\", " +
            "charset=utf-8, realm=\"subnet.domain.com\"";
        Header authChallenge3 = new BasicHeader(AUTH.WWW_AUTH, challenge3);
        authscheme.processChallenge(authChallenge3);
        Header authResponse4 = authscheme.authenticate(cred, request, context);
        Map<String, String> table4 = parseAuthResponse(authResponse4);
        Assert.assertEquals("00000001", table4.get("nc"));

        String cnonce4 = authscheme.getCnonce();
        String sessionKey4 = authscheme.getA1();

        Assert.assertFalse(cnonce1.equals(cnonce4));
        Assert.assertFalse(sessionKey1.equals(sessionKey4));
    }

    @Test
    public void testHttpEntityDigest() throws Exception {
        HttpEntityDigester digester = new HttpEntityDigester(MessageDigest.getInstance("MD5"));
        Assert.assertNull(digester.getDigest());
        digester.write('a');
        digester.write('b');
        digester.write('c');
        digester.write(0xe4);
        digester.write(0xf6);
        digester.write(0xfc);
        digester.write(new byte[] { 'a', 'b', 'c'});
        Assert.assertNull(digester.getDigest());
        digester.close();
        Assert.assertEquals("acd2b59cd01c7737d8069015584c6cac", DigestScheme.encode(digester.getDigest()));
        try {
            digester.write('a');
            Assert.fail("IOException should have been thrown");
        } catch (IOException ex) {
        }
        try {
            digester.write(new byte[] { 'a', 'b', 'c'});
            Assert.fail("IOException should have been thrown");
        } catch (IOException ex) {
        }
    }

    @Test
    public void testDigestAuthenticationQopAuthInt() throws Exception {
        String challenge = "Digest realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\", " +
                "qop=\"auth,auth-int\"";
        Header authChallenge = new BasicHeader(AUTH.WWW_AUTH, challenge);
        HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("Post", "/");
        request.setEntity(new StringEntity("abc\u00e4\u00f6\u00fcabc", HTTP.DEF_CONTENT_CHARSET));
        Credentials cred = new UsernamePasswordCredentials("username","password");
        DigestScheme authscheme = new DigestScheme();
        HttpContext context = new BasicHttpContext();
        authscheme.processChallenge(authChallenge);
        Header authResponse = authscheme.authenticate(cred, request, context);

        Assert.assertEquals("Post:/:acd2b59cd01c7737d8069015584c6cac", authscheme.getA2());

        Map<String, String> table = parseAuthResponse(authResponse);
        Assert.assertEquals("username", table.get("username"));
        Assert.assertEquals("realm1", table.get("realm"));
        Assert.assertEquals("/", table.get("uri"));
        Assert.assertEquals("auth-int", table.get("qop"));
        Assert.assertEquals("f2a3f18799759d4f1a1c068b92b573cb", table.get("nonce"));
    }

    @Test
    public void testDigestAuthenticationQopAuthIntNullEntity() throws Exception {
        String challenge = "Digest realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\", " +
                "qop=\"auth,auth-int\"";
        Header authChallenge = new BasicHeader(AUTH.WWW_AUTH, challenge);
        HttpRequest request = new BasicHttpEntityEnclosingRequest("Post", "/");
        Credentials cred = new UsernamePasswordCredentials("username","password");
        DigestScheme authscheme = new DigestScheme();
        HttpContext context = new BasicHttpContext();
        authscheme.processChallenge(authChallenge);
        Header authResponse = authscheme.authenticate(cred, request, context);

        Assert.assertEquals("Post:/:d41d8cd98f00b204e9800998ecf8427e", authscheme.getA2());

        Map<String, String> table = parseAuthResponse(authResponse);
        Assert.assertEquals("username", table.get("username"));
        Assert.assertEquals("realm1", table.get("realm"));
        Assert.assertEquals("/", table.get("uri"));
        Assert.assertEquals("auth-int", table.get("qop"));
        Assert.assertEquals("f2a3f18799759d4f1a1c068b92b573cb", table.get("nonce"));
    }

    @Test
    public void testDigestAuthenticationQopAuthOrAuthIntNonRepeatableEntity() throws Exception {
        String challenge = "Digest realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\", " +
                "qop=\"auth,auth-int\"";
        Header authChallenge = new BasicHeader(AUTH.WWW_AUTH, challenge);
        HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("Post", "/");
        request.setEntity(new InputStreamEntity(new ByteArrayInputStream(new byte[] {'a'}), -1));
        Credentials cred = new UsernamePasswordCredentials("username","password");
        DigestScheme authscheme = new DigestScheme();
        HttpContext context = new BasicHttpContext();
        authscheme.processChallenge(authChallenge);
        Header authResponse = authscheme.authenticate(cred, request, context);

        Assert.assertEquals("Post:/", authscheme.getA2());

        Map<String, String> table = parseAuthResponse(authResponse);
        Assert.assertEquals("username", table.get("username"));
        Assert.assertEquals("realm1", table.get("realm"));
        Assert.assertEquals("/", table.get("uri"));
        Assert.assertEquals("auth", table.get("qop"));
        Assert.assertEquals("f2a3f18799759d4f1a1c068b92b573cb", table.get("nonce"));
    }

    @Test(expected=AuthenticationException.class)
    public void testDigestAuthenticationQopIntOnlyNonRepeatableEntity() throws Exception {
        String challenge = "Digest realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\", " +
                "qop=\"auth-int\"";
        Header authChallenge = new BasicHeader(AUTH.WWW_AUTH, challenge);
        HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("Post", "/");
        request.setEntity(new InputStreamEntity(new ByteArrayInputStream(new byte[] {'a'}), -1));
        Credentials cred = new UsernamePasswordCredentials("username","password");
        DigestScheme authscheme = new DigestScheme();
        HttpContext context = new BasicHttpContext();
        authscheme.processChallenge(authChallenge);
        authscheme.authenticate(cred, request, context);
    }

}
