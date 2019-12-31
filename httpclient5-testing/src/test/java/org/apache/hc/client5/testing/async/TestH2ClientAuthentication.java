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
package org.apache.hc.client5.testing.async;

import java.util.Arrays;
import java.util.Collection;

import org.apache.hc.client5.http.AuthenticationStrategy;
import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.H2AsyncClientBuilder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.testing.SSLTestContexts;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.Lookup;
import org.junit.Rule;
import org.junit.rules.ExternalResource;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class TestH2ClientAuthentication extends AbstractHttpAsyncClientAuthentication<CloseableHttpAsyncClient> {

    @Parameterized.Parameters(name = "HTTP/2 {0}")
    public static Collection<Object[]> protocols() {
        return Arrays.asList(new Object[][]{
                {URIScheme.HTTP},
                {URIScheme.HTTPS},
        });
    }

    protected H2AsyncClientBuilder clientBuilder;
    protected PoolingAsyncClientConnectionManager connManager;

    @Rule
    public ExternalResource clientResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            clientBuilder = H2AsyncClientBuilder.create()
                    .setDefaultRequestConfig(RequestConfig.custom()
                            .setConnectionRequestTimeout(TIMEOUT)
                            .setConnectTimeout(TIMEOUT)
                            .build())
                    .setTlsStrategy(new DefaultClientTlsStrategy(SSLTestContexts.createClientSSLContext()));
        }

    };

    public TestH2ClientAuthentication(final URIScheme scheme) {
        super(scheme, HttpVersion.HTTP_2);
    }

    @Override
    void setDefaultAuthSchemeRegistry(final Lookup<AuthSchemeFactory> authSchemeRegistry) {
        clientBuilder.setDefaultAuthSchemeRegistry(authSchemeRegistry);
    }

    @Override
    void setTargetAuthenticationStrategy(final AuthenticationStrategy targetAuthStrategy) {
        clientBuilder.setTargetAuthenticationStrategy(targetAuthStrategy);
    }

    @Override
    protected CloseableHttpAsyncClient createClient() throws Exception {
        return clientBuilder.build();
    }

}