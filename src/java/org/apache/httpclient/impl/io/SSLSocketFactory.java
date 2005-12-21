/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Copyright 2002-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
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
 *
 */

package org.apache.http.impl.io;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;

import javax.net.SocketFactory;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.apache.http.ConnectTimeoutException;
import org.apache.http.io.SecureSocketFactory;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

/**
 * <p>
 * SSLProtocolSocketFactory can be used to validate the identity of the HTTPS 
 * server against a list of trusted certificates and to authenticate to the HTTPS 
 * server using a private key. 
 * </p>
 * 
 * <p>
 * SSLProtocolSocketFactory will enable server authentication when supplied with
 * a {@link KeyStore truststore} file containg one or several trusted certificates. 
 * The client secure socket will reject the connection during the SSL session handshake 
 * if the target HTTPS server attempts to authenticate itself with a non-trusted 
 * certificate.
 * </p>
 * 
 * <p>
 * Use JDK keytool utility to import a trusted certificate and generate a truststore file:    
 *    <pre>
 *     keytool -import -alias "my server cert" -file server.crt -keystore my.truststore
 *    </pre>
 * </p>
 * 
 * <p>
 * SSLProtocolSocketFactory will enable client authentication when supplied with
 * a {@link KeyStore keystore} file containg a private key/public certificate pair. 
 * The client secure socket will use the private key to authenticate itself to the target 
 * HTTPS server during the SSL session handshake if requested to do so by the server. 
 * The target HTTPS server will in its turn verify the certificate presented by the client
 * in order to establish client's authenticity
 * </p>
 * 
 * <p>
 * Use the following sequence of actions to generate a keystore file
 * </p>
 *   <ul>
 *     <li>
 *      <p>
 *      Use JDK keytool utility to generate a new key
 *      <pre>keytool -genkey -v -alias "my client key" -validity 365 -keystore my.keystore</pre>
 *      For simplicity use the same password for the key as that of the keystore
 *      </p>
 *     </li>
 *     <li>
 *      <p>
 *      Issue a certificate signing request (CSR)
 *      <pre>keytool -certreq -alias "my client key" -file mycertreq.csr -keystore my.keystore</pre>
 *     </p>
 *     </li>
 *     <li>
 *      <p>
 *      Send the certificate request to the trusted Certificate Authority for signature. 
 *      One may choose to act as her own CA and sign the certificate request using a PKI 
 *      tool, such as OpenSSL.
 *      </p>
 *     </li>
 *     <li>
 *      <p>
 *       Import the trusted CA root certificate
 *       <pre>keytool -import -alias "my trusted ca" -file caroot.crt -keystore my.keystore</pre> 
 *      </p>
 *     </li>
 *     <li>
 *      <p>
 *       Import the PKCS#7 file containg the complete certificate chain
 *       <pre>keytool -import -alias "my client key" -file mycert.p7 -keystore my.keystore</pre> 
 *      </p>
 *     </li>
 *     <li>
 *      <p>
 *       Verify the content the resultant keystore file
 *       <pre>keytool -list -v -keystore my.keystore</pre> 
 *      </p>
 *     </li>
 *   </ul>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 */

public class SSLSocketFactory implements SecureSocketFactory {

    public static final String TLS   = "TLS";
    public static final String SSL   = "SSL";
    public static final String SSLV2 = "SSLv2";
    
    /**
     * The factory singleton.
     */
    private static final SSLSocketFactory DEFAULT_FACTORY = new SSLSocketFactory();
    
    /**
     * Gets an singleton instance of the SSLProtocolSocketFactory.
     * @return a SSLProtocolSocketFactory
     */
    public static SSLSocketFactory getSocketFactory() {
        return DEFAULT_FACTORY;
    }
    
    private final SSLContext sslcontext;
    private final SocketFactory socketfactory;

