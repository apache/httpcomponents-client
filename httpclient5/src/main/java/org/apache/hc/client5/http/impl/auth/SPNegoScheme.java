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
package org.apache.hc.client5.http.impl.auth;

import org.apache.hc.client5.http.DnsResolver;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.Oid;

/**
 * SPNEGO (Simple and Protected GSSAPI Negotiation Mechanism) authentication
 * scheme.
 *
 * @since 4.2
 */
public class SPNegoScheme extends GGSSchemeBase {

    private static final String SPNEGO_OID = "1.3.6.1.5.5.2";

    /**
     * @since 4.4
     */
    public SPNegoScheme(final DnsResolver dnsResolver, final boolean stripPort, final boolean useCanonicalHostname) {
        super(dnsResolver, stripPort, useCanonicalHostname);
    }

    public SPNegoScheme(final boolean stripPort) {
        super(stripPort);
    }

    public SPNegoScheme() {
        super();
    }

    @Override
    public String getName() {
        return "Negotiate";
    }

    @Override
    protected byte[] generateToken(final byte[] input, final String serviceName, final String authServer) throws GSSException {
        return generateGSSToken(input, new Oid(SPNEGO_OID), serviceName, authServer);
    }

    @Override
    public boolean isConnectionBased() {
        return true;
    }

}
