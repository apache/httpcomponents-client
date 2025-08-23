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
import java.lang.reflect.Field;
import java.net.ProxySelector;

import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecChainHandler;
import org.apache.hc.client5.http.impl.routing.DefaultProxyRoutePlanner;
import org.apache.hc.client5.http.impl.routing.DefaultRoutePlanner;
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestHttpClientBuilder {

    @Test
    void testAddInterceptorFirstDoesNotThrow() throws IOException {
        // HTTPCLIENT-2083
        HttpClients.custom()
                .addExecInterceptorFirst("first", NopExecChainHandler.INSTANCE)
                .build()
                .close();
    }

    @Test
    void testAddInterceptorLastDoesNotThrow() throws IOException {
        // HTTPCLIENT-2083
        HttpClients.custom()
                .addExecInterceptorLast("last", NopExecChainHandler.INSTANCE)
                .build()
                .close();
    }

    enum NopExecChainHandler implements ExecChainHandler {
        INSTANCE;

        @Override
        public ClassicHttpResponse execute(
                final ClassicHttpRequest request,
                final ExecChain.Scope scope,
                final ExecChain chain) throws IOException, HttpException {
            return chain.proceed(request, scope);
        }
    }

    @Test
    void testDefaultUsesSystemDefaultRoutePlanner() throws Exception {
        try (final InternalHttpClient client = (InternalHttpClient) HttpClients.custom().build()) {
            final Object planner = getPrivateField(client, "routePlanner");
            Assertions.assertNotNull(planner);
            Assertions.assertInstanceOf(SystemDefaultRoutePlanner.class, planner, "Default should be SystemDefaultRoutePlanner (auto-detect proxies)");
        }
    }

    @Test
    void testDisableProxyAutodetectionFallsBackToDefaultRoutePlanner() throws Exception {
        try (final InternalHttpClient client = (InternalHttpClient) HttpClients.custom()
                .disableProxyAutodetection()
                .build()) {
            final Object planner = getPrivateField(client, "routePlanner");
            Assertions.assertNotNull(planner);
            Assertions.assertInstanceOf(DefaultRoutePlanner.class, planner, "disableProxyAutodetection() should restore DefaultRoutePlanner");
        }
    }

    @Test
    void testExplicitProxyWinsOverAutodetection() throws Exception {
        try (final InternalHttpClient client = (InternalHttpClient) HttpClients.custom()
                .setProxy(new HttpHost("http", "proxy.local", 8080))
                .build()) {
            final Object planner = getPrivateField(client, "routePlanner");
            Assertions.assertNotNull(planner);
            Assertions.assertInstanceOf(DefaultProxyRoutePlanner.class, planner, "Explicit proxy must take precedence");
        }
    }

    @Test
    void testCustomRoutePlannerIsRespected() throws Exception {
        final HttpRoutePlanner custom = new HttpRoutePlanner() {
            @Override
            public org.apache.hc.client5.http.HttpRoute determineRoute(
                    final HttpHost host, final HttpContext context) {
                // trivial, never used in this test
                return new org.apache.hc.client5.http.HttpRoute(host);
            }
        };
        try (final InternalHttpClient client = (InternalHttpClient) HttpClients.custom()
                .setRoutePlanner(custom)
                .build()) {
            final Object planner = getPrivateField(client, "routePlanner");
            Assertions.assertSame(custom, planner, "Custom route planner must be used as-is");
        }
    }

    @Test
    void testProvidedProxySelectorIsUsedBySystemDefaultRoutePlanner() throws Exception {
        class TouchProxySelector extends ProxySelector {
            volatile boolean touched = false;
            @Override
            public java.util.List<java.net.Proxy> select(final java.net.URI uri) {
                touched = true;
                return java.util.Collections.singletonList(java.net.Proxy.NO_PROXY);
            }
            @Override
            public void connectFailed(final java.net.URI uri, final java.net.SocketAddress sa, final IOException ioe) { }
        }
        final TouchProxySelector selector = new TouchProxySelector();

        try (final InternalHttpClient client = (InternalHttpClient) HttpClients.custom()
                .setProxySelector(selector)
                .build()) {
            final Object planner = getPrivateField(client, "routePlanner");
            Assertions.assertInstanceOf(SystemDefaultRoutePlanner.class, planner);

            // Call determineRoute on the planner directly to avoid making a real request
            final SystemDefaultRoutePlanner sdrp = (SystemDefaultRoutePlanner) planner;
            sdrp.determineRoute(new HttpHost("http", "example.com", 80), HttpClientContext.create());

            Assertions.assertTrue(selector.touched, "Provided ProxySelector should be consulted");
        }
    }

    private static Object getPrivateField(final Object target, final String name) throws Exception {
        final Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }
}