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

import java.io.Closeable;
import java.io.IOException;
import java.net.ProxySelector;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.ConnectionKeepAliveStrategy;
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultConnectionKeepAliveStrategy;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.impl.DefaultThreadFactory;
import org.apache.hc.client5.http.impl.DefaultUserTokenHandler;
import org.apache.hc.client5.http.impl.IdleConnectionEvictor;
import org.apache.hc.client5.http.impl.NoopUserTokenHandler;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.routing.DefaultProxyRoutePlanner;
import org.apache.hc.client5.http.impl.routing.DefaultRoutePlanner;
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner;
import org.apache.hc.client5.http.nio.AsyncClientConnectionManager;
import org.apache.hc.client5.http.protocol.RequestDefaultHeaders;
import org.apache.hc.client5.http.protocol.RequestExpectContinue;
import org.apache.hc.client5.http.protocol.UserTokenHandler;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.config.ConnectionConfig;
import org.apache.hc.core5.http.config.H1Config;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.HttpProcessorBuilder;
import org.apache.hc.core5.http.protocol.RequestUserAgent;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.protocol.H2RequestConnControl;
import org.apache.hc.core5.http2.protocol.H2RequestContent;
import org.apache.hc.core5.http2.protocol.H2RequestTargetHost;
import org.apache.hc.core5.pool.ConnPoolControl;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOReactorException;
import org.apache.hc.core5.util.VersionInfo;

/**
 * Builder for {@link CloseableHttpAsyncClient} instances.
 * <p>
 * When a particular component is not explicitly set this class will
 * use its default implementation. System properties will be taken
 * into account when configuring the default implementations when
 * {@link #useSystemProperties()} method is called prior to calling
 * {@link #build()}.
 * </p>
 * <ul>
 *  <li>http.proxyHost</li>
 *  <li>http.proxyPort</li>
 *  <li>http.nonProxyHosts</li>
 *  <li>http.keepAlive</li>
 *  <li>http.agent</li>
 * </ul>
 * <p>
 * Please note that some settings used by this class can be mutually
 * exclusive and may not apply when building {@link CloseableHttpAsyncClient}
 * instances.
 * </p>
 *
 * @since 5.0
 */
public class HttpAsyncClientBuilder {

    private HttpVersion protocolVersion;
    private AsyncClientConnectionManager connManager;
    private boolean connManagerShared;
    private IOReactorConfig ioReactorConfig;
    private H1Config h1Config;
    private H2Config h2Config;
    private SchemePortResolver schemePortResolver;
    private ConnectionReuseStrategy reuseStrategy;
    private ConnectionKeepAliveStrategy keepAliveStrategy;
    private UserTokenHandler userTokenHandler;

    private LinkedList<HttpRequestInterceptor> requestFirst;
    private LinkedList<HttpRequestInterceptor> requestLast;
    private LinkedList<HttpResponseInterceptor> responseFirst;
    private LinkedList<HttpResponseInterceptor> responseLast;

    private HttpRoutePlanner routePlanner;
    private String userAgent;
    private HttpHost proxy;
    private Collection<? extends Header> defaultHeaders;
    private RequestConfig defaultRequestConfig;
    private boolean evictExpiredConnections;
    private boolean evictIdleConnections;
    private long maxIdleTime;
    private TimeUnit maxIdleTimeUnit;

    private boolean systemProperties;
    private boolean connectionStateDisabled;

    private List<Closeable> closeables;

    public static HttpAsyncClientBuilder create() {
        return new HttpAsyncClientBuilder();
    }

    protected HttpAsyncClientBuilder() {
        super();
    }

    /**
     * Sets HTTP protocol version.
     */
    public final HttpAsyncClientBuilder setProtocolVersion(final HttpVersion protocolVersion) {
        this.protocolVersion = protocolVersion;
        return this;
    }

    /**
     * Assigns {@link AsyncClientConnectionManager} instance.
     */
    public final HttpAsyncClientBuilder setConnectionManager(final AsyncClientConnectionManager connManager) {
        this.connManager = connManager;
        return this;
    }

    /**
     * Defines the connection manager is to be shared by multiple
     * client instances.
     * <p>
     * If the connection manager is shared its life-cycle is expected
     * to be managed by the caller and it will not be shut down
     * if the client is closed.
     *
     * @param shared defines whether or not the connection manager can be shared
     *  by multiple clients.
     */
    public final HttpAsyncClientBuilder setConnectionManagerShared(final boolean shared) {
        this.connManagerShared = shared;
        return this;
    }

