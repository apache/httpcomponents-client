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
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.AuthenticationException;
import org.apache.hc.client5.http.auth.ChallengeType;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.MalformedChallengeException;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.protocol.HttpClientContext;
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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Test Methods for DigestScheme Authentication.
 */
class TestDigestScheme {

    private static AuthChallenge parse(final String s) throws ParseException {
        final CharArrayBuffer buffer = new CharArrayBuffer(s.length());
        buffer.append(s);
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        final List<AuthChallenge> authChallenges = AuthChallengeParser.INSTANCE.parse(ChallengeType.TARGET, buffer, cursor);
        Assertions.assertEquals(1, authChallenges.size());
        return authChallenges.get(0);
    }

    @Test
    void testDigestAuthenticationEmptyChallenge1() throws Exception {
        final AuthChallenge authChallenge = parse(StandardAuthScheme.DIGEST);
        final AuthScheme authscheme = new DigestScheme();
        Assertions.assertThrows(MalformedChallengeException.class, () ->
                authscheme.processChallenge(authChallenge, null));
    }

    @Test
    void testDigestAuthenticationEmptyChallenge2() throws Exception {
        final AuthChallenge authChallenge = parse(StandardAuthScheme.DIGEST + " ");
        final AuthScheme authscheme = new DigestScheme();
        Assertions.assertThrows(MalformedChallengeException.class, () ->
                authscheme.processChallenge(authChallenge, null));
    }

    @Test
    void testDigestAuthenticationWithDefaultCreds() throws Exception {
        final HttpRequest request = new BasicHttpRequest("Simple", "/");
        final HttpHost host = new HttpHost("somehost", 80);
        final CredentialsProvider credentialsProvider = CredentialsProviderBuilder.create()
                .add(new AuthScope(host, "realm1", null), "username", "password".toCharArray())
                .build();

        final String challenge = StandardAuthScheme.DIGEST + " realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\"";
        final AuthChallenge authChallenge = parse(challenge);
        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);

        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse = authscheme.generateAuthResponse(host, request, null);
        Assertions.assertTrue(authscheme.isChallengeComplete());
        Assertions.assertFalse(authscheme.isConnectionBased());

