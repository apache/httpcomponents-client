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

    public static final ConnectionConfig DEFAULT = new Builder().build();

    private final Timeout connectTimeout;
    private final Timeout socketTimeout;
    private final TimeValue validateAfterInactivity;
    private final TimeValue timeToLive;

    /**
     * Intended for CDI compatibility
     */
    protected ConnectionConfig() {
        this(DEFAULT_CONNECT_TIMEOUT, null, null, null);
    }

    ConnectionConfig(
            final Timeout connectTimeout,
            final Timeout socketTimeout,
            final TimeValue validateAfterInactivity,
            final TimeValue timeToLive) {
        super();
        this.connectTimeout = connectTimeout;
        this.socketTimeout = socketTimeout;
        this.validateAfterInactivity = validateAfterInactivity;
        this.timeToLive = timeToLive;
    }

    /**
     * @see Builder#setSocketTimeout(Timeout)
     */
    public Timeout getSocketTimeout() {
        return socketTimeout;
    }

    /**
     * @see Builder#setConnectTimeout(Timeout)
     */
    public Timeout getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * @see Builder#setValidateAfterInactivity(TimeValue)
     */
    public TimeValue getValidateAfterInactivity() {
        return validateAfterInactivity;
    }

    /**
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
        builder.append(", validateAfterInactivity=").append(validateAfterInactivity);
        builder.append(", timeToLive=").append(timeToLive);
        builder.append("]");
        return builder.toString();
    }

    public static ConnectionConfig.Builder custom() {
        return new Builder();
    }

    public static ConnectionConfig.Builder copy(final ConnectionConfig config) {
        return new Builder()
                .setConnectTimeout(config.getConnectTimeout())
                .setSocketTimeout(config.getSocketTimeout())
                .setValidateAfterInactivity(config.getValidateAfterInactivity())
                .setTimeToLive(config.getTimeToLive());
    }

    public static class Builder {

        private Timeout socketTimeout;
        private Timeout connectTimeout;
        private TimeValue validateAfterInactivity;
        private TimeValue timeToLive;

        Builder() {
            super();
            this.connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        }

        /**
         * @see #setSocketTimeout(Timeout)
         */
        public Builder setSocketTimeout(final int soTimeout, final TimeUnit timeUnit) {
            this.socketTimeout = Timeout.of(soTimeout, timeUnit);
            return this;
        }

        /**
         * Determines the default socket timeout value for I/O operations.
         * <p>
         * Default: {@code null} (undefined)
         * </p>
         *
         * @return the default socket timeout value for I/O operations.
         */
        public Builder setSocketTimeout(final Timeout soTimeout) {
            this.socketTimeout = soTimeout;
            return this;
        }

        /**
         * Determines the timeout until a new connection is fully established.
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
         * Defines period of inactivity after which persistent connections must
         * be re-validated prior to being leased to the consumer. Negative values passed
         * to this method disable connection validation.
         * <p>
         * Default: {@code null} (undefined)
         * </p>
         */
        public Builder setValidateAfterInactivity(final TimeValue validateAfterInactivity) {
            this.validateAfterInactivity = validateAfterInactivity;
            return this;
        }

        /**
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
         */
        public Builder setTimeToLive(final TimeValue timeToLive) {
            this.timeToLive = timeToLive;
            return this;
        }

        /**
         * @see #setTimeToLive(TimeValue)
         */
        public Builder setTimeToLive(final long timeToLive, final TimeUnit timeUnit) {
            this.timeToLive = TimeValue.of(timeToLive, timeUnit);
            return this;
        }

        public ConnectionConfig build() {
            return new ConnectionConfig(
                    connectTimeout != null ? connectTimeout : DEFAULT_CONNECT_TIMEOUT,
                    socketTimeout,
                    validateAfterInactivity,
                    timeToLive);
        }

    }

}
