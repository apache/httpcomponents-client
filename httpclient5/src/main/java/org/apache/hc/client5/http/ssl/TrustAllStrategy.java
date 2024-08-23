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
package org.apache.hc.client5.http.ssl;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.ssl.TrustStrategy;

/**
 * A trust strategy that accepts all certificates as trusted.
 *
 * <h2>Security Warning</h2>
 * This trust strategy effectively disables trust verification of SSL / TLS,
 * and allows man-in-the-middle attacks. If possible avoid this trust strategy
 * and use more secure alternatives. For example, for self-signed certificates
 * prefer specifying a keystore containing the certificate chain when calling
 * the {@link org.apache.hc.core5.ssl.SSLContextBuilder} {@code loadTrustMaterial}
 * methods.
 *
 * @since 4.5.4
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public class TrustAllStrategy implements TrustStrategy {

    /**
     * Default instance of {@link TrustAllStrategy}.
     */
    public static final TrustAllStrategy INSTANCE = new TrustAllStrategy();

    @Override
    public boolean isTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {
        return true;
    }

}
