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

package org.apache.http.impl.client;

import java.net.ProxySelector;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.HttpVersion;
import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.auth.AuthSchemeFactory;
import org.apache.http.auth.AuthSchemeRegistry;
import org.apache.http.client.AuthenticationStrategy;
import org.apache.http.client.BackoffManager;
import org.apache.http.client.ConnectionBackoffStrategy;
import org.apache.http.client.CookieStore;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.ServiceUnavailableRetryStrategy;
import org.apache.http.client.UserTokenHandler;
import org.apache.http.client.params.AuthPolicy;
import org.apache.http.client.params.CookiePolicy;
import org.apache.http.client.protocol.RequestAcceptEncoding;
import org.apache.http.client.protocol.RequestAddCookies;
import org.apache.http.client.protocol.RequestAuthCache;
import org.apache.http.client.protocol.RequestClientConnControl;
import org.apache.http.client.protocol.RequestDefaultHeaders;
import org.apache.http.client.protocol.ResponseContentEncoding;
import org.apache.http.client.protocol.ResponseProcessCookies;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeLayeredSocketFactory;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.cookie.CookieSpecFactory;
import org.apache.http.cookie.CookieSpecRegistry;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.NoConnectionReuseStrategy;
import org.apache.http.impl.auth.BasicSchemeFactory;
import org.apache.http.impl.auth.DigestSchemeFactory;
import org.apache.http.impl.auth.KerberosSchemeFactory;
import org.apache.http.impl.auth.NTLMSchemeFactory;
import org.apache.http.impl.auth.SPNegoSchemeFactory;
import org.apache.http.impl.client.exec.BackoffStrategyExec;
import org.apache.http.impl.client.exec.ClientExecChain;
import org.apache.http.impl.client.exec.MainClientExec;
import org.apache.http.impl.client.exec.ProtocolExec;
import org.apache.http.impl.client.exec.RedirectExec;
import org.apache.http.impl.client.exec.RetryExec;
import org.apache.http.impl.client.exec.ServiceUnavailableRetryExec;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.conn.DefaultHttpRoutePlanner;
import org.apache.http.impl.conn.ProxySelectorRoutePlanner;
import org.apache.http.impl.conn.SchemeRegistryFactory;
import org.apache.http.impl.cookie.BestMatchSpecFactory;
import org.apache.http.impl.cookie.BrowserCompatSpecFactory;
import org.apache.http.impl.cookie.IgnoreSpecFactory;
import org.apache.http.impl.cookie.NetscapeDraftSpecFactory;
import org.apache.http.impl.cookie.RFC2109SpecFactory;
import org.apache.http.impl.cookie.RFC2965SpecFactory;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.params.SyncBasicHttpParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.protocol.ImmutableHttpProcessor;
import org.apache.http.protocol.RequestContent;
import org.apache.http.protocol.RequestExpectContinue;
import org.apache.http.protocol.RequestTargetHost;
import org.apache.http.protocol.RequestUserAgent;
import org.apache.http.util.VersionInfo;

