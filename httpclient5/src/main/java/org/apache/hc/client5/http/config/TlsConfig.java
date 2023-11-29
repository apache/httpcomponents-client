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

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ssl.TLS;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.util.Timeout;

/**
 * Immutable class encapsulating TLS protocol settings.
 *
 * @since 5.2
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class TlsConfig implements Cloneable {

    public static final TlsConfig DEFAULT = new Builder().build();

    private final Timeout handshakeTimeout;
    private final String[] supportedProtocols;
    private final String[] supportedCipherSuites;
    private final HttpVersionPolicy httpVersionPolicy;

    /**
     * Intended for CDI compatibility
     */
    protected TlsConfig() {
        this(null, null, null, null);
    }

    TlsConfig(
            final Timeout handshakeTimeout,
            final String[] supportedProtocols,
            final String[] supportedCipherSuites,
            final HttpVersionPolicy httpVersionPolicy) {
        super();
        this.handshakeTimeout = handshakeTimeout;
        this.supportedProtocols = supportedProtocols;
        this.supportedCipherSuites = supportedCipherSuites;
        this.httpVersionPolicy = httpVersionPolicy;
    }

    /**
     * @see Builder#setHandshakeTimeout(Timeout)
     */
    public Timeout getHandshakeTimeout() {
        return handshakeTimeout;
    }

    /**
     * @see Builder#setSupportedProtocols(String...)
     */
    public String[] getSupportedProtocols() {
        return supportedProtocols != null ? supportedProtocols.clone() : null;
    }

    /**
     * @see Builder#setSupportedCipherSuites(String...)
     */
    public String[] getSupportedCipherSuites() {
        return supportedCipherSuites != null ? supportedCipherSuites.clone() : null;
    }

    /**
     * @see Builder#setVersionPolicy(HttpVersionPolicy)
     */
    public HttpVersionPolicy getHttpVersionPolicy() {
        return httpVersionPolicy;
    }

    @Override
    protected TlsConfig clone() throws CloneNotSupportedException {
        return (TlsConfig) super.clone();
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("[");
        builder.append("handshakeTimeout=").append(handshakeTimeout);
        builder.append(", supportedProtocols=").append(Arrays.toString(supportedProtocols));
        builder.append(", supportedCipherSuites=").append(Arrays.toString(supportedCipherSuites));
        builder.append(", httpVersionPolicy=").append(httpVersionPolicy);
        builder.append("]");
        return builder.toString();
    }

    public static TlsConfig.Builder custom() {
        return new Builder();
    }

    public static TlsConfig.Builder copy(final TlsConfig config) {
        return new Builder()
                .setHandshakeTimeout(config.getHandshakeTimeout())
                .setSupportedProtocols(config.getSupportedProtocols())
                .setSupportedCipherSuites(config.getSupportedCipherSuites())
                .setVersionPolicy(config.getHttpVersionPolicy());
    }

    public static class Builder {

        private Timeout handshakeTimeout;
        private String[] supportedProtocols;
        private String[] supportedCipherSuites;
        private HttpVersionPolicy versionPolicy;

        /**
         * Determines the timeout used by TLS session negotiation exchanges (session handshake).
         * <p>
         * A timeout value of zero is interpreted as an infinite timeout.
         * </p>
         * <p>
         * Default: {@code null} (undefined)
         * </p>
         */
        public Builder setHandshakeTimeout(final Timeout handshakeTimeout) {
            this.handshakeTimeout = handshakeTimeout;
            return this;
        }

        /**
         * @see #setHandshakeTimeout(Timeout)
         */
        public Builder setHandshakeTimeout(final long handshakeTimeout, final TimeUnit timeUnit) {
            this.handshakeTimeout = Timeout.of(handshakeTimeout, timeUnit);
            return this;
        }

        /**
         * Determines supported TLS protocols.
         * <p>
         * Default: {@code null} (undefined)
         * </p>
         */
        public Builder setSupportedProtocols(final String... supportedProtocols) {
            this.supportedProtocols = supportedProtocols;
            return this;
        }

        /**
         * Determines supported TLS protocols.
         * <p>
         * Default: {@code null} (undefined)
         * </p>
         */
        public Builder setSupportedProtocols(final TLS... supportedProtocols) {
            this.supportedProtocols = new String[supportedProtocols.length];
            for (int i = 0; i < supportedProtocols.length; i++) {
                final TLS protocol = supportedProtocols[i];
                if (protocol != null) {
                    this.supportedProtocols[i] = protocol.id;
                }
            }
            return this;
        }

        /**
         * Determines supported cipher suites.
         * <p>
         * Default: {@code null} (undefined)
         * </p>
         */
        public Builder setSupportedCipherSuites(final String... supportedCipherSuites) {
            this.supportedCipherSuites = supportedCipherSuites;
            return this;
        }

        /**
         * Determines the HTTP protocol policy. By default, connections are expected to use TLS ALPN
         * extension to negotiate the application protocol to be used by both endpoints.
         * <p>
         * Default: {@link HttpVersionPolicy#NEGOTIATE}
         * </p>
         */
        public Builder setVersionPolicy(final HttpVersionPolicy versionPolicy) {
            this.versionPolicy = versionPolicy;
            return this;
        }

        public TlsConfig build() {
            return new TlsConfig(
                    handshakeTimeout,
                    supportedProtocols,
                    supportedCipherSuites,
                    versionPolicy != null ? versionPolicy : HttpVersionPolicy.NEGOTIATE);
        }

    }

}
