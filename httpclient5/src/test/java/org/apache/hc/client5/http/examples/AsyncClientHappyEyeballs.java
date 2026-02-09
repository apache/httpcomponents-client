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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import org.apache.hc.client5.http.Rfc6724AddressSelectingDnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.async.methods.SimpleResponseConsumer;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.ProtocolFamilyPreference;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

/**
 * <h2>Example: RFC 6724 DNS ordering + Happy Eyeballs (with console output)</h2>
 *
 * <p>This example shows how to:</p>
 * <ul>
 *   <li>Wrap the system DNS resolver with {@link org.apache.hc.client5.http.Rfc6724AddressSelectingDnsResolver}
 *       to apply <b>RFC 6724</b> destination address selection (IPv6/IPv4 ordering).</li>
 *   <li>Use {@link org.apache.hc.client5.http.config.ConnectionConfig} to enable <b>Happy Eyeballs v2</b> pacing
 *       and set a <b>protocol family preference</b> (e.g., {@code IPV4_ONLY}, {@code IPV6_ONLY}, {@code PREFER_IPV6},
 *       {@code PREFER_IPV4}, {@code INTERLEAVE}).</li>
 *   <li>Control the connect timeout so demos don’t stall on slow/broken networks.</li>
 * </ul>
 *
 * <h3>How to run with the example runner</h3>
 * <pre>
 * # Default (no args): hits http://ipv6-test.com/ and https://ipv6-test.com/
 * ./run-example.sh AsyncClientHappyEyeballs
 *
 * # Pass one URI (runner supports command-line args)
 * ./run-example.sh AsyncClientHappyEyeballs http://neverssl.com/
 *
 * # Pass multiple URIs
 * ./run-example.sh AsyncClientHappyEyeballs http://neverssl.com/ https://example.org/
 *
 * # Optional system properties (the runner forwards -D...):
 * #   -Dhc.he.pref=INTERLEAVE|PREFER_IPV4|PREFER_IPV6|IPV4_ONLY|IPV6_ONLY  (default: INTERLEAVE)
 * #   -Dhc.he.delay.ms=250        (Happy Eyeballs attempt spacing; default 250)
 * #   -Dhc.he.other.ms=50         (first other-family offset; default 50; clamped ≤ attempt delay)
 * #   -Dhc.connect.ms=10000       (TCP connect timeout; default 10000)
 *
 * ./run-example.sh AsyncClientHappyEyeballs http://neverssl.com/ \
 *   -Dhc.he.pref=INTERLEAVE -Dhc.he.delay.ms=250 -Dhc.he.other.ms=50 -Dhc.connect.ms=8000
 * </pre>
 *
 * <h3>What to expect</h3>
 * <ul>
 *   <li>For dual-stack hosts, the client schedules interleaved IPv6/IPv4 connects per the preference and delays.</li>
 *   <li>On networks without working IPv6, the IPv6 attempt will likely fail quickly while IPv4 succeeds.</li>
 *   <li>If you force {@code IPV6_ONLY} on a network without IPv6 routing, you’ll get
 *       {@code java.net.SocketException: Network is unreachable} — that’s expected.</li>
 * </ul>
 *
 * <h3>Tip</h3>
 * <p>For the clearest behavior, align the resolver bias and the connection preference:
 * construct the resolver with the same {@link ProtocolFamilyPreference} that you set in
 * {@link ConnectionConfig}.</p>
 */
public final class AsyncClientHappyEyeballs {

    private AsyncClientHappyEyeballs() {
    }

