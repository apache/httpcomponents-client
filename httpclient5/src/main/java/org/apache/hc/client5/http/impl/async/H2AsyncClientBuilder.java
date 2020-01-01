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
import java.net.InetSocketAddress;
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
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.auth.CredentialsProvider;
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
import org.apache.hc.client5.http.impl.auth.DigestSchemeFactory;
import org.apache.hc.client5.http.impl.auth.KerberosSchemeFactory;
import org.apache.hc.client5.http.impl.auth.NTLMSchemeFactory;
import org.apache.hc.client5.http.impl.auth.SPNegoSchemeFactory;
import org.apache.hc.client5.http.impl.auth.SystemDefaultCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.MultihomeConnectionInitiator;
import org.apache.hc.client5.http.impl.routing.DefaultRoutePlanner;
import org.apache.hc.client5.http.protocol.RedirectStrategy;
import org.apache.hc.client5.http.protocol.RequestAddCookies;
import org.apache.hc.client5.http.protocol.RequestAuthCache;
import org.apache.hc.client5.http.protocol.RequestDefaultHeaders;
import org.apache.hc.client5.http.protocol.RequestExpectContinue;
import org.apache.hc.client5.http.protocol.ResponseProcessCookies;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.concurrent.DefaultThreadFactory;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.function.Resolver;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.config.NamedElementChain;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.nio.command.ShutdownCommand;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.protocol.DefaultHttpProcessor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.HttpProcessorBuilder;
import org.apache.hc.core5.http.protocol.RequestTargetHost;
import org.apache.hc.core5.http.protocol.RequestUserAgent;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.nio.pool.H2ConnPool;
import org.apache.hc.core5.http2.protocol.H2RequestConnControl;
import org.apache.hc.core5.http2.protocol.H2RequestContent;
import org.apache.hc.core5.http2.protocol.H2RequestTargetHost;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.DefaultConnectingIOReactor;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOSession;
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

        enum Postion { FIRST, LAST }

        final RequestInterceptorEntry.Postion postion;
        final HttpRequestInterceptor interceptor;

        private RequestInterceptorEntry(final RequestInterceptorEntry.Postion postion, final HttpRequestInterceptor interceptor) {
            this.postion = postion;
            this.interceptor = interceptor;
        }
    }

    private static class ResponseInterceptorEntry {

        enum Postion { FIRST, LAST }

        final ResponseInterceptorEntry.Postion postion;
        final HttpResponseInterceptor interceptor;

        private ResponseInterceptorEntry(final ResponseInterceptorEntry.Postion postion, final HttpResponseInterceptor interceptor) {
            this.postion = postion;
            this.interceptor = interceptor;
        }
    }

    private static class ExecInterceptorEntry {

        enum Postion { BEFORE, AFTER, REPLACE, FIRST, LAST }

        final ExecInterceptorEntry.Postion postion;
        final String name;
        final AsyncExecChainHandler interceptor;
        final String existing;

        private ExecInterceptorEntry(
                final ExecInterceptorEntry.Postion postion,
                final String name,
                final AsyncExecChainHandler interceptor,
                final String existing) {
            this.postion = postion;
            this.name = name;
            this.interceptor = interceptor;
            this.existing = existing;
        }

    }

    private IOReactorConfig ioReactorConfig;
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

    public static H2AsyncClientBuilder create() {
        return new H2AsyncClientBuilder();
    }

    protected H2AsyncClientBuilder() {
        super();
    }

    /**
     * Sets {@link H2Config} configuration.
     */
    public final H2AsyncClientBuilder setH2Config(final H2Config h2Config) {
        this.h2Config = h2Config;
        return this;
    }

    /**
     * Sets {@link IOReactorConfig} configuration.
     */
    public final H2AsyncClientBuilder setIOReactorConfig(final IOReactorConfig ioReactorConfig) {
        this.ioReactorConfig = ioReactorConfig;
        return this;
    }

    /**
     * Sets {@link CharCodingConfig} configuration.
     */
    public final H2AsyncClientBuilder setCharCodingConfig(final CharCodingConfig charCodingConfig) {
        this.charCodingConfig = charCodingConfig;
        return this;
    }

    /**
     * Assigns {@link AuthenticationStrategy} instance for target
     * host authentication.
     */
    public final H2AsyncClientBuilder setTargetAuthenticationStrategy(
            final AuthenticationStrategy targetAuthStrategy) {
        this.targetAuthStrategy = targetAuthStrategy;
        return this;
    }

    /**
     * Assigns {@link AuthenticationStrategy} instance for proxy
     * authentication.
     */
    public final H2AsyncClientBuilder setProxyAuthenticationStrategy(
            final AuthenticationStrategy proxyAuthStrategy) {
        this.proxyAuthStrategy = proxyAuthStrategy;
        return this;
    }

    /**
     * Adds this protocol interceptor to the head of the protocol processing list.
     */
    public final H2AsyncClientBuilder addResponseInterceptorFirst(final HttpResponseInterceptor interceptor) {
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
    public final H2AsyncClientBuilder addResponseInterceptorLast(final HttpResponseInterceptor interceptor) {
        Args.notNull(interceptor, "Interceptor");
        if (responseInterceptors == null) {
            responseInterceptors = new LinkedList<>();
        }
        responseInterceptors.add(new ResponseInterceptorEntry(ResponseInterceptorEntry.Postion.LAST, interceptor));
        return this;
    }

    /**
     * Adds this execution interceptor before an existing interceptor.
     */
    public final H2AsyncClientBuilder addExecInterceptorBefore(final String existing, final String name, final AsyncExecChainHandler interceptor) {
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
    public final H2AsyncClientBuilder addExecInterceptorAfter(final String existing, final String name, final AsyncExecChainHandler interceptor) {
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
    public final H2AsyncClientBuilder replaceExecInterceptor(final String existing, final AsyncExecChainHandler interceptor) {
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
    public final H2AsyncClientBuilder addExecInterceptorFirst(final String name, final AsyncExecChainHandler interceptor) {
        Args.notNull(name, "Name");
        Args.notNull(interceptor, "Interceptor");
        execInterceptors.add(new ExecInterceptorEntry(ExecInterceptorEntry.Postion.FIRST, name, interceptor, null));
        return this;
    }

    /**
     * Add an interceptor to the tail of the processing list.
     */
    public final H2AsyncClientBuilder addExecInterceptorLast(final String name, final AsyncExecChainHandler interceptor) {
        Args.notNull(name, "Name");
        Args.notNull(interceptor, "Interceptor");
        execInterceptors.add(new ExecInterceptorEntry(ExecInterceptorEntry.Postion.LAST, name, interceptor, null));
        return this;
    }

    /**
     * Adds this protocol interceptor to the head of the protocol processing list.
     */
    public final H2AsyncClientBuilder addRequestInterceptorFirst(final HttpRequestInterceptor interceptor) {
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
    public final H2AsyncClientBuilder addRequestInterceptorLast(final HttpRequestInterceptor interceptor) {
        Args.notNull(interceptor, "Interceptor");
        if (requestInterceptors == null) {
            requestInterceptors = new LinkedList<>();
        }
        requestInterceptors.add(new RequestInterceptorEntry(RequestInterceptorEntry.Postion.LAST, interceptor));
        return this;
    }

    /**
     * Assigns {@link HttpRequestRetryStrategy} instance.
     * <p>
     * Please note this value can be overridden by the {@link #disableAutomaticRetries()}
     * method.
     */
    public final H2AsyncClientBuilder setRetryStrategy(final HttpRequestRetryStrategy retryStrategy) {
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
    public H2AsyncClientBuilder setRedirectStrategy(final RedirectStrategy redirectStrategy) {
        this.redirectStrategy = redirectStrategy;
        return this;
    }

    /**
     * Assigns {@link SchemePortResolver} instance.
     */
    public final H2AsyncClientBuilder setSchemePortResolver(final SchemePortResolver schemePortResolver) {
        this.schemePortResolver = schemePortResolver;
        return this;
    }

    /**
     * Assigns {@link DnsResolver} instance.
     */
    public final H2AsyncClientBuilder setDnsResolver(final DnsResolver dnsResolver) {
        this.dnsResolver = dnsResolver;
        return this;
    }

    /**
     * Assigns {@link TlsStrategy} instance.
     */
    public final H2AsyncClientBuilder setTlsStrategy(final TlsStrategy tlsStrategy) {
        this.tlsStrategy = tlsStrategy;
        return this;
    }

    /**
     * Assigns {@link ThreadFactory} instance.
     */
    public final H2AsyncClientBuilder setThreadFactory(final ThreadFactory threadFactory) {
        this.threadFactory = threadFactory;
        return this;
    }

    /**
     * Assigns {@code User-Agent} value.
     */
    public final H2AsyncClientBuilder setUserAgent(final String userAgent) {
        this.userAgent = userAgent;
        return this;
    }

    /**
     * Assigns default request header values.
     */
    public final H2AsyncClientBuilder setDefaultHeaders(final Collection<? extends Header> defaultHeaders) {
        this.defaultHeaders = defaultHeaders;
        return this;
    }

    /**
     * Assigns {@link HttpRoutePlanner} instance.
     */
    public final H2AsyncClientBuilder setRoutePlanner(final HttpRoutePlanner routePlanner) {
        this.routePlanner = routePlanner;
        return this;
    }

    /**
     * Assigns default {@link CredentialsProvider} instance which will be used
     * for request execution if not explicitly set in the client execution
     * context.
     */
    public final H2AsyncClientBuilder setDefaultCredentialsProvider(final CredentialsProvider credentialsProvider) {
        this.credentialsProvider = credentialsProvider;
        return this;
    }

    /**
     * Assigns default {@link org.apache.hc.client5.http.auth.AuthScheme} registry which will
     * be used for request execution if not explicitly set in the client execution
     * context.
     */
    public final H2AsyncClientBuilder setDefaultAuthSchemeRegistry(final Lookup<AuthSchemeFactory> authSchemeRegistry) {
        this.authSchemeRegistry = authSchemeRegistry;
        return this;
    }

    /**
     * Assigns default {@link org.apache.hc.client5.http.cookie.CookieSpec} registry
     * which will be used for request execution if not explicitly set in the client
     * execution context.
     */
    public final H2AsyncClientBuilder setDefaultCookieSpecRegistry(final Lookup<CookieSpecFactory> cookieSpecRegistry) {
        this.cookieSpecRegistry = cookieSpecRegistry;
        return this;
    }

    /**
     * Assigns default {@link CookieStore} instance which will be used for
     * request execution if not explicitly set in the client execution context.
     */
    public final H2AsyncClientBuilder setDefaultCookieStore(final CookieStore cookieStore) {
        this.cookieStore = cookieStore;
        return this;
    }

    /**
     * Assigns default {@link RequestConfig} instance which will be used
     * for request execution if not explicitly set in the client execution
     * context.
     */
    public final H2AsyncClientBuilder setDefaultRequestConfig(final RequestConfig config) {
        this.defaultRequestConfig = config;
        return this;
    }

    /**
     * Use system properties when creating and configuring default
     * implementations.
     */
    public final H2AsyncClientBuilder useSystemProperties() {
        this.systemProperties = true;
        return this;
    }

    /**
     * Disables automatic redirect handling.
     */
    public final H2AsyncClientBuilder disableRedirectHandling() {
        redirectHandlingDisabled = true;
        return this;
    }

    /**
     * Disables automatic request recovery and re-execution.
     */
    public final H2AsyncClientBuilder disableAutomaticRetries() {
        automaticRetriesDisabled = true;
        return this;
    }

    /**
     * Disables state (cookie) management.
     */
    public final H2AsyncClientBuilder disableCookieManagement() {
        this.cookieManagementDisabled = true;
        return this;
    }

    /**
     * Disables authentication scheme caching.
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
     * <p>
     * Please note this method has no effect if the instance of HttpClient is configuted to
     * use a shared connection manager.
     *
     * @param maxIdleTime maximum time persistent connections can stay idle while kept alive
     * in the connection pool. Connections whose inactivity period exceeds this value will
     * get closed and evicted from the pool.
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

    public CloseableHttpAsyncClient build() {
        final NamedElementChain<AsyncExecChainHandler> execChainDefinition = new NamedElementChain<>();
        execChainDefinition.addLast(
                new H2AsyncMainClientExec(),
                ChainElement.MAIN_TRANSPORT.name());

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

        execChainDefinition.addFirst(
                new AsyncConnectExec(
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
                new AsyncProtocolExec(httpProcessor, targetAuthStrategyCopy, proxyAuthStrategyCopy),
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
        final IOEventHandlerFactory ioEventHandlerFactory = new H2AsyncClientEventHandlerFactory(
                new DefaultHttpProcessor(new H2RequestContent(), new H2RequestTargetHost(), new H2RequestConnControl()),
                new HandlerFactory<AsyncPushConsumer>() {

                    @Override
                    public AsyncPushConsumer create(final HttpRequest request, final HttpContext context) throws HttpException {
                        return pushConsumerRegistry.get(request);
                    }

                },
                h2Config != null ? h2Config : H2Config.DEFAULT,
                charCodingConfig != null ? charCodingConfig : CharCodingConfig.DEFAULT);
        final DefaultConnectingIOReactor ioReactor = new DefaultConnectingIOReactor(
                ioEventHandlerFactory,
                ioReactorConfig != null ? ioReactorConfig : IOReactorConfig.DEFAULT,
                threadFactory != null ? threadFactory : new DefaultThreadFactory("httpclient-dispatch", true),
                LoggingIOSessionDecorator.INSTANCE,
                LoggingExceptionCallback.INSTANCE,
                null,
                new Callback<IOSession>() {

                    @Override
                    public void execute(final IOSession ioSession) {
                        ioSession.enqueue(new ShutdownCommand(CloseMode.GRACEFUL), Command.Priority.IMMEDIATE);
                    }

                });

        if (execInterceptors != null) {
            for (final ExecInterceptorEntry entry: execInterceptors) {
                switch (entry.postion) {
                    case AFTER:
                        execChainDefinition.addAfter(entry.existing, entry.interceptor, entry.name);
                        break;
                    case BEFORE:
                        execChainDefinition.addBefore(entry.existing, entry.interceptor, entry.name);
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
                    .register(StandardAuthScheme.NTLM, NTLMSchemeFactory.INSTANCE)
                    .register(StandardAuthScheme.SPNEGO, SPNegoSchemeFactory.DEFAULT)
                    .register(StandardAuthScheme.KERBEROS, KerberosSchemeFactory.DEFAULT)
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
                tlsStrategyCopy = DefaultClientTlsStrategy.getSystemDefault();
            } else {
                tlsStrategyCopy = DefaultClientTlsStrategy.getDefault();
            }
        }

        final MultihomeConnectionInitiator connectionInitiator = new MultihomeConnectionInitiator(ioReactor, dnsResolver);
        final H2ConnPool connPool = new H2ConnPool(connectionInitiator, new Resolver<HttpHost, InetSocketAddress>() {

            @Override
            public InetSocketAddress resolve(final HttpHost host) {
                return null;
            }

        }, tlsStrategyCopy);

        List<Closeable> closeablesCopy = closeables != null ? new ArrayList<>(closeables) : null;
        if (closeablesCopy == null) {
            closeablesCopy = new ArrayList<>(1);
        }
        if (evictIdleConnections) {
            final IdleConnectionEvictor connectionEvictor = new IdleConnectionEvictor(connPool,
                    maxIdleTime != null ? maxIdleTime : TimeValue.ofSeconds(30L));
            closeablesCopy.add(new Closeable() {

                @Override
                public void close() throws IOException {
                    connectionEvictor.shutdown();
                }

            });
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
        return AccessController.doPrivileged(new PrivilegedAction<String>() {
            @Override
            public String run() {
                return System.getProperty(key, defaultValue);
            }
        });
    }

    static class IdleConnectionEvictor implements Closeable {

        private final Thread thread;

        public IdleConnectionEvictor(final H2ConnPool connPool, final TimeValue maxIdleTime) {
            this.thread = new DefaultThreadFactory("idle-connection-evictor", true).newThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        while (!Thread.currentThread().isInterrupted()) {
                            maxIdleTime.sleep();
                            connPool.closeIdle(maxIdleTime);
                        }
                    } catch (final InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } catch (final Exception ex) {
                    }

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
