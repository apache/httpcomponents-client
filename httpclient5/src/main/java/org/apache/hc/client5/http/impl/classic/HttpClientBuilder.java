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

import java.io.Closeable;
import java.io.IOException;
import java.net.ProxySelector;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.hc.client5.http.AuthenticationStrategy;
import org.apache.hc.client5.http.ConnectionKeepAliveStrategy;
import org.apache.hc.client5.http.HttpRequestRetryStrategy;
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.UserTokenHandler;
import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.classic.BackoffManager;
import org.apache.hc.client5.http.classic.ConnectionBackoffStrategy;
import org.apache.hc.client5.http.classic.ExecChainHandler;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.CookieSpecFactory;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.entity.InputStreamFactory;
import org.apache.hc.client5.http.impl.ChainElement;
import org.apache.hc.client5.http.impl.CookieSpecSupport;
import org.apache.hc.client5.http.impl.DefaultAuthenticationStrategy;
import org.apache.hc.client5.http.impl.DefaultConnectionKeepAliveStrategy;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.impl.DefaultUserTokenHandler;
import org.apache.hc.client5.http.impl.IdleConnectionEvictor;
import org.apache.hc.client5.http.impl.NoopUserTokenHandler;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.auth.BasicSchemeFactory;
import org.apache.hc.client5.http.impl.auth.DigestSchemeFactory;
import org.apache.hc.client5.http.impl.auth.KerberosSchemeFactory;
import org.apache.hc.client5.http.impl.auth.NTLMSchemeFactory;
import org.apache.hc.client5.http.impl.auth.SPNegoSchemeFactory;
import org.apache.hc.client5.http.impl.auth.SystemDefaultCredentialsProvider;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.routing.DefaultProxyRoutePlanner;
import org.apache.hc.client5.http.impl.routing.DefaultRoutePlanner;
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.protocol.RedirectStrategy;
import org.apache.hc.client5.http.protocol.RequestAddCookies;
import org.apache.hc.client5.http.protocol.RequestAuthCache;
import org.apache.hc.client5.http.protocol.RequestClientConnControl;
import org.apache.hc.client5.http.protocol.RequestDefaultHeaders;
import org.apache.hc.client5.http.protocol.RequestExpectContinue;
import org.apache.hc.client5.http.protocol.ResponseProcessCookies;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.config.NamedElementChain;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.http.protocol.DefaultHttpProcessor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.HttpProcessorBuilder;
import org.apache.hc.core5.http.protocol.RequestContent;
import org.apache.hc.core5.http.protocol.RequestTargetHost;
import org.apache.hc.core5.http.protocol.RequestUserAgent;
import org.apache.hc.core5.pool.ConnPoolControl;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.apache.hc.core5.util.VersionInfo;

/**
 * Builder for {@link CloseableHttpClient} instances.
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
 * exclusive and may not apply when building {@link CloseableHttpClient}
 * instances.
 * </p>
 *
 * @since 4.3
 */
public class HttpClientBuilder {

    private static class RequestInterceptorEntry {

        enum Postion { FIRST, LAST }

        final Postion postion;
        final HttpRequestInterceptor interceptor;

        private RequestInterceptorEntry(final Postion postion, final HttpRequestInterceptor interceptor) {
            this.postion = postion;
            this.interceptor = interceptor;
        }
    }

    private static class ResponseInterceptorEntry {

        enum Postion { FIRST, LAST }

        final Postion postion;
        final HttpResponseInterceptor interceptor;

        private ResponseInterceptorEntry(final Postion postion, final HttpResponseInterceptor interceptor) {
            this.postion = postion;
            this.interceptor = interceptor;
        }
    }

    private static class ExecInterceptorEntry {

        enum Postion { BEFORE, AFTER, REPLACE, FIRST, LAST }

        final Postion postion;
        final String name;
        final ExecChainHandler interceptor;
        final String existing;

        private ExecInterceptorEntry(
                final Postion postion,
                final String name,
                final ExecChainHandler interceptor,
                final String existing) {
            this.postion = postion;
            this.name = name;
            this.interceptor = interceptor;
            this.existing = existing;
        }

    }