    /**
     * Sets {@link IOReactorConfig} configuration.
     */
    public final HttpAsyncClientBuilder setIOReactorConfig(final IOReactorConfig ioReactorConfig) {
        this.ioReactorConfig = ioReactorConfig;
        return this;
    }

    /**
     * Sets {@link H1Config} configuration.
     * <p>
     * Please note this setting applies only if {@link #setProtocolVersion(HttpVersion)} is
     * set to {@code HTTP/1.1} or below.
     */
    public final HttpAsyncClientBuilder setH1Config(final H1Config h1Config) {
        this.h1Config = h1Config;
        return this;
    }

    /**
     * Sets {@link H2Config} configuration.
     * <p>
     * Please note this setting applies only if {@link #setProtocolVersion(HttpVersion)} is
     * set to {@code HTTP/2} or above.
     */
    public final HttpAsyncClientBuilder setH2Config(final H2Config h2Config) {
        this.h2Config = h2Config;
        return this;
    }

    /**
     * Assigns {@link ConnectionReuseStrategy} instance.
     * <p>
     * Please note this setting applies only if {@link #setProtocolVersion(HttpVersion)} is
     * set to {@code HTTP/1.1} or below.
     */
    public final HttpAsyncClientBuilder setConnectionReuseStrategy(final ConnectionReuseStrategy reuseStrategy) {
        this.reuseStrategy = reuseStrategy;
        return this;
    }

    /**
     * Assigns {@link ConnectionKeepAliveStrategy} instance.
     */
    public final HttpAsyncClientBuilder setKeepAliveStrategy(final ConnectionKeepAliveStrategy keepAliveStrategy) {
        this.keepAliveStrategy = keepAliveStrategy;
        return this;
    }

    /**
     * Assigns {@link UserTokenHandler} instance.
     * <p>
     * Please note this value can be overridden by the {@link #disableConnectionState()}
     * method.
     * </p>
     */
    public final HttpAsyncClientBuilder setUserTokenHandler(final UserTokenHandler userTokenHandler) {
        this.userTokenHandler = userTokenHandler;
        return this;
    }

    /**
     * Disables connection state tracking.
     */
    public final HttpAsyncClientBuilder disableConnectionState() {
        connectionStateDisabled = true;
        return this;
    }

    /**
     * Assigns {@link SchemePortResolver} instance.
     */
    public final HttpAsyncClientBuilder setSchemePortResolver(final SchemePortResolver schemePortResolver) {
        this.schemePortResolver = schemePortResolver;
        return this;
    }

    /**
     * Assigns {@code User-Agent} value.
     */
    public final HttpAsyncClientBuilder setUserAgent(final String userAgent) {
        this.userAgent = userAgent;
        return this;
    }

    /**
     * Assigns default request header values.
     */
    public final HttpAsyncClientBuilder setDefaultHeaders(final Collection<? extends Header> defaultHeaders) {
        this.defaultHeaders = defaultHeaders;
        return this;
    }

    /**
     * Adds this protocol interceptor to the head of the protocol processing list.
     */
    public final HttpAsyncClientBuilder addInterceptorFirst(final HttpResponseInterceptor itcp) {
        if (itcp == null) {
            return this;
        }
        if (responseFirst == null) {
            responseFirst = new LinkedList<>();
        }
        responseFirst.addFirst(itcp);
        return this;
    }

    /**
     * Adds this protocol interceptor to the tail of the protocol processing list.
     */
    public final HttpAsyncClientBuilder addInterceptorLast(final HttpResponseInterceptor itcp) {
        if (itcp == null) {
            return this;
        }
        if (responseLast == null) {
            responseLast = new LinkedList<>();
        }
        responseLast.addLast(itcp);
        return this;
    }

    /**
     * Adds this protocol interceptor to the head of the protocol processing list.
     */
    public final HttpAsyncClientBuilder addInterceptorFirst(final HttpRequestInterceptor itcp) {
        if (itcp == null) {
            return this;
        }
        if (requestFirst == null) {
            requestFirst = new LinkedList<>();
        }
        requestFirst.addFirst(itcp);
        return this;
    }

    /**
     * Adds this protocol interceptor to the tail of the protocol processing list.
     */
    public final HttpAsyncClientBuilder addInterceptorLast(final HttpRequestInterceptor itcp) {
        if (itcp == null) {
            return this;
        }
        if (requestLast == null) {
            requestLast = new LinkedList<>();
        }
        requestLast.addLast(itcp);
        return this;
    }

