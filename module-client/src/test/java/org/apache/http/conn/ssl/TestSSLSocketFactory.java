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
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.RSAPrivateCrtKeySpec;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.localserver.LocalTestServer;
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

    static class TestX509HostnameVerifier implements X509HostnameVerifier {

        private boolean fired = false;
        
        public boolean verify(String host, SSLSession session) {
            return true;
        }

        public void verify(String host, SSLSocket ssl) throws IOException {
            this.fired = true;
        }

        public void verify(String host, String[] cns, String[] subjectAlts) throws SSLException {
        }

        public void verify(String host, X509Certificate cert) throws SSLException {
        }
        
        public boolean isFired() {
            return this.fired;
        }
        
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

        KeyManagerFactory kmfactory = KeyManagerFactory.getInstance(KeyManagerFactory
                .getDefaultAlgorithm());
        kmfactory.init(ks, pwd);
        KeyManager[] keymanagers = kmfactory.getKeyManagers();
            
        TrustManagerFactory tmfactory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmfactory.init(ks);
        TrustManager[] trustmanagers = tmfactory.getTrustManagers();
        
        SSLContext sslcontext = SSLContext.getInstance("TLSv1");
        sslcontext.init(keymanagers, trustmanagers, null);
        
        LocalTestServer server = new LocalTestServer(null, null, null, sslcontext);
        server.registerDefaultHandlers();
        server.start();
        try {
            
            TestX509HostnameVerifier hostnameVerifier = new TestX509HostnameVerifier();
            
            SSLSocketFactory socketFactory = new SSLSocketFactory(sslcontext);
            socketFactory.setHostnameVerifier(hostnameVerifier);
            
            Scheme https = new Scheme("https", socketFactory, 443); 
            DefaultHttpClient httpclient = new DefaultHttpClient();
            httpclient.getConnectionManager().getSchemeRegistry().register(https);
            
            HttpHost target = new HttpHost(
                    LocalTestServer.TEST_SERVER_ADDR.getHostName(),
                    server.getServicePort(),
                    "https");
            HttpGet httpget = new HttpGet("/random/100");
            HttpResponse response = httpclient.execute(target, httpget);
            assertEquals(200, response.getStatusLine().getStatusCode());
            assertTrue(hostnameVerifier.isFired());
        } finally {
            server.stop();
        }
    }

}