    private HttpRequestExecutor requestExec;
    private HttpClientConnectionManager connManager;
    private boolean connManagerShared;
    private SchemePortResolver schemePortResolver;
    private ConnectionReuseStrategy reuseStrategy;
    private ConnectionKeepAliveStrategy keepAliveStrategy;
    private AuthenticationStrategy targetAuthStrategy;
    private AuthenticationStrategy proxyAuthStrategy;
    private UserTokenHandler userTokenHandler;

    private LinkedList<RequestInterceptorEntry> requestInterceptors;
    private LinkedList<ResponseInterceptorEntry> responseInterceptors;
    private LinkedList<ExecInterceptorEntry> execInterceptors;

    private HttpRequestRetryStrategy retryStrategy;
    private HttpRoutePlanner routePlanner;
    private RedirectStrategy redirectStrategy;
    private ConnectionBackoffStrategy connectionBackoffStrategy;
    private BackoffManager backoffManager;
    private Lookup<AuthSchemeFactory> authSchemeRegistry;
    private Lookup<CookieSpecFactory> cookieSpecRegistry;
    private LinkedHashMap<String, InputStreamFactory> contentDecoderMap;
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
    private boolean redirectHandlingDisabled;
    private boolean automaticRetriesDisabled;
    private boolean contentCompressionDisabled;
    private boolean cookieManagementDisabled;
    private boolean authCachingDisabled;
    private boolean connectionStateDisabled;
    private boolean defaultUserAgentDisabled;

    private List<Closeable> closeables;

    public static HttpClientBuilder create() {
        return new HttpClientBuilder();
    }

    protected HttpClientBuilder() {
        super();
    }

    /**
     * Assigns {@link HttpRequestExecutor} instance.
     */
    public final HttpClientBuilder setRequestExecutor(final HttpRequestExecutor requestExec) {
        this.requestExec = requestExec;
        return this;
    }

