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

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.nio.AsyncClientConnectionManager;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.core5.concurrent.DefaultThreadFactory;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.protocol.DefaultHttpProcessor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.RequestUserAgent;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.protocol.H2RequestConnControl;
import org.apache.hc.core5.http2.protocol.H2RequestContent;
import org.apache.hc.core5.http2.protocol.H2RequestTargetHost;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.VersionInfo;

/**
 * Factory methods for {@link CloseableHttpAsyncClient} instances.
 *
 * @since 5.0
 */
public final class HttpAsyncClients {

    private HttpAsyncClients() {
        super();
    }

    /**
     * Creates builder object for construction of custom
     * {@link CloseableHttpAsyncClient} instances.
     */
    public static HttpAsyncClientBuilder custom() {
        return HttpAsyncClientBuilder.create();
    }

    /**
     * Creates {@link CloseableHttpAsyncClient} instance with default configuration.
     */
    public static CloseableHttpAsyncClient createDefault() {
        return HttpAsyncClientBuilder.create().build();
    }

    /**
     * Creates {@link CloseableHttpAsyncClient} instance with default
     * configuration and system properties.
     */
    public static CloseableHttpAsyncClient createSystem() {
        return HttpAsyncClientBuilder.create().useSystemProperties().build();
    }

    /**
     * Creates builder object for construction of custom HTTP/2
     * {@link CloseableHttpAsyncClient} instances optimized for HTTP/2 protocol
     * and message multiplexing
     */
    public static H2AsyncClientBuilder customHttp2() {
        return H2AsyncClientBuilder.create();
    }

    /**
     * Creates HTTP/2 {@link CloseableHttpAsyncClient} instance with default configuration
     * optimized for HTTP/2 protocol and message multiplexing.
     */
    public static CloseableHttpAsyncClient createHttp2Default() {
        return H2AsyncClientBuilder.create().build();
    }

    /**
     * Creates HTTP/2 {@link CloseableHttpAsyncClient} instance with default configuration and
     * system properties optimized for HTTP/2 protocol and message multiplexing.
     */
    public static CloseableHttpAsyncClient createHttp2System() {
        return H2AsyncClientBuilder.create().useSystemProperties().build();
    }

    private static HttpProcessor createMinimalProtocolProcessor() {
        return new DefaultHttpProcessor(
                new H2RequestContent(),
                new H2RequestTargetHost(),
                new H2RequestConnControl(),
                new RequestUserAgent(VersionInfo.getSoftwareInfo(
                        "Apache-HttpAsyncClient", "org.apache.hc.client5", HttpAsyncClients.class)));
    }

    private static MinimalHttpAsyncClient createMinimalHttpAsyncClientImpl(
            final IOEventHandlerFactory eventHandlerFactory,
            final AsyncPushConsumerRegistry pushConsumerRegistry,
            final HttpVersionPolicy versionPolicy,
            final IOReactorConfig ioReactorConfig,
            final AsyncClientConnectionManager connmgr,
            final SchemePortResolver schemePortResolver) {
        return new MinimalHttpAsyncClient(
                eventHandlerFactory,
                pushConsumerRegistry,
                versionPolicy,
                ioReactorConfig,
                new DefaultThreadFactory("httpclient-main", true),
                new DefaultThreadFactory("httpclient-dispatch", true),
                connmgr,
                schemePortResolver);
    }

    /**
     * Creates {@link MinimalHttpAsyncClient} instance optimized for
     * HTTP/1.1 and HTTP/2 message transport without advanced HTTP protocol
     * functionality.
     */
    public static MinimalHttpAsyncClient createMinimal(
            final HttpVersionPolicy versionPolicy,
            final H2Config h2Config,
            final Http1Config h1Config,
            final IOReactorConfig ioReactorConfig,
            final AsyncClientConnectionManager connmgr) {
        final AsyncPushConsumerRegistry pushConsumerRegistry = new AsyncPushConsumerRegistry();
        return createMinimalHttpAsyncClientImpl(
                new HttpAsyncClientEventHandlerFactory(
                        createMinimalProtocolProcessor(),
                        new HandlerFactory<AsyncPushConsumer>() {

                            @Override
                            public AsyncPushConsumer create(final HttpRequest request, final HttpContext context) throws HttpException {
                                return pushConsumerRegistry.get(request);
                            }

                        },
                        versionPolicy,
                        h2Config,
                        h1Config,
                        CharCodingConfig.DEFAULT,
                        DefaultConnectionReuseStrategy.INSTANCE),
                pushConsumerRegistry,
                versionPolicy,
                ioReactorConfig,
                connmgr,
                DefaultSchemePortResolver.INSTANCE);
    }

    /**
     * Creates {@link MinimalHttpAsyncClient} instance optimized for
     * HTTP/1.1 and HTTP/2 message transport without advanced HTTP protocol
     * functionality.
     */
    public static MinimalHttpAsyncClient createMinimal(
            final HttpVersionPolicy versionPolicy,
            final H2Config h2Config,
            final Http1Config h1Config,
            final IOReactorConfig ioReactorConfig) {
        return createMinimal(versionPolicy, h2Config, h1Config, ioReactorConfig,
                PoolingAsyncClientConnectionManagerBuilder.create().build());
    }

