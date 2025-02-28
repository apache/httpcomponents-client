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

package org.apache.hc.client5.http.auth.gss;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;

/**
 * Immutable class encapsulating GSS configuration options for the new mutual auth capable
 * SpnegoScheme.
 *
 * Unlike the deprecated {@link KerberosConfig}, this class uses explicit defaults, and
 * primitive booleans.
 *
 * Compared to {@link KerberosConfig} stripPort has been changed to addPort, and the default is now
 * false (same effect). The default for useCanonicalHostname has been changed to false from true.
 *
 * @since 5.5
 *
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class GssConfig implements Cloneable {


    public static final GssConfig DEFAULT = new Builder().build();
    public static final GssConfig LEGACY =
            new Builder().setIgnoreMissingToken(true).setRequestMutualAuth(false).build();

    private final boolean addPort;
    private final boolean useCanonicalHostname;
    private final boolean requestMutualAuth;
    private final boolean requestDelegCreds;
    private final boolean ignoreMissingToken;

    /**
     * Intended for CDI compatibility
    */
    protected GssConfig() {
        this(false, false, true, false, false);
    }

    GssConfig(
            final boolean addPort,
            final boolean useCanonicalHostname,
            final boolean requestMutualAuth,
            final boolean requestDelegCreds,
            final boolean ignoreMissingToken) {
        super();
        this.addPort = addPort;
        this.useCanonicalHostname = useCanonicalHostname;
        this.requestMutualAuth = requestMutualAuth;
        this.requestDelegCreds = requestDelegCreds;
        this.ignoreMissingToken = ignoreMissingToken;
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

    public boolean isIgnoreMissingToken() {
        return ignoreMissingToken;
    }

    @Override
    protected GssConfig clone() throws CloneNotSupportedException {
        return (GssConfig) super.clone();
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("[");
        builder.append("addPort=").append(addPort);
        builder.append(", useCanonicalHostname=").append(useCanonicalHostname);
        builder.append(", requestDelegCreds=").append(requestDelegCreds);
        builder.append(", requestMutualAuth=").append(requestMutualAuth);
        builder.append(", ignoreMissingToken=").append(ignoreMissingToken);
        builder.append("]");
        return builder.toString();
    }

    public static GssConfig.Builder custom() {
        return new Builder();
    }

    public static GssConfig.Builder copy(final GssConfig config) {
        return new Builder()
                .setAddPort(config.isAddPort())
                .setUseCanonicalHostname(config.isUseCanonicalHostname())
                .setRequestDelegCreds(config.isRequestDelegCreds())
                .setRequestMutualAuth(config.isRequestMutualAuth())
                .setIgnoreMissingToken(config.isIgnoreMissingToken());
    }

    public static class Builder {

        private boolean addPort = false;
        private boolean useCanonicalHostname = false;
        private boolean requestMutualAuth = true;
        private boolean requestDelegCreds = false;
        private boolean ignoreMissingToken = false;


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

        public Builder setIgnoreMissingToken(final boolean ignoreMissingToken) {
            this.ignoreMissingToken = ignoreMissingToken;
            return this;
        }

        public GssConfig build() {
            if (requestMutualAuth && ignoreMissingToken) {
                throw new IllegalArgumentException("If requestMutualAuth is set then ignoreMissingToken must not be set");
            }
            return new GssConfig(
                    addPort,
                    useCanonicalHostname,
                    requestMutualAuth,
                    requestDelegCreds,
                    ignoreMissingToken
                    );
        }

    }

}
