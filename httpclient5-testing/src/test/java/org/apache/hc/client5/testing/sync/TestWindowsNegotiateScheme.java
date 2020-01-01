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
package org.apache.hc.client5.testing.sync;

import java.io.IOException;

import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.win.WinHttpClients;
import org.apache.hc.client5.http.impl.win.WindowsNegotiateSchemeGetTokenFail;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.Assume;
import org.junit.Test;

/**
 * Unit tests for Windows negotiate authentication.
 */
public class TestWindowsNegotiateScheme extends LocalServerTestBase {

    @Test(timeout=30000) // this timeout (in ms) needs to be extended if you're actively debugging the code
    public void testNoInfiniteLoopOnSPNOutsideDomain() throws Exception {
        this.server.registerHandler("/", new HttpRequestHandler() {

            @Override
            public void handle(
                    final ClassicHttpRequest request,
                    final ClassicHttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                response.addHeader(HttpHeaders.WWW_AUTHENTICATE, StandardAuthScheme.SPNEGO);
                response.setCode(HttpStatus.SC_UNAUTHORIZED);
            }

        });
        Assume.assumeTrue("Test can only be run on Windows", WinHttpClients.isWinAuthAvailable());

        // HTTPCLIENT-1545
        // If a service principal name (SPN) from outside your Windows domain tree (e.g., HTTP/example.com) is used,
        // InitializeSecurityContext will return SEC_E_DOWNGRADE_DETECTED (decimal: -2146892976, hex: 0x80090350).
        // Because WindowsNegotiateScheme wasn't setting the completed state correctly when authentication fails,
        // HttpClient goes into an infinite loop, constantly retrying the negotiate authentication to kingdom
        // come. This error message, "The system detected a possible attempt to compromise security. Please ensure that
        // you can contact the server that authenticated you." is associated with SEC_E_DOWNGRADE_DETECTED.

        final Registry<AuthSchemeFactory> authSchemeRegistry = RegistryBuilder.<AuthSchemeFactory>create()
            .register(StandardAuthScheme.SPNEGO, new AuthSchemeFactory() {
                @Override
                public AuthScheme create(final HttpContext context) {
                    return new WindowsNegotiateSchemeGetTokenFail(StandardAuthScheme.SPNEGO, "HTTP/example.com");
                }
            }).build();
        final CloseableHttpClient customClient = HttpClientBuilder.create()
                .setDefaultAuthSchemeRegistry(authSchemeRegistry).build();

        final HttpHost target = start();
        final HttpGet httpGet = new HttpGet("/");
        try (final CloseableHttpResponse response = customClient.execute(target, httpGet)) {
            EntityUtils.consume(response.getEntity());
        }
    }

}
