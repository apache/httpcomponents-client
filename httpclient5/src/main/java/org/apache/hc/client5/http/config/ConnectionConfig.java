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

import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

/**
 * Immutable class encapsulating connection initialization and management settings.
 *
 * @since 5.2
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class ConnectionConfig implements Cloneable {

    private static final Timeout DEFAULT_CONNECT_TIMEOUT = Timeout.ofMinutes(3);

    /**
     * The default connection configuration.
     * <ul>
     *  <li>Timeout connectTimeout: 3 minutes</li>
     *  <li>Timeout socketTimeout: {@code null} (undefined)</li>
     *  <li>Timeout idleTimeout: {@code null} (undefined)</li>
     *  <li>TimeValue validateAfterInactivity: {@code null} (undefined)</li>
     *  <li>TimeValue timeToLive: {@code null} (undefined)</li>
     * </ul>
     */
    public static final ConnectionConfig DEFAULT = new Builder().build();

    private final Timeout connectTimeout;
    private final Timeout socketTimeout;
    private final Timeout idleTimeout;
    private final TimeValue validateAfterInactivity;
    private final TimeValue timeToLive;

    /**
     * Intended for CDI compatibility
     */
    protected ConnectionConfig() {
        this(DEFAULT_CONNECT_TIMEOUT, null, null, null, null);
    }

    ConnectionConfig(
            final Timeout connectTimeout,
            final Timeout socketTimeout,
            final Timeout idleTimeout,
            final TimeValue validateAfterInactivity,
            final TimeValue timeToLive) {
        super();
        this.connectTimeout = connectTimeout;
        this.socketTimeout = socketTimeout;
        this.idleTimeout = idleTimeout;
        this.validateAfterInactivity = validateAfterInactivity;
        this.timeToLive = timeToLive;
    }

    /**
     * Gets the default socket timeout value for I/O operations on connections created by this configuration.
     * A timeout value of zero is interpreted as an infinite timeout.
     *
     * @return the default socket timeout value, defaults to null.
     * @see Builder#setSocketTimeout(Timeout)
     */
    public Timeout getSocketTimeout() {
        return socketTimeout;
    }

    /**
     * Gets the timeout until the target endpoint acknowledges accepting the connection request.
     * <p>
     * Note that isn't the same time as the new connection being fully established. An HTTPS connection cannot be considered fully established until the TLS
     * handshake has been successfully completed.
     * </p>
     * <p>
     * A timeout value of zero is interpreted as an infinite timeout.
     * </p>
     *
     * @return the timeout until the target endpoint acknowledges accepting the connection request, defaults to 3 minutes.
     * @see Builder#setConnectTimeout(Timeout)
     */
    public Timeout getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * Gets the maximum period of idleness for a connection.
     * <p>
     * Implementations can use this value to discard connections that have been idle for too long.
     * </p>
     *
     * @return the maximum period of idleness for a connection, defaults to null.
     * @see Builder#setIdleTimeout(Timeout)
     */
    public Timeout getIdleTimeout() {
        return idleTimeout;
    }

    /**
     * Gets the period of inactivity after which persistent connections must be re-validated.
     *
     * @return the period of inactivity after which persistent connections must be re-validated, defaults to null.
     * @see Builder#setValidateAfterInactivity(TimeValue)
     */
    public TimeValue getValidateAfterInactivity() {
        return validateAfterInactivity;
    }

    /**
     * Gets the total span of time connections can be kept alive or execute requests.
     *
     * @return the total span of time connections can be kept alive or execute requests, defaults to null.
     * @see Builder#setTimeToLive(TimeValue) (TimeValue)
     */
    public TimeValue getTimeToLive() {
        return timeToLive;
    }

    @Override
    protected ConnectionConfig clone() throws CloneNotSupportedException {
        return (ConnectionConfig) super.clone();
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("[");
        builder.append("connectTimeout=").append(connectTimeout);
        builder.append(", socketTimeout=").append(socketTimeout);
        builder.append(", idleTimeout=").append(idleTimeout);
        builder.append(", validateAfterInactivity=").append(validateAfterInactivity);
        builder.append(", timeToLive=").append(timeToLive);
        builder.append("]");
        return builder.toString();
    }

    /**
     * Creates a new builder for {@link ConnectionConfig}.
     *
     * @return a new builder for {@link ConnectionConfig}.
     */
    public static ConnectionConfig.Builder custom() {
        return new Builder();
    }

    /**
     * Creates a new builder for {@link ConnectionConfig} based on an existing instance.
     *
     * @param config the instance to copy.
     * @return a new builder for {@link ConnectionConfig}.
     */
    public static ConnectionConfig.Builder copy(final ConnectionConfig config) {
        return new Builder()
                .setConnectTimeout(config.getConnectTimeout())
                .setSocketTimeout(config.getSocketTimeout())
                .setValidateAfterInactivity(config.getValidateAfterInactivity())
                .setTimeToLive(config.getTimeToLive());
    }

    /**
     * Builder for {@link ConnectionConfig}.
     */
    public static class Builder {

        private Timeout socketTimeout;
        private Timeout connectTimeout;
        private Timeout idleTimeout;
        private TimeValue validateAfterInactivity;
        private TimeValue timeToLive;

        Builder() {
            super();
            this.connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        }

        /**
         * Sets the default socket timeout value for I/O operations on connections created by this configuration.
         *
         * @param soTimeout The default socket timeout value for I/O operations.
         * @param timeUnit The time unit of the soTimeout parameter.
         * @return this instance.
         * @see #setSocketTimeout(Timeout)
         */
        public Builder setSocketTimeout(final int soTimeout, final TimeUnit timeUnit) {
            this.socketTimeout = Timeout.of(soTimeout, timeUnit);
            return this;
        }

        /**
         * Sets the default socket timeout value for I/O operations on
         * connections created by this configuration.
         * A timeout value of zero is interpreted as an infinite timeout.
         * <p>
         * This value acts as a baseline at the connection management layer.
         * This parameter overrides the socket timeout setting applied at the I/O layer
         * and in its tuen can overridden by settings applied at the protocol layer
         * for the duration of a message exchange.
         * </p>
         * <p>
         * Default: {@code null} (undefined)
         * </p>
         *
         * @param soTimeout The default socket timeout value for I/O operations.
         * @return this instance.
         */
        public Builder setSocketTimeout(final Timeout soTimeout) {
            this.socketTimeout = soTimeout;
            return this;
        }

        /**
         * Sets the timeout until a new connection is fully established.
         * <p>
         * A timeout value of zero is interpreted as an infinite timeout.
         * </p>
         * <p>
         * Default: 3 minutes
         * </p>
         *
         * @param connectTimeout The timeout until a new connection is fully established.
         * @return this instance.
         */
        public Builder setConnectTimeout(final Timeout connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        /**
         * Sets the timeout until a new connection is fully established.
         *
         * @param connectTimeout The timeout until a new connection is fully established.
         * @param timeUnit The time unit of the timeout parameter.
         * @return this instance.
         * @see #setConnectTimeout(Timeout)
         */
        public Builder setConnectTimeout(final long connectTimeout, final TimeUnit timeUnit) {
            this.connectTimeout = Timeout.of(connectTimeout, timeUnit);
            return this;
        }

        /**
         * Determines the maximum period of idleness for a connection.
         * Connections that are idle for longer than {@code idleTimeout} are no
         * longer eligible for reuse.
         * <p>
         * A timeout value of zero is interpreted as an infinite timeout.
         * </p>
         * <p>
         * Default: {@code null} (undefined)
         * </p>
         *
         * @param idleTimeout The maximum period of idleness for a connection.
         * @return this instance.
         *
         * @since 5.6
         */
        public Builder setIdleTimeout(final Timeout idleTimeout) {
            this.idleTimeout = idleTimeout;
            return this;
        }

        /**
         * Sets the maximum period of idleness for a connection.
         * <p>
         * Connections that are idle for longer than {@code idleTimeout} are no longer eligible for reuse.
         * </p>
         *
         * @param idleTimeout The maximum period of idleness for a connection.
         * @param timeUnit
         * @return this instance.
         * @see #setIdleTimeout(Timeout)
         */
        public Builder setIdleTimeout(final long idleTimeout, final TimeUnit timeUnit) {
            this.idleTimeout = Timeout.of(idleTimeout, timeUnit);
            return this;
        }

        /**
         * Sets the period of inactivity after which persistent connections must
         * be re-validated prior to being leased to the consumer. Negative values passed
         * to this method disable connection validation.
         * <p>
         * Default: {@code null} (undefined)
         * </p>
         *
         * @param validateAfterInactivity The period of inactivity after which persistent connections must be re-validated.
         * @return this instance.
         */
        public Builder setValidateAfterInactivity(final TimeValue validateAfterInactivity) {
            this.validateAfterInactivity = validateAfterInactivity;
            return this;
        }

        /**
         * Sets the period of inactivity after which persistent connections must be re-validated prior to being leased to the consumer.
         *
         * @param validateAfterInactivity The period of inactivity after which persistent connections must be re-validated.
         * @param timeUnit The time unit of the validateAfterInactivity parameter.
         * @return this instance.
         * @see #setValidateAfterInactivity(TimeValue)
         */
        public Builder setValidateAfterInactivity(final long validateAfterInactivity, final TimeUnit timeUnit) {
            this.validateAfterInactivity = TimeValue.of(validateAfterInactivity, timeUnit);
            return this;
        }

        /**
         * Defines the total span of time connections can be kept alive or execute requests.
         * <p>
         * Default: {@code null} (undefined)
         * </p>
         *
         * @param timeToLive The total span of time connections can be kept alive or execute requests.
         * @return this instance.
         */
        public Builder setTimeToLive(final TimeValue timeToLive) {
            this.timeToLive = timeToLive;
            return this;
        }

        /**
         * Sets the total span of time connections can be kept alive or execute requests.
         *
         * @param timeToLive The total span of time connections can be kept alive or execute requests.
         * @param timeUnit The time unit of the timeToLive parameter.
         * @return this instance.
         * @see #setTimeToLive(TimeValue)
         */
        public Builder setTimeToLive(final long timeToLive, final TimeUnit timeUnit) {
            this.timeToLive = TimeValue.of(timeToLive, timeUnit);
            return this;
        }

        /**
         * Builds a new {@link ConnectionConfig} instance based on the values provided to the setters methods.
         *
         * @return a new {@link ConnectionConfig} instance.
         */
        public ConnectionConfig build() {
            return new ConnectionConfig(
                    connectTimeout != null ? connectTimeout : DEFAULT_CONNECT_TIMEOUT,
                    socketTimeout,
                    idleTimeout,
                    validateAfterInactivity,
                    timeToLive);
        }

    }

}