    /**
     * Creates {@link MinimalHttpAsyncClient} instance optimized for
     * HTTP/1.1 and HTTP/2 message transport without advanced HTTP protocol
     * functionality.
     */
    public static MinimalHttpAsyncClient createMinimal(final H2Config h2Config, final Http1Config h1Config) {
        return createMinimal(HttpVersionPolicy.NEGOTIATE, h2Config, h1Config, IOReactorConfig.DEFAULT);
    }

    /**
     * Creates {@link MinimalHttpAsyncClient} instance optimized for
     * HTTP/1.1 and HTTP/2 message transport without advanced HTTP protocol
     * functionality.
     */
    public static MinimalHttpAsyncClient createMinimal() {
        return createMinimal(H2Config.DEFAULT, Http1Config.DEFAULT);
    }

    /**
     * Creates {@link MinimalHttpAsyncClient} instance optimized for
     * HTTP/1.1 and HTTP/2 message transport without advanced HTTP protocol
     * functionality.
     */
    public static MinimalHttpAsyncClient createMinimal(final AsyncClientConnectionManager connManager) {
        return createMinimal(
                HttpVersionPolicy.NEGOTIATE,
                H2Config.DEFAULT,
                Http1Config.DEFAULT,
                IOReactorConfig.DEFAULT,
                connManager);
    }

    private static MinimalH2AsyncClient createMinimalHttp2AsyncClientImpl(
            final IOEventHandlerFactory eventHandlerFactory,
            final AsyncPushConsumerRegistry pushConsumerRegistry,
            final IOReactorConfig ioReactorConfig,
            final DnsResolver dnsResolver,
            final TlsStrategy tlsStrategy) {
        return new MinimalH2AsyncClient(
                eventHandlerFactory,
                pushConsumerRegistry,
                ioReactorConfig,
                new DefaultThreadFactory("httpclient-main", true),
                new DefaultThreadFactory("httpclient-dispatch", true),
                dnsResolver,
                tlsStrategy);
    }

    /**
     * Creates {@link MinimalH2AsyncClient} instance optimized for HTTP/2 multiplexing message
     * transport without advanced HTTP protocol functionality.
     */
    public static MinimalH2AsyncClient createHttp2Minimal(
            final H2Config h2Config,
            final IOReactorConfig ioReactorConfig,
            final DnsResolver dnsResolver,
            final TlsStrategy tlsStrategy) {
        final AsyncPushConsumerRegistry pushConsumerRegistry = new AsyncPushConsumerRegistry();
        return createMinimalHttp2AsyncClientImpl(
                new H2AsyncClientEventHandlerFactory(
                        createMinimalProtocolProcessor(),
                        new HandlerFactory<AsyncPushConsumer>() {

                            @Override
                            public AsyncPushConsumer create(final HttpRequest request, final HttpContext context) throws HttpException {
                                return pushConsumerRegistry.get(request);
                            }

                        },
                        h2Config,
                        CharCodingConfig.DEFAULT),
                pushConsumerRegistry,
                ioReactorConfig,
                dnsResolver,
                tlsStrategy);
    }

    /**
     * Creates {@link MinimalH2AsyncClient} instance optimized for HTTP/2 multiplexing message
     * transport without advanced HTTP protocol functionality.
     */
    public static MinimalH2AsyncClient createHttp2Minimal(
            final H2Config h2Config,
            final IOReactorConfig ioReactorConfig,
            final TlsStrategy tlsStrategy) {
        return createHttp2Minimal(h2Config, ioReactorConfig, SystemDefaultDnsResolver.INSTANCE, tlsStrategy);
    }

    /**
     * Creates {@link MinimalH2AsyncClient} instance optimized for HTTP/2 multiplexing message
     * transport without advanced HTTP protocol functionality.
     */
    public static MinimalH2AsyncClient createHttp2Minimal(
            final H2Config h2Config,
            final IOReactorConfig ioReactorConfig) {
        return createHttp2Minimal(h2Config, ioReactorConfig, DefaultClientTlsStrategy.getDefault());
    }

    /**
     * Creates {@link MinimalH2AsyncClient} instance optimized for HTTP/2 multiplexing message
     * transport without advanced HTTP protocol functionality.
     */
    public static MinimalH2AsyncClient createHttp2Minimal(final H2Config h2Config) {
        return createHttp2Minimal(h2Config, IOReactorConfig.DEFAULT);
    }

    /**
     * Creates {@link MinimalH2AsyncClient} instance optimized for HTTP/2 multiplexing message
     * transport without advanced HTTP protocol functionality.
     */
    public static MinimalH2AsyncClient createHttp2Minimal() {
        return createHttp2Minimal(H2Config.DEFAULT);
    }

}
