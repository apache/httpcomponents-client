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
package org.apache.http.impl.auth;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.Consts;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.auth.AuthChallenge;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.ParserCursor;
import org.apache.http.util.CharArrayBuffer;
import org.apache.http.util.EncodingUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 * Basic authentication test cases.
 */
public class TestBasicScheme {

    private static AuthChallenge parse(final String s) {
        final CharArrayBuffer buffer = new CharArrayBuffer(s.length());
        buffer.append(s);
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        final List<AuthChallenge> authChallenges = AuthChallengeParser.INSTANCE.parse(buffer, cursor);
        Assert.assertEquals(1, authChallenges.size());
        return authChallenges.get(0);
    }

    @Test
    public void testBasicAuthenticationEmptyChallenge() throws Exception {
        final String challenge = "Basic";
        final AuthChallenge authChallenge = parse(challenge);
        final AuthScheme authscheme = new BasicScheme();
        authscheme.processChallenge(authChallenge, null);
        Assert.assertNull(authscheme.getRealm());
    }

    @Test
    public void testBasicAuthenticationWith88591Chars() throws Exception {
        final int[] germanChars = { 0xE4, 0x2D, 0xF6, 0x2D, 0xFc };
        final StringBuilder buffer = new StringBuilder();
        for (final int germanChar : germanChars) {
            buffer.append((char)germanChar);
        }

        final HttpHost host  = new HttpHost("somehost", 80);
        final AuthScope authScope = new AuthScope(host, "some realm", null);
        final UsernamePasswordCredentials creds = new UsernamePasswordCredentials("dh", buffer.toString());
        final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(authScope, creds);
        final BasicScheme authscheme = new BasicScheme(Consts.ISO_8859_1);

        Assert.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final String authResponse = authscheme.generateAuthResponse(host, request, null);
        Assert.assertEquals("Basic ZGg65C32Lfw=", authResponse);
    }

    @Test
    public void testBasicAuthentication() throws Exception {
        final AuthChallenge authChallenge = parse("Basic realm=\"test\"");

        final BasicScheme authscheme = new BasicScheme();
        authscheme.processChallenge(authChallenge, null);

        final HttpHost host  = new HttpHost("somehost", 80);
        final AuthScope authScope = new AuthScope(host, "test", null);
        final UsernamePasswordCredentials creds = new UsernamePasswordCredentials("testuser", "testpass");
        final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(authScope, creds);

        final HttpRequest request = new BasicHttpRequest("GET", "/");
        Assert.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse = authscheme.generateAuthResponse(host, request, null);

        final String expected = "Basic " + EncodingUtils.getAsciiString(
            Base64.encodeBase64(EncodingUtils.getAsciiBytes("testuser:testpass")));
        Assert.assertEquals(expected, authResponse);
        Assert.assertEquals("test", authscheme.getRealm());
        Assert.assertTrue(authscheme.isChallengeComplete());
        Assert.assertFalse(authscheme.isConnectionBased());
    }

    @Test
    public void testBasicProxyAuthentication() throws Exception {
        final AuthChallenge authChallenge = parse("Basic realm=\"test\"");

        final BasicScheme authscheme = new BasicScheme();
        authscheme.processChallenge(authChallenge, null);

        final HttpHost host  = new HttpHost("somehost", 80);
        final AuthScope authScope = new AuthScope(host, "test", null);
        final UsernamePasswordCredentials creds = new UsernamePasswordCredentials("testuser", "testpass");
        final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(authScope, creds);

        final HttpRequest request = new BasicHttpRequest("GET", "/");
        Assert.assertTrue(authscheme.isResponseReady(host, credentialsProvider, null));
        final String authResponse = authscheme.generateAuthResponse(host, request, null);

        final String expected = "Basic " + EncodingUtils.getAsciiString(
            Base64.encodeBase64(EncodingUtils.getAsciiBytes("testuser:testpass")));
        Assert.assertEquals(expected, authResponse);
        Assert.assertEquals("test", authscheme.getRealm());
        Assert.assertTrue(authscheme.isChallengeComplete());
        Assert.assertFalse(authscheme.isConnectionBased());
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

        Assert.assertEquals(basicScheme.getName(), authScheme.getName());
        Assert.assertEquals(basicScheme.getRealm(), authScheme.getRealm());
        Assert.assertEquals(basicScheme.isChallengeComplete(), authScheme.isChallengeComplete());
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

        Assert.assertEquals(basicScheme.getName(), authScheme.getName());
        Assert.assertEquals(basicScheme.getRealm(), authScheme.getRealm());
        Assert.assertEquals(basicScheme.isChallengeComplete(), authScheme.isChallengeComplete());
    }

}
