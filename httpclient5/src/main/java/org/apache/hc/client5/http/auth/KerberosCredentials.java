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

import org.apache.hc.client5.http.auth.gss.GssCredentials;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.ietf.jgss.GSSCredential;

/**
 * Kerberos specific {@link Credentials} representation based on {@link GSSCredential}.
 *
 * @since 4.4
 *
 * The original KerberosCredentials class has been renamed to
 * org.apache.hc.client5.http.auth.gss.GssCredentials.
 *
 * @deprecated Do not use. The old GGS based experimental authentication schemes are no longer
 * supported.
 * Use org.apache.hc.client5.http.impl.auth.gss.SpnegoScheme, or consider using Basic or Bearer
 * authentication with TLS instead.
 * @see org.apache.hc.client5.http.impl.auth.gss.SpnegoScheme
 * @see org.apache.hc.client5.http.auth.gss.GssConfig
 * @see org.apache.hc.client5.http.auth.gss.GssCredentials
 */
@Deprecated
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class KerberosCredentials extends GssCredentials {

    public KerberosCredentials(final GSSCredential gssCredential) {
        super(gssCredential);
    }

}
