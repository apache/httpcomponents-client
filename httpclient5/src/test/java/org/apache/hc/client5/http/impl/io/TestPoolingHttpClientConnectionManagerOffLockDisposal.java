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
package org.apache.hc.client5.http.impl.io;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.Socket;
import java.net.SocketAddress;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLSession;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.io.ConnectionEndpoint;
import org.apache.hc.client5.http.io.ManagedHttpClientConnection;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.io.HttpConnectionFactory;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Test;

class TestPoolingHttpClientConnectionManagerOffLockDisposal {

    // Simulates slow close only for GRACEFUL
    static final class SleeperConnection implements ManagedHttpClientConnection {
        private volatile boolean open = true;
        private volatile Timeout soTimeout = Timeout.DISABLED;
        private final long sleepMillis;

        SleeperConnection(final long sleepMillis) {
            this.sleepMillis = sleepMillis;
        }

        @Override
        public void bind(final Socket socket) {
        }

        @Override
        public void close(final CloseMode closeMode) {
            try {
                if (closeMode == CloseMode.GRACEFUL) {
                    Thread.sleep(sleepMillis);
                }
            } catch (final InterruptedException ignore) {
                Thread.currentThread().interrupt();
            } finally {
                open = false;
            }
        }

        @Override
        public void close() {
            close(CloseMode.GRACEFUL);
        }

        @Override
        public EndpointDetails getEndpointDetails() {
            return null;
        }

        @Override
        public SocketAddress getLocalAddress() {
            return null;
        }

        @Override
        public SocketAddress getRemoteAddress() {
            return null;
        }

        @Override
        public Socket getSocket() {
            return null;
        }

        @Override
        public SSLSession getSSLSession() {
            return null;
        }

        @Override
        public void passivate() {
        }

        @Override
        public void activate() {
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public boolean isConsistent() {
            return true;
        }

        @Override
        public boolean isDataAvailable(final Timeout timeout) {
            return false;
        }

        @Override
        public boolean isStale() {
            return false;
        }

        @Override
        public void setSocketTimeout(final Timeout timeout) {
            this.soTimeout = timeout;
        }

        @Override
        public Timeout getSocketTimeout() {
            return soTimeout;
        }

        @Override
        public void sendRequestHeader(final ClassicHttpRequest request) {
        }

        @Override
        public void sendRequestEntity(final ClassicHttpRequest request) {
        }

        @Override
        public void flush() {
        }

        @Override
        public ClassicHttpResponse receiveResponseHeader() {
            return null;
        }

        @Override
        public void receiveResponseEntity(final ClassicHttpResponse response) {
        }

        @Override
        public void terminateRequest(final ClassicHttpRequest request) {
        }

        @Override
        public ProtocolVersion getProtocolVersion() {
            return null;
        }
    }

    private static HttpConnectionFactory<ManagedHttpClientConnection> sleeperFactory(final long ms) {
        return socket -> new SleeperConnection(ms);
    }

    private static ConnectionEndpoint lease(final PoolingHttpClientConnectionManager mgr,
                                            final String id, final HttpRoute route, final Object state,
                                            final long sec) throws Exception {
        return mgr.lease(id, route, Timeout.ofSeconds((int) sec), state).get(Timeout.ofSeconds((int) sec));
    }

    // Measure only the lease latency; release happens outside this window
    private static long leaseAndMeasure(final PoolingHttpClientConnectionManager mgr,
                                        final String id, final HttpRoute route, final Object state,
                                        final long sec) throws Exception {
        final long start = System.nanoTime();
        final ConnectionEndpoint ep = lease(mgr, id, route, state, sec);
        final long elapsed = (System.nanoTime() - start) / 1_000_000L;
        mgr.release(ep, state, TimeValue.ofSeconds(30)); // keep-alive, goes back to AVAILABLE
        return elapsed;
    }

    private static PoolingHttpClientConnectionManager newMgrStrict(final long sleeperMs) {
        return PoolingHttpClientConnectionManagerBuilder.create()
                .setOffLockDisposalEnabled(true)
                .setConnPoolPolicy(PoolReusePolicy.LIFO)
                .setPoolConcurrencyPolicy(PoolConcurrencyPolicy.STRICT)
                .setConnectionFactory(sleeperFactory(sleeperMs))
                .build();
    }

