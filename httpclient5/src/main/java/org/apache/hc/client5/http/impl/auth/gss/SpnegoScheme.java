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

import org.apache.hc.client5.http.AuthenticationStrategy;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.impl.auth.SPNegoScheme;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.Oid;

/**
 * SPNEGO (Simple and Protected GSSAPI Negotiation Mechanism) authentication
 * scheme.
 * <p>
 * This is the new mutual authentication capable Scheme which replaces the old deprecated non mutual
 * authentication capable {@link SPNegoScheme}
 * </p>
 *
 * <p>
 * Note that this scheme is not enabled by default. To use it, you need create a custom
 * {@link AuthenticationStrategy} and a custom
 * {@link org.apache.hc.client5.http.auth.AuthSchemeFactory}
 * {@link org.apache.hc.core5.http.config.Registry},
 * and set them on the HttpClientBuilder.
 * </p>
 *
 * <pre>
 * {@code
 * private static class SpnegoAuthenticationStrategy extends DefaultAuthenticationStrategy {
 *   private static final List<String> SPNEGO_SCHEME_PRIORITY =
 *       Collections.unmodifiableList(
 *           Arrays.asList(StandardAuthScheme.SPNEGO
 *           // Add other Schemes as needed
 *           );
 *
 *   protected final List<String> getSchemePriority() {
 *     return SPNEGO_SCHEME_PRIORITY;
 *   }
 * }
 *
 * AuthenticationStrategy spnegoStrategy = new SpnegoAuthenticationStrategy();
 *
 * AuthSchemeFactory spnegoFactory = new SpnegoSchemeFactory();
 * Registry<AuthSchemeFactory> mutualSchemeRegistry = RegistryBuilder.<AuthSchemeFactory>create()
 *     .register(StandardAuthScheme.SPNEGO, spnegoFactory)
 *     //register other schemes as needed
 *     .build();
 *
 * CloseableHttpClient mutualClient = HttpClientBuilder.create()
 *    .setTargetAuthenticationStrategy(spnegoStrategy);
 *    .setDefaultAuthSchemeRegistry(spnegoSchemeRegistry);
 *    .build();
 * }
 * </pre>
 *
 * @since 5.5
 */
public class SpnegoScheme extends GssSchemeBase {

    private static final String SPNEGO_OID_STRING = "1.3.6.1.5.5.2";
    private static final Oid SPNEGO_OID;
    static {
        try {
            SPNEGO_OID = new Oid(SPNEGO_OID_STRING);
        } catch (final GSSException e) {
            throw new IllegalStateException("Failed to create OID for SPNEGO mechanism", e);
        }
    }

    /**
     * @since 5.0
     */
    public SpnegoScheme(final org.apache.hc.client5.http.auth.gss.GssConfig config, final DnsResolver dnsResolver) {
        super(config, dnsResolver);
    }

    public SpnegoScheme() {
        super();
    }

    @Override
    public String getName() {
        return StandardAuthScheme.SPNEGO;
    }

    @Override
    protected byte[] generateToken(final byte[] input, final String gssServiceName, final String gssHostname) throws GSSException {
        return generateGSSToken(input, SPNEGO_OID, gssServiceName, gssHostname);
    }

    @Override
    public boolean isConnectionBased() {
        return true;
    }

}
