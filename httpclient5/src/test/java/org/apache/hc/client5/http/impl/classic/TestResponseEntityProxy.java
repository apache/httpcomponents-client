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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.classic.ExecRuntime;
import org.apache.hc.client5.http.io.ConnectionEndpoint;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.io.LeaseRequest;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.impl.io.ChunkedInputStream;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.http.impl.io.SessionInputBufferImpl;
import org.apache.hc.core5.http.io.SessionInputBuffer;
import org.apache.hc.core5.http.io.entity.BasicHttpEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;

class TestResponseEntityProxy {

    @Mock
    private ClassicHttpResponse response;
    @Mock
    private ExecRuntime execRuntime;
    @Mock
    private HttpEntity entity;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        Mockito.when(entity.isStreaming()).thenReturn(Boolean.TRUE);
        Mockito.when(response.getEntity()).thenReturn(entity);
    }

    @Test
    void testGetTrailersWithNoChunkedInputStream() throws Exception {
        final ByteArrayInputStream inputStream = new ByteArrayInputStream("Test payload".getBytes());
        Mockito.when(entity.getContent()).thenReturn(inputStream);
        final ArgumentCaptor<HttpEntity> httpEntityArgumentCaptor = ArgumentCaptor.forClass(HttpEntity.class);

        ResponseEntityProxy.enhance(response, execRuntime);

        Mockito.verify(response).setEntity(httpEntityArgumentCaptor.capture());
        final HttpEntity wrappedEntity = httpEntityArgumentCaptor.getValue();

        final InputStream is = wrappedEntity.getContent();
        while (is.read() != -1) {
        } // read until the end
        final Supplier<List<? extends Header>> trailers = wrappedEntity.getTrailers();

        Assertions.assertTrue(trailers.get().isEmpty());
    }

    @Test
    void testGetTrailersWithChunkedInputStream() throws Exception {
        final SessionInputBuffer sessionInputBuffer = new SessionInputBufferImpl(100);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream("0\r\nX-Test-Trailer-Header: test\r\n".getBytes());
        final ChunkedInputStream chunkedInputStream = new ChunkedInputStream(sessionInputBuffer, inputStream);

        Mockito.when(entity.getContent()).thenReturn(chunkedInputStream);
        final ArgumentCaptor<HttpEntity> httpEntityArgumentCaptor = ArgumentCaptor.forClass(HttpEntity.class);

        ResponseEntityProxy.enhance(response, execRuntime);

        Mockito.verify(response).setEntity(httpEntityArgumentCaptor.capture());
        final HttpEntity wrappedEntity = httpEntityArgumentCaptor.getValue();

        final InputStream is = wrappedEntity.getContent();
        while (is.read() != -1) {
        } // consume the stream so it can reach to trailers and parse
        final Supplier<List<? extends Header>> trailers = wrappedEntity.getTrailers();
        final List<? extends Header> headers = trailers.get();

        Assertions.assertEquals(1, headers.size());
        final Header header = headers.get(0);
        Assertions.assertEquals("X-Test-Trailer-Header", header.getName());
        Assertions.assertEquals("test", header.getValue());
    }

    @Test
    void testWriteToNullDrainsAndReleasesStream() throws Exception {
        final SessionInputBuffer sessionInputBuffer = new SessionInputBufferImpl(100);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream("0\r\nX-Test-Trailer-Header: test\r\n".getBytes());
        final ChunkedInputStream chunkedInputStream = new ChunkedInputStream(sessionInputBuffer, inputStream);
        final CloseableHttpResponse resp = new CloseableHttpResponse(new BasicClassicHttpResponse(200), execRuntime);
        final HttpEntity entity = new BasicHttpEntity(chunkedInputStream, null, true);
        Assertions.assertTrue(entity.isStreaming());
        resp.setEntity(entity);

        ResponseEntityProxy.enhance(resp, execRuntime);

        final HttpEntity wrappedEntity = resp.getEntity();

        wrappedEntity.writeTo(null);
        Mockito.verify(execRuntime).releaseEndpoint();

        final Supplier<List<? extends Header>> trailers = wrappedEntity.getTrailers();
        final List<? extends Header> headers = trailers.get();

        Assertions.assertEquals(1, headers.size());
        final Header header = headers.get(0);
        Assertions.assertEquals("X-Test-Trailer-Header", header.getName());
        Assertions.assertEquals("test", header.getValue());


    }

    @Test
    void testCleanupDiscardsEndpointWhenDisconnectEndpointThrows() throws Exception {
        // Simulate a dead socket: disconnectEndpoint() throws (endpoint.close() on
        // a broken connection). Without the fix, discardEndpoint() would be skipped
        // and the connection would remain permanently leased in the pool.
        Mockito.when(execRuntime.isEndpointConnected()).thenReturn(Boolean.TRUE);
        Mockito.doThrow(new IOException("simulated dead socket"))
                .when(execRuntime).disconnectEndpoint();

        final ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        ResponseEntityProxy.enhance(response, execRuntime);
        Mockito.verify(response).setEntity(captor.capture());
        final HttpEntity wrappedEntity = captor.getValue();

        Assertions.assertThrows(IOException.class, wrappedEntity::close);

        Mockito.verify(execRuntime).disconnectEndpoint();
        Mockito.verify(execRuntime).discardEndpoint();
    }

    /**
     * Regression test for HTTPCLIENT-2432.
     * <p>
     * On paths that reach {@code cleanup()} while the endpoint is still leased
     * (e.g. {@code streamAbort()}, or {@code streamClosed()} swallowing a
     * {@code SocketException}), {@code disconnectEndpoint()} is invoked on a
     * connection whose socket may be in a broken state. Prior to the fix, an
     * {@code IOException} from {@code endpoint.close()} propagated out and
     * skipped {@code discardEndpoint()} - the only path that calls
     * {@code manager.release(...)} and returns the lease to the pool. Under
     * load this exhausted the pool.
     * <p>
     * The test wires a real {@link InternalExecRuntime} to a fake connection
     * manager whose leased endpoint throws from {@code close()}, then triggers
     * {@code cleanup()} via {@code streamAbort()} and asserts the manager
     * received exactly one {@code release()} call.
     */
    @Test
    void testPoolLeaseReturnedWhenDisconnectEndpointThrows() throws Exception {
        final AtomicInteger releaseCount = new AtomicInteger();
        final ConnectionEndpoint brokenEndpoint = new ConnectionEndpoint() {
            @Override
            public ClassicHttpResponse execute(final String id, final ClassicHttpRequest request,
                                               final HttpRequestExecutor executor, final HttpContext context) {
                throw new UnsupportedOperationException();
            }
            @Override
            public boolean isConnected() {
                return true;
            }
            @Override
            public void setSocketTimeout(final Timeout timeout) {
            }
            @Override
            public void close(final CloseMode closeMode) {
            }
            @Override
            public void close() throws IOException {
                throw new IOException("simulated dead socket");
            }
        };

        final HttpClientConnectionManager fakeManager = new HttpClientConnectionManager() {
            @Override
            public LeaseRequest lease(final String id, final HttpRoute route,
                                      final Timeout requestTimeout, final Object state) {
                return new LeaseRequest() {
                    @Override
                    public ConnectionEndpoint get(final Timeout timeout) {
                        return brokenEndpoint;
                    }
                    @Override
                    public boolean cancel() {
                        return false;
                    }
                };
            }
            @Override
            public void release(final ConnectionEndpoint endpoint, final Object newState,
                                final TimeValue validDuration) {
                releaseCount.incrementAndGet();
            }
            @Override
            public void connect(final ConnectionEndpoint endpoint, final TimeValue connectTimeout,
                                final HttpContext context) {
            }
            @Override
            public void upgrade(final ConnectionEndpoint endpoint, final HttpContext context) {
            }
            @Override
            public void close() {
            }
            @Override
            public void close(final CloseMode closeMode) {
            }
        };

        final InternalExecRuntime runtime = new InternalExecRuntime(
                LoggerFactory.getLogger(TestResponseEntityProxy.class),
                fakeManager,
                new HttpRequestExecutor(),
                null);
        runtime.acquireEndpoint("id1", new HttpRoute(new HttpHost("localhost", 80)),
                null, HttpClientContext.create());

        final ResponseEntityProxy proxy = new ResponseEntityProxy(entity, runtime);

        Assertions.assertThrows(IOException.class, () -> proxy.streamAbort(null));

        Assertions.assertEquals(1, releaseCount.get(),
                "connection lease must be returned to the pool even when disconnectEndpoint() throws");
    }
}