    /**
     * Assigns default proxy value.
     * <p>
     * Please note this value can be overridden by the {@link #setRoutePlanner(
     *   HttpRoutePlanner)} method.
     */
    public final HttpAsyncClientBuilder setProxy(final HttpHost proxy) {
        this.proxy = proxy;
        return this;
    }

    /**
     * Assigns {@link HttpRoutePlanner} instance.
     */
    public final HttpAsyncClientBuilder setRoutePlanner(final HttpRoutePlanner routePlanner) {
        this.routePlanner = routePlanner;
        return this;
    }

    /**
     * Assigns default {@link RequestConfig} instance which will be used
     * for request execution if not explicitly set in the client execution
     * context.
     */
    public final HttpAsyncClientBuilder setDefaultRequestConfig(final RequestConfig config) {
        this.defaultRequestConfig = config;
        return this;
    }

    /**
     * Use system properties when creating and configuring default
     * implementations.
     */
    public final HttpAsyncClientBuilder useSystemProperties() {
        this.systemProperties = true;
        return this;
    }

    /**
     * Makes this instance of HttpClient proactively evict expired connections from the
     * connection pool using a background thread.
     * <p>
     * One MUST explicitly close HttpClient with {@link CloseableHttpAsyncClient#close()} in order
     * to stop and release the background thread.
     * <p>
     * Please note this method has no effect if the instance of HttpClient is configuted to
     * use a shared connection manager.
     *
     * @see #setConnectionManagerShared(boolean)
     * @see ConnPoolControl#closeExpired()
     */
    public final HttpAsyncClientBuilder evictExpiredConnections() {
        evictExpiredConnections = true;
        return this;
    }

    /**
     * Makes this instance of HttpClient proactively evict idle connections from the
     * connection pool using a background thread.
     * <p>
     * One MUST explicitly close HttpClient with {@link CloseableHttpAsyncClient#close()}
     * in order to stop and release the background thread.
     * <p>
     * Please note this method has no effect if the instance of HttpClient is configuted to
     * use a shared connection manager.
     *
     * @see #setConnectionManagerShared(boolean)
     * @see ConnPoolControl#closeIdle(long, TimeUnit)
     *
     * @param maxIdleTime maximum time persistent connections can stay idle while kept alive
     * in the connection pool. Connections whose inactivity period exceeds this value will
     * get closed and evicted from the pool.
     * @param maxIdleTimeUnit time unit for the above parameter.
     */
    public final HttpAsyncClientBuilder evictIdleConnections(final long maxIdleTime, final TimeUnit maxIdleTimeUnit) {
        this.evictIdleConnections = true;
        this.maxIdleTime = maxIdleTime;
        this.maxIdleTimeUnit = maxIdleTimeUnit;
        return this;
    }

    /**
     * For internal use.
     */
    protected void addCloseable(final Closeable closeable) {
        if (closeable == null) {
            return;
        }
        if (closeables == null) {
            closeables = new ArrayList<>();
        }
        closeables.add(closeable);
    }

