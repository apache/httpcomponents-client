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

/**
 * Authentication schemes by their names supported by the HttpClient.
 *
 * @since 4.3
 */
public final class StandardAuthScheme {

    private StandardAuthScheme() {
        // no instances
    }

    /**
     * Basic authentication scheme (considered inherently insecure without TLS,
     * but most widely supported).
     */
    public static final String BASIC = "Basic";

    /**
     * Digest authentication scheme.
     */
    public static final String DIGEST = "Digest";

    /**
     * Bearer authentication scheme (should be used with TLS).
     */
    public static final String BEARER = "Bearer";

    /**
     * The NTLM authentication scheme is a proprietary Microsoft Windows
     * authentication protocol as defined in [MS-NLMP].
     *
     * @deprecated Do not use. the NTLM authentication scheme is no longer supported.
     * Consider using Basic or Bearer authentication with TLS instead.
     */
    @Deprecated
    public static final String NTLM = "NTLM";

    /**
     * SPNEGO authentication scheme as defined in RFC 4559 and RFC 4178.
     *
     * @deprecated Do not use. The GGS based experimental authentication schemes are no longer
     * supported. Consider using Basic or Bearer authentication with TLS instead.
     */
    @Deprecated
    public static final String SPNEGO = "Negotiate";

    /**
     * Kerberos authentication scheme as defined in RFC 4120.
     *
     * @deprecated Do not use. The GGS based experimental authentication schemes are no longer
     * supported. Consider using Basic or Bearer authentication with TLS instead.
     */
    @Deprecated
    public static final String KERBEROS = "Kerberos";

}
