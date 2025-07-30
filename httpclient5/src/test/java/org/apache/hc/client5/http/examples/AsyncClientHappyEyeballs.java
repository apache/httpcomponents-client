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

import java.util.concurrent.Future;

import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.async.methods.SimpleResponseConsumer;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.HappyEyeballsV2AsyncClientConnectionOperator;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.io.CloseMode;

/**
 * Example demonstrating how to enable <em>Happy&nbsp;Eyeballs v2</em> (RFC&nbsp;8305)
 * in the async client with RFC&nbsp;6724 address selection and on-demand TLS upgrade.
 *
 * <p><strong>What this example shows</strong></p>
 * <ul>
 *   <li>Creating a {@link HappyEyeballsV2AsyncClientConnectionOperator} that:
 *     <ul>
 *       <li>resolves A/AAAA via {@link SystemDefaultDnsResolver},</li>
 *       <li>orders candidates per RFC&nbsp;6724,</li>
 *       <li>stagger-dials dual-stack targets (≈50&nbsp;ms opposite-family kick, then 250&nbsp;ms pacing), and</li>
 *       <li>cancels outstanding attempts once one connects.</li>
 *     </ul>
 *   </li>
 *   <li>Registering an HTTPS {@link TlsStrategy} and wiring it into the operator so that
 *       HTTPS routes are upgraded to TLS during connect.</li>
 *   <li>Executing one HTTP and one HTTPS request to a dual-stack host.</li>
 * </ul>
 *
 * <p><strong>Running</strong></p>
 * <ol>
 *   <li>Ensure logging is configured (e.g., log4j-slf4j) if you want to see
 *       <code>HEv2: dialing/connected</code> debug lines.</li>
 *   <li>Run the {@code main}. You should see the HTTP request (often 301 to HTTPS)
 *       followed by an HTTPS request with a TLS handshake driven by the configured {@link TlsStrategy}.</li>
 * </ol>
 *
 * <p><strong>Knobs you can tweak</strong></p>
 * <ul>
 *   <li>Use the constructor {@code new HappyEyeballsV2AsyncClientConnectionOperator(dns, attemptDelayMillis, scheduler, tlsRegistry)}
 *       if you want a custom pacing interval or to supply your own scheduler.</li>
 *   <li>Swap {@link SystemDefaultDnsResolver} for a custom {@code DnsResolver} if needed.</li>
 *   <li>Customize the {@link ClientTlsStrategyBuilder} to control trust material, SNI, ALPN, etc.</li>
 * </ul>
 *
 * <p><strong>References</strong></p>
 * <ul>
 *   <li>RFC&nbsp;8305: Happy Eyeballs Version 2</li>
 *   <li>RFC&nbsp;6724: Default Address Selection for IPv6</li>
 * </ul>
 *
 * @since 5.6
 */
public final class AsyncClientHappyEyeballs {

    public static void main(final String[] args) throws Exception {
        final SystemDefaultDnsResolver dnsResolver = new SystemDefaultDnsResolver();

        final TlsStrategy tls = ClientTlsStrategyBuilder.create()
                .useSystemProperties()
                .build();

        final Registry<TlsStrategy> tlsRegistry = RegistryBuilder.<TlsStrategy>create()
                .register(URIScheme.HTTPS.id, tls)
                .build();

        final HappyEyeballsV2AsyncClientConnectionOperator operator =
                new HappyEyeballsV2AsyncClientConnectionOperator(dnsResolver, tlsRegistry);

        final PoolingAsyncClientConnectionManager cm = PoolingAsyncClientConnectionManagerBuilder.create()
                .setConnectionOperator(operator)
                .build();

        final CloseableHttpAsyncClient client = HttpAsyncClients.custom()
                .setConnectionManager(cm)
                .build();

        client.start();

        final String[] schemes = {URIScheme.HTTP.id, URIScheme.HTTPS.id};
        for (final String scheme : schemes) {
            final SimpleHttpRequest request = SimpleRequestBuilder.get()
                    .setHttpHost(new HttpHost(scheme, "ipv6-test.com"))
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
        }

        System.out.println("Shutting down");
        client.close(CloseMode.GRACEFUL);
        operator.shutdown(CloseMode.GRACEFUL);
    }
}
