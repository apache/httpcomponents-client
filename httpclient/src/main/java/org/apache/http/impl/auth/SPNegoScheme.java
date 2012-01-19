/*
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */

package org.apache.http.impl.auth;

import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.Credentials;
import org.apache.http.protocol.HttpContext;
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

    public SPNegoScheme(boolean stripPort) {
        super(stripPort);
    }

    public SPNegoScheme() {
        super(false);
    }

    public String getSchemeName() {
        return "Negotiate";
    }

    /**
     * Produces SPNEGO authorization Header based on token created by
     * processChallenge.
     *
     * @param credentials not used by the SPNEGO scheme.
     * @param request The request being authenticated
     *
     * @throws AuthenticationException if authentication string cannot
     *   be generated due to an authentication failure
     *
     * @return SPNEGO authentication Header
     */
    @Override
    public Header authenticate(
            final Credentials credentials,
            final HttpRequest request,
            final HttpContext context) throws AuthenticationException {
        return super.authenticate(credentials, request, context);
    }

    @Override
    protected byte[] generateToken(final byte[] input, final String authServer) throws GSSException {
        return generateGSSToken(input, new Oid(SPNEGO_OID), authServer);
    }

    /**
     * There are no valid parameters for SPNEGO authentication so this
     * method always returns <code>null</code>.
     *
     * @return <code>null</code>
     */
    public String getParameter(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Parameter name may not be null");
        }
        return null;
    }

    /**
     * The concept of an authentication realm is not supported by the Negotiate
     * authentication scheme. Always returns <code>null</code>.
     *
     * @return <code>null</code>
     */
    public String getRealm() {
        return null;
    }

    /**
     * Returns <tt>true</tt>. SPNEGO authentication scheme is connection based.
     *
     * @return <tt>true</tt>.
     */
    public boolean isConnectionBased() {
        return true;
    }

}
