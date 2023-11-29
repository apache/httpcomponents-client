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
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.core5.annotation.Experimental;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.Oid;

/**
 * SPNEGO (Simple and Protected GSSAPI Negotiation Mechanism) authentication
 * scheme.
 * <p>
 * Please note this class is considered experimental and may be discontinued or removed
 * in the future.
 * </p>
 *
 * @since 4.2
 *
 * @deprecated Do not use. The GGS based experimental authentication schemes are no longer
 * supported. Consider using Basic or Bearer authentication with TLS instead.
 *
 * @see BasicScheme
 * @see BearerScheme
 */
@Deprecated
@Experimental
public class SPNegoScheme extends GGSSchemeBase {

    private static final String SPNEGO_OID = "1.3.6.1.5.5.2";

    /**
     * @since 5.0
     */
    public SPNegoScheme(final org.apache.hc.client5.http.auth.KerberosConfig config, final DnsResolver dnsResolver) {
        super(config, dnsResolver);
    }

    public SPNegoScheme() {
        super();
    }

    @Override
    public String getName() {
        return StandardAuthScheme.SPNEGO;
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
