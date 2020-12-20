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
package org.apache.hc.client5.http.impl.classic;

import java.io.IOException;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.io.CloseMode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

@SuppressWarnings("boxing") // test code
public class TestHttpClientBuilderInterceptors {

    private HttpServer localServer;
    private String uri;
    private CloseableHttpClient httpClient;

    @Before
    public void before() throws Exception {
        this.localServer = ServerBootstrap.bootstrap()
                .register("/test", (request, response, context) -> {
                    final Header testInterceptorHeader = request.getHeader("X-Test-Interceptor");
                    if (testInterceptorHeader != null) {
                        response.setHeader(testInterceptorHeader);
                    }
                    response.setCode(200);
                }).create();

        this.localServer.start();
        uri = "http://localhost:" + this.localServer.getLocalPort() + "/test";
        final HttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnPerRoute(5)
                .build();
        httpClient = HttpClientBuilder.create()
                .setConnectionManager(cm)
                .addExecInterceptorLast("test-interceptor", (request, scope, chain) -> {
                    request.setHeader("X-Test-Interceptor", "active");
                    return chain.proceed(request, scope);
                })
                .build();
    }

    @After
    public void after() throws Exception {
        this.httpClient.close(CloseMode.IMMEDIATE);
        this.localServer.stop();
    }

    @Test
    public void testAddExecInterceptorLastShouldBeExecuted() throws IOException, HttpException {
        final ClassicHttpRequest request = new HttpPost(uri);
        final ClassicHttpResponse response = httpClient.execute(request);
        Assert.assertEquals(200, response.getCode());
        final Header testFilterHeader = response.getHeader("X-Test-Interceptor");
        Assert.assertNotNull(testFilterHeader);
    }

}
