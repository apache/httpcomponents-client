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

import java.util.Arrays;

import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.auth.AuthChallenge;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.ChallengeType;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.MalformedChallengeException;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HttpContext;
import org.junit.Assert;
import org.junit.Test;

public class TestStandardHttpScheme {

    static class TestAuthScheme extends StandardAuthScheme {

        private static final long serialVersionUID = 1L;

        @Override
        public void processChallenge(
                final ChallengeType challengeType, final AuthChallenge authChallenge) throws MalformedChallengeException {
            update(challengeType, authChallenge);
        }

        @Override
        public Header authenticate(
                final Credentials credentials,
                final HttpRequest request,
                final HttpContext context) throws AuthenticationException {
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
        authscheme.processChallenge(ChallengeType.TARGET, new AuthChallenge("Test", null, Arrays.asList(
                new BasicNameValuePair("realm", "realm1"), new BasicNameValuePair("this", "blah"))));

        Assert.assertEquals("test", authscheme.getSchemeName());
        Assert.assertEquals("test(TARGET) {realm=realm1, this=blah}", authscheme.toString());
        Assert.assertEquals("realm1", authscheme.getParameter("realm"));
        Assert.assertEquals(null, authscheme.getParameter("test"));
        Assert.assertEquals("blah", authscheme.getParameter("this"));
    }

}

