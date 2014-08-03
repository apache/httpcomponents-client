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

package org.apache.http.conn.ssl;

import java.security.cert.X509Certificate;

import javax.net.ssl.SSLException;

/**
 * Abstract base class for all standard {@link X509HostnameVerifier}
 * implementations.
 *
 * @since 4.0
 *
 * @deprecated (4.4) use {@link AbstractBaseHostnameVerifier} or
 *  {@link org.apache.http.conn.ssl.AbstractCommonHostnameVerifier}
 */
@Deprecated
public abstract class AbstractVerifier extends AbstractCommonHostnameVerifier {

    public static String[] getCNs(final X509Certificate cert) {
        final String subjectPrincipal = cert.getSubjectX500Principal().toString();
        try {
            return extractCNs(subjectPrincipal);
        } catch (SSLException ex) {
            return null;
        }
    }

    /**
     * Extracts the array of SubjectAlt DNS names from an X509Certificate.
     * Returns null if there aren't any.
     * <p/>
     * Note:  Java doesn't appear able to extract international characters
     * from the SubjectAlts.  It can only extract international characters
     * from the CN field.
     * <p/>
     * (Or maybe the version of OpenSSL I'm using to test isn't storing the
     * international characters correctly in the SubjectAlts?).
     *
     * @param cert X509Certificate
     * @return Array of SubjectALT DNS names stored in the certificate.
     */
    public static String[] getDNSSubjectAlts(final X509Certificate cert) {
        return extractSubjectAlts(cert, null);
    }

}