    public static void main(final String[] args) throws Exception {
        // --- Read settings from system properties (with sensible defaults) ---
        final ProtocolFamilyPreference pref = parsePref(System.getProperty("hc.he.pref"), ProtocolFamilyPreference.INTERLEAVE);
        final long attemptDelayMs = parseLong(System.getProperty("hc.he.delay.ms"), 250L);
        final long otherFamilyDelayMs = Math.min(parseLong(System.getProperty("hc.he.other.ms"), 50L), attemptDelayMs);
        final long connectMs = parseLong(System.getProperty("hc.connect.ms"), 10000L); // 10s default

        // --- Resolve targets from CLI args (or fall back to ipv6-test.com pair) ---
        final List<URI> targets = new ArrayList<URI>();
        if (args != null && args.length > 0) {
            for (int i = 0; i < args.length; i++) {
                final URI u = safeParse(args[i]);
                if (u != null) {
                    targets.add(u);
                } else {
                    System.out.println("Skipping invalid URI: " + args[i]);
                }
            }
        } else {
            try {
                targets.add(new URI("http://ipv6-test.com/"));
                targets.add(new URI("https://ipv6-test.com/"));
            } catch (final URISyntaxException ignore) {
            }
        }

        // --- Print banner so the runner shows the configuration up front ---
        System.out.println("Happy Eyeballs: pref=" + pref
                + ", attemptDelay=" + attemptDelayMs + "ms"
                + ", otherFamilyDelay=" + otherFamilyDelayMs + "ms"
                + ", connectTimeout=" + connectMs + "ms");

        // --- DNS resolver with RFC 6724 selection (biased using the same pref for clarity) ---
        final Rfc6724AddressSelectingDnsResolver dnsResolver =
                new Rfc6724AddressSelectingDnsResolver(SystemDefaultDnsResolver.INSTANCE, pref);

        // --- Connection config enabling HEv2 pacing and family preference ---
        final ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setStaggeredConnectEnabled(true)
                .setHappyEyeballsAttemptDelay(TimeValue.ofMilliseconds(attemptDelayMs))
                .setHappyEyeballsOtherFamilyDelay(TimeValue.ofMilliseconds(otherFamilyDelayMs))
                .setProtocolFamilyPreference(pref).setConnectTimeout(Timeout.ofMilliseconds(connectMs))

                .build();

        final RequestConfig requestConfig = RequestConfig.custom()
                .build();

        // --- TLS strategy (uses system properties for trust/key stores, ALPN, etc.) ---
        final TlsStrategy tls = ClientTlsStrategyBuilder.create()
                .useSystemProperties()
                .buildAsync();

        // --- Connection manager wires in DNS + ConnectionConfig + TLS ---
        final PoolingAsyncClientConnectionManager cm =
                PoolingAsyncClientConnectionManagerBuilder.create()
                        .setDnsResolver(dnsResolver)
                        .setDefaultConnectionConfig(connectionConfig)
                        .setTlsStrategy(tls)
                        .build();

        final CloseableHttpAsyncClient client = HttpAsyncClients.custom()
                .setConnectionManager(cm)
                .setDefaultRequestConfig(requestConfig)
                .build();

        client.start();

        // --- Execute each target once ---
        for (int i = 0; i < targets.size(); i++) {
            final URI uri = targets.get(i);
            final HttpHost host = new HttpHost(
                    uri.getScheme(),
                    uri.getHost(),
                    computePort(uri)
            );
            final String path = buildPathAndQuery(uri);

            final SimpleHttpRequest request = SimpleRequestBuilder.get()
                    .setHttpHost(host)
                    .setPath(path)
                    .build();

            System.out.println("Executing request " + request);
            final Future<SimpleHttpResponse> future = client.execute(
                    SimpleRequestProducer.create(request),
                    SimpleResponseConsumer.create(),
                    new FutureCallback<SimpleHttpResponse>() {
                        @Override
                        public void completed(final SimpleHttpResponse response) {
                            System.out.println(request + " -> " + new StatusLine(response));
                            System.out.println(response.getBody());
                        }

                        @Override
                        public void failed(final Exception ex) {
                            System.out.println(request + " -> " + ex);
                        }

                        @Override
                        public void cancelled() {
                            System.out.println(request + " cancelled");
                        }
                    });

            try {
                future.get();
            } catch (final java.util.concurrent.ExecutionException ex) {
                // Show the root cause without a giant stack trace in the example
                System.out.println(request + " -> " + ex.getCause());
            }
        }

        System.out.println("Shutting down");
        client.close(CloseMode.GRACEFUL);
        cm.close(CloseMode.GRACEFUL);
    }

    // ------------ helpers (Java 8 friendly) ------------

    private static int computePort(final URI uri) {
        final int p = uri.getPort();
        if (p >= 0) {
            return p;
        }
        final String scheme = uri.getScheme();
        if ("http".equalsIgnoreCase(scheme)) {
            return 80;
        }
        if ("https".equalsIgnoreCase(scheme)) {
            return 443;
        }
        return -1;
    }

    private static String buildPathAndQuery(final URI uri) {
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        final String query = uri.getRawQuery();
        if (query != null && !query.isEmpty()) {
            return path + "?" + query;
        }
        return path;
    }

    private static long parseLong(final String s, final long defVal) {
        if (s == null) {
            return defVal;
        }
        try {
            return Long.parseLong(s.trim());
        } catch (final NumberFormatException ignore) {
            return defVal;
        }
    }

    private static ProtocolFamilyPreference parsePref(final String s, final ProtocolFamilyPreference defVal) {
        if (s == null) {
            return defVal;
        }
        final String u = s.trim().toUpperCase(java.util.Locale.ROOT);
        if ("IPV6_ONLY".equals(u)) {
            return ProtocolFamilyPreference.IPV6_ONLY;
        }
        if ("IPV4_ONLY".equals(u)) {
            return ProtocolFamilyPreference.IPV4_ONLY;
        }
        if ("PREFER_IPV6".equals(u)) {
            return ProtocolFamilyPreference.PREFER_IPV6;
        }
        if ("PREFER_IPV4".equals(u)) {
            return ProtocolFamilyPreference.PREFER_IPV4;
        }
        if ("INTERLEAVE".equals(u)) {
            return ProtocolFamilyPreference.INTERLEAVE;
        }
        return defVal;
    }

    private static URI safeParse(final String s) {
        try {
            final URI u = new URI(s);
            final String scheme = u.getScheme();
            if (!URIScheme.HTTP.same(scheme) && !URIScheme.HTTPS.same(scheme)) {
                System.out.println("Unsupported scheme (only http/https): " + s);
                return null;
            }
            if (u.getHost() == null) {
                System.out.println("Missing host in URI: " + s);
                return null;
            }
            return u;
        } catch (final URISyntaxException ex) {
            return null;
        }
    }
}
