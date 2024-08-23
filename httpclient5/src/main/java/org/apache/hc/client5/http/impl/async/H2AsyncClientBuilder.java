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
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ThreadFactory;

import org.apache.hc.client5.http.AuthenticationStrategy;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.HttpRequestRetryStrategy;
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.CookieSpecFactory;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.ChainElement;
import org.apache.hc.client5.http.impl.CookieSpecSupport;
import org.apache.hc.client5.http.impl.DefaultAuthenticationStrategy;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.auth.BasicSchemeFactory;
import org.apache.hc.client5.http.impl.auth.BearerSchemeFactory;
import org.apache.hc.client5.http.impl.auth.DigestSchemeFactory;
import org.apache.hc.client5.http.impl.auth.SystemDefaultCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.MultihomeConnectionInitiator;
import org.apache.hc.client5.http.impl.routing.DefaultRoutePlanner;
import org.apache.hc.client5.http.protocol.RedirectStrategy;
import org.apache.hc.client5.http.protocol.RequestAddCookies;
import org.apache.hc.client5.http.protocol.RequestDefaultHeaders;
import org.apache.hc.client5.http.protocol.RequestExpectContinue;
import org.apache.hc.client5.http.protocol.ResponseProcessCookies;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.concurrent.DefaultThreadFactory;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.function.Resolver;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.config.NamedElementChain;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.nio.command.ShutdownCommand;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.protocol.DefaultHttpProcessor;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.HttpProcessorBuilder;
import org.apache.hc.core5.http.protocol.RequestTargetHost;
import org.apache.hc.core5.http.protocol.RequestUserAgent;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.protocol.H2RequestConnControl;
import org.apache.hc.core5.http2.protocol.H2RequestContent;
import org.apache.hc.core5.http2.protocol.H2RequestTargetHost;
import org.apache.hc.core5.io.CloseMode;
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
 * Builder for HTTP/2 only {@link CloseableHttpAsyncClient} instances.
 * <p>
 * Concurrent message exchanges with the same connection route executed
 * with these {@link CloseableHttpAsyncClient} instances will get
 * automatically multiplexed over a single physical HTTP/2 connection.
 * </p>
 * <p>
 * When a particular component is not explicitly set this class will
 * use its default implementation.
 * <p>
 *
 * @since 5.0
 */
public class H2AsyncClientBuilder {

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

    private IOReactorConfig ioReactorConfig;
    private IOSessionListener ioSessionListener;
    private H2Config h2Config;
    private CharCodingConfig charCodingConfig;
    private SchemePortResolver schemePortResolver;
    private AuthenticationStrategy targetAuthStrategy;
    private AuthenticationStrategy proxyAuthStrategy;

    private LinkedList<RequestInterceptorEntry> requestInterceptors;
    private LinkedList<ResponseInterceptorEntry> responseInterceptors;
    private LinkedList<ExecInterceptorEntry> execInterceptors;

    private HttpRoutePlanner routePlanner;
    private RedirectStrategy redirectStrategy;
    private HttpRequestRetryStrategy retryStrategy;

    private Lookup<AuthSchemeFactory> authSchemeRegistry;
    private Lookup<CookieSpecFactory> cookieSpecRegistry;
    private CookieStore cookieStore;
    private CredentialsProvider credentialsProvider;

    private String userAgent;
    private Collection<? extends Header> defaultHeaders;
    private RequestConfig defaultRequestConfig;
    private Resolver<HttpHost, ConnectionConfig> connectionConfigResolver;
    private boolean evictIdleConnections;
    private TimeValue maxIdleTime;

    private boolean systemProperties;
    private boolean automaticRetriesDisabled;
    private boolean redirectHandlingDisabled;
    private boolean cookieManagementDisabled;
    private boolean authCachingDisabled;

    private DnsResolver dnsResolver;
    private TlsStrategy tlsStrategy;

    private ThreadFactory threadFactory;

    private List<Closeable> closeables;


    private Callback<Exception> ioReactorExceptionCallback;

    private Decorator<IOSession> ioSessionDecorator;

    public static H2AsyncClientBuilder create() {
        return new H2AsyncClientBuilder();
    }