/**
 * {@link HttpClient} builder.
 * <p>
 * The following system properties are taken into account by this class
 *  if the {@link #useSystemProperties()} method is called.
 * <ul>
 *  <li>ssl.TrustManagerFactory.algorithm</li>
 *  <li>javax.net.ssl.trustStoreType</li>
 *  <li>javax.net.ssl.trustStore</li>
 *  <li>javax.net.ssl.trustStoreProvider</li>
 *  <li>javax.net.ssl.trustStorePassword</li>
 *  <li>java.home</li>
 *  <li>ssl.KeyManagerFactory.algorithm</li>
 *  <li>javax.net.ssl.keyStoreType</li>
 *  <li>javax.net.ssl.keyStore</li>
 *  <li>javax.net.ssl.keyStoreProvider</li>
 *  <li>javax.net.ssl.keyStorePassword</li>
 *  <li>http.proxyHost</li>
 *  <li>http.proxyPort</li>
 *  <li>http.nonProxyHosts</li>
 *  <li>http.keepAlive</li>
 *  <li>http.maxConnections</li>
 * </ul>
 * </p>
 * <p>
 * This class sets up the following parameters if not explicitly set:
 * <ul>
 * <li>Version: HttpVersion.HTTP_1_1</li>
 * <li>ContentCharset: HTTP.DEFAULT_CONTENT_CHARSET</li>
 * <li>NoTcpDelay: true</li>
 * <li>SocketBufferSize: 8192</li>
 * <li>UserAgent: Apache-HttpClient/release (java 1.5)</li>
 * </ul>
 * <p>
 * The following parameters can be used to customize the behavior of {@link HttpClient}
 * produced by this builder:
 * <ul>
 *  <li>{@link org.apache.http.params.CoreProtocolPNames#PROTOCOL_VERSION}</li>
 *  <li>{@link org.apache.http.params.CoreProtocolPNames#STRICT_TRANSFER_ENCODING}</li>
 *  <li>{@link org.apache.http.params.CoreProtocolPNames#HTTP_ELEMENT_CHARSET}</li>
 *  <li>{@link org.apache.http.params.CoreProtocolPNames#USE_EXPECT_CONTINUE}</li>
 *  <li>{@link org.apache.http.params.CoreProtocolPNames#WAIT_FOR_CONTINUE}</li>
 *  <li>{@link org.apache.http.params.CoreProtocolPNames#USER_AGENT}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#TCP_NODELAY}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#SO_TIMEOUT}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#SO_LINGER}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#SO_REUSEADDR}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#SOCKET_BUFFER_SIZE}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#CONNECTION_TIMEOUT}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#MAX_LINE_LENGTH}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#MAX_HEADER_COUNT}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#STALE_CONNECTION_CHECK}</li>
 *  <li>{@link org.apache.http.conn.params.ConnRoutePNames#FORCED_ROUTE}</li>
 *  <li>{@link org.apache.http.conn.params.ConnRoutePNames#LOCAL_ADDRESS}</li>
 *  <li>{@link org.apache.http.conn.params.ConnRoutePNames#DEFAULT_PROXY}</li>
 *  <li>{@link org.apache.http.cookie.params.CookieSpecPNames#DATE_PATTERNS}</li>
 *  <li>{@link org.apache.http.cookie.params.CookieSpecPNames#SINGLE_COOKIE_HEADER}</li>
 *  <li>{@link org.apache.http.auth.params.AuthPNames#CREDENTIAL_CHARSET}</li>
 *  <li>{@link org.apache.http.client.params.ClientPNames#COOKIE_POLICY}</li>
 *  <li>{@link org.apache.http.client.params.ClientPNames#HANDLE_AUTHENTICATION}</li>
 *  <li>{@link org.apache.http.client.params.ClientPNames#HANDLE_REDIRECTS}</li>
 *  <li>{@link org.apache.http.client.params.ClientPNames#MAX_REDIRECTS}</li>
 *  <li>{@link org.apache.http.client.params.ClientPNames#ALLOW_CIRCULAR_REDIRECTS}</li>
 *  <li>{@link org.apache.http.client.params.ClientPNames#VIRTUAL_HOST}</li>
 *  <li>{@link org.apache.http.client.params.ClientPNames#DEFAULT_HOST}</li>
 *  <li>{@link org.apache.http.client.params.ClientPNames#DEFAULT_HEADERS}</li>
 *  <li>{@link org.apache.http.client.params.ClientPNames#CONN_MANAGER_TIMEOUT}</li>
 * </ul>
 *
 * @since 4.3
 */
@NotThreadSafe
public class HttpClientBuilder {

