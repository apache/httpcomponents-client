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

import org.apache.commons.codec.binary.Base64;
import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.auth.AUTH;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EncodingUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 * Basic authentication test cases.
 */
public class TestBasicScheme {

    @Test
    public void testBasicAuthenticationEmptyChallenge() throws Exception {
        final String challenge = "Basic";
        final Header header = new BasicHeader(AUTH.WWW_AUTH, challenge);
        final AuthScheme authscheme = new BasicScheme();
        authscheme.processChallenge(header);
        Assert.assertNull(authscheme.getRealm());
    }

    @Test
    public void testBasicAuthenticationWith88591Chars() throws Exception {
        final int[] germanChars = { 0xE4, 0x2D, 0xF6, 0x2D, 0xFc };
        final StringBuilder buffer = new StringBuilder();
        for (final int germanChar : germanChars) {
            buffer.append((char)germanChar);
        }

        final UsernamePasswordCredentials creds = new UsernamePasswordCredentials("dh", buffer.toString());
        final BasicScheme authscheme = new BasicScheme(Consts.ISO_8859_1);

        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final HttpContext context = new BasicHttpContext();
        final Header header = authscheme.authenticate(creds, request, context);
        Assert.assertEquals("Basic ZGg65C32Lfw=", header.getValue());
    }

    @Test
    public void testBasicAuthentication() throws Exception {
        final UsernamePasswordCredentials creds =
            new UsernamePasswordCredentials("testuser", "testpass");

        final Header challenge = new BasicHeader(AUTH.WWW_AUTH, "Basic realm=\"test\"");

        final BasicScheme authscheme = new BasicScheme();
        authscheme.processChallenge(challenge);

        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final HttpContext context = new BasicHttpContext();
        final Header authResponse = authscheme.authenticate(creds, request, context);

        final String expected = "Basic " + EncodingUtils.getAsciiString(
            Base64.encodeBase64(EncodingUtils.getAsciiBytes("testuser:testpass")));
        Assert.assertEquals(AUTH.WWW_AUTH_RESP, authResponse.getName());
        Assert.assertEquals(expected, authResponse.getValue());
        Assert.assertEquals("test", authscheme.getRealm());
        Assert.assertTrue(authscheme.isComplete());
        Assert.assertFalse(authscheme.isConnectionBased());
    }

    @Test
    public void testBasicProxyAuthentication() throws Exception {
        final UsernamePasswordCredentials creds =
            new UsernamePasswordCredentials("testuser", "testpass");

        final Header challenge = new BasicHeader(AUTH.PROXY_AUTH, "Basic realm=\"test\"");

        final BasicScheme authscheme = new BasicScheme();
        authscheme.processChallenge(challenge);

        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final HttpContext context = new BasicHttpContext();
        final Header authResponse = authscheme.authenticate(creds, request, context);

        final String expected = "Basic " + EncodingUtils.getAsciiString(
            Base64.encodeBase64(EncodingUtils.getAsciiBytes("testuser:testpass")));
        Assert.assertEquals(AUTH.PROXY_AUTH_RESP, authResponse.getName());
        Assert.assertEquals(expected, authResponse.getValue());
        Assert.assertEquals("test", authscheme.getRealm());
        Assert.assertTrue(authscheme.isComplete());
        Assert.assertFalse(authscheme.isConnectionBased());
    }

    @Test
    public void testSerialization() throws Exception {
        final Header challenge = new BasicHeader(AUTH.PROXY_AUTH, "Basic realm=\"test\"");

        final BasicScheme basicScheme = new BasicScheme();
        basicScheme.processChallenge(challenge);

        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        final ObjectOutputStream out = new ObjectOutputStream(buffer);
        out.writeObject(basicScheme);
        out.flush();
        final byte[] raw = buffer.toByteArray();
        final ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(raw));
        final BasicScheme authScheme = (BasicScheme) in.readObject();

        Assert.assertEquals(basicScheme.getSchemeName(), authScheme.getSchemeName());
        Assert.assertEquals(basicScheme.getRealm(), authScheme.getRealm());
        Assert.assertEquals(basicScheme.isComplete(), authScheme.isComplete());
        Assert.assertEquals(true, basicScheme.isProxy());
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

        Assert.assertEquals(basicScheme.getSchemeName(), authScheme.getSchemeName());
        Assert.assertEquals(basicScheme.getRealm(), authScheme.getRealm());
        Assert.assertEquals(basicScheme.isComplete(), authScheme.isComplete());
        Assert.assertEquals(false, basicScheme.isProxy());
    }

}
