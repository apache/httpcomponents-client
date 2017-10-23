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
package org.apache.http.impl.auth;

import org.apache.http.annotation.Contract;
import org.apache.http.annotation.ThreadingBehavior;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthSchemeFactory;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.KerberosConfig;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;

/**
 * {@link AuthSchemeProvider} implementation that creates and initializes
 * {@link KerberosScheme} instances.
 *
 * @since 4.2
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
@SuppressWarnings("deprecation")
public class KerberosSchemeFactory implements AuthSchemeFactory, AuthSchemeProvider {

    private final KerberosConfig config;

    /**
     * @since 4.6
     */
    public KerberosSchemeFactory(final KerberosConfig config) {
        super();
        this.config = config != null ? config : KerberosConfig.DEFAULT;
    }

    /**
     * @since 4.4
     */
    public KerberosSchemeFactory(final boolean stripPort, final boolean useCanonicalHostname) {
        super();
        this.config = KerberosConfig.custom()
                .setStripPort(stripPort)
                .setUseCanonicalHostname(useCanonicalHostname)
                .build();
    }

    public KerberosSchemeFactory(final boolean stripPort) {
        super();
        this.config = KerberosConfig.custom()
                .setStripPort(stripPort)
                .setUseCanonicalHostname(true)
                .build();
    }

    public KerberosSchemeFactory() {
        this(true, true);
    }

    public boolean isStripPort() {
        return config.getStripPort() != KerberosConfig.Option.DISABLE;
    }

    public boolean isUseCanonicalHostname() {
        return config.getUseCanonicalHostname() != KerberosConfig.Option.DISABLE;
    }

    @Override
    public AuthScheme newInstance(final HttpParams params) {
        return new KerberosScheme(config);
    }

    @Override
    public AuthScheme create(final HttpContext context) {
        return new KerberosScheme(config);
    }

}