    /**
     * Assigns {@link HttpClientConnectionManager} instance.
     */
    public final HttpClientBuilder setConnectionManager(
            final HttpClientConnectionManager connManager) {
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
     * </p>
     *
     * @param shared defines whether or not the connection manager can be shared
     *  by multiple clients.
     *
     * @since 4.4
     */
    public final HttpClientBuilder setConnectionManagerShared(
            final boolean shared) {
        this.connManagerShared = shared;
        return this;
    }

    /**
     * Assigns {@link ConnectionReuseStrategy} instance.
     */
    public final HttpClientBuilder setConnectionReuseStrategy(
            final ConnectionReuseStrategy reuseStrategy) {
        this.reuseStrategy = reuseStrategy;
        return this;
    }

    /**
     * Assigns {@link ConnectionKeepAliveStrategy} instance.
     */
    public final HttpClientBuilder setKeepAliveStrategy(
            final ConnectionKeepAliveStrategy keepAliveStrategy) {
        this.keepAliveStrategy = keepAliveStrategy;
        return this;
    }

    /**
     * Assigns {@link AuthenticationStrategy} instance for target
     * host authentication.
     */
    public final HttpClientBuilder setTargetAuthenticationStrategy(
            final AuthenticationStrategy targetAuthStrategy) {
        this.targetAuthStrategy = targetAuthStrategy;
        return this;
    }

    /**
     * Assigns {@link AuthenticationStrategy} instance for proxy
     * authentication.
     */
    public final HttpClientBuilder setProxyAuthenticationStrategy(
            final AuthenticationStrategy proxyAuthStrategy) {
        this.proxyAuthStrategy = proxyAuthStrategy;
        return this;
    }

    /**
     * Assigns {@link UserTokenHandler} instance.
     * <p>
     * Please note this value can be overridden by the {@link #disableConnectionState()}
     * method.
     * </p>
     */
    public final HttpClientBuilder setUserTokenHandler(final UserTokenHandler userTokenHandler) {
        this.userTokenHandler = userTokenHandler;
        return this;
    }

    /**
     * Disables connection state tracking.
     */
    public final HttpClientBuilder disableConnectionState() {
        connectionStateDisabled = true;
        return this;
    }

    /**
     * Assigns {@link SchemePortResolver} instance.
     */
    public final HttpClientBuilder setSchemePortResolver(
            final SchemePortResolver schemePortResolver) {
        this.schemePortResolver = schemePortResolver;
        return this;
    }

    /**
     * Assigns {@code User-Agent} value.
     */
    public final HttpClientBuilder setUserAgent(final String userAgent) {
        this.userAgent = userAgent;
        return this;
    }

    /**
     * Assigns default request header values.
     */
    public final HttpClientBuilder setDefaultHeaders(final Collection<? extends Header> defaultHeaders) {
        this.defaultHeaders = defaultHeaders;
        return this;
    }

    /**
     * Adds this protocol interceptor to the head of the protocol processing list.
     */
    public final HttpClientBuilder addResponseInterceptorFirst(final HttpResponseInterceptor interceptor) {
        Args.notNull(interceptor, "Interceptor");
        if (responseInterceptors == null) {
            responseInterceptors = new LinkedList<>();
        }
        responseInterceptors.add(new ResponseInterceptorEntry(ResponseInterceptorEntry.Postion.FIRST, interceptor));
        return this;
    }

    /**
     * Adds this protocol interceptor to the tail of the protocol processing list.
     */
    public final HttpClientBuilder addResponseInterceptorLast(final HttpResponseInterceptor interceptor) {
        Args.notNull(interceptor, "Interceptor");
        if (responseInterceptors == null) {
            responseInterceptors = new LinkedList<>();
        }
        responseInterceptors.add(new ResponseInterceptorEntry(ResponseInterceptorEntry.Postion.LAST, interceptor));
        return this;
    }

    /**
     * Adds this protocol interceptor to the head of the protocol processing list.
     */
    public final HttpClientBuilder addRequestInterceptorFirst(final HttpRequestInterceptor interceptor) {
        Args.notNull(interceptor, "Interceptor");
        if (requestInterceptors == null) {
            requestInterceptors = new LinkedList<>();
        }
        requestInterceptors.add(new RequestInterceptorEntry(RequestInterceptorEntry.Postion.FIRST, interceptor));
        return this;
    }

    /**
     * Adds this protocol interceptor to the tail of the protocol processing list.
     */
    public final HttpClientBuilder addRequestInterceptorLast(final HttpRequestInterceptor interceptor) {
        Args.notNull(interceptor, "Interceptor");
        if (requestInterceptors == null) {
            requestInterceptors = new LinkedList<>();
        }
        requestInterceptors.add(new RequestInterceptorEntry(RequestInterceptorEntry.Postion.LAST, interceptor));
        return this;
    }

    /**
     * Adds this execution interceptor before an existing interceptor.
     */
    public final HttpClientBuilder addExecInterceptorBefore(final String existing, final String name, final ExecChainHandler interceptor) {
        Args.notBlank(existing, "Existing");
        Args.notBlank(name, "Name");
        Args.notNull(interceptor, "Interceptor");
        if (execInterceptors == null) {
            execInterceptors = new LinkedList<>();
        }
        execInterceptors.add(new ExecInterceptorEntry(ExecInterceptorEntry.Postion.BEFORE, name, interceptor, existing));
        return this;
    }

    /**
     * Adds this execution interceptor after interceptor with the given name.
     */
    public final HttpClientBuilder addExecInterceptorAfter(final String existing, final String name, final ExecChainHandler interceptor) {
        Args.notBlank(existing, "Existing");
        Args.notBlank(name, "Name");
        Args.notNull(interceptor, "Interceptor");
        if (execInterceptors == null) {
            execInterceptors = new LinkedList<>();
        }
        execInterceptors.add(new ExecInterceptorEntry(ExecInterceptorEntry.Postion.AFTER, name, interceptor, existing));
        return this;
    }

    /**
     * Replace an existing interceptor with the given name with new interceptor.
     */
    public final HttpClientBuilder replaceExecInterceptor(final String existing, final ExecChainHandler interceptor) {
        Args.notBlank(existing, "Existing");
        Args.notNull(interceptor, "Interceptor");
        if (execInterceptors == null) {
            execInterceptors = new LinkedList<>();
        }
        execInterceptors.add(new ExecInterceptorEntry(ExecInterceptorEntry.Postion.REPLACE, existing, interceptor, existing));
        return this;
    }

    /**
     * Add an interceptor to the head of the processing list.
     */
    public final HttpClientBuilder addExecInterceptorFirst(final String name, final ExecChainHandler interceptor) {
        Args.notNull(name, "Name");
        Args.notNull(interceptor, "Interceptor");
        execInterceptors.add(new ExecInterceptorEntry(ExecInterceptorEntry.Postion.FIRST, name, interceptor, null));
        return this;
    }

    /**
     * Add an interceptor to the tail of the processing list.
     */
    public final HttpClientBuilder addExecInterceptorLast(final String name, final ExecChainHandler interceptor) {
        Args.notNull(name, "Name");
        Args.notNull(interceptor, "Interceptor");
        execInterceptors.add(new ExecInterceptorEntry(ExecInterceptorEntry.Postion.LAST, name, interceptor, null));
        return this;
    }

    /**
     * Disables state (cookie) management.
     */
    public final HttpClientBuilder disableCookieManagement() {
        this.cookieManagementDisabled = true;
        return this;
    }

    /**
     * Disables automatic content decompression.
     */
    public final HttpClientBuilder disableContentCompression() {
        contentCompressionDisabled = true;
        return this;
    }

    /**
     * Disables authentication scheme caching.
     */
    public final HttpClientBuilder disableAuthCaching() {
        this.authCachingDisabled = true;
        return this;
    }

    /**
     * Assigns {@link HttpRequestRetryStrategy} instance.
     * <p>
     * Please note this value can be overridden by the {@link #disableAutomaticRetries()}
     * method.
     */
    public final HttpClientBuilder setRetryStrategy(final HttpRequestRetryStrategy retryStrategy) {
        this.retryStrategy = retryStrategy;
        return this;
    }

    /**
     * Disables automatic request recovery and re-execution.
     */
    public final HttpClientBuilder disableAutomaticRetries() {
        automaticRetriesDisabled = true;
        return this;
    }

    /**
     * Assigns default proxy value.
     * <p>
     * Please note this value can be overridden by the {@link #setRoutePlanner(
     *   org.apache.hc.client5.http.routing.HttpRoutePlanner)} method.
     */
    public final HttpClientBuilder setProxy(final HttpHost proxy) {
        this.proxy = proxy;
        return this;
    }

    /**
     * Assigns {@link HttpRoutePlanner} instance.
     */
    public final HttpClientBuilder setRoutePlanner(final HttpRoutePlanner routePlanner) {
        this.routePlanner = routePlanner;
        return this;
    }

    /**
     * Assigns {@link RedirectStrategy} instance.
     * <p>
     * Please note this value can be overridden by the {@link #disableRedirectHandling()}
     * method.
     * </p>
`     */
    public final HttpClientBuilder setRedirectStrategy(final RedirectStrategy redirectStrategy) {
        this.redirectStrategy = redirectStrategy;
        return this;
    }

    /**
     * Disables automatic redirect handling.
     */
    public final HttpClientBuilder disableRedirectHandling() {
        redirectHandlingDisabled = true;
        return this;
    }

    /**
     * Assigns {@link ConnectionBackoffStrategy} instance.
     */
    public final HttpClientBuilder setConnectionBackoffStrategy(
            final ConnectionBackoffStrategy connectionBackoffStrategy) {
        this.connectionBackoffStrategy = connectionBackoffStrategy;
        return this;
    }

    /**
     * Assigns {@link BackoffManager} instance.
     */
    public final HttpClientBuilder setBackoffManager(final BackoffManager backoffManager) {
        this.backoffManager = backoffManager;
        return this;
    }

    /**
     * Assigns default {@link CookieStore} instance which will be used for
     * request execution if not explicitly set in the client execution context.
     */
    public final HttpClientBuilder setDefaultCookieStore(final CookieStore cookieStore) {
        this.cookieStore = cookieStore;
        return this;
    }

    /**
     * Assigns default {@link CredentialsProvider} instance which will be used
     * for request execution if not explicitly set in the client execution
     * context.
     */
    public final HttpClientBuilder setDefaultCredentialsProvider(
            final CredentialsProvider credentialsProvider) {
        this.credentialsProvider = credentialsProvider;
        return this;
    }

    /**
     * Assigns default {@link org.apache.hc.client5.http.auth.AuthScheme} registry which will
     * be used for request execution if not explicitly set in the client execution
     * context.
     */
    public final HttpClientBuilder setDefaultAuthSchemeRegistry(
            final Lookup<AuthSchemeFactory> authSchemeRegistry) {
        this.authSchemeRegistry = authSchemeRegistry;
        return this;
    }

    /**
     * Assigns default {@link org.apache.hc.client5.http.cookie.CookieSpec} registry which will
     * be used for request execution if not explicitly set in the client execution
     * context.
     *
     * @see CookieSpecSupport
     *
     */
    public final HttpClientBuilder setDefaultCookieSpecRegistry(
            final Lookup<CookieSpecFactory> cookieSpecRegistry) {
        this.cookieSpecRegistry = cookieSpecRegistry;
        return this;
    }


    /**
     * Assigns a map of {@link org.apache.hc.client5.http.entity.InputStreamFactory}s
     * to be used for automatic content decompression.
     */
    public final HttpClientBuilder setContentDecoderRegistry(
            final LinkedHashMap<String, InputStreamFactory> contentDecoderMap) {
        this.contentDecoderMap = contentDecoderMap;
        return this;
    }

    /**
     * Assigns default {@link RequestConfig} instance which will be used
     * for request execution if not explicitly set in the client execution
     * context.
     */
    public final HttpClientBuilder setDefaultRequestConfig(final RequestConfig config) {
        this.defaultRequestConfig = config;
        return this;
    }

    /**
     * Use system properties when creating and configuring default
     * implementations.
     */
    public final HttpClientBuilder useSystemProperties() {
        this.systemProperties = true;
        return this;
    }

    /**
     * Makes this instance of HttpClient proactively evict expired connections from the
     * connection pool using a background thread.
     * <p>
     * One MUST explicitly close HttpClient with {@link CloseableHttpClient#close()} in order
     * to stop and release the background thread.
     * <p>
     * Please note this method has no effect if the instance of HttpClient is configured to
     * use a shared connection manager.
     *
     * @see #setConnectionManagerShared(boolean)
     * @see ConnPoolControl#closeExpired()
     *
     * @since 4.4
     */
    public final HttpClientBuilder evictExpiredConnections() {
        evictExpiredConnections = true;
        return this;
    }

    /**
     * Makes this instance of HttpClient proactively evict idle connections from the
     * connection pool using a background thread.
     * <p>
     * One MUST explicitly close HttpClient with {@link CloseableHttpClient#close()} in order
     * to stop and release the background thread.
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
     *
     * @since 4.4
     */
    public final HttpClientBuilder evictIdleConnections(final TimeValue maxIdleTime) {
        this.evictIdleConnections = true;
        this.maxIdleTime = maxIdleTime;
        return this;
    }

    /**
     * Disables the default user agent set by this builder if none has been provided by the user.
     *
     * @since 4.5.7
     */
    public final HttpClientBuilder disableDefaultUserAgent() {
        this.defaultUserAgentDisabled = true;
        return this;
    }

    /**
     * Request exec chain customization and extension.
     * <p>
     * For internal use.
     */
    @Internal
    protected void customizeExecChain(final NamedElementChain<ExecChainHandler> execChainDefinition) {
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

    public CloseableHttpClient build() {
        // Create main request executor
        // We copy the instance fields to avoid changing them, and rename to avoid accidental use of the wrong version
        HttpRequestExecutor requestExecCopy = this.requestExec;
        if (requestExecCopy == null) {
            requestExecCopy = new HttpRequestExecutor();
        }
        HttpClientConnectionManager connManagerCopy = this.connManager;
        if (connManagerCopy == null) {
            connManagerCopy = PoolingHttpClientConnectionManagerBuilder.create().build();
        }
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

        ConnectionKeepAliveStrategy keepAliveStrategyCopy = this.keepAliveStrategy;
        if (keepAliveStrategyCopy == null) {
            keepAliveStrategyCopy = DefaultConnectionKeepAliveStrategy.INSTANCE;
        }
        AuthenticationStrategy targetAuthStrategyCopy = this.targetAuthStrategy;
        if (targetAuthStrategyCopy == null) {
            targetAuthStrategyCopy = DefaultAuthenticationStrategy.INSTANCE;
        }
        AuthenticationStrategy proxyAuthStrategyCopy = this.proxyAuthStrategy;
        if (proxyAuthStrategyCopy == null) {
            proxyAuthStrategyCopy = DefaultAuthenticationStrategy.INSTANCE;
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
            if (userAgentCopy == null && !defaultUserAgentDisabled) {
                userAgentCopy = VersionInfo.getSoftwareInfo("Apache-HttpClient",
                        "org.apache.hc.client5", getClass());
            }
        }

        final NamedElementChain<ExecChainHandler> execChainDefinition = new NamedElementChain<>();
        execChainDefinition.addLast(
                new MainClientExec(connManagerCopy, reuseStrategyCopy, keepAliveStrategyCopy, userTokenHandlerCopy),
                ChainElement.MAIN_TRANSPORT.name());
        execChainDefinition.addFirst(
                new ConnectExec(
                        reuseStrategyCopy,
                        new DefaultHttpProcessor(new RequestTargetHost(), new RequestUserAgent(userAgentCopy)),
                        proxyAuthStrategyCopy),
                ChainElement.CONNECT.name());

        final HttpProcessorBuilder b = HttpProcessorBuilder.create();
        if (requestInterceptors != null) {
            for (final RequestInterceptorEntry entry: requestInterceptors) {
                if (entry.postion == RequestInterceptorEntry.Postion.FIRST) {
                    b.addFirst(entry.interceptor);
                }
            }
        }
        if (responseInterceptors != null) {
            for (final ResponseInterceptorEntry entry: responseInterceptors) {
                if (entry.postion == ResponseInterceptorEntry.Postion.FIRST) {
                    b.addFirst(entry.interceptor);
                }
            }
        }
        b.addAll(
                new RequestDefaultHeaders(defaultHeaders),
                new RequestContent(),
                new RequestTargetHost(),
                new RequestClientConnControl(),
                new RequestUserAgent(userAgentCopy),
                new RequestExpectContinue());
        if (!cookieManagementDisabled) {
            b.add(new RequestAddCookies());
        }
        if (!authCachingDisabled) {
            b.add(new RequestAuthCache());
        }
        if (!cookieManagementDisabled) {
            b.add(new ResponseProcessCookies());
        }
        if (requestInterceptors != null) {
            for (final RequestInterceptorEntry entry: requestInterceptors) {
                if (entry.postion == RequestInterceptorEntry.Postion.LAST) {
                    b.addFirst(entry.interceptor);
                }
            }
        }
        if (responseInterceptors != null) {
            for (final ResponseInterceptorEntry entry: responseInterceptors) {
                if (entry.postion == ResponseInterceptorEntry.Postion.LAST) {
                    b.addFirst(entry.interceptor);
                }
            }
        }
        final HttpProcessor httpProcessor = b.build();
        execChainDefinition.addFirst(
                new ProtocolExec(httpProcessor, targetAuthStrategyCopy, proxyAuthStrategyCopy),
                ChainElement.PROTOCOL.name());

        // Add request retry executor, if not disabled
        if (!automaticRetriesDisabled) {
            HttpRequestRetryStrategy retryStrategyCopy = this.retryStrategy;
            if (retryStrategyCopy == null) {
                retryStrategyCopy = DefaultHttpRequestRetryStrategy.INSTANCE;
            }
            execChainDefinition.addFirst(
                    new HttpRequestRetryExec(retryStrategyCopy),
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
            } else if (systemProperties) {
                routePlannerCopy = new SystemDefaultRoutePlanner(
                        schemePortResolverCopy, ProxySelector.getDefault());
            } else {
                routePlannerCopy = new DefaultRoutePlanner(schemePortResolverCopy);
            }
        }

        if (!contentCompressionDisabled) {
            if (contentDecoderMap != null) {
                final List<String> encodings = new ArrayList<>(contentDecoderMap.keySet());
                final RegistryBuilder<InputStreamFactory> b2 = RegistryBuilder.create();
                for (final Map.Entry<String, InputStreamFactory> entry: contentDecoderMap.entrySet()) {
                    b2.register(entry.getKey(), entry.getValue());
                }
                final Registry<InputStreamFactory> decoderRegistry = b2.build();
                execChainDefinition.addFirst(
                        new ContentCompressionExec(encodings, decoderRegistry, true),
                        ChainElement.COMPRESS.name());
            } else {
                execChainDefinition.addFirst(new ContentCompressionExec(true), ChainElement.COMPRESS.name());
            }
        }

        // Add redirect executor, if not disabled
        if (!redirectHandlingDisabled) {
            RedirectStrategy redirectStrategyCopy = this.redirectStrategy;
            if (redirectStrategyCopy == null) {
                redirectStrategyCopy = DefaultRedirectStrategy.INSTANCE;
            }
            execChainDefinition.addFirst(
                    new RedirectExec(routePlannerCopy, redirectStrategyCopy),
                    ChainElement.REDIRECT.name());
        }

        // Optionally, add connection back-off executor
        if (this.backoffManager != null && this.connectionBackoffStrategy != null) {
            execChainDefinition.addFirst(new BackoffStrategyExec(this.connectionBackoffStrategy, this.backoffManager),
                    ChainElement.BACK_OFF.name());
        }

        if (execInterceptors != null) {
            for (final ExecInterceptorEntry entry: execInterceptors) {
                switch (entry.postion) {
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
                        execChainDefinition.addLast(entry.interceptor, entry.name);
                        break;
                }
            }
        }

        customizeExecChain(execChainDefinition);

        NamedElementChain<ExecChainHandler>.Node current = execChainDefinition.getLast();
        ExecChainElement execChain = null;
        while (current != null) {
            execChain = new ExecChainElement(current.getValue(), execChain);
            current = current.getPrevious();
        }

        Lookup<AuthSchemeFactory> authSchemeRegistryCopy = this.authSchemeRegistry;
        if (authSchemeRegistryCopy == null) {
            authSchemeRegistryCopy = RegistryBuilder.<AuthSchemeFactory>create()
                .register(StandardAuthScheme.BASIC, BasicSchemeFactory.INSTANCE)
                .register(StandardAuthScheme.DIGEST, DigestSchemeFactory.INSTANCE)
                .register(StandardAuthScheme.NTLM, NTLMSchemeFactory.INSTANCE)
                .register(StandardAuthScheme.SPNEGO, SPNegoSchemeFactory.DEFAULT)
                .register(StandardAuthScheme.KERBEROS, KerberosSchemeFactory.DEFAULT)
                .build();
        }
        Lookup<CookieSpecFactory> cookieSpecRegistryCopy = this.cookieSpecRegistry;
        if (cookieSpecRegistryCopy == null) {
            cookieSpecRegistryCopy = CookieSpecSupport.createDefault();
        }

        CookieStore defaultCookieStore = this.cookieStore;
        if (defaultCookieStore == null) {
            defaultCookieStore = new BasicCookieStore();
        }

        CredentialsProvider defaultCredentialsProvider = this.credentialsProvider;
        if (defaultCredentialsProvider == null) {
            if (systemProperties) {
                defaultCredentialsProvider = new SystemDefaultCredentialsProvider();
            } else {
                defaultCredentialsProvider = new BasicCredentialsProvider();
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
                            maxIdleTime, maxIdleTime);
                    closeablesCopy.add(new Closeable() {

                        @Override
                        public void close() throws IOException {
                            connectionEvictor.shutdown();
                            try {
                                connectionEvictor.awaitTermination(Timeout.ofSeconds(1));
                            } catch (final InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                            }
                        }

                    });
                    connectionEvictor.start();
                }
            }
            closeablesCopy.add(connManagerCopy);
        }

        return new InternalHttpClient(
                connManagerCopy,
                requestExecCopy,
                execChain,
                routePlannerCopy,
                cookieSpecRegistryCopy,
                authSchemeRegistryCopy,
                defaultCookieStore,
                defaultCredentialsProvider,
                defaultRequestConfig != null ? defaultRequestConfig : RequestConfig.DEFAULT,
                closeablesCopy);
    }

}
