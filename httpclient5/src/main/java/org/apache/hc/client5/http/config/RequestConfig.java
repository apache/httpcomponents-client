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

package org.apache.hc.client5.http.config;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

/**
 *  Immutable class encapsulating request configuration items.
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class RequestConfig implements Cloneable {

    private static final Timeout DEFAULT_CONNECTION_REQUEST_TIMEOUT = Timeout.ofMinutes(3);
    private static final Timeout DEFAULT_CONNECT_TIMEOUT = Timeout.ofMinutes(3);
    private static final TimeValue DEFAULT_CONN_KEEP_ALIVE = TimeValue.ofMinutes(3);

    public static final RequestConfig DEFAULT = new Builder().build();

    private final boolean expectContinueEnabled;
    private final HttpHost proxy;
    private final String cookieSpec;
    private final boolean redirectsEnabled;
    private final boolean circularRedirectsAllowed;
    private final int maxRedirects;
    private final boolean authenticationEnabled;
    private final Collection<String> targetPreferredAuthSchemes;
    private final Collection<String> proxyPreferredAuthSchemes;
    private final Timeout connectionRequestTimeout;
    private final Timeout connectTimeout;
    private final Timeout responseTimeout;
    private final TimeValue connectionKeepAlive;
    private final boolean contentCompressionEnabled;
    private final boolean hardCancellationEnabled;

    /**
     * Intended for CDI compatibility
    */
    protected RequestConfig() {
        this(false, null, null, false, false, 0, false, null, null,
                DEFAULT_CONNECTION_REQUEST_TIMEOUT, DEFAULT_CONNECT_TIMEOUT, null, DEFAULT_CONN_KEEP_ALIVE, false, false);
    }

    RequestConfig(
            final boolean expectContinueEnabled,
            final HttpHost proxy,
            final String cookieSpec,
            final boolean redirectsEnabled,
            final boolean circularRedirectsAllowed,
            final int maxRedirects,
            final boolean authenticationEnabled,
            final Collection<String> targetPreferredAuthSchemes,
            final Collection<String> proxyPreferredAuthSchemes,
            final Timeout connectionRequestTimeout,
            final Timeout connectTimeout,
            final Timeout responseTimeout,
            final TimeValue connectionKeepAlive,
            final boolean contentCompressionEnabled,
            final boolean hardCancellationEnabled) {
        super();
        this.expectContinueEnabled = expectContinueEnabled;
        this.proxy = proxy;
        this.cookieSpec = cookieSpec;
        this.redirectsEnabled = redirectsEnabled;
        this.circularRedirectsAllowed = circularRedirectsAllowed;
        this.maxRedirects = maxRedirects;
        this.authenticationEnabled = authenticationEnabled;
        this.targetPreferredAuthSchemes = targetPreferredAuthSchemes;
        this.proxyPreferredAuthSchemes = proxyPreferredAuthSchemes;
        this.connectionRequestTimeout = connectionRequestTimeout;
        this.connectTimeout = connectTimeout;
        this.responseTimeout = responseTimeout;
        this.connectionKeepAlive = connectionKeepAlive;
        this.contentCompressionEnabled = contentCompressionEnabled;
        this.hardCancellationEnabled = hardCancellationEnabled;
    }

    /**
     * @see Builder#setExpectContinueEnabled(boolean)
     */
    public boolean isExpectContinueEnabled() {
        return expectContinueEnabled;
    }

    /**
     * @see Builder#setProxy(HttpHost)
     */
    public HttpHost getProxy() {
        return proxy;
    }

    /**
     * @see Builder#setCookieSpec(String)
     */
    public String getCookieSpec() {
        return cookieSpec;
    }

    /**
     * @see Builder#setRedirectsEnabled(boolean)
     */
    public boolean isRedirectsEnabled() {
        return redirectsEnabled;
    }

    /**
     * @see Builder#setCircularRedirectsAllowed(boolean)
     */
    public boolean isCircularRedirectsAllowed() {
        return circularRedirectsAllowed;
    }

    /**
     * @see Builder#setMaxRedirects(int)
     */
    public int getMaxRedirects() {
        return maxRedirects;
    }

    /**
     * @see Builder#setAuthenticationEnabled(boolean)
     */
    public boolean isAuthenticationEnabled() {
        return authenticationEnabled;
    }

    /**
     * @see Builder#setTargetPreferredAuthSchemes(Collection)
     */
    public Collection<String> getTargetPreferredAuthSchemes() {
        return targetPreferredAuthSchemes;
    }

    /**
     * @see Builder#setProxyPreferredAuthSchemes(Collection)
     */
    public Collection<String> getProxyPreferredAuthSchemes() {
        return proxyPreferredAuthSchemes;
    }

    /**
     * @see Builder#setConnectionRequestTimeout(Timeout)
     */
    public Timeout getConnectionRequestTimeout() {
        return connectionRequestTimeout;
    }

    /**
     * @see Builder#setConnectTimeout(Timeout)
     */
    public Timeout getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * @see Builder#setResponseTimeout(Timeout)
     */
    public Timeout getResponseTimeout() {
        return responseTimeout;
    }

    /**
     * @see Builder#setConnectionKeepAlive(TimeValue)
     */
    public TimeValue getConnectionKeepAlive() {
        return connectionKeepAlive;
    }

    /**
     * @see Builder#setContentCompressionEnabled(boolean)
     */
    public boolean isContentCompressionEnabled() {
        return contentCompressionEnabled;
    }

    /**
     * @see Builder#setHardCancellationEnabled(boolean)
     */
    public boolean isHardCancellationEnabled() {
        return hardCancellationEnabled;
    }

    @Override
    protected RequestConfig clone() throws CloneNotSupportedException {
        return (RequestConfig) super.clone();
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("[");
        builder.append("expectContinueEnabled=").append(expectContinueEnabled);
        builder.append(", proxy=").append(proxy);
        builder.append(", cookieSpec=").append(cookieSpec);
        builder.append(", redirectsEnabled=").append(redirectsEnabled);
        builder.append(", maxRedirects=").append(maxRedirects);
        builder.append(", circularRedirectsAllowed=").append(circularRedirectsAllowed);
        builder.append(", authenticationEnabled=").append(authenticationEnabled);
        builder.append(", targetPreferredAuthSchemes=").append(targetPreferredAuthSchemes);
        builder.append(", proxyPreferredAuthSchemes=").append(proxyPreferredAuthSchemes);
        builder.append(", connectionRequestTimeout=").append(connectionRequestTimeout);
        builder.append(", connectTimeout=").append(connectTimeout);
        builder.append(", responseTimeout=").append(responseTimeout);
        builder.append(", connectionKeepAlive=").append(connectionKeepAlive);
        builder.append(", contentCompressionEnabled=").append(contentCompressionEnabled);
        builder.append(", hardCancellationEnabled=").append(hardCancellationEnabled);
        builder.append("]");
        return builder.toString();
    }

    public static RequestConfig.Builder custom() {
        return new Builder();
    }

    public static RequestConfig.Builder copy(final RequestConfig config) {
        return new Builder()
            .setExpectContinueEnabled(config.isExpectContinueEnabled())
            .setProxy(config.getProxy())
            .setCookieSpec(config.getCookieSpec())
            .setRedirectsEnabled(config.isRedirectsEnabled())
            .setCircularRedirectsAllowed(config.isCircularRedirectsAllowed())
            .setMaxRedirects(config.getMaxRedirects())
            .setAuthenticationEnabled(config.isAuthenticationEnabled())
            .setTargetPreferredAuthSchemes(config.getTargetPreferredAuthSchemes())
            .setProxyPreferredAuthSchemes(config.getProxyPreferredAuthSchemes())
            .setConnectionRequestTimeout(config.getConnectionRequestTimeout())
            .setConnectTimeout(config.getConnectTimeout())
            .setResponseTimeout(config.getResponseTimeout())
            .setConnectionKeepAlive(config.getConnectionKeepAlive())
            .setContentCompressionEnabled(config.isContentCompressionEnabled())
            .setHardCancellationEnabled(config.isHardCancellationEnabled());
    }

    public static class Builder {

        private boolean expectContinueEnabled;
        private HttpHost proxy;
        private String cookieSpec;
        private boolean redirectsEnabled;
        private boolean circularRedirectsAllowed;
        private int maxRedirects;
        private boolean authenticationEnabled;
        private Collection<String> targetPreferredAuthSchemes;
        private Collection<String> proxyPreferredAuthSchemes;
        private Timeout connectionRequestTimeout;
        private Timeout connectTimeout;
        private Timeout responseTimeout;
        private TimeValue connectionKeepAlive;
        private boolean contentCompressionEnabled;
        private boolean hardCancellationEnabled;

        Builder() {
            super();
            this.redirectsEnabled = true;
            this.maxRedirects = 50;
            this.authenticationEnabled = true;
            this.connectionRequestTimeout = DEFAULT_CONNECTION_REQUEST_TIMEOUT;
            this.connectTimeout = DEFAULT_CONNECT_TIMEOUT;
            this.contentCompressionEnabled = true;
            this.hardCancellationEnabled = true;
        }

        /**
         * Determines whether the 'Expect: 100-Continue' handshake is enabled
         * for entity enclosing methods. The purpose of the 'Expect: 100-Continue'
         * handshake is to allow a client that is sending a request message with
         * a request body to determine if the origin server is willing to
         * accept the request (based on the request headers) before the client
         * sends the request body.
         * <p>
         * The use of the 'Expect: 100-continue' handshake can result in
         * a noticeable performance improvement for entity enclosing requests
         * (such as POST and PUT) that require the target server's
         * authentication.
         * </p>
         * <p>
         * 'Expect: 100-continue' handshake should be used with caution, as it
         * may cause problems with HTTP servers and proxies that do not support
         * HTTP/1.1 protocol.
         * </p>
         * <p>
         * Default: {@code false}
         * </p>
         */
        public Builder setExpectContinueEnabled(final boolean expectContinueEnabled) {
            this.expectContinueEnabled = expectContinueEnabled;
            return this;
        }

        /**
         * Returns HTTP proxy to be used for request execution.
         * <p>
         * Default: {@code null}
         * </p>
         */
        public Builder setProxy(final HttpHost proxy) {
            this.proxy = proxy;
            return this;
        }

        /**
         * Determines the name of the cookie specification to be used for HTTP state
         * management.
         * <p>
         * Default: {@code null}
         * </p>
         */
        public Builder setCookieSpec(final String cookieSpec) {
            this.cookieSpec = cookieSpec;
            return this;
        }

        /**
         * Determines whether redirects should be handled automatically.
         * <p>
         * Default: {@code true}
         * </p>
         */
        public Builder setRedirectsEnabled(final boolean redirectsEnabled) {
            this.redirectsEnabled = redirectsEnabled;
            return this;
        }

        /**
         * Determines whether circular redirects (redirects to the same location) should
         * be allowed. The HTTP spec is not sufficiently clear whether circular redirects
         * are permitted, therefore optionally they can be enabled
         * <p>
         * Default: {@code false}
         * </p>
         */
        public Builder setCircularRedirectsAllowed(final boolean circularRedirectsAllowed) {
            this.circularRedirectsAllowed = circularRedirectsAllowed;
            return this;
        }

        /**
         * Returns the maximum number of redirects to be followed. The limit on number
         * of redirects is intended to prevent infinite loops.
         * <p>
         * Default: {@code 50}
         * </p>
         */
        public Builder setMaxRedirects(final int maxRedirects) {
            this.maxRedirects = maxRedirects;
            return this;
        }

        /**
         * Determines whether authentication should be handled automatically.
         * <p>
         * Default: {@code true}
         * </p>
         */
        public Builder setAuthenticationEnabled(final boolean authenticationEnabled) {
            this.authenticationEnabled = authenticationEnabled;
            return this;
        }

        /**
         * Determines the order of preference for supported authentication schemes
         * by their names when authenticating with the target host.
         * <p>
         * Default: {@code null}
         * </p>
         */
        public Builder setTargetPreferredAuthSchemes(final Collection<String> targetPreferredAuthSchemes) {
            this.targetPreferredAuthSchemes = targetPreferredAuthSchemes;
            return this;
        }

        /**
         * Determines the order of preference for supported authentication schemes
         * by their names when authenticating with the proxy host.
         * <p>
         * Default: {@code null}
         * </p>
         */
        public Builder setProxyPreferredAuthSchemes(final Collection<String> proxyPreferredAuthSchemes) {
            this.proxyPreferredAuthSchemes = proxyPreferredAuthSchemes;
            return this;
        }
        /**
         * Returns the connection lease request timeout used when requesting
         * a connection from the connection manager.
         * <p>
         * A timeout value of zero is interpreted as an infinite timeout.
         * </p>
         * <p>
         * Default: 3 minutes.
         * </p>
         */
        public Builder setConnectionRequestTimeout(final Timeout connectionRequestTimeout) {
            this.connectionRequestTimeout = connectionRequestTimeout;
            return this;
        }

        /**
         * @see #setConnectionRequestTimeout(Timeout)
         */
        public Builder setConnectionRequestTimeout(final long connectionRequestTimeout, final TimeUnit timeUnit) {
            this.connectionRequestTimeout = Timeout.of(connectionRequestTimeout, timeUnit);
            return this;
        }

        /**
         * Determines the timeout until a new connection is fully established.
         * This may also include transport security negotiation exchanges
         * such as {@code SSL} or {@code TLS} protocol negotiation).
         * <p>
         * A timeout value of zero is interpreted as an infinite timeout.
         * </p>
         * <p>
         * Default: 3 minutes
         * </p>
         */
        public Builder setConnectTimeout(final Timeout connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        /**
         * @see #setConnectTimeout(Timeout)
         */
        public Builder setConnectTimeout(final long connectTimeout, final TimeUnit timeUnit) {
            this.connectTimeout = Timeout.of(connectTimeout, timeUnit);
            return this;
        }

        /**
         * Determines the timeout until arrival of a response from the opposite
         * endpoint.
         * <p>
         * A timeout value of zero is interpreted as an infinite timeout.
         * </p>
         * <p>
         * Please note that response timeout may be unsupported by
         * HTTP transports with message multiplexing.
         * </p>
         * <p>
         * Default: {@code null}
         * </p>
         *
         * @since 5.0
         */
        public Builder setResponseTimeout(final Timeout responseTimeout) {
            this.responseTimeout = responseTimeout;
            return this;
        }

        /**
         * @see #setResponseTimeout(Timeout)
         */
        public Builder setResponseTimeout(final long responseTimeout, final TimeUnit timeUnit) {
            this.responseTimeout = Timeout.of(responseTimeout, timeUnit);
            return this;
        }

        /**
         * Determines the default of value of connection keep-alive time period when not
         * explicitly communicated by the origin server with a {@code Keep-Alive} response
         * header.
         * <p>
         * A negative value is interpreted as an infinite keep-alive period.
         * </p>
         * <p>
         * Default: 1 minute
         * </p>
         *
         * @since 5.0
         */
        public Builder setConnectionKeepAlive(final TimeValue connectionKeepAlive) {
            this.connectionKeepAlive = connectionKeepAlive;
            return this;
        }

        /**
         * @see #setConnectionKeepAlive(TimeValue)
         */
        public Builder setDefaultKeepAlive(final long defaultKeepAlive, final TimeUnit timeUnit) {
            this.connectionKeepAlive = TimeValue.of(defaultKeepAlive, timeUnit);
            return this;
        }

        /**
         * Determines whether the target server is requested to compress content.
         * <p>
         * Default: {@code true}
         * </p>
         *
         * @since 4.5
         */
        public Builder setContentCompressionEnabled(final boolean contentCompressionEnabled) {
            this.contentCompressionEnabled = contentCompressionEnabled;
            return this;
        }

        /**
         * Determines whether request cancellation, such as through {@code
         * Future#cancel(boolean)}, should kill the underlying connection. If this
         * option is set to false, the client will attempt to preserve the
         * underlying connection by allowing the request to complete in the
         * background, discarding the response.
         * <p>
         * Note that when this option is {@code true}, cancelling a request may
         * cause other requests to fail, if they are waiting to use the same
         * connection.
         * </p>
         * <p>
         * On HTTP/2, this option has no effect. Request cancellation will always
         * result in the stream being cancelled with a {@code RST_STREAM}. This
         * has no effect on connection reuse.
         * </p>
         * <p>
         * On non-asynchronous clients, this option has no effect. Request
         * cancellation, such as through {@code HttpUriRequestBase#cancel()}, will
         * always kill the underlying connection.
         * </p>
         * <p>
         * Default: {@code true}
         * </p>
         *
         * @since 5.0
         */
        public Builder setHardCancellationEnabled(final boolean hardCancellationEnabled) {
            this.hardCancellationEnabled = hardCancellationEnabled;
            return this;
        }

        public RequestConfig build() {
            return new RequestConfig(
                    expectContinueEnabled,
                    proxy,
                    cookieSpec,
                    redirectsEnabled,
                    circularRedirectsAllowed,
                    maxRedirects,
                    authenticationEnabled,
                    targetPreferredAuthSchemes,
                    proxyPreferredAuthSchemes,
                    connectionRequestTimeout != null ? connectionRequestTimeout : DEFAULT_CONNECTION_REQUEST_TIMEOUT,
                    connectTimeout != null ? connectTimeout : DEFAULT_CONNECT_TIMEOUT,
                    responseTimeout,
                    connectionKeepAlive != null ? connectionKeepAlive : DEFAULT_CONN_KEEP_ALIVE,
                    contentCompressionEnabled,
                    hardCancellationEnabled);
        }

    }

}
