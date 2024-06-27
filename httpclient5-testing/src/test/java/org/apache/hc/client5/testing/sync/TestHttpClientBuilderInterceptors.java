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

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.testing.extension.sync.ClientProtocolLevel;
import org.apache.hc.client5.testing.extension.sync.TestClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("boxing") // test code
class TestHttpClientBuilderInterceptors extends AbstractIntegrationTestBase {

    public TestHttpClientBuilderInterceptors() {
        super(URIScheme.HTTP, ClientProtocolLevel.STANDARD);
    }

    @BeforeEach
    void before() {
        configureServer(bootstrap -> bootstrap
                .register("/test", (request, response, context) -> {
                    final Header testInterceptorHeader = request.getHeader("X-Test-Interceptor");
                    if (testInterceptorHeader != null) {
                        response.setHeader(testInterceptorHeader);
                    }
                    response.setCode(200);
                }));
        configureClient(builder -> builder
                .addExecInterceptorLast("test-interceptor", (request, scope, chain) -> {
                    request.setHeader("X-Test-Interceptor", "active");
                    return chain.proceed(request, scope);
                }));
    }

    @Test
    void testAddExecInterceptorLastShouldBeExecuted() throws Exception {
        final HttpHost httpHost = startServer();
        final TestClient client = client();
        final ClassicHttpRequest request = new HttpPost("/test");
        final HttpResponse response = client.execute(httpHost, request, httpResponse -> {
            EntityUtils.consume(httpResponse.getEntity());
            return httpResponse;
        });
        Assertions.assertEquals(200, response.getCode());
        final Header testFilterHeader = response.getHeader("X-Test-Interceptor");
        Assertions.assertNotNull(testFilterHeader);
    }

}