    private HttpRequestExecutor requestExec;
    private SchemeLayeredSocketFactory sslSocketFactory;
    private HttpClientConnectionManager connManager;
    private ConnectionReuseStrategy reuseStrategy;
    private ConnectionKeepAliveStrategy keepAliveStrategy;
    private AuthenticationStrategy targetAuthStrategy;
    private AuthenticationStrategy proxyAuthStrategy;
    private UserTokenHandler userTokenHandler;

    private HttpProcessor httpprocessor;
    private LinkedList<HttpRequestInterceptor> requestFirst;
    private LinkedList<HttpRequestInterceptor> requestLast;
    private LinkedList<HttpResponseInterceptor> responseFirst;
    private LinkedList<HttpResponseInterceptor> responseLast;

    private HttpRequestRetryHandler retryHandler;

    private HttpRoutePlanner routePlanner;
    private RedirectStrategy redirectStrategy;

    private ConnectionBackoffStrategy connectionBackoffStrategy;
    private BackoffManager backoffManager;

    private ServiceUnavailableRetryStrategy serviceUnavailStrategy;
    
    private HttpParams params;

    private Map<String, AuthSchemeFactory> authShemes;
    private Map<String, CookieSpecFactory> cookieSpecs;
    private CookieStore cookieStore;
    private CredentialsProvider credentialsProvider;

    private boolean systemProperties;
    private boolean redirectHandlingDisabled;
    private boolean automaticRetriesDisabled;
    private boolean contentCompressionDisabled;
    private boolean cookieManagementDisabled;
    private boolean authCachingDisabled;
    private boolean connectionStateDisabled;

    private int maxConnTotal = 0;
    private int maxConnPerRoute = 0;

    public HttpClientBuilder() {
        super();
    }

    public final HttpClientBuilder setRequestExecutor(final HttpRequestExecutor requestExec) {
        this.requestExec = requestExec;
        return this;
    }

    public final HttpClientBuilder setSSLSocketFactory(final SchemeLayeredSocketFactory sslSocketFactory) {
        this.sslSocketFactory = sslSocketFactory;
        return this;
    }

    public final HttpClientBuilder setConnectionManager(final HttpClientConnectionManager connManager) {
        this.connManager = connManager;
        return this;
    }

    public final HttpClientBuilder setMaxConnTotal(int maxConnTotal) {
        this.maxConnTotal = maxConnTotal;
        return this;
    }

    public final HttpClientBuilder setMaxConnPerRoute(int maxConnPerRoute) {
        this.maxConnPerRoute = maxConnPerRoute;
        return this;
    }

    public final HttpClientBuilder setConnectionReuseStrategy(
            final ConnectionReuseStrategy reuseStrategy) {
        this.reuseStrategy = reuseStrategy;
        return this;
    }

    public final HttpClientBuilder setKeepAliveStrategy(
            final ConnectionKeepAliveStrategy keepAliveStrategy) {
        this.keepAliveStrategy = keepAliveStrategy;
        return this;
    }

    public final HttpClientBuilder setUserTokenHandler(final UserTokenHandler userTokenHandler) {
        this.userTokenHandler = userTokenHandler;
        return this;
    }

    public final HttpClientBuilder setTargetAuthenticationStrategy(
            final AuthenticationStrategy targetAuthStrategy) {
        this.targetAuthStrategy = targetAuthStrategy;
        return this;
    }

    public final HttpClientBuilder setProxyAuthenticationStrategy(
            final AuthenticationStrategy proxyAuthStrategy) {
        this.proxyAuthStrategy = proxyAuthStrategy;
        return this;
    }

    public final HttpClientBuilder setHttpProcessor(final HttpProcessor httpprocessor) {
        this.httpprocessor = httpprocessor;
        return this;
    }

    public final HttpClientBuilder addInterceptorFirst(final HttpResponseInterceptor itcp) {
        if (itcp == null) {
            return this;
        }
        if (responseFirst == null) {
            responseFirst = new LinkedList<HttpResponseInterceptor>();
        }
        responseFirst.addFirst(itcp);
        return this;
    }