    protected H2AsyncClientBuilder() {
        super();
    }

    /**
     * Sets {@link H2Config} configuration.
     *
     * @return this instance.
     */
    public final H2AsyncClientBuilder setH2Config(final H2Config h2Config) {
        this.h2Config = h2Config;
        return this;
    }

    /**
     * Sets {@link IOReactorConfig} configuration.
     *
     * @return this instance.
     */
    public final H2AsyncClientBuilder setIOReactorConfig(final IOReactorConfig ioReactorConfig) {
        this.ioReactorConfig = ioReactorConfig;
        return this;
    }

    /**
     * Sets {@link IOSessionListener} listener.
     *
     * @return this instance.
     * @since 5.2
     */
    public final H2AsyncClientBuilder setIOSessionListener(final IOSessionListener ioSessionListener) {
        this.ioSessionListener = ioSessionListener;
        return this;
    }

    /**
     * Sets {@link CharCodingConfig} configuration.
     *
     * @return this instance.
     */
    public final H2AsyncClientBuilder setCharCodingConfig(final CharCodingConfig charCodingConfig) {
        this.charCodingConfig = charCodingConfig;
        return this;
    }

    /**
     * Sets {@link AuthenticationStrategy} instance for target
     * host authentication.
     *
     * @return this instance.
     */
    public final H2AsyncClientBuilder setTargetAuthenticationStrategy(
            final AuthenticationStrategy targetAuthStrategy) {
        this.targetAuthStrategy = targetAuthStrategy;
        return this;
    }

    /**
     * Sets {@link AuthenticationStrategy} instance for proxy
     * authentication.
     *
     * @return this instance.
     */
    public final H2AsyncClientBuilder setProxyAuthenticationStrategy(
            final AuthenticationStrategy proxyAuthStrategy) {
        this.proxyAuthStrategy = proxyAuthStrategy;
        return this;
    }

    /**
     * Sets the callback that will be invoked when the client's IOReactor encounters an uncaught exception.
     *
     * @return this instance.
     * @since 5.2
     */
    public final H2AsyncClientBuilder setIoReactorExceptionCallback(final Callback<Exception> ioReactorExceptionCallback) {
        this.ioReactorExceptionCallback = ioReactorExceptionCallback;
        return this;
    }


    /**
     * Sets the {@link IOSession} {@link Decorator} that will be use with the client's IOReactor.
     *
     * @return this instance.
     * @since 5.2
     */
    public final H2AsyncClientBuilder setIoSessionDecorator(final Decorator<IOSession> ioSessionDecorator) {
        this.ioSessionDecorator = ioSessionDecorator;
        return this;
    }

    /**
     * Adds this protocol interceptor to the head of the protocol processing list.
     *
     * @return this instance.
     */
    public final H2AsyncClientBuilder addResponseInterceptorFirst(final HttpResponseInterceptor interceptor) {
        Args.notNull(interceptor, "Interceptor");
        if (responseInterceptors == null) {
            responseInterceptors = new LinkedList<>();
        }
        responseInterceptors.add(new ResponseInterceptorEntry(ResponseInterceptorEntry.Position.FIRST, interceptor));
        return this;
    }

    /**
     * Adds this protocol interceptor to the tail of the protocol processing list.
     *
     * @return this instance.
     */
    public final H2AsyncClientBuilder addResponseInterceptorLast(final HttpResponseInterceptor interceptor) {
        Args.notNull(interceptor, "Interceptor");
        if (responseInterceptors == null) {
            responseInterceptors = new LinkedList<>();
        }
        responseInterceptors.add(new ResponseInterceptorEntry(ResponseInterceptorEntry.Position.LAST, interceptor));
        return this;
    }

