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

import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.nio.AsyncClientConnectionManager;
import org.apache.hc.core5.concurrent.DefaultThreadFactory;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.H1Config;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.protocol.DefaultHttpProcessor;
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
public class HttpAsyncClients {

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

    private static HttpProcessor createMinimalProtocolProcessor() {
        return new DefaultHttpProcessor(
                new H2RequestContent(),
                new H2RequestTargetHost(),
                new H2RequestConnControl(),
                new RequestUserAgent(VersionInfo.getSoftwareInfo(
                        "Apache-HttpAsyncClient", "org.apache.hc.client5", HttpAsyncClients.class)));
    }

    private static MinimalHttpAsyncClient createMinimalImpl(
            final IOEventHandlerFactory eventHandlerFactory,
            final AsyncPushConsumerRegistry pushConsumerRegistry,
            final HttpVersionPolicy versionPolicy,
            final IOReactorConfig ioReactorConfig,
            final AsyncClientConnectionManager connmgr) {
        return new MinimalHttpAsyncClient(
                eventHandlerFactory,
                pushConsumerRegistry,
                versionPolicy,
                ioReactorConfig,
                new DefaultThreadFactory("httpclient-main", true),
                new DefaultThreadFactory("httpclient-dispatch", true),
                connmgr);
    }

    /**
     * Creates {@link MinimalHttpAsyncClient} instance that provides
     * essential HTTP/1.1 and HTTP/2 message transport only.
     */
    public static MinimalHttpAsyncClient createMinimal(
            final HttpVersionPolicy versionPolicy,
            final H2Config h2Config,
            final H1Config h1Config,
            final IOReactorConfig ioReactorConfig,
            final AsyncClientConnectionManager connmgr) {
        return createMinimalImpl(
                new HttpAsyncClientEventHandlerFactory(
                        createMinimalProtocolProcessor(),
                        null,
                        versionPolicy,
                        h2Config,
                        h1Config,
                        CharCodingConfig.DEFAULT,
                        DefaultConnectionReuseStrategy.INSTANCE),
                new AsyncPushConsumerRegistry(),
                versionPolicy,
                ioReactorConfig,
                connmgr);
    }

    /**
     * Creates {@link MinimalHttpAsyncClient} instance that provides
     * essential HTTP/1.1 and HTTP/2 message transport only.
     */
    public static MinimalHttpAsyncClient createMinimal(
            final HttpVersionPolicy versionPolicy,
            final H2Config h2Config,
            final H1Config h1Config,
            final IOReactorConfig ioReactorConfig) {
        return createMinimal(versionPolicy, h2Config, h1Config, ioReactorConfig,
                PoolingAsyncClientConnectionManagerBuilder.create().build());
    }

    /**
     * Creates {@link MinimalHttpAsyncClient} instance that provides
     * essential HTTP/1.1 and HTTP/2 message transport only.
     */
    public static MinimalHttpAsyncClient createMinimal(
            final HttpVersionPolicy versionPolicy,
            final H2Config h2Config,
            final H1Config h1Config) {
        return createMinimal(versionPolicy, h2Config, h1Config, IOReactorConfig.DEFAULT);
    }

    /**
     * Creates {@link MinimalHttpAsyncClient} instance that provides
     * essential HTTP/1.1 and HTTP/2 message transport only.
     */
    public static MinimalHttpAsyncClient createMinimal() {
        return createMinimal(
                HttpVersionPolicy.NEGOTIATE,
                H2Config.DEFAULT,
                H1Config.DEFAULT);
    }

    /**
     * Creates {@link MinimalHttpAsyncClient} instance that provides
     * essential HTTP/1.1 transport only.
     */
    public static MinimalHttpAsyncClient createMinimal(final H1Config h1Config, final IOReactorConfig ioReactorConfig) {
        return createMinimal(
                HttpVersionPolicy.FORCE_HTTP_1,
                H2Config.DEFAULT,
                h1Config,
                ioReactorConfig);
    }

    /**
     * Creates {@link MinimalHttpAsyncClient} instance that provides
     * essential HTTP/2 transport only.
     */
    public static MinimalHttpAsyncClient createMinimal(final H2Config h2Config, final IOReactorConfig ioReactorConfig) {
        return createMinimal(
                HttpVersionPolicy.FORCE_HTTP_2,
                h2Config,
                H1Config.DEFAULT,
                ioReactorConfig);
    }

    /**
     * Creates {@link MinimalHttpAsyncClient} instance that provides
     * essential HTTP/1.1 and HTTP/2 message transport only.
     */
    public static MinimalHttpAsyncClient createMinimal(final AsyncClientConnectionManager connManager) {
        return createMinimal(
                HttpVersionPolicy.NEGOTIATE,
                H2Config.DEFAULT,
                H1Config.DEFAULT,
                IOReactorConfig.DEFAULT,
                connManager);
    }

}
