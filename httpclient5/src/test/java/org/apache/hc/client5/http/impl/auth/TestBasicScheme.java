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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.apache.hc.client5.http.auth.AuthChallenge;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.AuthenticationException;
import org.apache.hc.client5.http.auth.ChallengeType;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.ParserCursor;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Basic authentication test cases.
 */
public class TestBasicScheme {
    private static final Base64.Encoder BASE64_ENC = Base64.getEncoder();

    private static AuthChallenge parse(final String s) throws ParseException {
        final CharArrayBuffer buffer = new CharArrayBuffer(s.length());
        buffer.append(s);
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        final List<AuthChallenge> authChallenges = AuthChallengeParser.INSTANCE.parse(ChallengeType.TARGET, buffer, cursor);
        Assertions.assertEquals(1, authChallenges.size());
        return authChallenges.get(0);
    }

    @Test
    public void testBasicAuthenticationEmptyChallenge() throws Exception {
        final String challenge = StandardAuthScheme.BASIC;
        final AuthChallenge authChallenge = parse(challenge);
        final AuthScheme authscheme = new BasicScheme();
        authscheme.processChallenge(authChallenge, null);
        Assertions.assertNull(authscheme.getRealm());
    }

    @Test
    public void testBasicAuthentication() throws Exception {
        final AuthChallenge authChallenge = parse("Basic realm=\"test\"");

        final BasicScheme authscheme = new BasicScheme();
        authscheme.processChallenge(authChallenge, null);

        final HttpHost host  = new HttpHost("somehost", 80);
        final CredentialsProvider credentialsProvider = CredentialsProviderBuilder.create()
                .add(new AuthScope(host, "test", null), "testuser", "testpass".toCharArray())
                .build();

        final HttpRequest request = new BasicHttpRequest("GET", "/");
        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse = authscheme.generateAuthResponse(host, request, null);

        final byte[] testCreds =  "testuser:testpass".getBytes(StandardCharsets.US_ASCII);

        final String expected = "Basic " + BASE64_ENC.encodeToString(testCreds);

        Assertions.assertEquals(expected, authResponse);
        Assertions.assertEquals("test", authscheme.getRealm());
        Assertions.assertTrue(authscheme.isChallengeComplete());
        Assertions.assertFalse(authscheme.isConnectionBased());
    }

    static final String TEST_UTF8_PASSWORD = "123\u00A3";

    @Test
    public void testBasicAuthenticationDefaultCharset() throws Exception {
        final HttpHost host  = new HttpHost("somehost", 80);
        final UsernamePasswordCredentials creds = new UsernamePasswordCredentials("test", TEST_UTF8_PASSWORD.toCharArray());
        final BasicScheme authscheme = new BasicScheme();
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        authscheme.initPreemptive(creds);
        final String authResponse = authscheme.generateAuthResponse(host, request, null);
        Assertions.assertEquals("Basic dGVzdDoxMjPCow==", authResponse);
    }

    @Test
    public void testBasicAuthenticationDefaultCharsetUTF8() throws Exception {
        final HttpHost host  = new HttpHost("somehost", 80);
        final UsernamePasswordCredentials creds = new UsernamePasswordCredentials("test", TEST_UTF8_PASSWORD.toCharArray());
        final BasicScheme authscheme = new BasicScheme();
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        authscheme.initPreemptive(creds);
        final String authResponse = authscheme.generateAuthResponse(host, request, null);
        Assertions.assertEquals("Basic dGVzdDoxMjPCow==", authResponse);
    }

    @Test
    public void testBasicAuthenticationWithCharset() throws Exception {
        final AuthChallenge authChallenge = parse("Basic realm=\"test\", charset=\"utf-8\"");

        final BasicScheme authscheme = new BasicScheme();
        authscheme.processChallenge(authChallenge, null);

        final HttpHost host  = new HttpHost("somehost", 80);
        final CredentialsProvider credentialsProvider = CredentialsProviderBuilder.create()
                .add(new AuthScope(host, "test", null), "test", TEST_UTF8_PASSWORD.toCharArray())
                .build();

        final HttpRequest request = new BasicHttpRequest("GET", "/");
        Assertions.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse = authscheme.generateAuthResponse(host, request, null);
        Assertions.assertEquals("Basic dGVzdDoxMjPCow==", authResponse);
        Assertions.assertEquals("test", authscheme.getRealm());
        Assertions.assertTrue(authscheme.isChallengeComplete());
        Assertions.assertFalse(authscheme.isConnectionBased());
    }

