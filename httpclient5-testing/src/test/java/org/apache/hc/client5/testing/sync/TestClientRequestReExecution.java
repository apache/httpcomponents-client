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

import java.util.concurrent.atomic.AtomicInteger;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.testing.classic.RandomHandler;
import org.apache.hc.client5.testing.classic.ServiceUnavailableDecorator;
import org.apache.hc.client5.testing.extension.sync.ClientProtocolLevel;
import org.apache.hc.client5.testing.extension.sync.TestClient;
import org.apache.hc.core5.function.Resolver;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

abstract class TestClientRequestReExecution extends AbstractIntegrationTestBase {

    public TestClientRequestReExecution(final URIScheme scheme) {
        super(scheme, ClientProtocolLevel.STANDARD);
    }

    @BeforeEach
    void setup() {
        final Resolver<HttpRequest, TimeValue> serviceAvailabilityResolver = new Resolver<HttpRequest, TimeValue>() {

            private final AtomicInteger count = new AtomicInteger(0);

            @Override
            public TimeValue resolve(final HttpRequest request) {
                final int n = count.incrementAndGet();
                return n <= 3 ? TimeValue.ofSeconds(1) : null;
            }

        };

        configureServer(bootstrap -> bootstrap.setExchangeHandlerDecorator(handler
                -> new ServiceUnavailableDecorator(handler, serviceAvailabilityResolver)));
    }

    @Test
    void testGiveUpAfterOneRetry() throws Exception {
        configureServer(bootstrap -> bootstrap.register("/random/*", new RandomHandler()));
        final HttpHost target = startServer();

        configureClient(builder -> builder
                .setRetryStrategy(new DefaultHttpRequestRetryStrategy(1, TimeValue.ofSeconds(1))));
        final TestClient client = client();

        final HttpClientContext context = HttpClientContext.create();
        final ClassicHttpRequest request = ClassicRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/random/2048")
                .build();
        final HttpResponse response = client.execute(target, request, context, r -> {
            EntityUtils.consume(r.getEntity());
            return r;
        });
        Assertions.assertEquals(HttpStatus.SC_SERVICE_UNAVAILABLE, response.getCode());
    }

    @Test
    void testDoNotGiveUpEasily() throws Exception {
        configureServer(bootstrap -> bootstrap.register("/random/*", new RandomHandler()));
        final HttpHost target = startServer();

        configureClient(builder -> builder
                .setRetryStrategy(new DefaultHttpRequestRetryStrategy(5, TimeValue.ofSeconds(1))));
        final TestClient client = client();

        final HttpClientContext context = HttpClientContext.create();
        final ClassicHttpRequest request = ClassicRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/random/2048")
                .build();
        final HttpResponse response = client.execute(target, request, context, r -> {
            EntityUtils.consume(r.getEntity());
            return r;
        });
        Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
    }

}
