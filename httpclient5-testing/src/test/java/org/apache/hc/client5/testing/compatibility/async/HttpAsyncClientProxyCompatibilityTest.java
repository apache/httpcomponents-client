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
package org.apache.hc.client5.testing.compatibility.async;

import java.util.concurrent.Future;

import org.apache.hc.client5.http.ContextBuilder;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.testing.extension.async.HttpAsyncClientResource;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class HttpAsyncClientProxyCompatibilityTest {

    static final Timeout TIMEOUT = Timeout.ofSeconds(5);

    private final HttpHost target;
    private final HttpHost proxy;
    @RegisterExtension
    private final HttpAsyncClientResource clientResource;
    private final BasicCredentialsProvider credentialsProvider;

    public HttpAsyncClientProxyCompatibilityTest(final HttpHost target, final HttpHost proxy) throws Exception {
        this.target = target;
        this.proxy = proxy;
        this.clientResource = new HttpAsyncClientResource(HttpVersionPolicy.FORCE_HTTP_1);
        this.clientResource.configure(builder -> builder.setProxy(proxy));
        this.credentialsProvider = new BasicCredentialsProvider();
    }

    CloseableHttpAsyncClient client() {
        return clientResource.client();
    }

    HttpClientContext context() {
        return ContextBuilder.create()
                .useCredentialsProvider(credentialsProvider)
                .build();
    }

    void addCredentials(final AuthScope authScope, final Credentials credentials) {
        credentialsProvider.setCredentials(authScope, credentials);
    }

    @Test
    void test_auth_failure_wrong_proxy_credentials() throws Exception {
        addCredentials(
                new AuthScope(proxy),
                new UsernamePasswordCredentials("testuser", "wrong password".toCharArray()));
        final CloseableHttpAsyncClient client = client();

        for (int i = 0; i < 10; i++) {
            final HttpClientContext context = context();

            final SimpleHttpRequest httpGetSecret = SimpleRequestBuilder.get()
                    .setHttpHost(target)
                    .setPath("/")
                    .build();
            final Future<SimpleHttpResponse> future = client.execute(httpGetSecret, context, null);
            final SimpleHttpResponse response = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertEquals(HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED, response.getCode());
        }
    }

}
