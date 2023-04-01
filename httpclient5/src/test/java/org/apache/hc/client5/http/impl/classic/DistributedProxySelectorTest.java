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


import org.apache.hc.client5.http.impl.routing.DistributedProxySelector;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DistributedProxySelectorTest {

    @Test
    void testConstructorThrowsExceptionWhenNullSelectors() {
        assertThrows(IllegalArgumentException.class, () -> new DistributedProxySelector(null));
    }

    @Test
    void testConstructorThrowsExceptionWhenEmptySelectors() {
        assertThrows(IllegalArgumentException.class, () -> new DistributedProxySelector(Collections.emptyList()));
    }

    @Test
    void testSelectReturnsProxyFromFirstSelector() {
        final ProxySelector selector1 = new ProxySelector() {
            @Override
            public List<Proxy> select(final URI uri) {
                return Arrays.asList(
                        new Proxy(Proxy.Type.HTTP, new InetSocketAddress("proxy1.example.com", 8080)),
                        new Proxy(Proxy.Type.HTTP, new InetSocketAddress("proxy2.example.com", 8080))
                );
            }

            @Override
            public void connectFailed(final URI uri, final SocketAddress sa, final IOException ioe) {
            }
        };

        final ProxySelector selector2 = new ProxySelector() {
            @Override
            public List<Proxy> select(final URI uri) {
                return Collections.singletonList(
                        new Proxy(Proxy.Type.HTTP, new InetSocketAddress("proxy3.example.com", 8080))
                );
            }

            @Override
            public void connectFailed(final URI uri, final SocketAddress sa, final IOException ioe) {
            }
        };

        final DistributedProxySelector failoverSelector = new DistributedProxySelector(Arrays.asList(selector1, selector2));

        final URI uri = URI.create("http://example.com");
        final List<Proxy> proxies = failoverSelector.select(uri);
        assertEquals(2, proxies.size());
        assertEquals("proxy1.example.com", ((InetSocketAddress) proxies.get(0).address()).getHostName());
        assertEquals("proxy2.example.com", ((InetSocketAddress) proxies.get(1).address()).getHostName());
    }

    @Test
    void testSelectReturnsProxyFromSecondSelector() {
        final ProxySelector selector1 = new ProxySelector() {
            @Override
            public List<Proxy> select(final URI uri) {
                return Collections.emptyList();
            }

            @Override
            public void connectFailed(final URI uri, final SocketAddress sa, final IOException ioe) {
            }
        };

        final ProxySelector selector2 = new ProxySelector() {
            @Override
            public List<Proxy> select(final URI uri) {
                return Collections.singletonList(
                        new Proxy(Proxy.Type.HTTP, new InetSocketAddress("proxy3.example.com", 8080))
                );
            }

            @Override
            public void connectFailed(final URI uri, final SocketAddress sa, final IOException ioe) {
            }
        };

        final DistributedProxySelector failoverSelector = new DistributedProxySelector(Arrays.asList(selector1, selector2));

        final URI uri = URI.create("http://example.com");
        final List<Proxy> proxies = failoverSelector.select(uri);
        assertEquals(1, proxies.size());
        assertEquals("proxy3.example.com", ((InetSocketAddress) proxies.get(0).address()).getHostName());
    }

    @Test
    void testSelectReturnsProxyFromThirdSelector() {
        final ProxySelector selector1 = new ProxySelector() {
            @Override
            public List<Proxy> select(final URI uri) {
                return Collections.emptyList();
            }

            @Override
            public void connectFailed(final URI uri, final SocketAddress sa, final IOException ioe) {
            }
        };

        final ProxySelector selector2 = mock(ProxySelector.class); // Create a mock object for ProxySelector

        when(selector2.select(any(URI.class))).thenReturn(Collections.singletonList(
                new Proxy(Proxy.Type.HTTP, new InetSocketAddress("proxy3.example.com", 8080))
        ));

        final DistributedProxySelector proxySelector = new DistributedProxySelector(Arrays.asList(selector1, selector2));

        final URI uri = URI.create("http://example.com");
        final List<Proxy> proxies = proxySelector.select(uri);

        // Assertions
        assertEquals(1, proxies.size(), "Expecting one proxy to be returned");
        assertEquals("proxy3.example.com", ((InetSocketAddress) proxies.get(0).address()).getHostName(), "Expecting proxy3.example.com to be returned");

        // Verify that selector2's connectFailed() method is called when a connection fails
        final SocketAddress sa = new InetSocketAddress("proxy3.example.com", 8080);
        final IOException ioe = new IOException("Connection refused");
        proxySelector.connectFailed(uri, sa, ioe);
        verify(selector2, never()).connectFailed(uri, sa, ioe);
    }

    @Test
    void testSelectReturnsProxyFromSecondSelectorWhenFirstSelectorReturnsEmptyList() {
        final ProxySelector selector1 = new ProxySelector() {
            @Override
            public List<Proxy> select(final URI uri) {
                return Collections.emptyList();
            }

            @Override
            public void connectFailed(final URI uri, final SocketAddress sa, final IOException ioe) {
            }
        };

        final ProxySelector selector2 = new ProxySelector() {
            @Override
            public List<Proxy> select(final URI uri) {
                return Collections.singletonList(
                        new Proxy(Proxy.Type.HTTP, new InetSocketAddress("proxy3.example.com", 8080))
                );
            }

            @Override
            public void connectFailed(final URI uri, final SocketAddress sa, final IOException ioe) {
            }
        };

        final DistributedProxySelector failoverSelector = new DistributedProxySelector(Arrays.asList(selector1, selector2));

        final URI uri = URI.create("http://example.com");
        final List<Proxy> proxies = failoverSelector.select(uri);
        assertEquals(1, proxies.size());
        assertEquals("proxy3.example.com", ((InetSocketAddress) proxies.get(0).address()).getHostName());
    }

    @Test
    void testSelectHandlesException() {
        final ProxySelector exceptionThrowingSelector = new ProxySelector() {
            @Override
            public List<Proxy> select(final URI uri) {
                throw new RuntimeException("Exception for testing");
            }

            @Override
            public void connectFailed(final URI uri, final SocketAddress sa, final IOException ioe) {
            }
        };

        final ProxySelector workingSelector = new ProxySelector() {
            @Override
            public List<Proxy> select(final URI uri) {
                return Collections.singletonList(
                        new Proxy(Proxy.Type.HTTP, new InetSocketAddress("proxy.example.com", 8080))
                );
            }

            @Override
            public void connectFailed(final URI uri, final SocketAddress sa, final IOException ioe) {
            }
        };

        final DistributedProxySelector distributedSelector = new DistributedProxySelector(Arrays.asList(exceptionThrowingSelector, workingSelector));

        final URI uri = URI.create("http://example.com");
        final List<Proxy> proxies = distributedSelector.select(uri);
        assertEquals(1, proxies.size());
        assertEquals("proxy.example.com", ((InetSocketAddress) proxies.get(0).address()).getHostName());
    }
}
