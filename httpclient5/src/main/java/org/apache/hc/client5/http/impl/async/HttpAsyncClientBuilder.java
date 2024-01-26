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
import java.net.ProxySelector;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ThreadFactory;

import org.apache.hc.client5.http.AuthenticationStrategy;
import org.apache.hc.client5.http.ConnectionKeepAliveStrategy;
import org.apache.hc.client5.http.HttpRequestRetryStrategy;
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.UserTokenHandler;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.CookieSpecFactory;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.ChainElement;
import org.apache.hc.client5.http.impl.CookieSpecSupport;
import org.apache.hc.client5.http.impl.DefaultAuthenticationStrategy;
import org.apache.hc.client5.http.impl.DefaultClientConnectionReuseStrategy;
import org.apache.hc.client5.http.impl.DefaultConnectionKeepAliveStrategy;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.impl.DefaultUserTokenHandler;
import org.apache.hc.client5.http.impl.IdleConnectionEvictor;
import org.apache.hc.client5.http.impl.NoopUserTokenHandler;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.auth.BasicSchemeFactory;
import org.apache.hc.client5.http.impl.auth.BearerSchemeFactory;
import org.apache.hc.client5.http.impl.auth.DigestSchemeFactory;
import org.apache.hc.client5.http.impl.auth.SystemDefaultCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.routing.DefaultProxyRoutePlanner;
import org.apache.hc.client5.http.impl.routing.DefaultRoutePlanner;
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner;
import org.apache.hc.client5.http.nio.AsyncClientConnectionManager;
import org.apache.hc.client5.http.protocol.RedirectStrategy;
import org.apache.hc.client5.http.protocol.RequestAddCookies;
import org.apache.hc.client5.http.protocol.RequestDefaultHeaders;
import org.apache.hc.client5.http.protocol.RequestExpectContinue;
import org.apache.hc.client5.http.protocol.RequestUpgrade;
import org.apache.hc.client5.http.protocol.RequestValidateTrace;
import org.apache.hc.client5.http.protocol.ResponseProcessCookies;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.concurrent.DefaultThreadFactory;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.config.NamedElementChain;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.nio.command.ShutdownCommand;
import org.apache.hc.core5.http.protocol.DefaultHttpProcessor;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.HttpProcessorBuilder;
import org.apache.hc.core5.http.protocol.RequestTargetHost;
import org.apache.hc.core5.http.protocol.RequestUserAgent;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.protocol.H2RequestConnControl;
import org.apache.hc.core5.http2.protocol.H2RequestContent;
import org.apache.hc.core5.http2.protocol.H2RequestTargetHost;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.pool.ConnPoolControl;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.DefaultConnectingIOReactor;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.IOSessionListener;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.VersionInfo;