    public final HttpClientBuilder addInterceptorLast(final HttpResponseInterceptor itcp) {
        if (itcp == null) {
            return this;
        }
        if (responseLast == null) {
            responseLast = new LinkedList<HttpResponseInterceptor>();
        }
        responseLast.addLast(itcp);
        return this;
    }

    public final HttpClientBuilder addInterceptorFirst(final HttpRequestInterceptor itcp) {
        if (itcp == null) {
            return this;
        }
        if (requestFirst == null) {
            requestFirst = new LinkedList<HttpRequestInterceptor>();
        }
        requestFirst.addFirst(itcp);
        return this;
    }

    public final HttpClientBuilder addInterceptorLast(final HttpRequestInterceptor itcp) {
        if (itcp == null) {
            return this;
        }
        if (requestLast == null) {
            requestLast = new LinkedList<HttpRequestInterceptor>();
        }
        requestLast.addLast(itcp);
        return this;
    }

    public final HttpClientBuilder setRetryHandler(final HttpRequestRetryHandler retryHandler) {
        this.retryHandler = retryHandler;
        return this;
    }

    public final HttpClientBuilder setRoutePlanner(final HttpRoutePlanner routePlanner) {
        this.routePlanner = routePlanner;
        return this;
    }

    public final HttpClientBuilder setRedirectStrategy(final RedirectStrategy redirectStrategy) {
        this.redirectStrategy = redirectStrategy;
        return this;
    }

    public final HttpClientBuilder setConnectionBackoffStrategy(
            final ConnectionBackoffStrategy connectionBackoffStrategy) {
        this.connectionBackoffStrategy = connectionBackoffStrategy;
        return this;
    }

    public final HttpClientBuilder setBackoffManager(final BackoffManager backoffManager) {
        this.backoffManager = backoffManager;
        return this;
    }
    
    public final HttpClientBuilder setServiceUnavailableRetryStrategy(
            final ServiceUnavailableRetryStrategy serviceUnavailStrategy) {
        this.serviceUnavailStrategy = serviceUnavailStrategy;
        return this;
    }

    public final HttpClientBuilder setParams(final HttpParams params) {
        this.params = params;
        return this;
    }

    public final HttpClientBuilder setCookieStore(final CookieStore cookieStore) {
        this.cookieStore = cookieStore;
        return this;
    }

    public final HttpClientBuilder setCredentialsProvider(
            final CredentialsProvider credentialsProvider) {
        this.credentialsProvider = credentialsProvider;
        return this;
    }

    public final HttpClientBuilder registerAuthScheme(
            final String name, final AuthSchemeFactory authSchemeFactory) {
        if (this.authShemes == null) {
            this.authShemes = new HashMap<String, AuthSchemeFactory>();
        }
        this.authShemes.put(name, authSchemeFactory);
        return this;
    }

    public final HttpClientBuilder registerCookiePolicy(
            final String name, final CookieSpecFactory cookieSpecFactory) {
        if (this.cookieSpecs == null) {
            this.cookieSpecs = new HashMap<String, CookieSpecFactory>();
        }
        this.cookieSpecs.put(name, cookieSpecFactory);
        return this;
    }

    public final HttpClientBuilder disableRedirectHandling() {
        redirectHandlingDisabled = true;
        return this;
    }

    public final HttpClientBuilder disableAutomaticRetries() {
        automaticRetriesDisabled = true;
        return this;
    }

    public final HttpClientBuilder disableConnectionState() {
        connectionStateDisabled = true;
        return this;
    }

    public final HttpClientBuilder disableContentCompression() {
        contentCompressionDisabled = true;
        return this;
    }

    public final HttpClientBuilder useSystemProperties() {
        systemProperties = true;
        return this;
    }

    protected ClientExecChain decorateMainExec(final ClientExecChain mainExec) {
        return mainExec;
    }

