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
package org.apache.hc.client5.http.examples;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.StatusLine;

/**
 * This example demonstrates how to use SNI to send requests to a virtual HTTPS
 * endpoint using the classic I/O.
 */
public class ClientSNI {

    public final static void main(final String[] args) throws Exception {
        try (CloseableHttpClient httpclient = HttpClients.createSystem()) {

            final HttpHost target = new HttpHost("https", "www.google.com");
            final HttpGet httpget = new HttpGet("https://www.google.ch/");

            System.out.println("Executing request " + httpget.getMethod() + " " + httpget.getUri());

            final HttpClientContext clientContext = HttpClientContext.create();
            httpclient.execute(target, httpget, clientContext, response -> {
                System.out.println("----------------------------------------");
                System.out.println(httpget + "->" + new StatusLine(response));
                EntityUtils.consume(response.getEntity());
                final SSLSession sslSession = clientContext.getSSLSession();
                if (sslSession != null) {
                    try {
                        System.out.println("Peer: " + sslSession.getPeerPrincipal());
                        System.out.println("TLS protocol: " + sslSession.getProtocol());
                        System.out.println("TLS cipher suite: " + sslSession.getCipherSuite());
                    } catch (final SSLPeerUnverifiedException ignore) {
                    }
                }
                return null;
            });
        }
    }

}
