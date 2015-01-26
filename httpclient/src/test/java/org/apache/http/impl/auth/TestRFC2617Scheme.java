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
import java.nio.charset.Charset;

import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.auth.AUTH;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.MalformedChallengeException;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BufferedHeader;
import org.apache.http.util.CharArrayBuffer;
import org.junit.Assert;
import org.junit.Test;

public class TestRFC2617Scheme {

    static class TestAuthScheme extends RFC2617Scheme {

        private static final long serialVersionUID = 1L;

        public TestAuthScheme() {
            super();
        }

        public TestAuthScheme(final Charset charset) {
            super(charset);
        }

        @Override
        @Deprecated
        public Header authenticate(
                final Credentials credentials,
                final HttpRequest request) throws AuthenticationException {
            return null;
        }

        @Override
        public String getSchemeName() {
            return "test";
        }

        @Override
        public boolean isComplete() {
            return false;
        }

        @Override
        public boolean isConnectionBased() {
            return false;
        }

    }

    @Test
    public void testProcessChallenge() throws Exception {
        final TestAuthScheme authscheme = new TestAuthScheme();
        final Header header = new BasicHeader(
                AUTH.WWW_AUTH,
                "Test realm=\"realm1\", test, test1 =  stuff, test2 =  \"stuff, stuff\", test3=\"crap");

        authscheme.processChallenge(header);

        Assert.assertEquals("test", authscheme.getSchemeName());
        Assert.assertEquals("TEST", authscheme.toString());
        Assert.assertEquals("realm1", authscheme.getParameter("realm"));
        Assert.assertEquals(null, authscheme.getParameter("test"));
        Assert.assertEquals("stuff", authscheme.getParameter("test1"));
        Assert.assertEquals("stuff, stuff", authscheme.getParameter("test2"));
        Assert.assertEquals("crap", authscheme.getParameter("test3"));
        Assert.assertEquals(null, authscheme.getParameter(null));
    }

    @Test
    public void testProcessChallengeWithLotsOfBlanks() throws Exception {
        final TestAuthScheme authscheme = new TestAuthScheme();
        final CharArrayBuffer buffer = new CharArrayBuffer(32);
        buffer.append(" WWW-Authenticate:    Test       realm=\"realm1\"");
        final Header header = new BufferedHeader(buffer);


        authscheme.processChallenge(header);

        Assert.assertEquals("test", authscheme.getSchemeName());
        Assert.assertEquals("realm1", authscheme.getParameter("realm"));
    }

    @Test
    public void testNullHeader() throws Exception {
        final TestAuthScheme authscheme = new TestAuthScheme();
        try {
            authscheme.processChallenge(null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
        }
    }

    @Test(expected=MalformedChallengeException.class)
    public void testInvalidHeader() throws Exception {
        final TestAuthScheme authscheme = new TestAuthScheme();
        final Header header = new BasicHeader("whatever", "Test realm=\"realm1\"");
        authscheme.processChallenge(header);
    }

    @Test(expected=MalformedChallengeException.class)
    public void testInvalidSchemeName() throws Exception {
        final TestAuthScheme authscheme = new TestAuthScheme();
        final Header header = new BasicHeader(AUTH.WWW_AUTH, "Not-a-Test realm=\"realm1\"");
        authscheme.processChallenge(header);
    }

    @Test(expected=MalformedChallengeException.class)
    public void testInvalidHeaderValue() throws Exception {
        final TestAuthScheme authscheme = new TestAuthScheme();
        final Header header = new BasicHeader("whatever", "whatever");
        authscheme.processChallenge(header);
    }

    @Test
    public void testSerialization() throws Exception {
        final Header challenge = new BasicHeader(AUTH.WWW_AUTH, "test realm=\"test\", blah=blah, yada=\"yada yada\"");

        final TestAuthScheme testScheme = new TestAuthScheme(Consts.ISO_8859_1);
        testScheme.processChallenge(challenge);

        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        final ObjectOutputStream out = new ObjectOutputStream(buffer);
        out.writeObject(testScheme);
        out.flush();
        final byte[] raw = buffer.toByteArray();
        final ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(raw));
        final TestAuthScheme authScheme = (TestAuthScheme) in.readObject();

        Assert.assertEquals(Consts.ISO_8859_1, authScheme.getCredentialsCharset());
        Assert.assertEquals("test", authScheme.getParameter("realm"));
        Assert.assertEquals("blah", authScheme.getParameter("blah"));
        Assert.assertEquals("yada yada", authScheme.getParameter("yada"));
    }

}