    @Test
    public void testSerialization() throws Exception {
        final AuthChallenge authChallenge = parse("Basic realm=\"test\"");

        final BasicScheme basicScheme = new BasicScheme();
        basicScheme.processChallenge(authChallenge, null);

        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        final ObjectOutputStream out = new ObjectOutputStream(buffer);
        out.writeObject(basicScheme);
        out.flush();
        final byte[] raw = buffer.toByteArray();
        final ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(raw));
        final BasicScheme authScheme = (BasicScheme) in.readObject();

        Assertions.assertEquals(basicScheme.getName(), authScheme.getName());
        Assertions.assertEquals(basicScheme.getRealm(), authScheme.getRealm());
        Assertions.assertEquals(basicScheme.isChallengeComplete(), authScheme.isChallengeComplete());
    }

    @Test
    public void testSerializationUnchallenged() throws Exception {
        final BasicScheme basicScheme = new BasicScheme();

        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        final ObjectOutputStream out = new ObjectOutputStream(buffer);
        out.writeObject(basicScheme);
        out.flush();
        final byte[] raw = buffer.toByteArray();
        final ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(raw));
        final BasicScheme authScheme = (BasicScheme) in.readObject();

        Assertions.assertEquals(basicScheme.getName(), authScheme.getName());
        Assertions.assertEquals(basicScheme.getRealm(), authScheme.getRealm());
        Assertions.assertEquals(basicScheme.isChallengeComplete(), authScheme.isChallengeComplete());
    }

    @Test
    public void testBasicAuthenticationUserCredentialsMissing() throws Exception {
        final BasicScheme authscheme = new BasicScheme();
        final HttpHost host  = new HttpHost("somehost", 80);
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        Assertions.assertThrows(AuthenticationException.class, () -> authscheme.generateAuthResponse(host, request, null));
    }

    @Test
    public void testBasicAuthenticationUsernameWithBlank() throws Exception {
        final BasicScheme authscheme = new BasicScheme();
        final HttpHost host  = new HttpHost("somehost", 80);
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        authscheme.initPreemptive(new UsernamePasswordCredentials("blah blah", null));
        authscheme.generateAuthResponse(host, request, null);
    }

    @Test
    public void testBasicAuthenticationUsernameWithTab() throws Exception {
        final BasicScheme authscheme = new BasicScheme();
        final HttpHost host  = new HttpHost("somehost", 80);
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        authscheme.initPreemptive(new UsernamePasswordCredentials("blah\tblah", null));
        Assertions.assertThrows(AuthenticationException.class, () -> authscheme.generateAuthResponse(host, request, null));
    }

    @Test
    public void testBasicAuthenticationUsernameWithColon() throws Exception {
        final BasicScheme authscheme = new BasicScheme();
        final HttpHost host  = new HttpHost("somehost", 80);
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        authscheme.initPreemptive(new UsernamePasswordCredentials("blah:blah", null));
        Assertions.assertThrows(AuthenticationException.class, () -> authscheme.generateAuthResponse(host, request, null));
    }

    @Test
    public void testBasicAuthenticationPasswordWithControlCharacters() throws Exception {
        final BasicScheme authscheme = new BasicScheme();
        final HttpHost host = new HttpHost("somehost", 80);
        final HttpRequest request = new BasicHttpRequest("GET", "/");

        // Creating a password with a control character (ASCII code 0-31 or 127)
        final char[] password = new char[]{'p', 'a', 's', 's', 0x1F, 'w', 'o', 'r', 'd'};
        authscheme.initPreemptive(new UsernamePasswordCredentials("username", password));

        // Expecting an AuthenticationException due to control character in password
        Assertions.assertThrows(AuthenticationException.class, () -> authscheme.generateAuthResponse(host, request, null));
    }

}
