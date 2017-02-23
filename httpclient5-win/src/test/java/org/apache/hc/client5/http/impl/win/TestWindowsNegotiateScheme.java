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

import java.io.IOException;

import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.AuthSchemeProvider;
import org.apache.hc.client5.http.config.AuthSchemes;
import org.apache.hc.client5.http.impl.sync.CloseableHttpClient;
import org.apache.hc.client5.http.impl.sync.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.sync.HttpClientBuilder;
import org.apache.hc.client5.http.localserver.LocalServerTestBase;
import org.apache.hc.client5.http.sync.methods.HttpGet;
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
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import com.sun.jna.platform.win32.Sspi.CtxtHandle;
import com.sun.jna.platform.win32.Sspi.SecBufferDesc;
import com.sun.jna.platform.win32.Win32Exception;
import com.sun.jna.platform.win32.WinError;

/**
 * Unit tests for Windows negotiate authentication.
 */
public class TestWindowsNegotiateScheme extends LocalServerTestBase {

    @Before @Override
    public void setUp() throws Exception {
        super.setUp();
        this.serverBootstrap.registerHandler("/", new HttpRequestHandler() {

            @Override
            public void handle(
                    final ClassicHttpRequest request,
                    final ClassicHttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                response.addHeader(HttpHeaders.WWW_AUTHENTICATE, AuthSchemes.SPNEGO);
                response.setCode(HttpStatus.SC_UNAUTHORIZED);
            }

        });
    }

    @After @Override
    public void shutDown() throws Exception {
        super.shutDown();
    }

    @Test(timeout=30000) // this timeout (in ms) needs to be extended if you're actively debugging the code
    public void testNoInfiniteLoopOnSPNOutsideDomain() throws Exception {
        Assume.assumeTrue("Test can only be run on Windows", WinHttpClients.isWinAuthAvailable());

        // HTTPCLIENT-1545
        // If a service principal name (SPN) from outside your Windows domain tree (e.g., HTTP/example.com) is used,
        // InitializeSecurityContext will return SEC_E_DOWNGRADE_DETECTED (decimal: -2146892976, hex: 0x80090350).
        // Because WindowsNegotiateScheme wasn't setting the completed state correctly when authentication fails,
        // HttpClient goes into an infinite loop, constantly retrying the negotiate authentication to kingdom
        // come. This error message, "The system detected a possible attempt to compromise security. Please ensure that
        // you can contact the server that authenticated you." is associated with SEC_E_DOWNGRADE_DETECTED.

        final Registry<AuthSchemeProvider> authSchemeRegistry = RegistryBuilder.<AuthSchemeProvider>create()
            .register(AuthSchemes.SPNEGO, new AuthSchemeProvider() {
                @Override
                public AuthScheme create(final HttpContext context) {
                    return new WindowsNegotiateSchemeGetTokenFail(AuthSchemes.SPNEGO, "HTTP/example.com");
                }
            }).build();
        final CloseableHttpClient customClient = HttpClientBuilder.create()
                .setDefaultAuthSchemeRegistry(authSchemeRegistry).build();

        final HttpHost target = start();
        final HttpGet httpGet = new HttpGet("/");
        try (CloseableHttpResponse response = customClient.execute(target, httpGet)) {
            EntityUtils.consume(response.getEntity());
        }
    }

    private final class WindowsNegotiateSchemeGetTokenFail extends WindowsNegotiateScheme {

        public WindowsNegotiateSchemeGetTokenFail(final String scheme, final String servicePrincipalName) {
            super(scheme, servicePrincipalName);
        }

        @Override
        String getToken(final CtxtHandle continueCtx, final SecBufferDesc continueToken, final String targetName) {
            dispose();
            /* We will rather throw SEC_E_TARGET_UNKNOWN because SEC_E_DOWNGRADE_DETECTED is not
             * available on Windows XP and this unit test always fails.
             */
            throw new Win32Exception(WinError.SEC_E_TARGET_UNKNOWN);
        }

    }

}