    protected ClientExecChain decorateProtocolExec(final ClientExecChain protocolExec) {
        return protocolExec;
    }

    public HttpClient build() {
        // Create main request executor
        HttpRequestExecutor requestExec = this.requestExec;
        if (requestExec == null) {
            requestExec = new HttpRequestExecutor();
        }
        HttpClientConnectionManager connManager = this.connManager;
        if (connManager == null) {
            SchemeRegistry schemeRegistry = systemProperties ?
                    SchemeRegistryFactory.createSystemDefault() :
                        SchemeRegistryFactory.createDefault();
            if (sslSocketFactory != null) {
                schemeRegistry.register(new Scheme("https", 443, sslSocketFactory));
            }
            PoolingHttpClientConnectionManager poolingmgr = new PoolingHttpClientConnectionManager(
                    schemeRegistry);
            if (systemProperties) {
                String s = System.getProperty("http.keepAlive");
                if ("true".equalsIgnoreCase(s)) {
                    s = System.getProperty("http.maxConnections", "5");
                    int max = Integer.parseInt(s);
                    poolingmgr.setDefaultMaxPerRoute(max);
                    poolingmgr.setMaxTotal(2 * max);
                }
            } else {
                if (maxConnTotal > 0) {
                    poolingmgr.setMaxTotal(maxConnTotal);
                }
                if (maxConnPerRoute > 0) {
                    poolingmgr.setDefaultMaxPerRoute(maxConnPerRoute);
                }
            }
            connManager = poolingmgr;
        }
        ConnectionReuseStrategy reuseStrategy = this.reuseStrategy;
        if (reuseStrategy == null) {
            if (systemProperties) {
                String s = System.getProperty("http.keepAlive");
                if ("true".equalsIgnoreCase(s)) {
                    reuseStrategy = new DefaultConnectionReuseStrategy();
                } else {
                    reuseStrategy = new NoConnectionReuseStrategy();
                }
            } else {
                reuseStrategy = new DefaultConnectionReuseStrategy();
            }
        }
        ConnectionKeepAliveStrategy keepAliveStrategy = this.keepAliveStrategy;
        if (keepAliveStrategy == null) {
            keepAliveStrategy = new DefaultConnectionKeepAliveStrategy();
        }
        AuthenticationStrategy targetAuthStrategy = this.targetAuthStrategy;
        if (targetAuthStrategy == null) {
            targetAuthStrategy = new TargetAuthenticationStrategy();
        }
        AuthenticationStrategy proxyAuthStrategy = this.proxyAuthStrategy;
        if (proxyAuthStrategy == null) {
            proxyAuthStrategy = new ProxyAuthenticationStrategy();
        }
        UserTokenHandler userTokenHandler = this.userTokenHandler;
        if (userTokenHandler == null) {
            if (!connectionStateDisabled) {
                userTokenHandler = new DefaultUserTokenHandler();
            } else {
                userTokenHandler = new NoopUserTokenHandler();
            }
        }
        ClientExecChain execChain = new MainClientExec(
                requestExec,
                connManager,
                reuseStrategy,
                keepAliveStrategy,
                targetAuthStrategy,
                proxyAuthStrategy,
                userTokenHandler);

        execChain = decorateMainExec(execChain);

        HttpProcessor httpprocessor = this.httpprocessor;
        if (httpprocessor == null) {
            ListBuilder<HttpRequestInterceptor> reqlb = new ListBuilder<HttpRequestInterceptor>();
            reqlb.addAll(requestFirst);
            reqlb.addAll(
                    new RequestDefaultHeaders(),
                    new RequestContent(),
                    new RequestTargetHost(),
                    new RequestClientConnControl(),
                    new RequestUserAgent(),
                    new RequestExpectContinue());
            if (!cookieManagementDisabled) {
                reqlb.add(new RequestAddCookies());
            }
            if (!contentCompressionDisabled) {
                reqlb.add(new RequestAcceptEncoding());
            }
            if (!authCachingDisabled) {
                reqlb.add(new RequestAuthCache());
            }
            reqlb.addAll(requestLast);

            ListBuilder<HttpResponseInterceptor> reslb = new ListBuilder<HttpResponseInterceptor>();
            reslb.addAll(responseFirst);
            if (!cookieManagementDisabled) {
                reslb.add(new ResponseProcessCookies());
            }
            if (!contentCompressionDisabled) {
                reslb.add(new ResponseContentEncoding());
            }
            reslb.addAll(responseLast);
            List<HttpRequestInterceptor> reqincps = reqlb.build();
            List<HttpResponseInterceptor> resincps = reslb.build();
            httpprocessor = new ImmutableHttpProcessor(
                    reqincps.toArray(new HttpRequestInterceptor[reqincps.size()]),
                    resincps.toArray(new HttpResponseInterceptor[resincps.size()]));
        }
        execChain = new ProtocolExec(execChain, httpprocessor);

        execChain = decorateProtocolExec(execChain);

        // Add request retry executor, if not disabled
        if (!automaticRetriesDisabled) {
            HttpRequestRetryHandler retryHandler = this.retryHandler;
            if (retryHandler == null) {
                retryHandler = new DefaultHttpRequestRetryHandler();
            }
            execChain = new RetryExec(execChain, retryHandler);
        }

        // Add redirect executor, if not disabled
        HttpRoutePlanner routePlanner = this.routePlanner;
        if (routePlanner == null) {
            if (systemProperties) {
                routePlanner = new ProxySelectorRoutePlanner(
                        connManager.getSchemeRegistry(),
                        ProxySelector.getDefault());
            } else {
                routePlanner = new DefaultHttpRoutePlanner(connManager.getSchemeRegistry());
            }
        }
        if (!redirectHandlingDisabled) {
            RedirectStrategy redirectStrategy = this.redirectStrategy;
            if (redirectStrategy == null) {
                redirectStrategy = new DefaultRedirectStrategy();
            }
            execChain = new RedirectExec(execChain, routePlanner, redirectStrategy);
        }

        // Optionally, add service unavailable retry executor
        ServiceUnavailableRetryStrategy serviceUnavailStrategy = this.serviceUnavailStrategy;
        if (serviceUnavailStrategy != null) {
            execChain = new ServiceUnavailableRetryExec(execChain, serviceUnavailStrategy);
        }
        // Optionally, add connection back-off executor
        BackoffManager backoffManager = this.backoffManager;
        ConnectionBackoffStrategy connectionBackoffStrategy = this.connectionBackoffStrategy;
        if (backoffManager != null && connectionBackoffStrategy != null) {
            execChain = new BackoffStrategyExec(execChain, connectionBackoffStrategy, backoffManager);
        }

        HttpParams params = this.params;
        if (params == null) {
            params = new SyncBasicHttpParams();
            setDefaultHttpParams(params);
        }

        CookieSpecRegistry cookieSpecRegistry = new CookieSpecRegistry();
        cookieSpecRegistry.register(CookiePolicy.BEST_MATCH, new BestMatchSpecFactory());
        cookieSpecRegistry.register(CookiePolicy.BROWSER_COMPATIBILITY, new BrowserCompatSpecFactory());
        cookieSpecRegistry.register(CookiePolicy.NETSCAPE, new NetscapeDraftSpecFactory());
        cookieSpecRegistry.register(CookiePolicy.RFC_2109, new RFC2109SpecFactory());
        cookieSpecRegistry.register(CookiePolicy.RFC_2965, new RFC2965SpecFactory());
        cookieSpecRegistry.register(CookiePolicy.IGNORE_COOKIES, new IgnoreSpecFactory());
        if (cookieSpecs != null) {
            for (Map.Entry<String, CookieSpecFactory> entry: cookieSpecs.entrySet()) {
                cookieSpecRegistry.register(entry.getKey(), entry.getValue());
            }
        }

        AuthSchemeRegistry authSchemeRegistry = new AuthSchemeRegistry();
        authSchemeRegistry.register(AuthPolicy.BASIC, new BasicSchemeFactory());
        authSchemeRegistry.register(AuthPolicy.DIGEST, new DigestSchemeFactory());
        authSchemeRegistry.register(AuthPolicy.NTLM, new NTLMSchemeFactory());
        authSchemeRegistry.register(AuthPolicy.SPNEGO, new SPNegoSchemeFactory());
        authSchemeRegistry.register(AuthPolicy.KERBEROS, new KerberosSchemeFactory());
        if (authShemes != null) {
            for (Map.Entry<String, AuthSchemeFactory> entry: authShemes.entrySet()) {
                authSchemeRegistry.register(entry.getKey(), entry.getValue());
            }
        }

        CookieStore defaultCookieStore = this.cookieStore;
        if (defaultCookieStore == null) {
            defaultCookieStore = new BasicCookieStore();
        }

        CredentialsProvider defaultCredentialsProvider = this.credentialsProvider;
        if (defaultCredentialsProvider == null) {
            defaultCredentialsProvider = new BasicCredentialsProvider();
        }

        return new InternalHttpClient(
                execChain,
                connManager,
                routePlanner,
                cookieSpecRegistry,
                authSchemeRegistry,
                defaultCookieStore,
                defaultCredentialsProvider,
                params);
    }