    public SSLSocketFactory(
        String algorithm, 
        final KeyStore keystore, 
        final String keystorePassword, 
        final KeyStore truststore,
        final SecureRandom random) 
        throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException
    {
        super();
        if (algorithm == null) {
            algorithm = TLS;
        }
        KeyManager[] keymanagers = null;
        if (keystore != null) {
            keymanagers = createKeyManagers(keystore, keystorePassword);
        }
        TrustManager[] trustmanagers = null;
        if (truststore != null) {
            trustmanagers = createTrustManagers(keystore);
        }
        this.sslcontext = SSLContext.getInstance(algorithm);
        this.sslcontext.init(keymanagers, trustmanagers, random);
        this.socketfactory = this.sslcontext.getSocketFactory();
    }

    public SSLSocketFactory(
            final KeyStore keystore, 
            final String keystorePassword, 
            final KeyStore truststore) 
            throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException
    {
        this(TLS, keystore, keystorePassword, truststore, null);
    }

    public SSLSocketFactory(final KeyStore keystore, final String keystorePassword) 
            throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException
    {
        this(TLS, keystore, keystorePassword, null, null);
    }

    public SSLSocketFactory(final KeyStore truststore) 
            throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException
    {
        this(TLS, null, null, truststore, null);
    }

    public SSLSocketFactory() {
        super();
        this.sslcontext = null;
        this.socketfactory = javax.net.ssl.SSLSocketFactory.getDefault(); 
    }

    private static KeyManager[] createKeyManagers(final KeyStore keystore, final String password)
        throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException {
        if (keystore == null) {
            throw new IllegalArgumentException("Keystore may not be null");
        }
        KeyManagerFactory kmfactory = KeyManagerFactory.getInstance(
            KeyManagerFactory.getDefaultAlgorithm());
        kmfactory.init(keystore, password != null ? password.toCharArray(): null);
        return kmfactory.getKeyManagers(); 
    }

    private static TrustManager[] createTrustManagers(final KeyStore keystore)
        throws KeyStoreException, NoSuchAlgorithmException { 
        if (keystore == null) {
            throw new IllegalArgumentException("Keystore may not be null");
        }
        TrustManagerFactory tmfactory = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm());
        tmfactory.init(keystore);
        return tmfactory.getTrustManagers();
    }

    /**
     * Attempts to get a new socket connection to the given host within the given time limit.
     *  
     * @param host the host name/IP
     * @param port the port on the host
     * @param localAddress the local host name/IP to bind the socket to
     * @param localPort the port on the local machine
     * @param params {@link HttpConnectionParams Http connection parameters}
     * 
     * @return Socket a new socket
     * 
     * @throws IOException if an I/O error occurs while creating the socket
     * @throws UnknownHostException if the IP address of the host cannot be
     * determined
     * @throws ConnectTimeoutException if socket cannot be connected within the
     *  given time limit
     * 
     * @since 3.0
     */
    public Socket createSocket(
        final String host,
        final int port,
        final InetAddress localAddress,
        final int localPort,
        final HttpParams params
    ) throws IOException, UnknownHostException, ConnectTimeoutException {
        if (params == null) {
            throw new IllegalArgumentException("Parameters may not be null");
        }
        Socket socket = this.socketfactory.createSocket();
        if (localAddress != null) {
            socket.bind(new InetSocketAddress(localAddress, localPort));
        }
        int timeout = HttpConnectionParams.getConnectionTimeout(params);
        socket.connect(new InetSocketAddress(host, port), timeout);
        return socket;
    }

    /**
     * @see SecureProtocolSocketFactory#createSocket(java.net.Socket,java.lang.String,int,boolean)
     */
    public Socket createSocket(
        Socket socket,
        String host,
        int port,
        boolean autoClose)
        throws IOException, UnknownHostException
    {
        return this.sslcontext.getSocketFactory().createSocket(
            socket,
            host,
            port,
            autoClose
        );
    }
    
}
