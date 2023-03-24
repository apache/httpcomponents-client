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

package org.apache.hc.client5.http.examples;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.async.methods.SimpleResponseConsumer;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.HappyEyeballsV2AsyncClientConnectionOperator;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.nio.AsyncClientConnectionOperator;
import org.apache.hc.client5.http.ssl.ConscryptClientTlsStrategy;
import org.apache.hc.core5.concurrent.DefaultThreadFactory;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Example class demonstrating how to use the Happy Eyeballs V2 connection operator in the Apache
 * HttpComponents asynchronous HTTP client.
 */
public class HappyEyeballsV2AsyncClientConnectionOperatorExample {

    public static void main(final String[] args) throws ExecutionException, InterruptedException, IOException {

        // Create a custom AsyncConnectionFactory
        final HappyEyeballsV2AsyncClientConnectionOperator connectionOperator = new HappyEyeballsV2AsyncClientConnectionOperator(
                RegistryBuilder.<TlsStrategy>create().register(URIScheme.HTTPS.getId(), ConscryptClientTlsStrategy.getDefault()).build(),
                DefaultSchemePortResolver.INSTANCE,
                new MultipleHostsDnsResolver(new String[]{"ipv6.google.com", "ipv4.google.com", "facebook.com", "yahoo.com"}, new int[]{443, 443, 443, 443}));


        final PoolingAsyncClientConnectionManager connectionManager = new CustomPoolingAsyncClientConnectionManager(
                connectionOperator,
                PoolConcurrencyPolicy.LAX,
                PoolReusePolicy.LIFO,
                TimeValue.ofMinutes(1));

        connectionManager.setConnectionConfigResolver(httpRoute -> ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(2))
                .setSocketTimeout(Timeout.ofSeconds(1))
                .setValidateAfterInactivity(TimeValue.ofSeconds(10))
                .setTimeToLive(TimeValue.ofMinutes(1))
                .build());

        // Create a custom AsyncClient
        final CloseableHttpAsyncClient client = HttpAsyncClients.custom()
                .setConnectionManager(connectionManager)
                .setThreadFactory(new DefaultThreadFactory("async-client", true))
                .build();

        // Start the CloseableHttpAsyncClient
        client.start();


        final SimpleHttpRequest request = SimpleRequestBuilder.get()
                .setHttpHost(new HttpHost("www.google.com"))
                .setPath("/")
                .build();

        System.out.println("Executing request " + request);
        final Future<SimpleHttpResponse> future = client.execute(
                SimpleRequestProducer.create(request),
                SimpleResponseConsumer.create(),
                new FutureCallback<SimpleHttpResponse>() {

                    @Override
                    public void completed(final SimpleHttpResponse response) {
                        System.out.println(request + "->" + new StatusLine(response));
                        System.out.println(response.getBody());
                    }

                    @Override
                    public void failed(final Exception ex) {
                        System.out.println(request + "->" + ex);
                    }

                    @Override
                    public void cancelled() {
                        System.out.println(request + " cancelled");
                    }

                });
        future.get();

        System.out.println("Shutting down");
        client.close(CloseMode.GRACEFUL);

    }

    /**
     * Custom implementation of PoolingAsyncClientConnectionManager that uses the specified connection operator
     * and pool configuration.
     */
    private static class CustomPoolingAsyncClientConnectionManager extends PoolingAsyncClientConnectionManager {

        CustomPoolingAsyncClientConnectionManager(
                final AsyncClientConnectionOperator connectionOperator,
                final PoolConcurrencyPolicy poolConcurrencyPolicy,
                final PoolReusePolicy poolReusePolicy,
                final TimeValue timeToLive) {
            super(connectionOperator, poolConcurrencyPolicy, poolReusePolicy, timeToLive);
        }

    }

    /**
     * A DNS resolver that resolves hostnames against multiple hosts and ports.
     */
    static class MultipleHostsDnsResolver implements DnsResolver {

        /**
         * The array of hostnames to use for resolving.
         */
        private final String[] hosts;

        /**
         * The array of ports to use for resolving.
         */
        private final int[] ports;

        public MultipleHostsDnsResolver(final String[] hosts, final int[] ports) {
            this.hosts = hosts;
            this.ports = ports;
        }

        public static final SystemDefaultDnsResolver INSTANCE = new SystemDefaultDnsResolver();

        @Override
        public InetAddress[] resolve(final String host) throws UnknownHostException {
            final List<InetSocketAddress> addresses = new ArrayList<>();
            for (int i = 0; i < hosts.length; i++) {
                final InetAddress[] inetAddresses = InetAddress.getAllByName(hosts[i]);
                for (final InetAddress inetAddress : inetAddresses) {
                    addresses.add(new InetSocketAddress(inetAddress, ports[i]));
                }
            }

            // Convert InetSocketAddress objects to InetAddress objects
            final List<InetAddress> result = new ArrayList<>();
            for (final InetSocketAddress address : addresses) {
                result.add(address.getAddress());
            }
            return result.toArray(new InetAddress[0]);
        }

        @Override
        public String resolveCanonicalHostname(final String host) throws UnknownHostException {
            if (host == null) {
                return null;
            }
            final InetAddress in = InetAddress.getByName(host);
            final String canonicalServer = in.getCanonicalHostName();
            if (in.getHostAddress().contentEquals(canonicalServer)) {
                return host;
            }
            return canonicalServer;
        }

    }

}