    /**
     * Adds this execution interceptor before an existing interceptor.
     *
     * @return this instance.
     */
    public final H2AsyncClientBuilder addExecInterceptorBefore(final String existing, final String name, final AsyncExecChainHandler interceptor) {
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
     *
     * @return this instance.
     */
    public final H2AsyncClientBuilder addExecInterceptorAfter(final String existing, final String name, final AsyncExecChainHandler interceptor) {
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
     *
     * @return this instance.
     */
    public final H2AsyncClientBuilder replaceExecInterceptor(final String existing, final AsyncExecChainHandler interceptor) {
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
     *
     * @return this instance.
     */
    public final H2AsyncClientBuilder addExecInterceptorFirst(final String name, final AsyncExecChainHandler interceptor) {
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
     *
     * @return this instance.
     */
    public final H2AsyncClientBuilder addExecInterceptorLast(final String name, final AsyncExecChainHandler interceptor) {
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
     *
     * @return this instance.
     */
    public final H2AsyncClientBuilder addRequestInterceptorFirst(final HttpRequestInterceptor interceptor) {
        Args.notNull(interceptor, "Interceptor");
        if (requestInterceptors == null) {
            requestInterceptors = new LinkedList<>();
        }
        requestInterceptors.add(new RequestInterceptorEntry(RequestInterceptorEntry.Position.FIRST, interceptor));
        return this;
    }

    /**
     * Adds this protocol interceptor to the tail of the protocol processing list.
     *
     * @return this instance.
     */
    public final H2AsyncClientBuilder addRequestInterceptorLast(final HttpRequestInterceptor interceptor) {
        Args.notNull(interceptor, "Interceptor");
        if (requestInterceptors == null) {
            requestInterceptors = new LinkedList<>();
        }
        requestInterceptors.add(new RequestInterceptorEntry(RequestInterceptorEntry.Position.LAST, interceptor));
        return this;
    }

    /**
     * Sets {@link HttpRequestRetryStrategy} instance.
     * <p>
     * Please note this value can be overridden by the {@link #disableAutomaticRetries()}
     * method.
     * </p>
     *
     * @return this instance.
     */
    public final H2AsyncClientBuilder setRetryStrategy(final HttpRequestRetryStrategy retryStrategy) {
        this.retryStrategy = retryStrategy;
        return this;
    }

    /**
     * Sets {@link RedirectStrategy} instance.
     * <p>
     * Please note this value can be overridden by the {@link #disableRedirectHandling()}
     * method.
     * </p>
     *
     * @return this instance.
     */
    public H2AsyncClientBuilder setRedirectStrategy(final RedirectStrategy redirectStrategy) {
        this.redirectStrategy = redirectStrategy;
        return this;
    }

    /**
     * Sets {@link SchemePortResolver} instance.
     *
     * @return this instance.
     */
    public final H2AsyncClientBuilder setSchemePortResolver(final SchemePortResolver schemePortResolver) {
        this.schemePortResolver = schemePortResolver;
        return this;
    }

    /**
     * Sets {@link DnsResolver} instance.
     *
     * @return this instance.
     */
    public final H2AsyncClientBuilder setDnsResolver(final DnsResolver dnsResolver) {
        this.dnsResolver = dnsResolver;
        return this;
    }

    /**
     * Sets {@link TlsStrategy} instance.
     *
     * @return this instance.
     */
    public final H2AsyncClientBuilder setTlsStrategy(final TlsStrategy tlsStrategy) {
        this.tlsStrategy = tlsStrategy;
        return this;
    }

    /**
     * Sets {@link ThreadFactory} instance.
     *
     * @return this instance.
     */
    public final H2AsyncClientBuilder setThreadFactory(final ThreadFactory threadFactory) {
        this.threadFactory = threadFactory;
        return this;
    }

    /**
     * Sets {@code User-Agent} value.
     *
     * @return this instance.
     */
    public final H2AsyncClientBuilder setUserAgent(final String userAgent) {
        this.userAgent = userAgent;
        return this;
    }

    /**
     * Sets default request header values.
     *
     * @return this instance.
     */
    public final H2AsyncClientBuilder setDefaultHeaders(final Collection<? extends Header> defaultHeaders) {
        this.defaultHeaders = defaultHeaders;
        return this;
    }

    /**
     * Sets {@link HttpRoutePlanner} instance.
     *
     * @return this instance.
     */
    public final H2AsyncClientBuilder setRoutePlanner(final HttpRoutePlanner routePlanner) {
        this.routePlanner = routePlanner;
        return this;
    }

    /**
     * Sets default {@link CredentialsProvider} instance which will be used
     * for request execution if not explicitly set in the client execution
     * context.
     *
     * @return this instance.
     */
    public final H2AsyncClientBuilder setDefaultCredentialsProvider(final CredentialsProvider credentialsProvider) {
        this.credentialsProvider = credentialsProvider;
        return this;
    }

    /**
     * Sets default {@link org.apache.hc.client5.http.auth.AuthScheme} registry which will
     * be used for request execution if not explicitly set in the client execution
     * context.
     *
     * @return this instance.
     */
    public final H2AsyncClientBuilder setDefaultAuthSchemeRegistry(final Lookup<AuthSchemeFactory> authSchemeRegistry) {
        this.authSchemeRegistry = authSchemeRegistry;
        return this;
    }

    /**
     * Sets default {@link org.apache.hc.client5.http.cookie.CookieSpec} registry
     * which will be used for request execution if not explicitly set in the client
     * execution context.
     *
     * @return this instance.
     */
    public final H2AsyncClientBuilder setDefaultCookieSpecRegistry(final Lookup<CookieSpecFactory> cookieSpecRegistry) {
        this.cookieSpecRegistry = cookieSpecRegistry;
        return this;
    }

    /**
     * Sets default {@link CookieStore} instance which will be used for
     * request execution if not explicitly set in the client execution context.
     *
     * @return this instance.
     */
    public final H2AsyncClientBuilder setDefaultCookieStore(final CookieStore cookieStore) {
        this.cookieStore = cookieStore;
        return this;
    }

    /**
     * Sets default {@link RequestConfig} instance which will be used
     * for request execution if not explicitly set in the client execution
     * context.
     *
     * @return this instance.
     */
    public final H2AsyncClientBuilder setDefaultRequestConfig(final RequestConfig config) {
        this.defaultRequestConfig = config;
        return this;
    }

    /**
     * Sets {@link Resolver} for {@link ConnectionConfig} on a per host basis.
     *
     * @return this instance.
     * @since 5.2
     */
    public final H2AsyncClientBuilder setConnectionConfigResolver(final Resolver<HttpHost, ConnectionConfig> connectionConfigResolver) {
        this.connectionConfigResolver = connectionConfigResolver;
        return this;
    }

    /**
     * Sets the same {@link ConnectionConfig} for all hosts.
     *
     * @return this instance.
     * @since 5.2
     */
    public final H2AsyncClientBuilder setDefaultConnectionConfig(final ConnectionConfig connectionConfig) {
        this.connectionConfigResolver = (host) -> connectionConfig;
        return this;
    }

    /**
     * Use system properties when creating and configuring default
     * implementations.
     *
     * @return this instance.
     */
    public final H2AsyncClientBuilder useSystemProperties() {
        this.systemProperties = true;
        return this;
    }

    /**
     * Disables automatic redirect handling.
     *
     * @return this instance.
     */
    public final H2AsyncClientBuilder disableRedirectHandling() {
        redirectHandlingDisabled = true;
        return this;
    }

    /**
     * Disables automatic request recovery and re-execution.
     *
     * @return this instance.
     */
    public final H2AsyncClientBuilder disableAutomaticRetries() {
        automaticRetriesDisabled = true;
        return this;
    }

    /**
     * Disables state (cookie) management.
     *
     * @return this instance.
     */
    public final H2AsyncClientBuilder disableCookieManagement() {
        this.cookieManagementDisabled = true;
        return this;
    }

    /**
     * Disables authentication scheme caching.
     *
     * @return this instance.
     */
    public final H2AsyncClientBuilder disableAuthCaching() {
        this.authCachingDisabled = true;
        return this;
    }

    /**
     * Makes this instance of HttpClient proactively evict idle connections from the
     * connection pool using a background thread.
     * <p>
     * One MUST explicitly close HttpClient with {@link CloseableHttpAsyncClient#close()}
     * in order to stop and release the background thread.
     * </p>
     * <p>
     * Please note this method has no effect if the instance of HttpClient is configured to
     * use a shared connection manager.
     * </p>
     *
     * @param maxIdleTime maximum time persistent connections can stay idle while kept alive
     * in the connection pool. Connections whose inactivity period exceeds this value will
     * get closed and evicted from the pool.
     *
     * @return this instance.
     */
    public final H2AsyncClientBuilder evictIdleConnections(final TimeValue maxIdleTime) {
        this.evictIdleConnections = true;
        this.maxIdleTime = maxIdleTime;
        return this;
    }

    /**
     * Request exec chain customization and extension.
     * <p>
     * For internal use.
     * </p>
     */
    @Internal
    protected void customizeExecChain(final NamedElementChain<AsyncExecChainHandler> execChainDefinition) {
    }

    /**
     * Adds to the list of {@link Closeable} resources to be managed by the client.
     * <p>
     * For internal use.
     * </p>
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

    public CloseableHttpAsyncClient build() {
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
                new RequestDefaultHeaders(defaultHeaders),
                new RequestUserAgent(userAgentCopy),
                new RequestExpectContinue(),
                new H2RequestContent(),
                new H2RequestConnControl());
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
                new H2AsyncMainClientExec(httpProcessor),
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
            routePlannerCopy = new DefaultRoutePlanner(schemePortResolverCopy);
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

        final AsyncPushConsumerRegistry pushConsumerRegistry = new AsyncPushConsumerRegistry();
        final IOEventHandlerFactory ioEventHandlerFactory = new H2AsyncClientProtocolStarter(
                HttpProcessorBuilder.create().build(),
                (request, context) -> pushConsumerRegistry.get(request),
                h2Config != null ? h2Config : H2Config.DEFAULT,
                charCodingConfig != null ? charCodingConfig : CharCodingConfig.DEFAULT);
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
                        // Don't add last, after H2AsyncMainClientExec, as that does not delegate to the chain
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

        TlsStrategy tlsStrategyCopy = this.tlsStrategy;
        if (tlsStrategyCopy == null) {
            if (systemProperties) {
                tlsStrategyCopy = DefaultClientTlsStrategy.createSystemDefault();
            } else {
                tlsStrategyCopy = DefaultClientTlsStrategy.createDefault();
            }
        }

        final MultihomeConnectionInitiator connectionInitiator = new MultihomeConnectionInitiator(ioReactor, dnsResolver);
        final InternalH2ConnPool connPool = new InternalH2ConnPool(connectionInitiator, host -> null, tlsStrategyCopy);
        connPool.setConnectionConfigResolver(connectionConfigResolver);

        List<Closeable> closeablesCopy = closeables != null ? new ArrayList<>(closeables) : null;
        if (closeablesCopy == null) {
            closeablesCopy = new ArrayList<>(1);
        }
        if (evictIdleConnections) {
            final IdleConnectionEvictor connectionEvictor = new IdleConnectionEvictor(connPool,
                    maxIdleTime != null ? maxIdleTime : TimeValue.ofSeconds(30L));
            closeablesCopy.add(connectionEvictor::shutdown);
            connectionEvictor.start();
        }
        closeablesCopy.add(connPool);

        return new InternalH2AsyncClient(
                ioReactor,
                execChain,
                pushConsumerRegistry,
                threadFactory != null ? threadFactory : new DefaultThreadFactory("httpclient-main", true),
                connPool,
                routePlannerCopy,
                cookieSpecRegistryCopy,
                authSchemeRegistryCopy,
                cookieStoreCopy,
                credentialsProviderCopy,
                defaultRequestConfig,
                closeablesCopy);
    }

    private static String getProperty(final String key, final String defaultValue) {
        return AccessController.doPrivileged((PrivilegedAction<String>) () -> System.getProperty(key, defaultValue));
    }

    static class IdleConnectionEvictor implements Closeable {

        private final Thread thread;

        public IdleConnectionEvictor(final InternalH2ConnPool connPool, final TimeValue maxIdleTime) {
            this.thread = new DefaultThreadFactory("idle-connection-evictor", true).newThread(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        maxIdleTime.sleep();
                        connPool.closeIdle(maxIdleTime);
                    }
                } catch (final InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (final Exception ex) {
                }

            });
        }

        public void start() {
            thread.start();
        }

        public void shutdown() {
            thread.interrupt();
        }

        @Override
        public void close() throws IOException {
            shutdown();
        }

    }

}