    public CloseableHttpAsyncClient build() {
        AsyncClientConnectionManager connManagerCopy = this.connManager;
        if (connManagerCopy == null) {
            connManagerCopy = PoolingAsyncClientConnectionManagerBuilder.create().build();
        }

        ConnectionKeepAliveStrategy keepAliveStrategyCopy = this.keepAliveStrategy;
        if (keepAliveStrategyCopy == null) {
            keepAliveStrategyCopy = DefaultConnectionKeepAliveStrategy.INSTANCE;
        }

        UserTokenHandler userTokenHandlerCopy = this.userTokenHandler;
        if (userTokenHandlerCopy == null) {
            if (!connectionStateDisabled) {
                userTokenHandlerCopy = DefaultUserTokenHandler.INSTANCE;
            } else {
                userTokenHandlerCopy = NoopUserTokenHandler.INSTANCE;
            }
        }

        String userAgentCopy = this.userAgent;
        if (userAgentCopy == null) {
            if (systemProperties) {
                userAgentCopy = System.getProperty("http.agent");
            }
            if (userAgentCopy == null) {
                userAgentCopy = VersionInfo.getSoftwareInfo("Apache-HttpAsyncClient",
                        "org.apache.hc.client5", getClass());
            }
        }

        final HttpProcessorBuilder b = HttpProcessorBuilder.create();
        if (requestFirst != null) {
            for (final HttpRequestInterceptor i: requestFirst) {
                b.addFirst(i);
            }
        }
        if (responseFirst != null) {
            for (final HttpResponseInterceptor i: responseFirst) {
                b.addFirst(i);
            }
        }
        b.addAll(
                new RequestDefaultHeaders(defaultHeaders),
                new H2RequestContent(),
                new H2RequestTargetHost(),
                new H2RequestConnControl(),
                new RequestUserAgent(userAgentCopy),
                new RequestExpectContinue());
        if (requestLast != null) {
            for (final HttpRequestInterceptor i: requestLast) {
                b.addLast(i);
            }
        }
        if (responseLast != null) {
            for (final HttpResponseInterceptor i: responseLast) {
                b.addLast(i);
            }
        }
        final HttpProcessor httpProcessor = b.build();

        HttpRoutePlanner routePlannerCopy = this.routePlanner;
        if (routePlannerCopy == null) {
            SchemePortResolver schemePortResolverCopy = this.schemePortResolver;
            if (schemePortResolverCopy == null) {
                schemePortResolverCopy = DefaultSchemePortResolver.INSTANCE;
            }
            if (proxy != null) {
                routePlannerCopy = new DefaultProxyRoutePlanner(proxy, schemePortResolverCopy);
            } else if (systemProperties) {
                routePlannerCopy = new SystemDefaultRoutePlanner(
                        schemePortResolverCopy, ProxySelector.getDefault());
            } else {
                routePlannerCopy = new DefaultRoutePlanner(schemePortResolverCopy);
            }
        }
        List<Closeable> closeablesCopy = closeables != null ? new ArrayList<>(closeables) : null;
        if (!this.connManagerShared) {
            if (closeablesCopy == null) {
                closeablesCopy = new ArrayList<>(1);
            }
            if (evictExpiredConnections || evictIdleConnections) {
                if (connManagerCopy instanceof ConnPoolControl) {
                    final IdleConnectionEvictor connectionEvictor = new IdleConnectionEvictor((ConnPoolControl<?>) connManagerCopy,
                            maxIdleTime > 0 ? maxIdleTime : 10, maxIdleTimeUnit != null ? maxIdleTimeUnit : TimeUnit.SECONDS);
                    closeablesCopy.add(new Closeable() {

                        @Override
                        public void close() throws IOException {
                            connectionEvictor.shutdown();
                        }

                    });
                    connectionEvictor.start();
                }
            }
            closeablesCopy.add(connManagerCopy);
        }
        final AsyncPushConsumerRegistry pushConsumerRegistry = new AsyncPushConsumerRegistry();
        final IOEventHandlerFactory ioEventHandlerFactory;
        if (protocolVersion != null && protocolVersion.greaterEquals(HttpVersion.HTTP_2)) {
            ioEventHandlerFactory = new DefaultAsyncHttp2ClientEventHandlerFactory(
                    httpProcessor,
                    new HandlerFactory<AsyncPushConsumer>() {

                        @Override
                        public AsyncPushConsumer create(final HttpRequest request) throws HttpException {
                            return pushConsumerRegistry.get(request);
                        }

                    },
                    StandardCharsets.US_ASCII,
                    h2Config != null ? h2Config : H2Config.DEFAULT);
        } else {
            ConnectionReuseStrategy reuseStrategyCopy = this.reuseStrategy;
            if (reuseStrategyCopy == null) {
                if (systemProperties) {
                    final String s = System.getProperty("http.keepAlive", "true");
                    if ("true".equalsIgnoreCase(s)) {
                        reuseStrategyCopy = DefaultConnectionReuseStrategy.INSTANCE;
                    } else {
                        reuseStrategyCopy = new ConnectionReuseStrategy() {
                            @Override
                            public boolean keepAlive(
                                    final HttpRequest request, final HttpResponse response, final HttpContext context) {
                                return false;
                            }
                        };
                    }
                } else {
                    reuseStrategyCopy = DefaultConnectionReuseStrategy.INSTANCE;
                }
            }
            ioEventHandlerFactory = new DefaultAsyncHttp1ClientEventHandlerFactory(
                    httpProcessor,
                    h1Config != null ? h1Config : H1Config.DEFAULT,
                    ConnectionConfig.DEFAULT,
                    reuseStrategyCopy);
        }
        try {
            return new InternalHttpAsyncClient(
                    ioEventHandlerFactory,
                    pushConsumerRegistry,
                    ioReactorConfig != null ? ioReactorConfig : IOReactorConfig.DEFAULT,
                    new DefaultThreadFactory("httpclient-main", true),
                    new DefaultThreadFactory("httpclient-dispatch", true),
                    connManagerCopy,
                    routePlannerCopy,
                    keepAliveStrategyCopy,
                    userTokenHandlerCopy,
                    defaultRequestConfig,
                    closeablesCopy);
        } catch (IOReactorException ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

}