/**
 * Builder for {@link CloseableHttpAsyncClient} instances that can negotiate
 * the most optimal HTTP protocol version during the {@code TLS} handshake
 * with {@code ALPN} extension if supported by the Java runtime.
 * <p>
 * Concurrent message exchanges executed by {@link CloseableHttpAsyncClient}
 * instances created with this builder will get automatically assigned to
 * separate connections leased from the connection pool.
 * </p>
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
 *  <li>https.proxyHost</li>
 *  <li>https.proxyPort</li>
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

    private static class RequestInterceptorEntry {

        enum Position { FIRST, LAST }

        final RequestInterceptorEntry.Position position;
        final HttpRequestInterceptor interceptor;

        private RequestInterceptorEntry(final RequestInterceptorEntry.Position position, final HttpRequestInterceptor interceptor) {
            this.position = position;
            this.interceptor = interceptor;
        }
    }

    private static class ResponseInterceptorEntry {

        enum Position { FIRST, LAST }

        final ResponseInterceptorEntry.Position position;
        final HttpResponseInterceptor interceptor;

        private ResponseInterceptorEntry(final ResponseInterceptorEntry.Position position, final HttpResponseInterceptor interceptor) {
            this.position = position;
            this.interceptor = interceptor;
        }
    }

    private static class ExecInterceptorEntry {

        enum Position { BEFORE, AFTER, REPLACE, FIRST, LAST }

        final ExecInterceptorEntry.Position position;
        final String name;
        final AsyncExecChainHandler interceptor;
        final String existing;

        private ExecInterceptorEntry(
                final ExecInterceptorEntry.Position position,
                final String name,
                final AsyncExecChainHandler interceptor,
                final String existing) {
            this.position = position;
            this.name = name;
            this.interceptor = interceptor;
            this.existing = existing;
        }

    }

    /**
     * @deprecated TLS should be configured by the connection manager
     */
    @Deprecated
    private TlsConfig tlsConfig;
    private AsyncClientConnectionManager connManager;
    private boolean connManagerShared;
    private IOReactorConfig ioReactorConfig;
    private IOSessionListener ioSessionListener;
    private Callback<Exception> ioReactorExceptionCallback;
    private Http1Config h1Config;
    private H2Config h2Config;
    private CharCodingConfig charCodingConfig;
    private SchemePortResolver schemePortResolver;
    private ConnectionKeepAliveStrategy keepAliveStrategy;
    private UserTokenHandler userTokenHandler;
    private AuthenticationStrategy targetAuthStrategy;
    private AuthenticationStrategy proxyAuthStrategy;
    private Decorator<IOSession> ioSessionDecorator;

    private LinkedList<RequestInterceptorEntry> requestInterceptors;
    private LinkedList<ResponseInterceptorEntry> responseInterceptors;
    private LinkedList<ExecInterceptorEntry> execInterceptors;

    private HttpRoutePlanner routePlanner;
    private RedirectStrategy redirectStrategy;
    private HttpRequestRetryStrategy retryStrategy;

    private ConnectionReuseStrategy reuseStrategy;

    private Lookup<AuthSchemeFactory> authSchemeRegistry;
    private Lookup<CookieSpecFactory> cookieSpecRegistry;
    private CookieStore cookieStore;
    private CredentialsProvider credentialsProvider;

    private String userAgent;
    private HttpHost proxy;
    private Collection<? extends Header> defaultHeaders;
    private RequestConfig defaultRequestConfig;
    private boolean evictExpiredConnections;
    private boolean evictIdleConnections;
    private TimeValue maxIdleTime;

    private boolean systemProperties;
    private boolean automaticRetriesDisabled;
    private boolean redirectHandlingDisabled;
    private boolean cookieManagementDisabled;
    private boolean authCachingDisabled;
    private boolean connectionStateDisabled;

    private ThreadFactory threadFactory;

    private List<Closeable> closeables;

    private ProxySelector proxySelector;

    public static HttpAsyncClientBuilder create() {
        return new HttpAsyncClientBuilder();
    }

    protected HttpAsyncClientBuilder() {
        super();
    }

    /**
     * Sets HTTP protocol version policy.
     *
     * @deprecated Use {@link TlsConfig} and connection manager methods
     */
    @Deprecated
    public final HttpAsyncClientBuilder setVersionPolicy(final HttpVersionPolicy versionPolicy) {
        this.tlsConfig = versionPolicy != null ? TlsConfig.custom().setVersionPolicy(versionPolicy).build() : null;
        return this;
    }

    /**
     * Sets {@link Http1Config} configuration.
     */
    public final HttpAsyncClientBuilder setHttp1Config(final Http1Config h1Config) {
        this.h1Config = h1Config;
        return this;
    }

    /**
     * Sets {@link H2Config} configuration.
     */
    public final HttpAsyncClientBuilder setH2Config(final H2Config h2Config) {
        this.h2Config = h2Config;
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
     * Sets {@link IOSessionListener} listener.
     *
     * @since 5.2
     */
    public final HttpAsyncClientBuilder setIOSessionListener(final IOSessionListener ioSessionListener) {
        this.ioSessionListener = ioSessionListener;
        return this;
    }

    /**
     * Sets the callback that will be invoked when the client's IOReactor encounters an uncaught exception.
     *
     * @since 5.1
     */
    public final HttpAsyncClientBuilder setIoReactorExceptionCallback(final Callback<Exception> ioReactorExceptionCallback) {
        this.ioReactorExceptionCallback = ioReactorExceptionCallback;
        return this;
    }

    /**
     * Sets {@link CharCodingConfig} configuration.
     */
    public final HttpAsyncClientBuilder setCharCodingConfig(final CharCodingConfig charCodingConfig) {
        this.charCodingConfig = charCodingConfig;
        return this;
    }

    /**
     * Assigns {@link ConnectionReuseStrategy} instance.
     * <p>
     * Please note this strategy applies to HTTP/1.0 and HTTP/1.1 connections only
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
     * Assigns {@link AuthenticationStrategy} instance for target
     * host authentication.
     */
    public final HttpAsyncClientBuilder setTargetAuthenticationStrategy(
            final AuthenticationStrategy targetAuthStrategy) {
        this.targetAuthStrategy = targetAuthStrategy;
        return this;
    }

    /**
     * Assigns {@link AuthenticationStrategy} instance for proxy
     * authentication.
     */
    public final HttpAsyncClientBuilder setProxyAuthenticationStrategy(
            final AuthenticationStrategy proxyAuthStrategy) {
        this.proxyAuthStrategy = proxyAuthStrategy;
        return this;
    }

    /**
     * Sets the {@link IOSession} {@link Decorator} that will be use with the client's IOReactor.
     *
     * @since 5.2
     */
    public final HttpAsyncClientBuilder setIoSessionDecorator(final Decorator<IOSession> ioSessionDecorator) {
        this.ioSessionDecorator = ioSessionDecorator;
        return this;
    }

    /**
     * Adds this protocol interceptor to the head of the protocol processing list.
     */
    public final HttpAsyncClientBuilder addResponseInterceptorFirst(final HttpResponseInterceptor interceptor) {
        Args.notNull(interceptor, "Interceptor");
        if (responseInterceptors == null) {
            responseInterceptors = new LinkedList<>();
        }
        responseInterceptors.add(new ResponseInterceptorEntry(ResponseInterceptorEntry.Position.FIRST, interceptor));
        return this;
    }

    /**
     * Adds this protocol interceptor to the tail of the protocol processing list.
     */
    public final HttpAsyncClientBuilder addResponseInterceptorLast(final HttpResponseInterceptor interceptor) {
        Args.notNull(interceptor, "Interceptor");
        if (responseInterceptors == null) {
            responseInterceptors = new LinkedList<>();
        }
        responseInterceptors.add(new ResponseInterceptorEntry(ResponseInterceptorEntry.Position.LAST, interceptor));
        return this;
    }

    /**
     * Adds this execution interceptor before an existing interceptor.
     */
    public final HttpAsyncClientBuilder addExecInterceptorBefore(final String existing, final String name, final AsyncExecChainHandler interceptor) {
        Args.notBlank(existing, "Existing");
        Args.notBlank(name, "Name");
        Args.notNull(interceptor, "Interceptor");
        if (execInterceptors == null) {
            execInterceptors = new LinkedList<>();
        }
        execInterceptors.add(new ExecInterceptorEntry(ExecInterceptorEntry.Position.BEFORE, name, interceptor, existing));
        return this;
    }

    /**
     * Adds this execution interceptor after interceptor with the given name.
     */
    public final HttpAsyncClientBuilder addExecInterceptorAfter(final String existing, final String name, final AsyncExecChainHandler interceptor) {
        Args.notBlank(existing, "Existing");
        Args.notBlank(name, "Name");
        Args.notNull(interceptor, "Interceptor");
        if (execInterceptors == null) {
            execInterceptors = new LinkedList<>();
        }
        execInterceptors.add(new ExecInterceptorEntry(ExecInterceptorEntry.Position.AFTER, name, interceptor, existing));
        return this;
    }

    /**
     * Replace an existing interceptor with the given name with new interceptor.
     */
    public final HttpAsyncClientBuilder replaceExecInterceptor(final String existing, final AsyncExecChainHandler interceptor) {
        Args.notBlank(existing, "Existing");
        Args.notNull(interceptor, "Interceptor");
        if (execInterceptors == null) {
            execInterceptors = new LinkedList<>();
        }
        execInterceptors.add(new ExecInterceptorEntry(ExecInterceptorEntry.Position.REPLACE, existing, interceptor, existing));
        return this;
    }

    /**
     * Add an interceptor to the head of the processing list.
     */
    public final HttpAsyncClientBuilder addExecInterceptorFirst(final String name, final AsyncExecChainHandler interceptor) {
        Args.notNull(name, "Name");
        Args.notNull(interceptor, "Interceptor");
        if (execInterceptors == null) {
            execInterceptors = new LinkedList<>();
        }
        execInterceptors.add(new ExecInterceptorEntry(ExecInterceptorEntry.Position.FIRST, name, interceptor, null));
        return this;
    }

    /**
     * Add an interceptor to the tail of the processing list.
     */
    public final HttpAsyncClientBuilder addExecInterceptorLast(final String name, final AsyncExecChainHandler interceptor) {
        Args.notNull(name, "Name");
        Args.notNull(interceptor, "Interceptor");
        if (execInterceptors == null) {
            execInterceptors = new LinkedList<>();
        }
        execInterceptors.add(new ExecInterceptorEntry(ExecInterceptorEntry.Position.LAST, name, interceptor, null));
        return this;
    }

    /**
     * Adds this protocol interceptor to the head of the protocol processing list.
     */
    public final HttpAsyncClientBuilder addRequestInterceptorFirst(final HttpRequestInterceptor interceptor) {
        Args.notNull(interceptor, "Interceptor");
        if (requestInterceptors == null) {
            requestInterceptors = new LinkedList<>();
        }
        requestInterceptors.add(new RequestInterceptorEntry(RequestInterceptorEntry.Position.FIRST, interceptor));
        return this;
    }

    /**
     * Adds this protocol interceptor to the tail of the protocol processing list.
     */
    public final HttpAsyncClientBuilder addRequestInterceptorLast(final HttpRequestInterceptor interceptor) {
        Args.notNull(interceptor, "Interceptor");
        if (requestInterceptors == null) {
            requestInterceptors = new LinkedList<>();
        }
        requestInterceptors.add(new RequestInterceptorEntry(RequestInterceptorEntry.Position.LAST, interceptor));
        return this;
    }

    /**
     * Assigns {@link HttpRequestRetryStrategy} instance.
     * <p>
     * Please note this value can be overridden by the {@link #disableAutomaticRetries()}
     * method.
     */
    public final HttpAsyncClientBuilder setRetryStrategy(final HttpRequestRetryStrategy retryStrategy) {
        this.retryStrategy = retryStrategy;
        return this;
    }

    /**
     * Assigns {@link RedirectStrategy} instance.
     * <p>
     * Please note this value can be overridden by the {@link #disableRedirectHandling()}
     * method.
     * </p>
     */
    public HttpAsyncClientBuilder setRedirectStrategy(final RedirectStrategy redirectStrategy) {
        this.redirectStrategy = redirectStrategy;
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
     * Assigns {@link ThreadFactory} instance.
     */
    public final HttpAsyncClientBuilder setThreadFactory(final ThreadFactory threadFactory) {
        this.threadFactory = threadFactory;
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
     * Sets the {@link java.net.ProxySelector} that will be used to select the proxies
     * to be used for establishing HTTP connections. If a non-null proxy selector is set,
     * it will take precedence over the proxy settings configured in the client.
     *
     * @param proxySelector the {@link java.net.ProxySelector} to be used, or null to use
     *                      the default system proxy selector.
     * @return this {@link HttpAsyncClientBuilder} instance, to allow for method chaining.
     */
    public final HttpAsyncClientBuilder setProxySelector(final ProxySelector proxySelector) {
        this.proxySelector = proxySelector;
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
     * Assigns default {@link CredentialsProvider} instance which will be used
     * for request execution if not explicitly set in the client execution
     * context.
     */
    public final HttpAsyncClientBuilder setDefaultCredentialsProvider(final CredentialsProvider credentialsProvider) {
        this.credentialsProvider = credentialsProvider;
        return this;
    }

    /**
     * Assigns default {@link org.apache.hc.client5.http.auth.AuthScheme} registry which will
     * be used for request execution if not explicitly set in the client execution
     * context.
     */
    public final HttpAsyncClientBuilder setDefaultAuthSchemeRegistry(final Lookup<AuthSchemeFactory> authSchemeRegistry) {
        this.authSchemeRegistry = authSchemeRegistry;
        return this;
    }

    /**
     * Assigns default {@link org.apache.hc.client5.http.cookie.CookieSpec} registry
     * which will be used for request execution if not explicitly set in the client
     * execution context.
     */
    public final HttpAsyncClientBuilder setDefaultCookieSpecRegistry(final Lookup<CookieSpecFactory> cookieSpecRegistry) {
        this.cookieSpecRegistry = cookieSpecRegistry;
        return this;
    }

    /**
     * Assigns default {@link CookieStore} instance which will be used for
     * request execution if not explicitly set in the client execution context.
     */
    public final HttpAsyncClientBuilder setDefaultCookieStore(final CookieStore cookieStore) {
        this.cookieStore = cookieStore;
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
     * Disables connection state tracking.
     */
    public final HttpAsyncClientBuilder disableConnectionState() {
        connectionStateDisabled = true;
        return this;
    }

    /**
     * Disables automatic redirect handling.
     */
    public final HttpAsyncClientBuilder disableRedirectHandling() {
        redirectHandlingDisabled = true;
        return this;
    }

    /**
     * Disables automatic request recovery and re-execution.
     */
    public final HttpAsyncClientBuilder disableAutomaticRetries() {
        automaticRetriesDisabled = true;
        return this;
    }

    /**
     * Disables state (cookie) management.
     */
    public final HttpAsyncClientBuilder disableCookieManagement() {
        this.cookieManagementDisabled = true;
        return this;
    }

    /**
     * Disables authentication scheme caching.
     */
    public final HttpAsyncClientBuilder disableAuthCaching() {
        this.authCachingDisabled = true;
        return this;
    }

    /**
     * Makes this instance of HttpClient proactively evict expired connections from the
     * connection pool using a background thread.
     * <p>
     * One MUST explicitly close HttpClient with {@link CloseableHttpAsyncClient#close()} in order
     * to stop and release the background thread.
     * <p>
     * Please note this method has no effect if the instance of HttpClient is configured to
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
     * Please note this method has no effect if the instance of HttpClient is configured to
     * use a shared connection manager.
     *
     * @see #setConnectionManagerShared(boolean)
     * @see ConnPoolControl#closeIdle(TimeValue)
     *
     * @param maxIdleTime maximum time persistent connections can stay idle while kept alive
     * in the connection pool. Connections whose inactivity period exceeds this value will
     * get closed and evicted from the pool.
     */
    public final HttpAsyncClientBuilder evictIdleConnections(final TimeValue maxIdleTime) {
        this.evictIdleConnections = true;
        this.maxIdleTime = maxIdleTime;
        return this;
    }

    /**
     * Request exec chain customization and extension.
     * <p>
     * For internal use.
     */
    @Internal
    protected void customizeExecChain(final NamedElementChain<AsyncExecChainHandler> execChainDefinition) {
    }

    /**
     * Adds to the list of {@link Closeable} resources to be managed by the client.
     * <p>
     * For internal use.
     */
    @Internal
    protected void addCloseable(final Closeable closeable) {
        if (closeable == null) {
            return;
        }
        if (closeables == null) {
            closeables = new ArrayList<>();
        }
        closeables.add(closeable);
    }
    @SuppressWarnings("deprecated")
    public CloseableHttpAsyncClient build() {
        AsyncClientConnectionManager connManagerCopy = this.connManager;
        if (connManagerCopy == null) {
            final PoolingAsyncClientConnectionManagerBuilder connectionManagerBuilder = PoolingAsyncClientConnectionManagerBuilder.create();
            if (systemProperties) {
                connectionManagerBuilder.useSystemProperties();
            }
            connManagerCopy = connectionManagerBuilder.build();
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

        AuthenticationStrategy targetAuthStrategyCopy = this.targetAuthStrategy;
        if (targetAuthStrategyCopy == null) {
            targetAuthStrategyCopy = DefaultAuthenticationStrategy.INSTANCE;
        }
        AuthenticationStrategy proxyAuthStrategyCopy = this.proxyAuthStrategy;
        if (proxyAuthStrategyCopy == null) {
            proxyAuthStrategyCopy = DefaultAuthenticationStrategy.INSTANCE;
        }

        String userAgentCopy = this.userAgent;
        if (userAgentCopy == null) {
            if (systemProperties) {
                userAgentCopy = getProperty("http.agent", null);
            }
            if (userAgentCopy == null) {
                userAgentCopy = VersionInfo.getSoftwareInfo("Apache-HttpAsyncClient",
                        "org.apache.hc.client5", getClass());
            }
        }

        final HttpProcessorBuilder b = HttpProcessorBuilder.create();
        if (requestInterceptors != null) {
            for (final RequestInterceptorEntry entry: requestInterceptors) {
                if (entry.position == RequestInterceptorEntry.Position.FIRST) {
                    b.addFirst(entry.interceptor);
                }
            }
        }
        if (responseInterceptors != null) {
            for (final ResponseInterceptorEntry entry: responseInterceptors) {
                if (entry.position == ResponseInterceptorEntry.Position.FIRST) {
                    b.addFirst(entry.interceptor);
                }
            }
        }
        b.addAll(
                new H2RequestTargetHost(),
                new RequestValidateTrace(),
                new RequestDefaultHeaders(defaultHeaders),
                new H2RequestContent(),
                new H2RequestConnControl(),
                new RequestUserAgent(userAgentCopy),
                new RequestExpectContinue(),
                new RequestUpgrade());
        if (!cookieManagementDisabled) {
            b.add(RequestAddCookies.INSTANCE);
        }
        if (!cookieManagementDisabled) {
            b.add(ResponseProcessCookies.INSTANCE);
        }
        if (requestInterceptors != null) {
            for (final RequestInterceptorEntry entry: requestInterceptors) {
                if (entry.position == RequestInterceptorEntry.Position.LAST) {
                    b.addLast(entry.interceptor);
                }
            }
        }
        if (responseInterceptors != null) {
            for (final ResponseInterceptorEntry entry: responseInterceptors) {
                if (entry.position == ResponseInterceptorEntry.Position.LAST) {
                    b.addLast(entry.interceptor);
                }
            }
        }

        final HttpProcessor httpProcessor = b.build();

        final NamedElementChain<AsyncExecChainHandler> execChainDefinition = new NamedElementChain<>();
        execChainDefinition.addLast(
                new HttpAsyncMainClientExec(httpProcessor, keepAliveStrategyCopy, userTokenHandlerCopy),
                ChainElement.MAIN_TRANSPORT.name());

        execChainDefinition.addFirst(
                new AsyncConnectExec(
                        new DefaultHttpProcessor(new RequestTargetHost(), new RequestUserAgent(userAgentCopy)),
                        proxyAuthStrategyCopy,
                        schemePortResolver != null ? schemePortResolver : DefaultSchemePortResolver.INSTANCE,
                        authCachingDisabled),
                ChainElement.CONNECT.name());

        execChainDefinition.addFirst(
                new AsyncProtocolExec(
                        targetAuthStrategyCopy,
                        proxyAuthStrategyCopy,
                        schemePortResolver != null ? schemePortResolver : DefaultSchemePortResolver.INSTANCE,
                        authCachingDisabled),
                ChainElement.PROTOCOL.name());

        // Add request retry executor, if not disabled
        if (!automaticRetriesDisabled) {
            HttpRequestRetryStrategy retryStrategyCopy = this.retryStrategy;
            if (retryStrategyCopy == null) {
                retryStrategyCopy = DefaultHttpRequestRetryStrategy.INSTANCE;
            }
            execChainDefinition.addFirst(
                    new AsyncHttpRequestRetryExec(retryStrategyCopy),
                    ChainElement.RETRY.name());
        }

        HttpRoutePlanner routePlannerCopy = this.routePlanner;
        if (routePlannerCopy == null) {
            SchemePortResolver schemePortResolverCopy = this.schemePortResolver;
            if (schemePortResolverCopy == null) {
                schemePortResolverCopy = DefaultSchemePortResolver.INSTANCE;
            }
            if (proxy != null) {
                routePlannerCopy = new DefaultProxyRoutePlanner(proxy, schemePortResolverCopy);
            } else if (this.proxySelector != null) {
                routePlannerCopy = new SystemDefaultRoutePlanner(schemePortResolverCopy, this.proxySelector);
            } else if (systemProperties) {
                final ProxySelector defaultProxySelector = AccessController.doPrivileged((PrivilegedAction<ProxySelector>) ProxySelector::getDefault);
                routePlannerCopy = new SystemDefaultRoutePlanner(schemePortResolverCopy, defaultProxySelector);
            } else {
                routePlannerCopy = new DefaultRoutePlanner(schemePortResolverCopy);
            }
        }

        // Add redirect executor, if not disabled
        if (!redirectHandlingDisabled) {
            RedirectStrategy redirectStrategyCopy = this.redirectStrategy;
            if (redirectStrategyCopy == null) {
                redirectStrategyCopy = DefaultRedirectStrategy.INSTANCE;
            }
            execChainDefinition.addFirst(
                    new AsyncRedirectExec(routePlannerCopy, redirectStrategyCopy),
                    ChainElement.REDIRECT.name());
        }

        List<Closeable> closeablesCopy = closeables != null ? new ArrayList<>(closeables) : null;
        if (!this.connManagerShared) {
            if (closeablesCopy == null) {
                closeablesCopy = new ArrayList<>(1);
            }
            if (evictExpiredConnections || evictIdleConnections) {
                if (connManagerCopy instanceof ConnPoolControl) {
                    final IdleConnectionEvictor connectionEvictor = new IdleConnectionEvictor((ConnPoolControl<?>) connManagerCopy,
                            maxIdleTime,  maxIdleTime);
                    closeablesCopy.add(connectionEvictor::shutdown);
                    connectionEvictor.start();
                }
            }
            closeablesCopy.add(connManagerCopy);
        }
        ConnectionReuseStrategy reuseStrategyCopy = this.reuseStrategy;
        if (reuseStrategyCopy == null) {
            if (systemProperties) {
                final String s = getProperty("http.keepAlive", "true");
                if ("true".equalsIgnoreCase(s)) {
                    reuseStrategyCopy = DefaultClientConnectionReuseStrategy.INSTANCE;
                } else {
                    reuseStrategyCopy = (request, response, context) -> false;
                }
            } else {
                reuseStrategyCopy = DefaultClientConnectionReuseStrategy.INSTANCE;
            }
        }
        final AsyncPushConsumerRegistry pushConsumerRegistry = new AsyncPushConsumerRegistry();
        final IOEventHandlerFactory ioEventHandlerFactory = new HttpAsyncClientProtocolNegotiationStarter(
                HttpProcessorBuilder.create().build(),
                (request, context) -> pushConsumerRegistry.get(request),
                h2Config != null ? h2Config : H2Config.DEFAULT,
                h1Config != null ? h1Config : Http1Config.DEFAULT,
                charCodingConfig != null ? charCodingConfig : CharCodingConfig.DEFAULT,
                reuseStrategyCopy);
        final DefaultConnectingIOReactor ioReactor = new DefaultConnectingIOReactor(
                ioEventHandlerFactory,
                ioReactorConfig != null ? ioReactorConfig : IOReactorConfig.DEFAULT,
                threadFactory != null ? threadFactory : new DefaultThreadFactory("httpclient-dispatch", true),
                ioSessionDecorator != null ? ioSessionDecorator : LoggingIOSessionDecorator.INSTANCE,
                ioReactorExceptionCallback != null ? ioReactorExceptionCallback : LoggingExceptionCallback.INSTANCE,
                ioSessionListener,
                ioSession -> ioSession.enqueue(new ShutdownCommand(CloseMode.GRACEFUL), Command.Priority.IMMEDIATE));

        if (execInterceptors != null) {
            for (final ExecInterceptorEntry entry: execInterceptors) {
                switch (entry.position) {
                    case AFTER:
                        execChainDefinition.addAfter(entry.existing, entry.interceptor, entry.name);
                        break;
                    case BEFORE:
                        execChainDefinition.addBefore(entry.existing, entry.interceptor, entry.name);
                        break;
                    case REPLACE:
                        execChainDefinition.replace(entry.existing, entry.interceptor);
                        break;
                    case FIRST:
                        execChainDefinition.addFirst(entry.interceptor, entry.name);
                        break;
                    case LAST:
                        // Don't add last, after HttpAsyncMainClientExec, as that does not delegate to the chain
                        // Instead, add the interceptor just before it, making it effectively the last interceptor
                        execChainDefinition.addBefore(ChainElement.MAIN_TRANSPORT.name(), entry.interceptor, entry.name);
                        break;
                }
            }
        }

        customizeExecChain(execChainDefinition);

        NamedElementChain<AsyncExecChainHandler>.Node current = execChainDefinition.getLast();
        AsyncExecChainElement execChain = null;
        while (current != null) {
            execChain = new AsyncExecChainElement(current.getValue(), execChain);
            current = current.getPrevious();
        }

        Lookup<AuthSchemeFactory> authSchemeRegistryCopy = this.authSchemeRegistry;
        if (authSchemeRegistryCopy == null) {
            authSchemeRegistryCopy = RegistryBuilder.<AuthSchemeFactory>create()
                    .register(StandardAuthScheme.BASIC, BasicSchemeFactory.INSTANCE)
                    .register(StandardAuthScheme.DIGEST, DigestSchemeFactory.INSTANCE)
                    .register(StandardAuthScheme.BEARER, BearerSchemeFactory.INSTANCE)
                    .build();
        }
        Lookup<CookieSpecFactory> cookieSpecRegistryCopy = this.cookieSpecRegistry;
        if (cookieSpecRegistryCopy == null) {
            cookieSpecRegistryCopy = CookieSpecSupport.createDefault();
        }

        CookieStore cookieStoreCopy = this.cookieStore;
        if (cookieStoreCopy == null) {
            cookieStoreCopy = new BasicCookieStore();
        }

        CredentialsProvider credentialsProviderCopy = this.credentialsProvider;
        if (credentialsProviderCopy == null) {
            if (systemProperties) {
                credentialsProviderCopy = new SystemDefaultCredentialsProvider();
            } else {
                credentialsProviderCopy = new BasicCredentialsProvider();
            }
        }

        return new InternalHttpAsyncClient(
                ioReactor,
                execChain,
                pushConsumerRegistry,
                threadFactory != null ? threadFactory : new DefaultThreadFactory("httpclient-main", true),
                connManagerCopy,
                routePlannerCopy,
                tlsConfig,
                cookieSpecRegistryCopy,
                authSchemeRegistryCopy,
                cookieStoreCopy,
                credentialsProviderCopy,
                defaultRequestConfig,
                closeablesCopy);
    }

    private String getProperty(final String key, final String defaultValue) {
        return AccessController.doPrivileged((PrivilegedAction<String>) () -> System.getProperty(key, defaultValue));
    }

}
