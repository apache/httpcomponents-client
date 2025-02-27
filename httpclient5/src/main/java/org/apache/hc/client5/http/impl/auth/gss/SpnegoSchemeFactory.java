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
package org.apache.hc.client5.http.impl.auth.gss;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Experimental;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * {@link AuthSchemeFactory} implementation that creates and initialises
 * {@link SpnegoScheme} instances.
 * <p>
 * This replaces the old deprecated {@link org.apache.hc.client5.http.impl.auth.SPNegoSchemeFactory}
 * </p>
 *
 * @since 5.5
 *
 * @see SPNegoSchemeFactory
 */
@Contract(threading = ThreadingBehavior.STATELESS)
@Experimental
public class SpnegoSchemeFactory implements AuthSchemeFactory {

    /**
     * Singleton instance for the default configuration.
     */
    public static final SpnegoSchemeFactory DEFAULT = new SpnegoSchemeFactory(org.apache.hc.client5.http.auth.gss.GssConfig.DEFAULT,
            SystemDefaultDnsResolver.INSTANCE);

    private final org.apache.hc.client5.http.auth.gss.GssConfig config;
    private final DnsResolver dnsResolver;

    /**
     * @since 5.5
     */
    public SpnegoSchemeFactory(final org.apache.hc.client5.http.auth.gss.GssConfig config, final DnsResolver dnsResolver) {
        super();
        this.config = config;
        this.dnsResolver = dnsResolver;
    }

    @Override
    public AuthScheme create(final HttpContext context) {
        return new SpnegoScheme(this.config, this.dnsResolver);
    }

}
