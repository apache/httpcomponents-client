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

import java.io.IOException;
import java.io.InputStream;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import org.apache.http.annotation.Immutable;
import org.apache.http.util.Args;

/**
 * Abstract base class for all standard {@link org.apache.http.conn.ssl.X509HostnameVerifier}
 * implementations that provides common methods extracting
 * {@link java.security.cert.X509Certificate} instance to be verified from either
 * {@link javax.net.ssl.SSLSocket} or {@link javax.net.ssl.SSLSession}.
 *
 * @since 4.4
 */
@Immutable
public abstract class AbstractBaseHostnameVerifier implements X509HostnameVerifier {

    @Override
    public final void verify(final String host, final SSLSocket ssl)
          throws IOException {
        Args.notNull(host, "Host");
        SSLSession session = ssl.getSession();
        if(session == null) {
            // In our experience this only happens under IBM 1.4.x when
            // spurious (unrelated) certificates show up in the server'
            // chain.  Hopefully this will unearth the real problem:
            final InputStream in = ssl.getInputStream();
            in.available();
            /*
              If you're looking at the 2 lines of code above because
              you're running into a problem, you probably have two
              options:

                #1.  Clean up the certificate chain that your server
                     is presenting (e.g. edit "/etc/apache2/server.crt"
                     or wherever it is your server's certificate chain
                     is defined).

                                           OR

                #2.   Upgrade to an IBM 1.5.x or greater JVM, or switch
                      to a non-IBM JVM.
            */

            // If ssl.getInputStream().available() didn't cause an
            // exception, maybe at least now the session is available?
            session = ssl.getSession();
            if(session == null) {
                // If it's still null, probably a startHandshake() will
                // unearth the real problem.
                ssl.startHandshake();

                // Okay, if we still haven't managed to cause an exception,
                // might as well go for the NPE.  Or maybe we're okay now?
                session = ssl.getSession();
            }
        }

        final Certificate[] certs = session.getPeerCertificates();
        final X509Certificate x509 = (X509Certificate) certs[0];
        verify(host, x509);
    }

    @Override
    public final boolean verify(final String host, final SSLSession session) {
        try {
            final Certificate[] certs = session.getPeerCertificates();
            final X509Certificate x509 = (X509Certificate) certs[0];
            verify(host, x509);
            return true;
        }
        catch(final SSLException e) {
            return false;
        }
    }

}
