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
package org.apache.hc.client5.http.impl.win;

import org.apache.hc.client5.http.auth.AuthChallenge;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.ChallengeType;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.AuthChallengeParser;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.ParserCursor;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Basic authentication test cases.
 */
public class TestWindowsNegotiateScheme {
    private static AuthChallenge parse(final String s) throws ParseException {
        final CharArrayBuffer buffer = new CharArrayBuffer(s.length());
        buffer.append(s);
        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        final List<AuthChallenge> authChallenges =
                AuthChallengeParser.INSTANCE.parse(ChallengeType.TARGET, buffer, cursor);
        Assert.assertEquals(1, authChallenges.size());
        return authChallenges.get(0);
    }

    @Test
    public void testSPNEGOAuthenticationEmptyChallenge() throws Exception {
        final String challenge = StandardAuthScheme.SPNEGO;
        final AuthChallenge authChallenge = parse(challenge);
        final AuthScheme authscheme = new WindowsNegotiateScheme(StandardAuthScheme.SPNEGO, "HTTP/example.com");
        authscheme.processChallenge(authChallenge, null);
        Assert.assertNull(authscheme.getRealm());
    }

    @Test
    public void testSerialization() throws Exception {
        final AuthChallenge authChallenge = parse(StandardAuthScheme.SPNEGO + " realm=\"test\"");

        final WindowsNegotiateScheme authscheme =
                new WindowsNegotiateScheme(StandardAuthScheme.SPNEGO, "HTTP/example.com");
        authscheme.processChallenge(authChallenge, null);

        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        final ObjectOutputStream out = new ObjectOutputStream(buffer);
        out.writeObject(authscheme);
        out.flush();
        final byte[] raw = buffer.toByteArray();
        final ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(raw));
        final WindowsNegotiateScheme authScheme = (WindowsNegotiateScheme) in.readObject();

        Assert.assertEquals(authscheme.getName(), authScheme.getName());
        Assert.assertEquals(authscheme.getRealm(), authScheme.getRealm());
        Assert.assertEquals(authscheme.isChallengeComplete(), authScheme.isChallengeComplete());
    }

    @Test
    public void testSerializationUnchallenged() throws Exception {
        final WindowsNegotiateScheme basicScheme =
                new WindowsNegotiateScheme(StandardAuthScheme.SPNEGO, "HTTP/example.com");

        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        final ObjectOutputStream out = new ObjectOutputStream(buffer);
        out.writeObject(basicScheme);
        out.flush();
        final byte[] raw = buffer.toByteArray();
        final ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(raw));
        final WindowsNegotiateScheme authScheme = (WindowsNegotiateScheme) in.readObject();

        Assert.assertEquals(basicScheme.getName(), authScheme.getName());
        Assert.assertEquals(basicScheme.getRealm(), authScheme.getRealm());
        Assert.assertEquals(basicScheme.isChallengeComplete(), authScheme.isChallengeComplete());
    }
}