    private static PoolingHttpClientConnectionManager newMgrLax(final long sleeperMs) {
        return PoolingHttpClientConnectionManagerBuilder.create()
                .setOffLockDisposalEnabled(true)
                .setConnPoolPolicy(PoolReusePolicy.LIFO)
                .setPoolConcurrencyPolicy(PoolConcurrencyPolicy.LAX)
                .setConnectionFactory(sleeperFactory(sleeperMs))
                .build();
    }

    @Test
    void strictEviction_offLock_otherThreadLeasesFast() throws Exception {
        final PoolingHttpClientConnectionManager mgr = newMgrStrict(1200);

        final HttpRoute rA = new HttpRoute(new HttpHost(URIScheme.HTTP.id, "a.example", 80));
        final HttpRoute rB = new HttpRoute(new HttpHost(URIScheme.HTTP.id, "b.example", 80));
        final HttpRoute rC = new HttpRoute(new HttpHost(URIScheme.HTTP.id, "c.example", 80));

        mgr.setMaxTotal(2);
        mgr.setMaxPerRoute(rA, 1);
        mgr.setMaxPerRoute(rB, 1);
        mgr.setMaxPerRoute(rC, 1);

        final ConnectionEndpoint epA0 = lease(mgr, "seedA", rA, null, 2);
        mgr.release(epA0, null, TimeValue.ofSeconds(30));
        final ConnectionEndpoint epB0 = lease(mgr, "seedB", rB, null, 2);
        mgr.release(epB0, null, TimeValue.ofSeconds(30));

        final ExecutorService es = Executors.newFixedThreadPool(2);
        try {
            final Callable<Long> t1Lease = () -> {
                final long start = System.nanoTime();
                final ConnectionEndpoint epC = lease(mgr, "t1", rC, null, 3);
                mgr.release(epC, null, TimeValue.ofSeconds(5));
                return (System.nanoTime() - start) / 1_000_000L;
            };

            final Callable<Long> t2Lease = () -> {
                // small stagger so we overlap with t1’s drain window
                Thread.sleep(200);
                return leaseAndMeasure(mgr, "t2", rA, null, 2);
            };

            final long t1LeaseMs = es.submit(t1Lease).get(6, TimeUnit.SECONDS);
            final long t2LeaseMs = es.submit(t2Lease).get(6, TimeUnit.SECONDS);

            assertTrue(t1LeaseMs >= 900L, "T1 lease should include slow drain: " + t1LeaseMs + "ms");

            assertTrue(t2LeaseMs < 1900L, "T2 lease should complete without timing out: " + t2LeaseMs + "ms");
        } finally {
            es.shutdownNow();
            mgr.close(CloseMode.IMMEDIATE);
        }
    }


    @Test
    void leaseNotBlocked_LAX_stateMismatchDiscard_offLockDisposal() throws Exception {
        final PoolingHttpClientConnectionManager mgr = newMgrLax(1200);

        final HttpRoute route = new HttpRoute(new HttpHost(URIScheme.HTTP.id, "lax.example", 80));
        mgr.setMaxTotal(2);
        mgr.setMaxPerRoute(route, 2);

        final ConnectionEndpoint epA = lease(mgr, "tA", route, "A", 2);
        mgr.release(epA, "A", TimeValue.ofSeconds(30));

        final ExecutorService es = Executors.newFixedThreadPool(2);
        try {
            final Callable<Long> t1Lease = () -> {
                final long start = System.nanoTime();
                final ConnectionEndpoint epB = lease(mgr, "tB", route, "B", 3); // state mismatch discard
                // drainDisposals() runs in finally inside LeaseRequest#get → this lease measures the drain
                mgr.release(epB, "B", TimeValue.ofSeconds(5));
                return (System.nanoTime() - start) / 1_000_000L;
            };

            // T2: concurrent lease "B" should be fast
            final Callable<Long> t2Lease = () -> {
                Thread.sleep(50);
                return leaseAndMeasure(mgr, "t2", route, "B", 2);
            };

            final long t1LeaseMs = es.submit(t1Lease).get(6, TimeUnit.SECONDS);
            final long t2LeaseMs = es.submit(t2Lease).get(6, TimeUnit.SECONDS);

            // With drain in LeaseRequest#get, T1 lease is slow; T2 remains fast.
            assertTrue(t1LeaseMs >= 1000L, "T1 lease should include slow drain: " + t1LeaseMs + "ms");
            assertTrue(t2LeaseMs < 300L, "Lease blocked by in-thread discard: " + t2LeaseMs + "ms");

        } finally {
            es.shutdownNow();
            mgr.close(CloseMode.IMMEDIATE);
        }
    }
}
