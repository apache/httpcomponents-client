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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.hc.client5.http.auth.AuthChallenge;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.AuthenticationException;
import org.apache.hc.client5.http.auth.ChallengeType;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.MalformedChallengeException;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicHeaderValueParser;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.ParserCursor;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.junit.Assert;
import org.junit.Test;

/**
 * Test Methods for DigestScheme Authentication.
 */
public class TestDigestScheme {

    private static AuthChallenge parse(final String s) throws ParseException {
        final CharArrayBuffer buffer = new CharArrayBuffer(s.length());
        buffer.append(s);
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        final List<AuthChallenge> authChallenges = AuthChallengeParser.INSTANCE.parse(ChallengeType.TARGET, buffer, cursor);
        Assert.assertEquals(1, authChallenges.size());
        return authChallenges.get(0);
    }

    @Test(expected=MalformedChallengeException.class)
    public void testDigestAuthenticationEmptyChallenge1() throws Exception {
        final AuthChallenge authChallenge = parse(StandardAuthScheme.DIGEST);
        final AuthScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);
    }

    @Test(expected=MalformedChallengeException.class)
    public void testDigestAuthenticationEmptyChallenge2() throws Exception {
        final AuthChallenge authChallenge = parse(StandardAuthScheme.DIGEST + " ");
        final AuthScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);
    }

    @Test
    public void testDigestAuthenticationWithDefaultCreds() throws Exception {
        final HttpRequest request = new BasicHttpRequest("Simple", "/");
        final HttpHost host = new HttpHost("somehost", 80);
        final AuthScope authScope = new AuthScope(host, "realm1", null);
        final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        final Credentials creds = new UsernamePasswordCredentials("username","password".toCharArray());
        credentialsProvider.setCredentials(authScope, creds);

        final String challenge = StandardAuthScheme.DIGEST + " realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\"";
        final AuthChallenge authChallenge = parse(challenge);
        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);

        Assert.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse = authscheme.generateAuthResponse(host, request, null);
        Assert.assertTrue(authscheme.isChallengeComplete());
        Assert.assertFalse(authscheme.isConnectionBased());

        final Map<String, String> table = parseAuthResponse(authResponse);
        Assert.assertEquals("username", table.get("username"));
        Assert.assertEquals("realm1", table.get("realm"));
        Assert.assertEquals("/", table.get("uri"));
        Assert.assertEquals("f2a3f18799759d4f1a1c068b92b573cb", table.get("nonce"));
        Assert.assertEquals("e95a7ddf37c2eab009568b1ed134f89a", table.get("response"));
    }

    @Test
    public void testDigestAuthentication() throws Exception {
        final HttpRequest request = new BasicHttpRequest("Simple", "/");
        final HttpHost host = new HttpHost("somehost", 80);
        final AuthScope authScope = new AuthScope(host, "realm1", null);
        final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        final Credentials creds = new UsernamePasswordCredentials("username","password".toCharArray());
        credentialsProvider.setCredentials(authScope, creds);

        final String challenge = StandardAuthScheme.DIGEST + " realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\"";
        final AuthChallenge authChallenge = parse(challenge);
        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);

        Assert.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse = authscheme.generateAuthResponse(host, request, null);

        final Map<String, String> table = parseAuthResponse(authResponse);
        Assert.assertEquals("username", table.get("username"));
        Assert.assertEquals("realm1", table.get("realm"));
        Assert.assertEquals("/", table.get("uri"));
        Assert.assertEquals("f2a3f18799759d4f1a1c068b92b573cb", table.get("nonce"));
        Assert.assertEquals("e95a7ddf37c2eab009568b1ed134f89a", table.get("response"));
    }

    @Test
    public void testDigestAuthenticationInvalidInput() throws Exception {
        final HttpHost host = new HttpHost("somehost", 80);
        final AuthScope authScope = new AuthScope(host, "realm1", null);
        final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        final Credentials creds = new UsernamePasswordCredentials("username","password".toCharArray());
        credentialsProvider.setCredentials(authScope, creds);

        final String challenge = StandardAuthScheme.DIGEST + " realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\"";
        final AuthChallenge authChallenge = parse(challenge);
        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);

        try {
            authscheme.isResponseReady(null, credentialsProvider, null);
            Assert.fail("NullPointerException should have been thrown");
        } catch (final NullPointerException ex) {
        }
        try {
            authscheme.isResponseReady(host, null, null);
            Assert.fail("NullPointerException should have been thrown");
        } catch (final NullPointerException ex) {
        }
        try {
            authscheme.generateAuthResponse(host, null, null);
            Assert.fail("NullPointerException should have been thrown");
        } catch (final NullPointerException ex) {
        }
    }

    @Test
    public void testDigestAuthenticationWithSHA() throws Exception {
        final HttpRequest request = new BasicHttpRequest("Simple", "/");
        final HttpHost host = new HttpHost("somehost", 80);
        final AuthScope authScope = new AuthScope(host, "realm1", null);
        final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        final Credentials creds = new UsernamePasswordCredentials("username","password".toCharArray());
        credentialsProvider.setCredentials(authScope, creds);

        final String challenge = StandardAuthScheme.DIGEST + " realm=\"realm1\", " +
                "nonce=\"f2a3f18799759d4f1a1c068b92b573cb\", " +
                "algorithm=SHA";
        final AuthChallenge authChallenge = parse(challenge);
        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);

        Assert.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse = authscheme.generateAuthResponse(host, request, null);

        final Map<String, String> table = parseAuthResponse(authResponse);
        Assert.assertEquals("username", table.get("username"));
        Assert.assertEquals("realm1", table.get("realm"));
        Assert.assertEquals("/", table.get("uri"));
        Assert.assertEquals("f2a3f18799759d4f1a1c068b92b573cb", table.get("nonce"));
        Assert.assertEquals("8769e82e4e28ecc040b969562b9050580c6d186d", table.get("response"));
    }

    @Test
    public void testDigestAuthenticationWithQueryStringInDigestURI() throws Exception {
        final HttpRequest request = new BasicHttpRequest("Simple", "/?param=value");
        final HttpHost host = new HttpHost("somehost", 80);
        final AuthScope authScope = new AuthScope(host, "realm1", null);
        final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        final Credentials creds = new UsernamePasswordCredentials("username","password".toCharArray());
        credentialsProvider.setCredentials(authScope, creds);

        final String challenge = StandardAuthScheme.DIGEST + " realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\"";
        final AuthChallenge authChallenge = parse(challenge);
        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);

        Assert.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse = authscheme.generateAuthResponse(host, request, null);

        final Map<String, String> table = parseAuthResponse(authResponse);
        Assert.assertEquals("username", table.get("username"));
        Assert.assertEquals("realm1", table.get("realm"));
        Assert.assertEquals("/?param=value", table.get("uri"));
        Assert.assertEquals("f2a3f18799759d4f1a1c068b92b573cb", table.get("nonce"));
        Assert.assertEquals("a847f58f5fef0bc087bcb9c3eb30e042", table.get("response"));
    }

    @Test(expected=AuthenticationException.class)
    public void testDigestAuthenticationNoRealm() throws Exception {
        final HttpRequest request = new BasicHttpRequest("Simple", "/");
        final HttpHost host = new HttpHost("somehost", 80);
        final AuthScope authScope = new AuthScope(host, "realm1", null);
        final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        final Credentials creds = new UsernamePasswordCredentials("username","password".toCharArray());
        credentialsProvider.setCredentials(authScope, creds);

        final String challenge = StandardAuthScheme.DIGEST + " no-realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\"";
        final AuthChallenge authChallenge = parse(challenge);
        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);

        Assert.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        authscheme.generateAuthResponse(host, request, null);
    }

    @Test(expected=AuthenticationException.class)
    public void testDigestAuthenticationNoNonce() throws Exception {
        final HttpRequest request = new BasicHttpRequest("Simple", "/");
        final HttpHost host = new HttpHost("somehost", 80);
        final AuthScope authScope = new AuthScope(host, "realm1", null);
        final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        final Credentials creds = new UsernamePasswordCredentials("username","password".toCharArray());
        credentialsProvider.setCredentials(authScope, creds);

        final String challenge = StandardAuthScheme.DIGEST + " realm=\"realm1\", no-nonce=\"f2a3f18799759d4f1a1c068b92b573cb\"";
        final AuthChallenge authChallenge = parse(challenge);
        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);

        Assert.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        authscheme.generateAuthResponse(host, request, null);
    }

    /**
     * Test digest authentication using the MD5-sess algorithm.
     */
    @Test
    public void testDigestAuthenticationMD5Sess() throws Exception {
        // Example using Digest auth with MD5-sess

        final String realm="realm";
        final String username="username";
        final String password="password";
        final String nonce="e273f1776275974f1a120d8b92c5b3cb";

        final HttpRequest request = new BasicHttpRequest("Simple", "/");
        final HttpHost host = new HttpHost("somehost", 80);
        final AuthScope authScope = new AuthScope(host, realm, null);
        final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        final Credentials creds = new UsernamePasswordCredentials(username, password.toCharArray());
        credentialsProvider.setCredentials(authScope, creds);

        final String challenge=StandardAuthScheme.DIGEST + " realm=\"" + realm + "\", "
            + "nonce=\"" + nonce + "\", "
            + "opaque=\"SomeString\", "
            + "stale=false, "
            + "algorithm=MD5-sess, "
            + "qop=\"auth,auth-int\""; // we pass both but expect auth to be used

        final AuthChallenge authChallenge = parse(challenge);

        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);

        Assert.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse = authscheme.generateAuthResponse(host, request, null);

        Assert.assertTrue(authResponse.indexOf("nc=00000001") > 0); // test for quotes
        Assert.assertTrue(authResponse.indexOf("qop=auth") > 0); // test for quotes

        final Map<String, String> table = parseAuthResponse(authResponse);
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

        final String realm="realm";
        final String username="username";
        final String password="password";
        final String nonce="e273f1776275974f1a120d8b92c5b3cb";

        final HttpRequest request = new BasicHttpRequest("Simple", "/");
        final HttpHost host = new HttpHost("somehost", 80);
        final AuthScope authScope = new AuthScope(host, realm, null);
        final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        final Credentials creds = new UsernamePasswordCredentials(username, password.toCharArray());
        credentialsProvider.setCredentials(authScope, creds);

        final String challenge=StandardAuthScheme.DIGEST + " realm=\"" + realm + "\", "
            + "nonce=\"" + nonce + "\", "
            + "opaque=\"SomeString\", "
            + "stale=false, "
            + "algorithm=MD5-sess";

        final AuthChallenge authChallenge = parse(challenge);

        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);
        Assert.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse = authscheme.generateAuthResponse(host, request, null);

        final Map<String, String> table = parseAuthResponse(authResponse);
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

        final String realm="realm";
        final String username="username";
        final String password="password";
        final String nonce="e273f1776275974f1a120d8b92c5b3cb";

        final HttpRequest request = new BasicHttpRequest("Simple", "/");
        final HttpHost host = new HttpHost("somehost", 80);
        final AuthScope authScope = new AuthScope(host, realm, null);
        final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        final Credentials creds = new UsernamePasswordCredentials(username, password.toCharArray());
        credentialsProvider.setCredentials(authScope, creds);

        final String challenge=StandardAuthScheme.DIGEST + " realm=\"" + realm + "\", "
            + "nonce=\"" + nonce + "\", "
            + "opaque=\"SomeString\", "
            + "stale=false, "
            + "algorithm=MD5-sess, "
            + "qop=\"stuff\"";

        final AuthChallenge authChallenge = parse(challenge);

        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);

        Assert.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        authscheme.generateAuthResponse(host, request, null);
    }

    /**
     * Test digest authentication with unknown qop value
     */
    @Test(expected=AuthenticationException.class)
    public void testDigestAuthenticationUnknownAlgo() throws Exception {
        // Example using Digest auth with MD5-sess

        final String realm="realm";
        final String username="username";
        final String password="password";
        final String nonce="e273f1776275974f1a120d8b92c5b3cb";

        final HttpRequest request = new BasicHttpRequest("Simple", "/");
        final HttpHost host = new HttpHost("somehost", 80);
        final AuthScope authScope = new AuthScope(host, realm, null);
        final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        final Credentials creds = new UsernamePasswordCredentials(username, password.toCharArray());
        credentialsProvider.setCredentials(authScope, creds);

        final String challenge=StandardAuthScheme.DIGEST + " realm=\"" + realm + "\", "
            + "nonce=\"" + nonce + "\", "
            + "opaque=\"SomeString\", "
            + "stale=false, "
            + "algorithm=stuff, "
            + "qop=\"auth\"";

        final AuthChallenge authChallenge = parse(challenge);

        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);

        Assert.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        authscheme.generateAuthResponse(host, request, null);
    }

    @Test
    public void testDigestAuthenticationWithStaleNonce() throws Exception {
        final String challenge = StandardAuthScheme.DIGEST + " realm=\"realm1\", " +
                "nonce=\"f2a3f18799759d4f1a1c068b92b573cb\", stale=\"true\"";
        final AuthChallenge authChallenge = parse(challenge);
        final AuthScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);

        Assert.assertFalse(authscheme.isChallengeComplete());
    }

    private static Map<String, String> parseAuthResponse(final String authResponse) {
        if (!authResponse.startsWith(StandardAuthScheme.DIGEST + " ")) {
            return null;
        }
        final String s = authResponse.substring(7);
        final ParserCursor cursor = new ParserCursor(0, s.length());
        final HeaderElement[] elements = BasicHeaderValueParser.INSTANCE.parseElements(s, cursor);
        final Map<String, String> map = new HashMap<>(elements.length);
        for (final HeaderElement element : elements) {
            map.put(element.getName(), element.getValue());
        }
        return map;
    }

    @Test
    public void testDigestNouceCount() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final HttpHost host = new HttpHost("somehost", 80);
        final AuthScope authScope = new AuthScope(host, "realm1", null);
        final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        final Credentials creds = new UsernamePasswordCredentials("username","password".toCharArray());
        credentialsProvider.setCredentials(authScope, creds);

        final String challenge1 = StandardAuthScheme.DIGEST + " realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\", qop=auth";
        final AuthChallenge authChallenge1 = parse(challenge1);

        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge1, null);
        Assert.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse1 = authscheme.generateAuthResponse(host, request, null);

        final Map<String, String> table1 = parseAuthResponse(authResponse1);
        Assert.assertEquals("00000001", table1.get("nc"));

        Assert.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse2 = authscheme.generateAuthResponse(host, request, null);

        final Map<String, String> table2 = parseAuthResponse(authResponse2);
        Assert.assertEquals("00000002", table2.get("nc"));
        final String challenge2 = StandardAuthScheme.DIGEST + " realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\", qop=auth";
        final AuthChallenge authChallenge2 = parse(challenge2);
        authscheme.processChallenge(authChallenge2, null);

        Assert.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse3 = authscheme.generateAuthResponse(host, request, null);

        final Map<String, String> table3 = parseAuthResponse(authResponse3);
        Assert.assertEquals("00000003", table3.get("nc"));
        final String challenge3 = StandardAuthScheme.DIGEST + " realm=\"realm1\", nonce=\"e273f1776275974f1a120d8b92c5b3cb\", qop=auth";
        final AuthChallenge authChallenge3 = parse(challenge3);
        authscheme.processChallenge(authChallenge3, null);

        Assert.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse4 = authscheme.generateAuthResponse(host, request, null);

        final Map<String, String> table4 = parseAuthResponse(authResponse4);
        Assert.assertEquals("00000001", table4.get("nc"));
    }

    @Test
    public void testDigestMD5SessA1AndCnonceConsistency() throws Exception {
        final HttpHost host = new HttpHost("somehost", 80);
        final AuthScope authScope = new AuthScope(host, "subnet.domain.com", null);
        final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final Credentials creds = new UsernamePasswordCredentials("username","password".toCharArray());
        credentialsProvider.setCredentials(authScope, creds);

        final String challenge1 = StandardAuthScheme.DIGEST + " qop=\"auth\", algorithm=MD5-sess, nonce=\"1234567890abcdef\", " +
                "charset=utf-8, realm=\"subnet.domain.com\"";
        final AuthChallenge authChallenge1 = parse(challenge1);
        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge1, null);
        Assert.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse1 = authscheme.generateAuthResponse(host, request, null);

        final Map<String, String> table1 = parseAuthResponse(authResponse1);
        Assert.assertEquals("00000001", table1.get("nc"));
        final String cnonce1 = authscheme.getCnonce();
        final String sessionKey1 = authscheme.getA1();

        Assert.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse2 = authscheme.generateAuthResponse(host, request, null);
        final Map<String, String> table2 = parseAuthResponse(authResponse2);
        Assert.assertEquals("00000002", table2.get("nc"));
        final String cnonce2 = authscheme.getCnonce();
        final String sessionKey2 = authscheme.getA1();

        Assert.assertEquals(cnonce1, cnonce2);
        Assert.assertEquals(sessionKey1, sessionKey2);

        final String challenge2 = StandardAuthScheme.DIGEST + " qop=\"auth\", algorithm=MD5-sess, nonce=\"1234567890abcdef\", " +
            "charset=utf-8, realm=\"subnet.domain.com\"";
        final AuthChallenge authChallenge2 = parse(challenge2);
        authscheme.processChallenge(authChallenge2, null);
        Assert.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse3 = authscheme.generateAuthResponse(host, request, null);
        final Map<String, String> table3 = parseAuthResponse(authResponse3);
        Assert.assertEquals("00000003", table3.get("nc"));

        final String cnonce3 = authscheme.getCnonce();
        final String sessionKey3 = authscheme.getA1();

        Assert.assertEquals(cnonce1, cnonce3);
        Assert.assertEquals(sessionKey1, sessionKey3);

        final String challenge3 = StandardAuthScheme.DIGEST + " qop=\"auth\", algorithm=MD5-sess, nonce=\"fedcba0987654321\", " +
            "charset=utf-8, realm=\"subnet.domain.com\"";
        final AuthChallenge authChallenge3 = parse(challenge3);
        authscheme.processChallenge(authChallenge3, null);
        Assert.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse4 = authscheme.generateAuthResponse(host, request, null);
        final Map<String, String> table4 = parseAuthResponse(authResponse4);
        Assert.assertEquals("00000001", table4.get("nc"));

        final String cnonce4 = authscheme.getCnonce();
        final String sessionKey4 = authscheme.getA1();

        Assert.assertFalse(cnonce1.equals(cnonce4));
        Assert.assertFalse(sessionKey1.equals(sessionKey4));
    }

    @Test
    public void testHttpEntityDigest() throws Exception {
        final HttpEntityDigester digester = new HttpEntityDigester(MessageDigest.getInstance("MD5"));
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
        Assert.assertEquals("acd2b59cd01c7737d8069015584c6cac", DigestScheme.formatHex(digester.getDigest()));
        try {
            digester.write('a');
            Assert.fail("IOException should have been thrown");
        } catch (final IOException ex) {
        }
        try {
            digester.write(new byte[] { 'a', 'b', 'c'});
            Assert.fail("IOException should have been thrown");
        } catch (final IOException ex) {
        }
    }

    @Test
    public void testDigestAuthenticationQopAuthInt() throws Exception {
        final ClassicHttpRequest request = new BasicClassicHttpRequest("Post", "/");
        request.setEntity(new StringEntity("abc\u00e4\u00f6\u00fcabc", StandardCharsets.ISO_8859_1));
        final HttpHost host = new HttpHost("somehost", 80);
        final AuthScope authScope = new AuthScope(host, "realm1", null);
        final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        final Credentials creds = new UsernamePasswordCredentials("username","password".toCharArray());
        credentialsProvider.setCredentials(authScope, creds);

        final String challenge = StandardAuthScheme.DIGEST + " realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\", " +
                "qop=\"auth,auth-int\"";
        final AuthChallenge authChallenge = parse(challenge);
        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);
        Assert.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse = authscheme.generateAuthResponse(host, request, null);

        Assert.assertEquals("Post:/:acd2b59cd01c7737d8069015584c6cac", authscheme.getA2());

        final Map<String, String> table = parseAuthResponse(authResponse);
        Assert.assertEquals("username", table.get("username"));
        Assert.assertEquals("realm1", table.get("realm"));
        Assert.assertEquals("/", table.get("uri"));
        Assert.assertEquals("auth-int", table.get("qop"));
        Assert.assertEquals("f2a3f18799759d4f1a1c068b92b573cb", table.get("nonce"));
    }

    @Test
    public void testDigestAuthenticationQopAuthIntNullEntity() throws Exception {
        final HttpRequest request = new BasicHttpRequest("Post", "/");
        final HttpHost host = new HttpHost("somehost", 80);
        final AuthScope authScope = new AuthScope(host, "realm1", null);
        final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        final Credentials creds = new UsernamePasswordCredentials("username","password".toCharArray());
        credentialsProvider.setCredentials(authScope, creds);

        final String challenge = StandardAuthScheme.DIGEST + " realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\", " +
                "qop=\"auth-int\"";
        final AuthChallenge authChallenge = parse(challenge);
        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);
        Assert.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse = authscheme.generateAuthResponse(host, request, null);

        Assert.assertEquals("Post:/:d41d8cd98f00b204e9800998ecf8427e", authscheme.getA2());

        final Map<String, String> table = parseAuthResponse(authResponse);
        Assert.assertEquals("username", table.get("username"));
        Assert.assertEquals("realm1", table.get("realm"));
        Assert.assertEquals("/", table.get("uri"));
        Assert.assertEquals("auth-int", table.get("qop"));
        Assert.assertEquals("f2a3f18799759d4f1a1c068b92b573cb", table.get("nonce"));
    }

    @Test
    public void testDigestAuthenticationQopAuthOrAuthIntNonRepeatableEntity() throws Exception {
        final ClassicHttpRequest request = new BasicClassicHttpRequest("Post", "/");
        request.setEntity(new InputStreamEntity(new ByteArrayInputStream(new byte[] {'a'}), -1, ContentType.DEFAULT_TEXT));
        final HttpHost host = new HttpHost("somehost", 80);
        final AuthScope authScope = new AuthScope(host, "realm1", null);
        final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        final Credentials creds = new UsernamePasswordCredentials("username","password".toCharArray());
        credentialsProvider.setCredentials(authScope, creds);

        final String challenge = StandardAuthScheme.DIGEST + " realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\", " +
                "qop=\"auth,auth-int\"";
        final AuthChallenge authChallenge = parse(challenge);
        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);
        Assert.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse = authscheme.generateAuthResponse(host, request, null);

        Assert.assertEquals("Post:/", authscheme.getA2());

        final Map<String, String> table = parseAuthResponse(authResponse);
        Assert.assertEquals("username", table.get("username"));
        Assert.assertEquals("realm1", table.get("realm"));
        Assert.assertEquals("/", table.get("uri"));
        Assert.assertEquals("auth", table.get("qop"));
        Assert.assertEquals("f2a3f18799759d4f1a1c068b92b573cb", table.get("nonce"));
    }

    @Test
    public void testParameterCaseSensitivity() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final HttpHost host = new HttpHost("somehost", 80);
        final AuthScope authScope = new AuthScope(host, "-", null);
        final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        final Credentials creds = new UsernamePasswordCredentials("username","password".toCharArray());
        credentialsProvider.setCredentials(authScope, creds);

        final String challenge = StandardAuthScheme.DIGEST + " Realm=\"-\", " +
                "nonce=\"YjYuNGYyYmJhMzUuY2I5ZDhlZDE5M2ZlZDM 1Mjk3NGJkNTIyYjgyNTcwMjQ=\", " +
                "opaque=\"98700A3D9CE17065E2246B41035C6609\", qop=\"auth\"";
        final AuthChallenge authChallenge = parse(challenge);
        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);
        Assert.assertEquals("-", authscheme.getRealm());

        Assert.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        authscheme.generateAuthResponse(host, request, null);
    }

    @Test(expected=AuthenticationException.class)
    public void testDigestAuthenticationQopIntOnlyNonRepeatableEntity() throws Exception {
        final ClassicHttpRequest request = new BasicClassicHttpRequest("Post", "/");
        request.setEntity(new InputStreamEntity(new ByteArrayInputStream(new byte[] {'a'}), -1, ContentType.DEFAULT_TEXT));
        final HttpHost host = new HttpHost("somehost", 80);
        final AuthScope authScope = new AuthScope(host, "realm1", null);
        final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        final Credentials creds = new UsernamePasswordCredentials("username","password".toCharArray());
        credentialsProvider.setCredentials(authScope, creds);

        final String challenge = StandardAuthScheme.DIGEST + " realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\", " +
                "qop=\"auth-int\"";
        final AuthChallenge authChallenge = parse(challenge);
        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);

        Assert.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        authscheme.generateAuthResponse(host, request, null);
    }

    @Test
    public void testSerialization() throws Exception {
        final String challenge = StandardAuthScheme.DIGEST + " realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\", " +
                "qop=\"auth,auth-int\"";
        final AuthChallenge authChallenge = parse(challenge);
        final DigestScheme digestScheme = new DigestScheme();
        digestScheme.processChallenge(authChallenge, null);

        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        final ObjectOutputStream out = new ObjectOutputStream(buffer);
        out.writeObject(digestScheme);
        out.flush();
        final byte[] raw = buffer.toByteArray();
        final ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(raw));
        final DigestScheme authScheme = (DigestScheme) in.readObject();

        Assert.assertEquals(digestScheme.getName(), authScheme.getName());
        Assert.assertEquals(digestScheme.getRealm(), authScheme.getRealm());
        Assert.assertEquals(digestScheme.isChallengeComplete(), authScheme.isChallengeComplete());
        Assert.assertEquals(digestScheme.getA1(), authScheme.getA1());
        Assert.assertEquals(digestScheme.getA2(), authScheme.getA2());
        Assert.assertEquals(digestScheme.getCnonce(), authScheme.getCnonce());
    }

}
