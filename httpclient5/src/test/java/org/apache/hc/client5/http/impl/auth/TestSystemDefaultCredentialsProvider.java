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

import java.net.Authenticator;
import java.net.Authenticator.RequestorType;
import java.net.InetAddress;
import java.net.PasswordAuthentication;
import java.net.URL;

import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

/**
 * Simple tests for {@link SystemDefaultCredentialsProvider}.
 */
public class TestSystemDefaultCredentialsProvider {

    private final static String PROXY_PROTOCOL1 = "http";
    private final static String PROXY_HOST1 = "proxyhost1";
    private final static int PROXY_PORT1 = 3128;
    private final static String PROMPT1 = "HttpClient authentication test prompt";
    private final static String TARGET_SCHEME1 = "https";
    private final static String TARGET_HOST1 = "targethost1";
    private final static int TARGET_PORT1 = 80;
    private final static PasswordAuthentication AUTH1 =
        new PasswordAuthentication("testUser", "testPassword".toCharArray());

    // It's not possible to mock static Authenticator methods. So we mock a delegate
    private final class DelegatedAuthenticator extends Authenticator {
        private final AuthenticatorDelegate authenticatorDelegate;

        private DelegatedAuthenticator(final AuthenticatorDelegate authenticatorDelegate) {
            this.authenticatorDelegate = authenticatorDelegate;
        }

        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            return authenticatorDelegate.getPasswordAuthentication(getRequestingHost(), getRequestingSite(),
                                                                   getRequestingPort(), getRequestingProtocol(),
                                                                   getRequestingPrompt(), getRequestingScheme(),
                                                                   getRequestingURL(), getRequestorType());
        }
    }

    private interface AuthenticatorDelegate {
        PasswordAuthentication getPasswordAuthentication(
            String host,
            InetAddress addr,
            int port,
            String protocol,
            String prompt,
            String scheme,
            URL url,
            RequestorType reqType);
    }

    @Test
    public void testSystemCredentialsProviderCredentials() throws Exception {

        final AuthenticatorDelegate authenticatorDelegate = installAuthenticator(AUTH1);

        final URL httpRequestUrl = new URL(TARGET_SCHEME1, TARGET_HOST1, TARGET_PORT1, "/");
        final AuthScope authScope = new AuthScope(PROXY_PROTOCOL1, PROXY_HOST1, PROXY_PORT1, PROMPT1, StandardAuthScheme.BASIC);
        final HttpCoreContext coreContext = new HttpCoreContext();
        coreContext.setAttribute(HttpCoreContext.HTTP_REQUEST, new HttpGet(httpRequestUrl.toURI()));

        final Credentials receivedCredentials =
            new SystemDefaultCredentialsProvider().getCredentials(authScope, coreContext);

        Mockito.verify(authenticatorDelegate).getPasswordAuthentication(PROXY_HOST1, null, PROXY_PORT1, PROXY_PROTOCOL1,
                                                                        PROMPT1, StandardAuthScheme.BASIC, httpRequestUrl,
                                                                        RequestorType.SERVER);
        Assert.assertNotNull(receivedCredentials);
        Assert.assertEquals(AUTH1.getUserName(), receivedCredentials.getUserPrincipal().getName());
        Assert.assertEquals(AUTH1.getPassword(), receivedCredentials.getPassword());
    }

    @Test
    public void testSystemCredentialsProviderNoContext() throws Exception {

        final AuthenticatorDelegate authenticatorDelegate = installAuthenticator(AUTH1);

        final AuthScope authScope = new AuthScope(PROXY_PROTOCOL1, PROXY_HOST1, PROXY_PORT1, PROMPT1, StandardAuthScheme.BASIC);

        final Credentials receivedCredentials =
            new SystemDefaultCredentialsProvider().getCredentials(authScope, null);

        Mockito.verify(authenticatorDelegate).getPasswordAuthentication(PROXY_HOST1, null, PROXY_PORT1, PROXY_PROTOCOL1,
                                                                        PROMPT1, StandardAuthScheme.BASIC, null,
                                                                        RequestorType.SERVER);
        Assert.assertNotNull(receivedCredentials);
        Assert.assertEquals(AUTH1.getUserName(), receivedCredentials.getUserPrincipal().getName());
        Assert.assertEquals(AUTH1.getPassword(), receivedCredentials.getPassword());
    }

    private AuthenticatorDelegate installAuthenticator(final PasswordAuthentication returedAuthentication) {
        final AuthenticatorDelegate authenticatorDelegate = Mockito.mock(AuthenticatorDelegate.class);
        Mockito.when(authenticatorDelegate.getPasswordAuthentication(ArgumentMatchers.anyString(),
                                                                     ArgumentMatchers.<InetAddress>any(), ArgumentMatchers.anyInt(),
                                                                     ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                                                                     ArgumentMatchers.anyString(), ArgumentMatchers.<URL>any(),
                                                                     ArgumentMatchers.<RequestorType>any())).thenReturn(returedAuthentication);
        Authenticator.setDefault(new DelegatedAuthenticator(authenticatorDelegate));
        return authenticatorDelegate;
    }
}
