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
     * @since 5.6
     */
    private static final TimeValue DEFAULT_HE_ATTEMPT_DELAY = TimeValue.ofMilliseconds(250);
    /**
     * @since 5.6
     */
    private static final TimeValue DEFAULT_HE_OTHER_FAMILY_DELAY = TimeValue.ofMilliseconds(50);

    public static final ConnectionConfig DEFAULT = new Builder().build();

    private final Timeout connectTimeout;
    private final Timeout socketTimeout;
    private final Timeout idleTimeout;
    private final TimeValue validateAfterInactivity;
    private final TimeValue timeToLive;

    /**
     * @since 5.6
     */
    private final boolean staggeredConnectEnabled;
    /**
     * @since 5.6
     */
    private final TimeValue happyEyeballsAttemptDelay;
    /**
     * @since 5.6
     */
    private final TimeValue happyEyeballsOtherFamilyDelay;
    /**
     * @since 5.6
     */
    private final ProtocolFamilyPreference protocolFamilyPreference;

    /**
     * Intended for CDI compatibility
     */
    protected ConnectionConfig() {
        this(DEFAULT_CONNECT_TIMEOUT, null, null, null, null, false, DEFAULT_HE_ATTEMPT_DELAY, DEFAULT_HE_OTHER_FAMILY_DELAY, ProtocolFamilyPreference.INTERLEAVE);
    }

    ConnectionConfig(
            final Timeout connectTimeout,
            final Timeout socketTimeout,
            final Timeout idleTimeout,
            final TimeValue validateAfterInactivity,
            final TimeValue timeToLive,
            final boolean staggeredConnectEnabled,
            final TimeValue happyEyeballsAttemptDelay,
            final TimeValue happyEyeballsOtherFamilyDelay,
            final ProtocolFamilyPreference protocolFamilyPreference) {
        super();
        this.connectTimeout = connectTimeout;
        this.socketTimeout = socketTimeout;
        this.idleTimeout = idleTimeout;
        this.validateAfterInactivity = validateAfterInactivity;
        this.timeToLive = timeToLive;
        this.staggeredConnectEnabled = staggeredConnectEnabled;
        this.happyEyeballsAttemptDelay = happyEyeballsAttemptDelay != null ? happyEyeballsAttemptDelay : DEFAULT_HE_ATTEMPT_DELAY;
        this.happyEyeballsOtherFamilyDelay = happyEyeballsOtherFamilyDelay != null ? happyEyeballsOtherFamilyDelay : DEFAULT_HE_OTHER_FAMILY_DELAY;
        this.protocolFamilyPreference = protocolFamilyPreference != null ? protocolFamilyPreference : ProtocolFamilyPreference.INTERLEAVE;
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
     * @see Builder#setIdleTimeout(Timeout)
     */
    public Timeout getIdleTimeout() {
        return idleTimeout;
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

    /**
     * Whether staggered (Happy Eyeballs–style) connection attempts are enabled.
     *
     * @see Builder#setStaggeredConnectEnabled(boolean)
     * @since 5.6
     */
    public boolean isStaggeredConnectEnabled() {
        return staggeredConnectEnabled;
    }

    /**
     * Delay between subsequent staggered connection attempts.
     *
     * @see Builder#setHappyEyeballsAttemptDelay(TimeValue)
     * @since 5.6
     */
    public TimeValue getHappyEyeballsAttemptDelay() {
        return happyEyeballsAttemptDelay;
    }

    /**
     * Initial delay before launching the first address of the other protocol family.
     *
     * @see Builder#setHappyEyeballsOtherFamilyDelay(TimeValue)
     * @since 5.6
     */
    public TimeValue getHappyEyeballsOtherFamilyDelay() {
        return happyEyeballsOtherFamilyDelay;
    }

    /**
     * Protocol family preference controlling address selection and ordering.
     *
     * @see Builder#setProtocolFamilyPreference(ProtocolFamilyPreference)
     * @since 5.6
     */
    public ProtocolFamilyPreference getProtocolFamilyPreference() {
        return protocolFamilyPreference;
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
        builder.append(", staggeredConnectEnabled=").append(staggeredConnectEnabled);
        builder.append(", happyEyeballsAttemptDelay=").append(happyEyeballsAttemptDelay);
        builder.append(", happyEyeballsOtherFamilyDelay=").append(happyEyeballsOtherFamilyDelay);
        builder.append(", protocolFamilyPreference=").append(protocolFamilyPreference);
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
                .setTimeToLive(config.getTimeToLive())
                .setStaggeredConnectEnabled(config.isStaggeredConnectEnabled())
                .setHappyEyeballsAttemptDelay(config.getHappyEyeballsAttemptDelay())
                .setHappyEyeballsOtherFamilyDelay(config.getHappyEyeballsOtherFamilyDelay())
                .setProtocolFamilyPreference(config.getProtocolFamilyPreference());
    }

    public static class Builder {

        private Timeout socketTimeout;
        private Timeout connectTimeout;
        private Timeout idleTimeout;
        private TimeValue validateAfterInactivity;
        private TimeValue timeToLive;

        // New fields (defaults)
        private boolean staggeredConnectEnabled = false; // disabled by default
        private TimeValue happyEyeballsAttemptDelay = DEFAULT_HE_ATTEMPT_DELAY;
        private TimeValue happyEyeballsOtherFamilyDelay = DEFAULT_HE_OTHER_FAMILY_DELAY;
        private ProtocolFamilyPreference protocolFamilyPreference = ProtocolFamilyPreference.INTERLEAVE;

        Builder() {
            super();
            this.connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        }

        /**
         * @return this instance.
         * @see #setSocketTimeout(Timeout)
         */
        public Builder setSocketTimeout(final int soTimeout, final TimeUnit timeUnit) {
            this.socketTimeout = Timeout.of(soTimeout, timeUnit);
            return this;
        }

        /**
         * Determines the default socket timeout value for I/O operations on
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
         * @return this instance.
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
         *
         * @return this instance.
         */
        public Builder setConnectTimeout(final Timeout connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        /**
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
         * @return this instance.
         *
         * @since 5.6
         */
        public Builder setIdleTimeout(final Timeout idleTimeout) {
            this.idleTimeout = idleTimeout;
            return this;
        }

        /**
         * @return this instance.
         * @see #setIdleTimeout(Timeout)
         */
        public Builder setIdleTimeout(final long idleTimeout, final TimeUnit timeUnit) {
            this.idleTimeout = Timeout.of(idleTimeout, timeUnit);
            return this;
        }

        /**
         * Defines period of inactivity after which persistent connections must
         * be re-validated prior to being leased to the consumer. Negative values passed
         * to this method disable connection validation.
         * <p>
         * Default: {@code null} (undefined)
         * </p>
         *
         * @return this instance.
         */
        public Builder setValidateAfterInactivity(final TimeValue validateAfterInactivity) {
            this.validateAfterInactivity = validateAfterInactivity;
            return this;
        }

        /**
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
         * @return this instance.
         */
        public Builder setTimeToLive(final TimeValue timeToLive) {
            this.timeToLive = timeToLive;
            return this;
        }

        /**
         * @return this instance.
         * @see #setTimeToLive(TimeValue)
         */
        public Builder setTimeToLive(final long timeToLive, final TimeUnit timeUnit) {
            this.timeToLive = TimeValue.of(timeToLive, timeUnit);
            return this;
        }

        /**
         * Enables or disables staggered (Happy Eyeballs–style) connection attempts.
         *
         * @since 5.6
         * @return this instance.
         */
        public Builder setStaggeredConnectEnabled(final boolean enabled) {
            this.staggeredConnectEnabled = enabled;
            return this;
        }

        /**
         * Sets the delay between staggered connection attempts.
         *
         * @since 5.6
         * @return this instance.
         */
        public Builder setHappyEyeballsAttemptDelay(final TimeValue delay) {
            this.happyEyeballsAttemptDelay = delay;
            return this;
        }

        /**
         * Sets the initial delay before launching the first address of the other
         * protocol family (IPv6 vs IPv4) when interleaving attempts.
         *
         * @since 5.6
         * @return this instance.
         */
        public Builder setHappyEyeballsOtherFamilyDelay(final TimeValue delay) {
            this.happyEyeballsOtherFamilyDelay = delay;
            return this;
        }

        /**
         * Sets the protocol family preference that guides address selection and ordering.
         *
         * @since 5.6
         * @return this instance.
         */
        public Builder setProtocolFamilyPreference(final ProtocolFamilyPreference preference) {
            this.protocolFamilyPreference = preference;
            return this;
        }

        public ConnectionConfig build() {
            return new ConnectionConfig(
                    connectTimeout != null ? connectTimeout : DEFAULT_CONNECT_TIMEOUT,
                    socketTimeout,
                    idleTimeout,
                    validateAfterInactivity,
                    timeToLive,
                    staggeredConnectEnabled,
                    happyEyeballsAttemptDelay != null ? happyEyeballsAttemptDelay : DEFAULT_HE_ATTEMPT_DELAY,
                    happyEyeballsOtherFamilyDelay != null ? happyEyeballsOtherFamilyDelay : DEFAULT_HE_OTHER_FAMILY_DELAY,
                    protocolFamilyPreference != null ? protocolFamilyPreference : ProtocolFamilyPreference.INTERLEAVE
            );
        }

    }

}
