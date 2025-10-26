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
package org.apache.hc.client5.http.impl.async;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecRuntime;
import org.apache.hc.client5.http.async.methods.InflatingAsyncDataConsumer;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.CancellableDependency;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class TestContentCompressionAsyncExec {

    @Mock
    private AsyncExecChain execChain;
    @Mock
    private AsyncEntityProducer entityProducer;
    @Mock
    private AsyncExecCallback originalCb;
    @Mock
    private AsyncExecRuntime execRuntime;
    @Mock
    private CancellableDependency dependency;

    private HttpClientContext context;
    private AsyncExecChain.Scope scope;
    private ContentCompressionAsyncExec impl;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);

        final HttpHost target = new HttpHost("somehost", 80);
        final HttpRequest req = new BasicHttpRequest(Method.GET, "/");
        context = HttpClientContext.create();

        scope = new AsyncExecChain.Scope(
                "test",
                new HttpRoute(target),
                req,
                dependency,
                context,
                execRuntime,
                null,
                new AtomicInteger());

        impl = new ContentCompressionAsyncExec();   // default = deflate
    }

    private AsyncExecCallback executeAndCapture(final HttpRequest request) throws Exception {
        final ArgumentCaptor<AsyncExecCallback> cap = ArgumentCaptor.forClass(AsyncExecCallback.class);
        doNothing().when(execChain).proceed(eq(request), eq(entityProducer), eq(scope), cap.capture());
        impl.execute(request, entityProducer, scope, execChain, originalCb);
        return cap.getValue();
    }

    @Test
    void testAcceptEncodingAdded() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, "/path");
        executeAndCapture(request);
        assertTrue(request.containsHeader(HttpHeaders.ACCEPT_ENCODING));
        assertEquals("gzip, x-gzip, deflate, zstd, br", request.getFirstHeader(HttpHeaders.ACCEPT_ENCODING).getValue());
    }

    @Test
    void testDeflateConsumerInserted() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, "/");
        final AsyncExecCallback cb = executeAndCapture(request);

        final HttpResponse rsp = new BasicHttpResponse(200, "OK");
        final EntityDetails details = mock(EntityDetails.class);
        when(details.getContentEncoding()).thenReturn("deflate");

        final AsyncDataConsumer downstream = new StringAsyncEntityConsumer();
        when(originalCb.handleResponse(same(rsp), same(details))).thenReturn(downstream);

        final AsyncDataConsumer wrapped = cb.handleResponse(rsp, details);

        assertNotNull(wrapped);
        assertTrue(wrapped instanceof InflatingAsyncDataConsumer);
        assertFalse(rsp.containsHeader(HttpHeaders.CONTENT_ENCODING));
    }

    @Test
    void testIdentityIsNoOp() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, "/");
        final AsyncExecCallback cb = executeAndCapture(request);

        final HttpResponse rsp = new BasicHttpResponse(200, "OK");
        final EntityDetails details = mock(EntityDetails.class);
        when(details.getContentEncoding()).thenReturn("identity");

        final AsyncDataConsumer downstream = new StringAsyncEntityConsumer();
        /* accept any EntityDetails instance */
        when(originalCb.handleResponse(eq(rsp), any(EntityDetails.class))).thenReturn(downstream);

        assertSame(downstream, cb.handleResponse(rsp, details));
    }

    @Test
    void testUnknownEncodingRejectedWhenFlagFalse() throws Exception {
        final LinkedHashMap<String, UnaryOperator<AsyncDataConsumer>> map = new LinkedHashMap<>();
        map.put("deflate", new UnaryOperator<AsyncDataConsumer>() {
            @Override
            public AsyncDataConsumer apply(final AsyncDataConsumer d) {
                return new InflatingAsyncDataConsumer(d, null);
            }
        });
        impl = new ContentCompressionAsyncExec(map, /*ignoreUnknown*/ false);

        final HttpRequest request = new BasicHttpRequest(Method.GET, "/");
        final AsyncExecCallback cb = executeAndCapture(request);

        final HttpResponse rsp = new BasicHttpResponse(200, "OK");
        final EntityDetails details = mock(EntityDetails.class);
        when(details.getContentEncoding()).thenReturn("whatever");

        assertThrows(HttpException.class, () -> cb.handleResponse(rsp, details));
    }

    @Test
    void testCompressionDisabledViaRequestConfig() throws Exception {
        context.setRequestConfig(RequestConfig.custom()
                .setContentCompressionEnabled(false)
                .build());
        final HttpRequest request = new BasicHttpRequest(Method.GET, "/");
        executeAndCapture(request);

        assertFalse(request.containsHeader(HttpHeaders.ACCEPT_ENCODING));
    }
}