    static class ListBuilder<E> {

        private final LinkedList<E> list;
        private final Map<Class<?>, E> uniqueClasses;

        ListBuilder() {
            this.list = new LinkedList<E>();
            this.uniqueClasses = new HashMap<Class<?>, E>();
        }

        public void add(final E e) {
            if (e == null) {
                return;
            }
            E previous = this.uniqueClasses.remove(e.getClass());
            if (previous != null) {
                this.list.remove(previous);
            }
            this.list.add(e);
            this.uniqueClasses.put(e.getClass(), e);
        }

        public void addAll(final Collection<E> c) {
            if (c == null) {
                return;
            }
            for (E e: c) {
                add(e);
            }
        }

        public void addAll(E... c) {
            if (c == null) {
                return;
            }
            for (E e: c) {
                add(e);
            }
        }

        public List<E> build() {
            return new ArrayList<E>(this.list);
        }

    }

    /**
     * Saves the default set of HttpParams in the provided parameter.
     * These are:
     * <ul>
     * <li>{@link CoreProtocolPNames#PROTOCOL_VERSION}: 1.1</li>
     * <li>{@link CoreProtocolPNames#HTTP_CONTENT_CHARSET}: ISO-8859-1</li>
     * <li>{@link CoreConnectionPNames#TCP_NODELAY}: true</li>
     * <li>{@link CoreConnectionPNames#SOCKET_BUFFER_SIZE}: 8192</li>
     * <li>{@link CoreProtocolPNames#USER_AGENT}: Apache-HttpClient/<release> (java 1.5)</li>
     * </ul>
     */
    public static void setDefaultHttpParams(HttpParams params) {
        HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
        HttpProtocolParams.setContentCharset(params, HTTP.DEF_CONTENT_CHARSET.name());
        HttpConnectionParams.setTcpNoDelay(params, true);
        HttpConnectionParams.setSocketBufferSize(params, 8192);

        // determine the release version from packaged version info
        final VersionInfo vi = VersionInfo.loadVersionInfo
            ("org.apache.http.client", HttpClientBuilder.class.getClassLoader());
        final String release = (vi != null) ?
            vi.getRelease() : VersionInfo.UNAVAILABLE;
        HttpProtocolParams.setUserAgent(params,
                "Apache-HttpClient/" + release + " (java 1.5)");
    }

}
