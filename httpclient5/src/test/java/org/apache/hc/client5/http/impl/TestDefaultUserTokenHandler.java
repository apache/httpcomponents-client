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
package org.apache.hc.client5.http.impl;

import java.security.Principal;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.auth.AuthExchange;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.BasicUserPrincipal;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TestDefaultUserTokenHandler {

    private static AuthScheme connectionBasedScheme(final Principal principal) {
        final AuthScheme authScheme = Mockito.mock(AuthScheme.class);
        Mockito.when(authScheme.isConnectionBased()).thenReturn(true);
        Mockito.when(authScheme.getPrincipal()).thenReturn(principal);
        return authScheme;
    }

    @Test
    void testConnectionBasedPrincipalRequestWithoutPort() {
        final Principal principal = new BasicUserPrincipal("alice");
        final HttpClientContext context = HttpClientContext.create();
        // ProtocolExec keys the target auth exchange with the default port resolved
        final AuthExchange authExchange = context.getAuthExchange(new HttpHost("https", "somehost", 443));
        authExchange.select(connectionBasedScheme(principal));
        authExchange.setState(AuthExchange.State.SUCCESS);

        final HttpRoute route = new HttpRoute(new HttpHost("https", "somehost", 443));
        final HttpGet request = new HttpGet("https://somehost/stuff");

        Assertions.assertEquals(principal, DefaultUserTokenHandler.INSTANCE.getUserToken(route, request, context));
    }

    @Test
    void testConnectionBasedPrincipalCustomSchemePortResolver() {
        final SchemePortResolver schemePortResolver = host -> host.getPort() > 0 ? host.getPort() : 8443;
        final Principal principal = new BasicUserPrincipal("alice");
        final HttpClientContext context = HttpClientContext.create();
        final AuthExchange authExchange = context.getAuthExchange(new HttpHost("https", "somehost", 8443));
        authExchange.select(connectionBasedScheme(principal));
        authExchange.setState(AuthExchange.State.SUCCESS);

        final HttpRoute route = new HttpRoute(new HttpHost("https", "somehost", 8443));
        final HttpGet request = new HttpGet("https://somehost/stuff");

        final DefaultUserTokenHandler userTokenHandler = new DefaultUserTokenHandler(schemePortResolver);
        Assertions.assertEquals(principal, userTokenHandler.getUserToken(route, request, context));
    }

}
