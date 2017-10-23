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

package org.apache.hc.client5.http.auth;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;

/**
 *  Immutable class encapsulating Kerberos configuration options.
 *
 *  @since 4.6
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class KerberosConfig implements Cloneable {

    public enum Option {

        DEFAULT,
        ENABLE,
        DISABLE

    }

    public static final KerberosConfig DEFAULT = new Builder().build();

    private final Option stripPort;
    private final Option useCanonicalHostname;
    private final Option requestDelegCreds;

    /**
     * Intended for CDI compatibility
    */
    protected KerberosConfig() {
        this(Option.DEFAULT, Option.DEFAULT, Option.DEFAULT);
    }

    KerberosConfig(
            final Option stripPort,
            final Option useCanonicalHostname,
            final Option requestDelegCreds) {
        super();
        this.stripPort = stripPort;
        this.useCanonicalHostname = useCanonicalHostname;
        this.requestDelegCreds = requestDelegCreds;
    }

    public Option getStripPort() {
        return stripPort;
    }

    public Option getUseCanonicalHostname() {
        return useCanonicalHostname;
    }

    public Option getRequestDelegCreds() {
        return requestDelegCreds;
    }

    @Override
    protected KerberosConfig clone() throws CloneNotSupportedException {
        return (KerberosConfig) super.clone();
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("[");
        builder.append("stripPort=").append(stripPort);
        builder.append(", useCanonicalHostname=").append(useCanonicalHostname);
        builder.append(", requestDelegCreds=").append(requestDelegCreds);
        builder.append("]");
        return builder.toString();
    }

    public static KerberosConfig.Builder custom() {
        return new Builder();
    }

    public static KerberosConfig.Builder copy(final KerberosConfig config) {
        return new Builder()
                .setStripPort(config.getStripPort())
                .setUseCanonicalHostname(config.getUseCanonicalHostname())
                .setRequestDelegCreds(config.getRequestDelegCreds());
    }

    public static class Builder {

        private Option stripPort;
        private Option useCanonicalHostname;
        private Option requestDelegCreds;

        Builder() {
            super();
            this.stripPort = Option.DEFAULT;
            this.useCanonicalHostname = Option.DEFAULT;
            this.requestDelegCreds = Option.DEFAULT;
        }

        public Builder setStripPort(final Option stripPort) {
            this.stripPort = stripPort;
            return this;
        }

        public Builder setStripPort(final boolean stripPort) {
            this.stripPort = stripPort ? Option.ENABLE : Option.DISABLE;
            return this;
        }

        public Builder setUseCanonicalHostname(final Option useCanonicalHostname) {
            this.useCanonicalHostname = useCanonicalHostname;
            return this;
        }

        public Builder setUseCanonicalHostname(final boolean useCanonicalHostname) {
            this.useCanonicalHostname = useCanonicalHostname ? Option.ENABLE : Option.DISABLE;
            return this;
        }

        public Builder setRequestDelegCreds(final Option requestDelegCreds) {
            this.requestDelegCreds = requestDelegCreds;
            return this;
        }

        public KerberosConfig build() {
            return new KerberosConfig(
                    stripPort,
                    useCanonicalHostname,
                    requestDelegCreds);
        }

    }

}
