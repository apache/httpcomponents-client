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
 * Immutable class encapsulating Kerberos configuration options for MutualSpnegoScheme.
 *
 * Unlike the deprecated {@link KerberosConfig}, this class uses explicit defaults, and
 * primitive booleans.
 *
 * Compared to {@link KerberosConfig} stripPort has been changed toAddPort, and the default is now
 * false (same effect). The default for useCanonicalHostname has been changed to false from true.
 *
 * @since 5.5
 *
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class MutualKerberosConfig implements Cloneable {


    public static final MutualKerberosConfig DEFAULT = new Builder().build();

    private final boolean addPort;
    private final boolean useCanonicalHostname;
    private final boolean requestMutualAuth;
    private final boolean requestDelegCreds;

    /**
     * Intended for CDI compatibility
    */
    protected MutualKerberosConfig() {
        this(false, false, true, false);
    }

    MutualKerberosConfig(
            final boolean stripPort,
            final boolean useCanonicalHostname,
            final boolean requestMutualAuth,
            final boolean requestDelegCreds) {
        super();
        this.addPort = stripPort;
        this.useCanonicalHostname = useCanonicalHostname;
        this.requestMutualAuth = requestMutualAuth;
        this.requestDelegCreds = requestDelegCreds;
    }

    public boolean isAddPort() {
        return addPort;
    }

    public boolean isUseCanonicalHostname() {
        return useCanonicalHostname;
    }

    public boolean isRequestDelegCreds() {
        return requestDelegCreds;
    }

    public boolean isRequestMutualAuth() {
        return requestMutualAuth;
    }

    @Override
    protected MutualKerberosConfig clone() throws CloneNotSupportedException {
        return (MutualKerberosConfig) super.clone();
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("[");
        builder.append("addPort=").append(addPort);
        builder.append(", useCanonicalHostname=").append(useCanonicalHostname);
        builder.append(", requestDelegCreds=").append(requestDelegCreds);
        builder.append(", requestMutualAuth=").append(requestMutualAuth);
        builder.append("]");
        return builder.toString();
    }

    public static MutualKerberosConfig.Builder custom() {
        return new Builder();
    }

    public static MutualKerberosConfig.Builder copy(final MutualKerberosConfig config) {
        return new Builder()
                .setAddPort(config.isAddPort())
                .setUseCanonicalHostname(config.isUseCanonicalHostname())
                .setRequestDelegCreds(config.isRequestDelegCreds())
                .setRequestMutualAuth(config.isRequestMutualAuth());
    }

    public static class Builder {

        private boolean addPort = false;
        private boolean useCanonicalHostname = false;
        private boolean requestMutualAuth = true;
        private boolean requestDelegCreds = false;

        Builder() {
            super();
        }

        public Builder setAddPort(final boolean addPort) {
            this.addPort = addPort;
            return this;
        }

        public Builder setUseCanonicalHostname(final boolean useCanonicalHostname) {
            this.useCanonicalHostname = useCanonicalHostname;
            return this;
        }

        public Builder setRequestMutualAuth(final boolean requestMutualAuth) {
            this.requestMutualAuth = requestMutualAuth;
            return this;
        }

        public Builder setRequestDelegCreds(final boolean requuestDelegCreds) {
            this.requestDelegCreds = requuestDelegCreds;
            return this;
        }

        public MutualKerberosConfig build() {
            return new MutualKerberosConfig(
                    addPort,
                    useCanonicalHostname,
                    requestMutualAuth,
                    requestDelegCreds
                    );
        }

    }

}
