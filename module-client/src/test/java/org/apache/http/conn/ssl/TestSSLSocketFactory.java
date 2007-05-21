/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.RSAPrivateCrtKeySpec;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLServerSocketFactory;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;

/**
 * Unit tests for {@link SSLSocketFactory}.
 *
 * @author Julius Davies
 * @since 8-Dec-2006
 */
public class TestSSLSocketFactory extends TestCase
      implements CertificatesToPlayWith {

    public TestSSLSocketFactory(String testName) {
        super(testName);
    }

    public static void main(String args[]) throws Exception {
        String[] testCaseName = { TestSSLSocketFactory.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    public static Test suite() {
        TestSuite ts = new TestSuite();
        ts.addTestSuite(TestSSLSocketFactory.class);
        return ts;
    }

    public void testCreateSocket() throws Exception {
        HttpParams params = new BasicHttpParams();
        String password = "changeit";
        char[] pwd = password.toCharArray();

        RSAPrivateCrtKeySpec k;
        k = new RSAPrivateCrtKeySpec(new BigInteger(RSA_PUBLIC_MODULUS, 16),
                                     new BigInteger(RSA_PUBLIC_EXPONENT, 10),
                                     new BigInteger(RSA_PRIVATE_EXPONENT, 16),
                                     new BigInteger(RSA_PRIME1, 16),
                                     new BigInteger(RSA_PRIME2, 16),
                                     new BigInteger(RSA_EXPONENT1, 16),
                                     new BigInteger(RSA_EXPONENT2, 16),
                                     new BigInteger(RSA_COEFFICIENT, 16));

        PrivateKey pk = KeyFactory.getInstance("RSA").generatePrivate(k);
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, null);

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        InputStream in1, in2, in3;
        in1 = new ByteArrayInputStream(X509_FOO);
        in2 = new ByteArrayInputStream(X509_INTERMEDIATE_CA);
        in3 = new ByteArrayInputStream(X509_ROOT_CA);
        X509Certificate[] chain = new X509Certificate[3];
        chain[0] = (X509Certificate) cf.generateCertificate(in1);
        chain[1] = (X509Certificate) cf.generateCertificate(in2);
        chain[2] = (X509Certificate) cf.generateCertificate(in3);

        ks.setKeyEntry("RSA_KEY", pk, pwd, chain);
        ks.setCertificateEntry("CERT", chain[2]); // Let's trust ourselves. :-)

        File tempFile = File.createTempFile("junit", "jks");
        try {
            String path = tempFile.getCanonicalPath();
            tempFile.deleteOnExit();
            FileOutputStream fOut = new FileOutputStream(tempFile);
            ks.store(fOut, pwd);
            fOut.close();

            System.setProperty("javax.net.ssl.keyStore", path);
            System.setProperty("javax.net.ssl.keyStorePassword", password);
            System.setProperty("javax.net.ssl.trustStore", path);
            System.setProperty("javax.net.ssl.trustStorePassword", password);

            ServerSocketFactory server = SSLServerSocketFactory.getDefault();
            // Let the operating system just choose an available port:
            ServerSocket serverSocket = server.createServerSocket(0);
            serverSocket.setSoTimeout(30000);
            int port = serverSocket.getLocalPort();
            // System.out.println("\nlistening on port: " + port);

            SSLSocketFactory ssf = SSLSocketFactory.getSocketFactory();
            ssf.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

            // Test 1 - createSocket()
            IOException[] e = new IOException[1];
            boolean[] success = new boolean[1];
            listen(serverSocket, e, success);
            Socket s = ssf.connectSocket(null, "localhost", port,
                                         null, 0, params);
            exerciseSocket(s, e, success);

            // Test 2 - createSocket( Socket ), where we upgrade a plain socket
            //          to SSL.
            success[0] = false;
            listen(serverSocket, e, success);
            s = new Socket("localhost", port);
            s = ssf.createSocket(s, "localhost", port, true);
            exerciseSocket(s, e, success);
        }
        finally {
            tempFile.delete();
        }
    }

    private static void listen(final ServerSocket ss,
                               final IOException[] e,
                               final boolean[] success) {
        Runnable r = new Runnable() {
            public void run() {
                try {
                    Socket s = ss.accept();
                    InputStream in = s.getInputStream();
                    OutputStream out = s.getOutputStream();
                    out.write("server says hello\n".getBytes());
                    byte[] buf = new byte[4096];
                    in.read(buf);
                    out.close();
                    in.close();
                    s.close();
                } catch(IOException ioe) {
                    e[0] = ioe;
                } finally {
                    success[0] = true;
                }
            }
        };
        new Thread(r).start();
        Thread.yield();
    }

    private static void exerciseSocket(Socket s, IOException[] e,
                                       boolean[] success)
          throws IOException {
        InputStream in = s.getInputStream();
        OutputStream out = s.getOutputStream();
        out.write(42);
        byte[] buf = new byte[4096];
        in.read(buf);
        out.close();
        in.close();
        s.close();
        // String response = new String( buf, 0, c );
        while(!success[0]) {
            Thread.yield();
        }
        if(e[0] != null) {
            throw e[0];
        }
    }


}
