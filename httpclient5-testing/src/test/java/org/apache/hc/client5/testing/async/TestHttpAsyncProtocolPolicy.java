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

import static org.hamcrest.MatcherAssert.assertThat;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.testing.extension.async.ClientProtocolLevel;
import org.apache.hc.client5.testing.extension.async.ServerProtocolLevel;
import org.apache.hc.client5.testing.extension.async.TestAsyncClient;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;

abstract class TestHttpAsyncProtocolPolicy extends AbstractIntegrationTestBase {

    private final HttpVersion version;

    public TestHttpAsyncProtocolPolicy(final URIScheme scheme, final HttpVersion version) {
        super(scheme, ClientProtocolLevel.STANDARD, version == HttpVersion.HTTP_2 ? ServerProtocolLevel.H2_ONLY : ServerProtocolLevel.STANDARD);
        this.version = version;
    }

    @Test
    void testRequestContext() throws Exception {
        configureServer(bootstrap -> bootstrap.register("/random/*", AsyncRandomHandler::new));
        final HttpHost target = startServer();

        final AtomicReference<ProtocolVersion> versionRef = new AtomicReference<>();
        configureClient(builder -> builder
                .setDefaultTlsConfig(TlsConfig.custom()
                        .setVersionPolicy(version.greaterEquals(HttpVersion.HTTP_2) ? HttpVersionPolicy.FORCE_HTTP_2 : HttpVersionPolicy.FORCE_HTTP_1)
                        .build())
                .addRequestInterceptorFirst((request, entity, context) ->
                        versionRef.set(context.getProtocolVersion())));
        final TestAsyncClient client = startClient();

        final Future<SimpleHttpResponse> future = client.execute(
                SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath("/random/2048")
                        .build(), null);
        final SimpleHttpResponse response = future.get();
        assertThat(response, CoreMatchers.notNullValue());
        assertThat(response.getCode(), CoreMatchers.equalTo(200));
        final String body = response.getBodyText();
        assertThat(body, CoreMatchers.notNullValue());
        assertThat(body.length(), CoreMatchers.equalTo(2048));
        assertThat(versionRef.get(), CoreMatchers.equalTo(version));
    }

}