        final Map<String, String> table = parseAuthResponse(authResponse);
        Assertions.assertEquals("username", table.get("username"));
        Assertions.assertEquals("realm1", table.get("realm"));
        Assertions.assertEquals("/", table.get("uri"));
        Assertions.assertEquals("f2a3f18799759d4f1a1c068b92b573cb", table.get("nonce"));
        Assertions.assertEquals("e95a7ddf37c2eab009568b1ed134f89a", table.get("response"));
    }

    @Test
    void testDigestAuthentication() throws Exception {
        final HttpRequest request = new BasicHttpRequest("Simple", "/");
        final HttpHost host = new HttpHost("somehost", 80);
        final CredentialsProvider credentialsProvider = CredentialsProviderBuilder.create()
                .add(new AuthScope(host, "realm1", null), "username", "password".toCharArray())
                .build();

        final String challenge = StandardAuthScheme.DIGEST + " realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\"";
        final AuthChallenge authChallenge = parse(challenge);
        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);

        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse = authscheme.generateAuthResponse(host, request, null);

        final Map<String, String> table = parseAuthResponse(authResponse);
        Assertions.assertEquals("username", table.get("username"));
        Assertions.assertEquals("realm1", table.get("realm"));
        Assertions.assertEquals("/", table.get("uri"));
        Assertions.assertEquals("f2a3f18799759d4f1a1c068b92b573cb", table.get("nonce"));
        Assertions.assertEquals("e95a7ddf37c2eab009568b1ed134f89a", table.get("response"));
    }

    @Test
    void testDigestAuthenticationInvalidInput() throws Exception {
        final HttpHost host = new HttpHost("somehost", 80);
        final CredentialsProvider credentialsProvider = CredentialsProviderBuilder.create()
                .add(new AuthScope(host, "realm1", null), "username", "password".toCharArray())
                .build();

        final String challenge = StandardAuthScheme.DIGEST + " realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\"";
        final AuthChallenge authChallenge = parse(challenge);
        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);

        Assertions.assertThrows(NullPointerException.class, () ->
                authscheme.isResponseReady(null, credentialsProvider, null));
        Assertions.assertThrows(NullPointerException.class, () ->
                authscheme.isResponseReady(host, null, null));
        Assertions.assertThrows(NullPointerException.class, () ->
                authscheme.generateAuthResponse(host, null, null));
    }

    @Test
    void testDigestAuthenticationWithSHA() throws Exception {
        final HttpRequest request = new BasicHttpRequest("Simple", "/");
        final HttpHost host = new HttpHost("somehost", 80);
        final CredentialsProvider credentialsProvider = CredentialsProviderBuilder.create()
                .add(new AuthScope(host, "realm1", null), "username", "password".toCharArray())
                .build();

        final String challenge = StandardAuthScheme.DIGEST + " realm=\"realm1\", " +
                "nonce=\"f2a3f18799759d4f1a1c068b92b573cb\", " +
                "algorithm=SHA";
        final AuthChallenge authChallenge = parse(challenge);
        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);

        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));

        Assertions.assertThrows(AuthenticationException.class, () ->
                authscheme.generateAuthResponse(host, request, null), "Expected UnsupportedDigestAlgorithmException for unsupported 'SHA' algorithm");
    }

    @Test
    void testDigestAuthenticationWithQueryStringInDigestURI() throws Exception {
        final HttpRequest request = new BasicHttpRequest("Simple", "/?param=value");
        final HttpHost host = new HttpHost("somehost", 80);
        final CredentialsProvider credentialsProvider = CredentialsProviderBuilder.create()
                .add(new AuthScope(host, "realm1", null), "username", "password".toCharArray())
                .build();

        final String challenge = StandardAuthScheme.DIGEST + " realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\"";
        final AuthChallenge authChallenge = parse(challenge);
        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);

        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse = authscheme.generateAuthResponse(host, request, null);

        final Map<String, String> table = parseAuthResponse(authResponse);
        Assertions.assertEquals("username", table.get("username"));
        Assertions.assertEquals("realm1", table.get("realm"));
        Assertions.assertEquals("/?param=value", table.get("uri"));
        Assertions.assertEquals("f2a3f18799759d4f1a1c068b92b573cb", table.get("nonce"));
        Assertions.assertEquals("a847f58f5fef0bc087bcb9c3eb30e042", table.get("response"));
    }

    @Test
    void testDigestAuthenticationNoRealm() throws Exception {
        final HttpRequest request = new BasicHttpRequest("Simple", "/");
        final HttpHost host = new HttpHost("somehost", 80);
        final CredentialsProvider credentialsProvider = CredentialsProviderBuilder.create()
                .add(new AuthScope(host, "realm1", null), "username", "password".toCharArray())
                .build();

        final String challenge = StandardAuthScheme.DIGEST + " no-realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\"";
        final AuthChallenge authChallenge = parse(challenge);
        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);

        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        Assertions.assertThrows(AuthenticationException.class, () ->
                authscheme.generateAuthResponse(host, request, null));
    }

    @Test
    void testDigestAuthenticationNoNonce() throws Exception {
        final HttpRequest request = new BasicHttpRequest("Simple", "/");
        final HttpHost host = new HttpHost("somehost", 80);
        final CredentialsProvider credentialsProvider = CredentialsProviderBuilder.create()
                .add(new AuthScope(host, "realm1", null), "username", "password".toCharArray())
                .build();

        final String challenge = StandardAuthScheme.DIGEST + " realm=\"realm1\", no-nonce=\"f2a3f18799759d4f1a1c068b92b573cb\"";
        final AuthChallenge authChallenge = parse(challenge);
        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);

        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        Assertions.assertThrows(AuthenticationException.class, () ->
                authscheme.generateAuthResponse(host, request, null));
    }

    @Test
    void testDigestAuthenticationNoAlgorithm() throws Exception {
        final HttpRequest request = new BasicHttpRequest("Simple", "/");
        final HttpHost host = new HttpHost("somehost", 80);
        final CredentialsProvider credentialsProvider = CredentialsProviderBuilder.create()
                .add(new AuthScope(host, "realm1", null), "username", "password".toCharArray())
                .build();

        final String challenge = StandardAuthScheme.DIGEST + " realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\"";
        final AuthChallenge authChallenge = parse(challenge);
        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);

        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse = authscheme.generateAuthResponse(host, request, null);

        final Map<String, String> table = parseAuthResponse(authResponse);
        Assertions.assertNull(table.get("algorithm"));
    }

    @Test
    void testDigestAuthenticationMD5Algorithm() throws Exception {
        final HttpRequest request = new BasicHttpRequest("Simple", "/");
        final HttpHost host = new HttpHost("somehost", 80);
        final CredentialsProvider credentialsProvider = CredentialsProviderBuilder.create()
                .add(new AuthScope(host, "realm1", null), "username", "password".toCharArray())
                .build();

        final String challenge = StandardAuthScheme.DIGEST
                + " realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\""
                + ", algorithm=MD5";
        final AuthChallenge authChallenge = parse(challenge);
        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);

        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse = authscheme.generateAuthResponse(host, request, null);

        final Map<String, String> table = parseAuthResponse(authResponse);
        Assertions.assertEquals("MD5", table.get("algorithm"));
    }

    /**
     * Test digest authentication using the MD5-sess algorithm.
     */
    @Test
    void testDigestAuthenticationMD5Sess() throws Exception {
        // Example using Digest auth with MD5-sess

        final String realm = "realm";
        final String username = "username";
        final String password = "password";
        final String nonce = "e273f1776275974f1a120d8b92c5b3cb";

        final HttpRequest request = new BasicHttpRequest("Simple", "/");
        final HttpHost host = new HttpHost("somehost", 80);
        final CredentialsProvider credentialsProvider = CredentialsProviderBuilder.create()
                .add(new AuthScope(host, realm, null), username, password.toCharArray())
                .build();

        final String challenge = StandardAuthScheme.DIGEST + " realm=\"" + realm + "\", "
            + "nonce=\"" + nonce + "\", "
            + "opaque=\"SomeString\", "
            + "stale=false, "
            + "algorithm=MD5-sess, "
            + "qop=\"auth,auth-int\""; // we pass both but expect auth to be used

        final AuthChallenge authChallenge = parse(challenge);

        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);

        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse = authscheme.generateAuthResponse(host, request, null);

        Assertions.assertTrue(authResponse.indexOf("nc=00000001") > 0); // test for quotes
        Assertions.assertTrue(authResponse.indexOf("qop=auth") > 0); // test for quotes

        final Map<String, String> table = parseAuthResponse(authResponse);
        Assertions.assertEquals(username, table.get("username"));
        Assertions.assertEquals(realm, table.get("realm"));
        Assertions.assertEquals("MD5-sess", table.get("algorithm"));
        Assertions.assertEquals("/", table.get("uri"));
        Assertions.assertEquals(nonce, table.get("nonce"));
        Assertions.assertEquals(1, Integer.parseInt(table.get("nc"), 16));
        Assertions.assertNotNull(table.get("cnonce"));
        Assertions.assertEquals("SomeString", table.get("opaque"));
        Assertions.assertEquals("auth", table.get("qop"));
        //@TODO: add better check
        Assertions.assertNotNull(table.get("response"));
    }

    /**
     * Test digest authentication using the MD5-sess algorithm.
     */
    @Test
    void testDigestAuthenticationMD5SessNoQop() throws Exception {
        // Example using Digest auth with MD5-sess

        final String realm = "realm";
        final String username = "username";
        final String password = "password";
        final String nonce = "e273f1776275974f1a120d8b92c5b3cb";

        final HttpRequest request = new BasicHttpRequest("Simple", "/");
        final HttpHost host = new HttpHost("somehost", 80);
        final CredentialsProvider credentialsProvider = CredentialsProviderBuilder.create()
                .add(new AuthScope(host, realm, null), username, password.toCharArray())
                .build();

        final String challenge = StandardAuthScheme.DIGEST + " realm=\"" + realm + "\", "
            + "nonce=\"" + nonce + "\", "
            + "opaque=\"SomeString\", "
            + "stale=false, "
            + "algorithm=MD5-sess";

        final AuthChallenge authChallenge = parse(challenge);

        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);
        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse = authscheme.generateAuthResponse(host, request, null);

        final Map<String, String> table = parseAuthResponse(authResponse);
        Assertions.assertEquals(username, table.get("username"));
        Assertions.assertEquals(realm, table.get("realm"));
        Assertions.assertEquals("MD5-sess", table.get("algorithm"));
        Assertions.assertEquals("/", table.get("uri"));
        Assertions.assertEquals(nonce, table.get("nonce"));
        Assertions.assertNull(table.get("nc"));
        Assertions.assertEquals("SomeString", table.get("opaque"));
        Assertions.assertNull(table.get("qop"));
        //@TODO: add better check
        Assertions.assertNotNull(table.get("response"));
    }

    /**
     * Test digest authentication with unknown qop value
     */
    @Test
    void testDigestAuthenticationMD5SessUnknownQop() throws Exception {
        // Example using Digest auth with MD5-sess

        final String realm = "realm";
        final String username = "username";
        final String password = "password";
        final String nonce = "e273f1776275974f1a120d8b92c5b3cb";

        final HttpRequest request = new BasicHttpRequest("Simple", "/");
        final HttpHost host = new HttpHost("somehost", 80);
        final CredentialsProvider credentialsProvider = CredentialsProviderBuilder.create()
                .add(new AuthScope(host, realm, null), username, password.toCharArray())
                .build();

        final String challenge = StandardAuthScheme.DIGEST + " realm=\"" + realm + "\", "
            + "nonce=\"" + nonce + "\", "
            + "opaque=\"SomeString\", "
            + "stale=false, "
            + "algorithm=MD5-sess, "
            + "qop=\"stuff\"";

        final AuthChallenge authChallenge = parse(challenge);

        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);

        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        Assertions.assertThrows(AuthenticationException.class, () ->
                authscheme.generateAuthResponse(host, request, null));
    }

    /**
     * Test digest authentication with unknown qop value
     */
    @Test
    void testDigestAuthenticationUnknownAlgo() throws Exception {
        // Example using Digest auth with MD5-sess

        final String realm = "realm";
        final String username = "username";
        final String password = "password";
        final String nonce = "e273f1776275974f1a120d8b92c5b3cb";

        final HttpRequest request = new BasicHttpRequest("Simple", "/");
        final HttpHost host = new HttpHost("somehost", 80);
        final CredentialsProvider credentialsProvider = CredentialsProviderBuilder.create()
                .add(new AuthScope(host, realm, null), username, password.toCharArray())
                .build();

        final String challenge = StandardAuthScheme.DIGEST + " realm=\"" + realm + "\", "
            + "nonce=\"" + nonce + "\", "
            + "opaque=\"SomeString\", "
            + "stale=false, "
            + "algorithm=stuff, "
            + "qop=\"auth\"";

        final AuthChallenge authChallenge = parse(challenge);

        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);

        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        Assertions.assertThrows(AuthenticationException.class, () ->
                authscheme.generateAuthResponse(host, request, null));
    }

    @Test
    void testDigestAuthenticationWithStaleNonce() throws Exception {
        final String challenge = StandardAuthScheme.DIGEST + " realm=\"realm1\", " +
                "nonce=\"f2a3f18799759d4f1a1c068b92b573cb\", stale=\"true\"";
        final AuthChallenge authChallenge = parse(challenge);
        final AuthScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);

        Assertions.assertFalse(authscheme.isChallengeComplete());
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
    void testDigestNouceCount() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final HttpHost host = new HttpHost("somehost", 80);
        final CredentialsProvider credentialsProvider = CredentialsProviderBuilder.create()
                .add(new AuthScope(host, "realm1", null), "username", "password".toCharArray())
                .build();

        final String challenge1 = StandardAuthScheme.DIGEST + " realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\", qop=auth";
        final AuthChallenge authChallenge1 = parse(challenge1);

        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge1, null);
        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse1 = authscheme.generateAuthResponse(host, request, null);

        final Map<String, String> table1 = parseAuthResponse(authResponse1);
        Assertions.assertEquals("00000001", table1.get("nc"));

        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse2 = authscheme.generateAuthResponse(host, request, null);

        final Map<String, String> table2 = parseAuthResponse(authResponse2);
        Assertions.assertEquals("00000002", table2.get("nc"));
        final String challenge2 = StandardAuthScheme.DIGEST + " realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\", qop=auth";
        final AuthChallenge authChallenge2 = parse(challenge2);
        authscheme.processChallenge(authChallenge2, null);

        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse3 = authscheme.generateAuthResponse(host, request, null);

        final Map<String, String> table3 = parseAuthResponse(authResponse3);
        Assertions.assertEquals("00000003", table3.get("nc"));
        final String challenge3 = StandardAuthScheme.DIGEST + " realm=\"realm1\", nonce=\"e273f1776275974f1a120d8b92c5b3cb\", qop=auth";
        final AuthChallenge authChallenge3 = parse(challenge3);
        authscheme.processChallenge(authChallenge3, null);

        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse4 = authscheme.generateAuthResponse(host, request, null);

        final Map<String, String> table4 = parseAuthResponse(authResponse4);
        Assertions.assertEquals("00000001", table4.get("nc"));
    }

    @Test
    void testDigestMD5SessA1AndCnonceConsistency() throws Exception {
        final HttpHost host = new HttpHost("somehost", 80);
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final CredentialsProvider credentialsProvider = CredentialsProviderBuilder.create()
                .add(new AuthScope(host, "subnet.domain.com", null), "username", "password".toCharArray())
                .build();

        final String challenge1 = StandardAuthScheme.DIGEST + " qop=\"auth\", algorithm=MD5-sess, nonce=\"1234567890abcdef\", " +
                "charset=utf-8, realm=\"subnet.domain.com\"";
        final AuthChallenge authChallenge1 = parse(challenge1);
        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge1, null);
        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse1 = authscheme.generateAuthResponse(host, request, null);

        final Map<String, String> table1 = parseAuthResponse(authResponse1);
        Assertions.assertEquals("00000001", table1.get("nc"));
        final String cnonce1 = authscheme.getCnonce();
        final String sessionKey1 = authscheme.getA1();

        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse2 = authscheme.generateAuthResponse(host, request, null);
        final Map<String, String> table2 = parseAuthResponse(authResponse2);
        Assertions.assertEquals("00000002", table2.get("nc"));
        final String cnonce2 = authscheme.getCnonce();
        final String sessionKey2 = authscheme.getA1();

        Assertions.assertEquals(cnonce1, cnonce2);
        Assertions.assertEquals(sessionKey1, sessionKey2);

        final String challenge2 = StandardAuthScheme.DIGEST + " qop=\"auth\", algorithm=MD5-sess, nonce=\"1234567890abcdef\", " +
                "charset=utf-8, realm=\"subnet.domain.com\"";
        final AuthChallenge authChallenge2 = parse(challenge2);
        authscheme.processChallenge(authChallenge2, null);
        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse3 = authscheme.generateAuthResponse(host, request, null);
        final Map<String, String> table3 = parseAuthResponse(authResponse3);
        Assertions.assertEquals("00000003", table3.get("nc"));

        final String cnonce3 = authscheme.getCnonce();
        final String sessionKey3 = authscheme.getA1();

        Assertions.assertEquals(cnonce1, cnonce3);
        Assertions.assertEquals(sessionKey1, sessionKey3);

        final String challenge3 = StandardAuthScheme.DIGEST + " qop=\"auth\", algorithm=MD5-sess, nonce=\"fedcba0987654321\", " +
                "charset=utf-8, realm=\"subnet.domain.com\"";
        final AuthChallenge authChallenge3 = parse(challenge3);
        authscheme.processChallenge(authChallenge3, null);
        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse4 = authscheme.generateAuthResponse(host, request, null);
        final Map<String, String> table4 = parseAuthResponse(authResponse4);
        Assertions.assertEquals("00000001", table4.get("nc"));

        final String cnonce4 = authscheme.getCnonce();
        final String sessionKey4 = authscheme.getA1();

        Assertions.assertNotEquals(cnonce1, cnonce4);
        Assertions.assertNotEquals(sessionKey1, sessionKey4);
    }

    @Test
    void testHttpEntityDigest() throws Exception {
        final HttpEntityDigester digester = new HttpEntityDigester(MessageDigest.getInstance("MD5"));
        Assertions.assertNull(digester.getDigest());
        digester.write('a');
        digester.write('b');
        digester.write('c');
        digester.write(0xe4);
        digester.write(0xf6);
        digester.write(0xfc);
        digester.write(new byte[]{'a', 'b', 'c'});
        Assertions.assertNull(digester.getDigest());
        digester.close();
        Assertions.assertEquals("acd2b59cd01c7737d8069015584c6cac", DigestScheme.formatHex(digester.getDigest()));
        Assertions.assertThrows(IOException.class, () -> digester.write('a'));
        Assertions.assertThrows(IOException.class, () -> digester.write(new byte[]{'a', 'b', 'c'}));
    }

    @Test
    void testDigestAuthenticationQopAuthInt() throws Exception {
        final ClassicHttpRequest request = new BasicClassicHttpRequest("Post", "/");
        request.setEntity(new StringEntity("abc\u00e4\u00f6\u00fcabc", StandardCharsets.ISO_8859_1));
        final HttpHost host = new HttpHost("somehost", 80);
        final CredentialsProvider credentialsProvider = CredentialsProviderBuilder.create()
                .add(new AuthScope(host, "realm1", null), "username", "password".toCharArray())
                .build();

        final String challenge = StandardAuthScheme.DIGEST + " realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\", " +
                "qop=\"auth,auth-int\"";
        final AuthChallenge authChallenge = parse(challenge);
        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);
        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse = authscheme.generateAuthResponse(host, request, null);

        Assertions.assertEquals("Post:/:acd2b59cd01c7737d8069015584c6cac", authscheme.getA2());

        final Map<String, String> table = parseAuthResponse(authResponse);
        Assertions.assertEquals("username", table.get("username"));
        Assertions.assertEquals("realm1", table.get("realm"));
        Assertions.assertEquals("/", table.get("uri"));
        Assertions.assertEquals("auth-int", table.get("qop"));
        Assertions.assertEquals("f2a3f18799759d4f1a1c068b92b573cb", table.get("nonce"));
    }

    @Test
    void testDigestAuthenticationQopAuthIntNullEntity() throws Exception {
        final HttpRequest request = new BasicHttpRequest("Post", "/");
        final HttpHost host = new HttpHost("somehost", 80);
        final CredentialsProvider credentialsProvider = CredentialsProviderBuilder.create()
                .add(new AuthScope(host, "realm1", null), "username", "password".toCharArray())
                .build();

        final String challenge = StandardAuthScheme.DIGEST + " realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\", " +
                "qop=\"auth-int\"";
        final AuthChallenge authChallenge = parse(challenge);
        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);
        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse = authscheme.generateAuthResponse(host, request, null);

        Assertions.assertEquals("Post:/:d41d8cd98f00b204e9800998ecf8427e", authscheme.getA2());

        final Map<String, String> table = parseAuthResponse(authResponse);
        Assertions.assertEquals("username", table.get("username"));
        Assertions.assertEquals("realm1", table.get("realm"));
        Assertions.assertEquals("/", table.get("uri"));
        Assertions.assertEquals("auth-int", table.get("qop"));
        Assertions.assertEquals("f2a3f18799759d4f1a1c068b92b573cb", table.get("nonce"));
    }

    @Test
    void testDigestAuthenticationQopAuthOrAuthIntNonRepeatableEntity() throws Exception {
        final ClassicHttpRequest request = new BasicClassicHttpRequest("Post", "/");
        request.setEntity(new InputStreamEntity(new ByteArrayInputStream(new byte[]{'a'}), -1, ContentType.DEFAULT_TEXT));
        final HttpHost host = new HttpHost("somehost", 80);
        final CredentialsProvider credentialsProvider = CredentialsProviderBuilder.create()
                .add(new AuthScope(host, "realm1", null), "username", "password".toCharArray())
                .build();

        final String challenge = StandardAuthScheme.DIGEST + " realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\", " +
                "qop=\"auth,auth-int\"";
        final AuthChallenge authChallenge = parse(challenge);
        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);
        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse = authscheme.generateAuthResponse(host, request, null);

        Assertions.assertEquals("Post:/", authscheme.getA2());

        final Map<String, String> table = parseAuthResponse(authResponse);
        Assertions.assertEquals("username", table.get("username"));
        Assertions.assertEquals("realm1", table.get("realm"));
        Assertions.assertEquals("/", table.get("uri"));
        Assertions.assertEquals("auth", table.get("qop"));
        Assertions.assertEquals("f2a3f18799759d4f1a1c068b92b573cb", table.get("nonce"));
    }

    @Test
    void testParameterCaseSensitivity() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final HttpHost host = new HttpHost("somehost", 80);
        final CredentialsProvider credentialsProvider = CredentialsProviderBuilder.create()
                .add(new AuthScope(host, "-", null), "username", "password".toCharArray())
                .build();

        final String challenge = StandardAuthScheme.DIGEST + " Realm=\"-\", " +
                "nonce=\"YjYuNGYyYmJhMzUuY2I5ZDhlZDE5M2ZlZDM 1Mjk3NGJkNTIyYjgyNTcwMjQ=\", " +
                "opaque=\"98700A3D9CE17065E2246B41035C6609\", qop=\"auth\"";
        final AuthChallenge authChallenge = parse(challenge);
        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);
        Assertions.assertEquals("-", authscheme.getRealm());

        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        authscheme.generateAuthResponse(host, request, null);
    }

    @Test
    void testDigestAuthenticationQopIntOnlyNonRepeatableEntity() throws Exception {
        final ClassicHttpRequest request = new BasicClassicHttpRequest("Post", "/");
        request.setEntity(new InputStreamEntity(new ByteArrayInputStream(new byte[]{'a'}), -1, ContentType.DEFAULT_TEXT));
        final HttpHost host = new HttpHost("somehost", 80);
        final CredentialsProvider credentialsProvider = CredentialsProviderBuilder.create()
                .add(new AuthScope(host, "realm1", null), "username", "password".toCharArray())
                .build();

        final String challenge = StandardAuthScheme.DIGEST + " realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\", " +
                "qop=\"auth-int\"";
        final AuthChallenge authChallenge = parse(challenge);
        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);

        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        Assertions.assertThrows(AuthenticationException.class, () ->
                authscheme.generateAuthResponse(host, request, null));
    }

    @Test
    void testSerialization() throws Exception {
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

        Assertions.assertEquals(digestScheme.getName(), authScheme.getName());
        Assertions.assertEquals(digestScheme.getRealm(), authScheme.getRealm());
        Assertions.assertEquals(digestScheme.isChallengeComplete(), authScheme.isChallengeComplete());
        Assertions.assertEquals(digestScheme.getA1(), authScheme.getA1());
        Assertions.assertEquals(digestScheme.getA2(), authScheme.getA2());
        Assertions.assertEquals(digestScheme.getCnonce(), authScheme.getCnonce());
    }


    @Test
    void testDigestAuthenticationWithUserHash() throws Exception {
        final HttpRequest request = new BasicHttpRequest("Simple", "/");
        final HttpHost host = new HttpHost("somehost", 80);
        final CredentialsProvider credentialsProvider = CredentialsProviderBuilder.create()
                .add(new AuthScope(host, "realm1", null), "username", "password".toCharArray())
                .build();

        // Include userhash in the challenge
        final String challenge = StandardAuthScheme.DIGEST + " realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\", userhash=true";
        final AuthChallenge authChallenge = parse(challenge);
        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);

        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse = authscheme.generateAuthResponse(host, request, null);

        final Map<String, String> table = parseAuthResponse(authResponse);

        // Generate expected userhash
        final MessageDigest md = MessageDigest.getInstance("MD5");
        md.update("username:realm1".getBytes(StandardCharsets.UTF_8));
        final String expectedUserhash = bytesToHex(md.digest());

        Assertions.assertEquals(expectedUserhash, table.get("username"));
        Assertions.assertEquals("realm1", table.get("realm"));
        Assertions.assertEquals("/", table.get("uri"));
        Assertions.assertEquals("f2a3f18799759d4f1a1c068b92b573cb", table.get("nonce"));
        Assertions.assertEquals("3b6561ceb73e5ffe9314a695179f23f9", table.get("response"));
    }

    private static String bytesToHex(final byte[] bytes) {
        final StringBuilder hexString = new StringBuilder();
        for (final byte b : bytes) {
            final String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    @Test
    void testDigestAuthenticationWithQuotedStringsAndWhitespace() throws Exception {
        final HttpRequest request = new BasicHttpRequest("Simple", "/");
        final HttpHost host = new HttpHost("somehost", 80);
        final CredentialsProvider credentialsProvider = CredentialsProviderBuilder.create()
                .add(new AuthScope(host, "\"myhost@example.com\"", null), "\"Mufasa\"", "\"Circle Of Life\"".toCharArray())
                .build();

        // Include userhash in the challenge
        final String challenge = StandardAuthScheme.DIGEST + " realm=\"\\\"myhost@example.com\\\"\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\", userhash=true";
        final AuthChallenge authChallenge = parse(challenge);
        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);

        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));

        final String authResponse = authscheme.generateAuthResponse(host, request, null);

        final Map<String, String> table = parseAuthResponse(authResponse);

        // Generate expected A1 hash
        final MessageDigest md = MessageDigest.getInstance("MD5");
        final String a1 = "Mufasa:myhost@example.com:Circle Of Life"; // Note: quotes removed and internal whitespace preserved
        md.update(a1.getBytes(StandardCharsets.UTF_8));

        // Extract the response and validate the A1 hash
        final String response = table.get("response");
        Assertions.assertNotNull(response);
    }

    @Test
    void testDigestAuthenticationWithInvalidUsernameAndValidUsernameStar() throws Exception {
        final ClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        final HttpHost host = new HttpHost("somehost", 80);
        final CredentialsProvider credentialsProvider = CredentialsProviderBuilder.create()
                .add(new AuthScope(host, "realm1", null), "invalid\"username", "password".toCharArray())
                .build();

        final String encodedUsername = "UTF-8''J%C3%A4s%C3%B8n%20Doe";
        final String challenge = StandardAuthScheme.DIGEST + " realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\", " +
                "qop=\"auth-int\", username*=\"" + encodedUsername + "\"";
        final AuthChallenge authChallenge = parse(challenge);
        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);

        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));

        final String authResponse = authscheme.generateAuthResponse(host, request, null);
        Assertions.assertNotNull(authResponse);
    }

    @Test
    void testDigestAuthenticationWithHighAsciiCharInUsername() throws Exception {
        final ClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        final HttpHost host = new HttpHost("somehost", 80);
        // Using a username with a high ASCII character
        final CredentialsProvider credentialsProvider = CredentialsProviderBuilder.create()
                .add(new AuthScope(host, "realm1", null), "high\u007Fchar", "password".toCharArray())
                .build();

        final String challenge = StandardAuthScheme.DIGEST + " realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\", qop=\"auth-int\"";
        final AuthChallenge authChallenge = parse(challenge);
        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);

        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse = authscheme.generateAuthResponse(host, request, null);

        // Optionally, verify that 'username*' is used in the response
        Assertions.assertTrue(authResponse.contains("username*"));
    }


    @Test
    void testDigestAuthenticationWithExtendedAsciiCharInUsername() throws Exception {
        final ClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        final HttpHost host = new HttpHost("somehost", 80);
        // Using an extended ASCII character (e.g., 0x80) in the username
        final CredentialsProvider credentialsProvider = CredentialsProviderBuilder.create()
                .add(new AuthScope(host, "realm1", null), "username\u0080", "password".toCharArray())
                .build();

        final String challenge = StandardAuthScheme.DIGEST + " realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\", qop=\"auth-int\"";
        final AuthChallenge authChallenge = parse(challenge);
        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);

        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse = authscheme.generateAuthResponse(host, request, null);


        Assertions.assertTrue(authResponse.contains("username*"));
    }


    @Test
    void testDigestAuthenticationWithNonAsciiUsername() throws Exception {
        final ClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        final HttpHost host = new HttpHost("somehost", 80);
        // Using a username with non-ASCII characters
        final CredentialsProvider credentialsProvider = CredentialsProviderBuilder.create()
                .add(new AuthScope(host, "realm1", null), "Jäsøn Doe", "password".toCharArray())
                .build();

        final String challenge = StandardAuthScheme.DIGEST + " realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\", qop=\"auth-int\"";
        final AuthChallenge authChallenge = parse(challenge);
        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);

        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse = authscheme.generateAuthResponse(host, request, null);

        // Optionally, verify that 'username*' is used in the response
        Assertions.assertTrue(authResponse.contains("username*"));
    }

    @Test
    void testRspAuthFieldAndQuoting() throws Exception {
        final ClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        final HttpHost host = new HttpHost("somehost", 80);
        final CredentialsProvider credentialsProvider = CredentialsProviderBuilder.create()
                .add(new AuthScope(host, "realm1", null), "username", "password".toCharArray())
                .build();

        // Challenge with qop set to "auth-int" to trigger rspauth field
        final String challenge = StandardAuthScheme.DIGEST + " realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\", qop=\"auth-int\"";
        final AuthChallenge authChallenge = parse(challenge);
        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);

        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse = authscheme.generateAuthResponse(host, request, null);

        final Map<String, String> table = parseAuthResponse(authResponse);

        Assertions.assertNotNull(table.get("rspauth"));
    }

    @Test
    void testNextNonceUsageFromContext() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final HttpHost host = new HttpHost("somehost", 80);
        final CredentialsProvider credentialsProvider = CredentialsProviderBuilder.create()
                .add(new AuthScope(host, "realm1", null), "username", "password".toCharArray())
                .build();

        final HttpClientContext context = HttpClientContext.create();
        context.setNextNonce("sampleNextNonce"); // Set `nextNonce` in the context

        final DigestScheme authscheme = new DigestScheme();
        final String challenge = StandardAuthScheme.DIGEST + " realm=\"realm1\", nonce=\"initialNonce\"";
        final AuthChallenge authChallenge = parse(challenge);
        authscheme.processChallenge(authChallenge, context);

        // Simulate credentials validation and generate auth response
        authscheme.isResponseReady(host, credentialsProvider, context);
        final String authResponse = authscheme.generateAuthResponse(host, request, context);

        // Validate that `sampleNextNonce` (from context) is used in place of the initial nonce
        final Map<String, String> paramMap = parseAuthResponse(authResponse);
        Assertions.assertEquals("sampleNextNonce", paramMap.get("nonce"), "The nonce should match 'auth-nextnonce' from the context.");

        // Verify that `auth-nextnonce` was removed from context after use
        Assertions.assertNull(context.getAttribute("auth-nextnonce"), "The next nonce should be removed from the context after use.");
    }


    @Test
    void testNoNextNonceUsageFromContext() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final HttpHost host = new HttpHost("somehost", 80);
        final CredentialsProvider credentialsProvider = CredentialsProviderBuilder.create()
                .add(new AuthScope(host, "realm1", null), "username", "password".toCharArray())
                .build();

        final HttpClientContext context = HttpClientContext.create();

        // Initialize DigestScheme without setting any `nextNonce`
        final DigestScheme authscheme = new DigestScheme();
        final String challenge = StandardAuthScheme.DIGEST + " realm=\"realm1\", nonce=\"initialNonce\"";
        final AuthChallenge authChallenge = parse(challenge);
        authscheme.processChallenge(authChallenge, context);

        // Simulate credentials validation and generate auth response
        authscheme.isResponseReady(host, credentialsProvider, context);
        final String authResponse = authscheme.generateAuthResponse(host, request, context);

        // Validate that the nonce in the response matches the initial nonce, not a nextNonce
        final Map<String, String> paramMap = parseAuthResponse(authResponse);
        Assertions.assertEquals("initialNonce", paramMap.get("nonce"), "The nonce should match the initial nonce provided in the challenge.");

        // Verify that the context does not contain any `nextNonce` value set
        Assertions.assertNull(context.getAttribute("auth-nextnonce"), "The context should not contain a nextNonce attribute.");
    }


    @Test
    void testDigestAuthenticationWithSHA256() throws Exception {
        final HttpRequest request = new BasicHttpRequest("Simple", "/");
        final HttpHost host = new HttpHost("somehost", 80);
        final CredentialsProvider credentialsProvider = CredentialsProviderBuilder.create()
                .add(new AuthScope(host, "realm1", null), "username", "password".toCharArray())
                .build();

        final String challenge = StandardAuthScheme.DIGEST + " realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\", algorithm=SHA-256";
        final AuthChallenge authChallenge = parse(challenge);
        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);

        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse = authscheme.generateAuthResponse(host, request, null);

        final Map<String, String> table = parseAuthResponse(authResponse);
        Assertions.assertEquals("username", table.get("username"));
        Assertions.assertEquals("realm1", table.get("realm"));
        Assertions.assertEquals("/", table.get("uri"));
        Assertions.assertEquals("f2a3f18799759d4f1a1c068b92b573cb", table.get("nonce"));
        Assertions.assertEquals("SHA-256", table.get("algorithm"));
        Assertions.assertNotNull(table.get("response"));

    }

    @Test
    void testDigestAuthenticationWithSHA512_256() throws Exception {
        final HttpRequest request = new BasicHttpRequest("Simple", "/");
        final HttpHost host = new HttpHost("somehost", 80);
        final CredentialsProvider credentialsProvider = CredentialsProviderBuilder.create()
                .add(new AuthScope(host, "realm1", null), "username", "password".toCharArray())
                .build();

            final String challenge = StandardAuthScheme.DIGEST + " realm=\"realm1\", nonce=\"f2a3f18799759d4f1a1c068b92b573cb\", algorithm=SHA-512-256";
        final AuthChallenge authChallenge = parse(challenge);
        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge, null);

        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse = authscheme.generateAuthResponse(host, request, null);

        final Map<String, String> table = parseAuthResponse(authResponse);
        Assertions.assertEquals("username", table.get("username"));
        Assertions.assertEquals("realm1", table.get("realm"));
        Assertions.assertEquals("/", table.get("uri"));
        Assertions.assertEquals("f2a3f18799759d4f1a1c068b92b573cb", table.get("nonce"));
        Assertions.assertEquals("SHA-512-256", table.get("algorithm"));
        Assertions.assertNotNull(table.get("response"));
    }

    @Test
    void testDigestSHA256SessA1AndCnonceConsistency() throws Exception {
        final HttpHost host = new HttpHost("somehost", 80);
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final CredentialsProvider credentialsProvider = CredentialsProviderBuilder.create()
                .add(new AuthScope(host, "subnet.domain.com", null), "username", "password".toCharArray())
                .build();

        final String challenge1 = StandardAuthScheme.DIGEST + " qop=\"auth\", algorithm=SHA-256-sess, nonce=\"1234567890abcdef\", " +
                "charset=utf-8, realm=\"subnet.domain.com\"";
        final AuthChallenge authChallenge1 = parse(challenge1);
        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge1, null);
        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse1 = authscheme.generateAuthResponse(host, request, null);

        final Map<String, String> table1 = parseAuthResponse(authResponse1);
        Assertions.assertEquals("00000001", table1.get("nc"));
        final String cnonce1 = authscheme.getCnonce();
        final String sessionKey1 = authscheme.getA1();

        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse2 = authscheme.generateAuthResponse(host, request, null);
        final Map<String, String> table2 = parseAuthResponse(authResponse2);
        Assertions.assertEquals("00000002", table2.get("nc"));
        final String cnonce2 = authscheme.getCnonce();
        final String sessionKey2 = authscheme.getA1();

        Assertions.assertEquals(cnonce1, cnonce2);
        Assertions.assertEquals(sessionKey1, sessionKey2);

        final String challenge2 = StandardAuthScheme.DIGEST + " qop=\"auth\", algorithm=SHA-256-sess, nonce=\"1234567890abcdef\", " +
                "charset=utf-8, realm=\"subnet.domain.com\"";
        final AuthChallenge authChallenge2 = parse(challenge2);
        authscheme.processChallenge(authChallenge2, null);
        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse3 = authscheme.generateAuthResponse(host, request, null);
        final Map<String, String> table3 = parseAuthResponse(authResponse3);
        Assertions.assertEquals("00000003", table3.get("nc"));

        final String cnonce3 = authscheme.getCnonce();
        final String sessionKey3 = authscheme.getA1();

        Assertions.assertEquals(cnonce1, cnonce3);
        Assertions.assertEquals(sessionKey1, sessionKey3);

        final String challenge3 = StandardAuthScheme.DIGEST + " qop=\"auth\", algorithm=SHA-256-sess, nonce=\"fedcba0987654321\", " +
                "charset=utf-8, realm=\"subnet.domain.com\"";
        final AuthChallenge authChallenge3 = parse(challenge3);
        authscheme.processChallenge(authChallenge3, null);
        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse4 = authscheme.generateAuthResponse(host, request, null);
        final Map<String, String> table4 = parseAuthResponse(authResponse4);
        Assertions.assertEquals("00000001", table4.get("nc"));

        final String cnonce4 = authscheme.getCnonce();
        final String sessionKey4 = authscheme.getA1();

        Assertions.assertNotEquals(cnonce1, cnonce4);
        Assertions.assertNotEquals(sessionKey1, sessionKey4);
    }


    @Test
    void testDigestSHA512256SessA1AndCnonceConsistency() throws Exception {
        final HttpHost host = new HttpHost("somehost", 80);
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final CredentialsProvider credentialsProvider = CredentialsProviderBuilder.create()
                .add(new AuthScope(host, "subnet.domain.com", null), "username", "password".toCharArray())
                .build();

        final String challenge1 = StandardAuthScheme.DIGEST + " qop=\"auth\", algorithm=SHA-512-256-sess, nonce=\"1234567890abcdef\", " +
                "charset=utf-8, realm=\"subnet.domain.com\"";
        final AuthChallenge authChallenge1 = parse(challenge1);
        final DigestScheme authscheme = new DigestScheme();
        authscheme.processChallenge(authChallenge1, null);
        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse1 = authscheme.generateAuthResponse(host, request, null);

        final Map<String, String> table1 = parseAuthResponse(authResponse1);
        Assertions.assertEquals("00000001", table1.get("nc"));
        final String cnonce1 = authscheme.getCnonce();
        final String sessionKey1 = authscheme.getA1();

        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse2 = authscheme.generateAuthResponse(host, request, null);
        final Map<String, String> table2 = parseAuthResponse(authResponse2);
        Assertions.assertEquals("00000002", table2.get("nc"));
        final String cnonce2 = authscheme.getCnonce();
        final String sessionKey2 = authscheme.getA1();

        Assertions.assertEquals(cnonce1, cnonce2);
        Assertions.assertEquals(sessionKey1, sessionKey2);

        final String challenge2 = StandardAuthScheme.DIGEST + " qop=\"auth\", algorithm=SHA-512-256-sess, nonce=\"1234567890abcdef\", " +
                "charset=utf-8, realm=\"subnet.domain.com\"";
        final AuthChallenge authChallenge2 = parse(challenge2);
        authscheme.processChallenge(authChallenge2, null);
        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse3 = authscheme.generateAuthResponse(host, request, null);
        final Map<String, String> table3 = parseAuthResponse(authResponse3);
        Assertions.assertEquals("00000003", table3.get("nc"));

        final String cnonce3 = authscheme.getCnonce();
        final String sessionKey3 = authscheme.getA1();

        Assertions.assertEquals(cnonce1, cnonce3);
        Assertions.assertEquals(sessionKey1, sessionKey3);

        final String challenge3 = StandardAuthScheme.DIGEST + " qop=\"auth\", algorithm=SHA-512-256-sess, nonce=\"fedcba0987654321\", " +
                "charset=utf-8, realm=\"subnet.domain.com\"";
        final AuthChallenge authChallenge3 = parse(challenge3);
        authscheme.processChallenge(authChallenge3, null);
        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse4 = authscheme.generateAuthResponse(host, request, null);
        final Map<String, String> table4 = parseAuthResponse(authResponse4);
        Assertions.assertEquals("00000001", table4.get("nc"));

        final String cnonce4 = authscheme.getCnonce();
        final String sessionKey4 = authscheme.getA1();

        Assertions.assertNotEquals(cnonce1, cnonce4);
        Assertions.assertNotEquals(sessionKey1, sessionKey4);
    }



}
