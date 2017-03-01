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

import java.nio.charset.StandardCharsets;

import org.apache.hc.client5.http.impl.DefaultThreadFactory;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.nio.AsyncClientConnectionManager;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.config.ConnectionConfig;
import org.apache.hc.core5.http.config.H1Config;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.protocol.DefaultHttpProcessor;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.RequestUserAgent;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.protocol.H2RequestConnControl;
import org.apache.hc.core5.http2.protocol.H2RequestContent;
import org.apache.hc.core5.http2.protocol.H2RequestTargetHost;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOReactorException;
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
     * Creates HTTP/1.1 {@link CloseableHttpAsyncClient} instance with default
     * configuration.
     */
    public static CloseableHttpAsyncClient createDefault() {
        return HttpAsyncClientBuilder.create().build();
    }

    /**
     * Creates HTTP/2 {@link CloseableHttpAsyncClient} instance with the given
     * configuration.
     */
    public static CloseableHttpAsyncClient createDefault(final H2Config config) {
        return HttpAsyncClientBuilder.create()
                .setProtocolVersion(HttpVersion.HTTP_2)
                .setH2Config(config)
                .build();
    }

    /**
     * Creates HTTP/1.1 {@link CloseableHttpAsyncClient} instance with default
     * configuration and system properties.
     */
    public static CloseableHttpAsyncClient createSystem() {
        return HttpAsyncClientBuilder.create().useSystemProperties().build();
    }

    /**
     * Creates HTTP/2 {@link CloseableHttpAsyncClient} instance with the given
     * configuration and system properties.
     */
    public static CloseableHttpAsyncClient createSystem(final H2Config config) {
        return HttpAsyncClientBuilder.create()
                .useSystemProperties()
                .setProtocolVersion(HttpVersion.HTTP_2)
                .setH2Config(config)
                .build();
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
            final AsyncClientConnectionManager connmgr) {
        try {
            return new MinimalHttpAsyncClient(
                    eventHandlerFactory,
                    pushConsumerRegistry,
                    IOReactorConfig.DEFAULT,
                    new DefaultThreadFactory("httpclient-main", true),
                    new DefaultThreadFactory("httpclient-dispatch", true),
                    connmgr);
        } catch (IOReactorException ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    private static MinimalHttpAsyncClient createMinimalImpl(
            final H1Config h1Config,
            final AsyncClientConnectionManager connmgr) {
        return createMinimalImpl(
                new DefaultAsyncHttp1ClientEventHandlerFactory(
                        createMinimalProtocolProcessor(),
                        h1Config,
                        ConnectionConfig.DEFAULT,
                        DefaultConnectionReuseStrategy.INSTANCE),
                new AsyncPushConsumerRegistry(),
                connmgr);
    }

    private static MinimalHttpAsyncClient createMinimalImpl(
            final H2Config h2Config,
            final AsyncClientConnectionManager connmgr) {
        final AsyncPushConsumerRegistry pushConsumerRegistry = new AsyncPushConsumerRegistry();
        return createMinimalImpl(
                new DefaultAsyncHttp2ClientEventHandlerFactory(
                        createMinimalProtocolProcessor(),
                        new HandlerFactory<AsyncPushConsumer>() {

                            @Override
                            public AsyncPushConsumer create(final HttpRequest request) throws HttpException {
                                return pushConsumerRegistry.get(request);
                            }

                        },
                        StandardCharsets.US_ASCII,
                        h2Config),
                pushConsumerRegistry,
                connmgr);
    }

    /**
     * Creates {@link CloseableHttpAsyncClient} instance that provides
     * essential HTTP/1.1 message transport only.
     */
    public static CloseableHttpAsyncClient createMinimal() {
        return createMinimalImpl(H1Config.DEFAULT, PoolingAsyncClientConnectionManagerBuilder.create().build());
    }

    /**
     * Creates {@link CloseableHttpAsyncClient} instance that provides
     * essential HTTP/1.1 message transport only.
     */
    public static CloseableHttpAsyncClient createMinimal(final AsyncClientConnectionManager connManager) {
        return createMinimalImpl(H1Config.DEFAULT, connManager);
    }

    /**
     * Creates {@link CloseableHttpAsyncClient} instance that provides
     * essential HTTP/2 message transport only.
     */
    public static CloseableHttpAsyncClient createMinimal(final H2Config h2Config) {
        return createMinimal(h2Config, PoolingAsyncClientConnectionManagerBuilder.create().build());
    }

    /**
     * Creates {@link CloseableHttpAsyncClient} instance that provides
     * essential HTTP/2 message transport only.
     */
    public static CloseableHttpAsyncClient createMinimal(
            final H2Config h2Config,
            final AsyncClientConnectionManager connManager) {
        return createMinimalImpl(h2Config, connManager);
    }

}
