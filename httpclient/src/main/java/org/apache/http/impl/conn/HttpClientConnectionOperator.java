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
 *
 */

package org.apache.http.impl.conn;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpHost;
import org.apache.http.annotation.Immutable;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.DnsResolver;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.conn.HttpInetSocketAddress;
import org.apache.http.conn.SocketClientConnection;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeLayeredSocketFactory;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.scheme.SchemeSocketFactory;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;

@Immutable
class HttpClientConnectionOperator {

    private final Log log = LogFactory.getLog(HttpClientConnectionManager.class);

    private final SchemeRegistry schemeRegistry;
    private final DnsResolver dnsResolver;

    HttpClientConnectionOperator(
            final SchemeRegistry schemeRegistry,
            final DnsResolver dnsResolver) {
        super();
        if (schemeRegistry == null) {
            throw new IllegalArgumentException("Scheme registry may nor be null");
        }
        this.schemeRegistry = schemeRegistry;
        this.dnsResolver = dnsResolver != null ? dnsResolver : SystemDefaultDnsResolver.INSTANCE;
    }

    public SchemeRegistry getSchemeRegistry() {
        return this.schemeRegistry;
    }

    public DnsResolver getDnsResolver() {
        return this.dnsResolver;
    }

    private SchemeRegistry getSchemeRegistry(final HttpContext context) {
        SchemeRegistry reg = (SchemeRegistry) context.getAttribute(
                ClientContext.SCHEME_REGISTRY);
        if (reg == null) {
            reg = this.schemeRegistry;
        }
        return reg;
    }

    public void connect(
            final SocketClientConnection conn,
            final HttpHost host,
            final InetAddress local,
            final HttpContext context,
            final HttpParams params) throws IOException {
        SchemeRegistry registry = getSchemeRegistry(context);
        Scheme schm = registry.getScheme(host.getSchemeName());
        SchemeSocketFactory sf = schm.getSchemeSocketFactory();

        InetAddress[] addresses = this.dnsResolver.resolve(host.getHostName());
        int port = schm.resolvePort(host.getPort());
        for (int i = 0; i < addresses.length; i++) {
            InetAddress address = addresses[i];
            boolean last = i == addresses.length - 1;

            Socket sock = sf.createSocket(params);
            conn.bind(sock);

            InetSocketAddress remoteAddress = new HttpInetSocketAddress(host, address, port);
            InetSocketAddress localAddress = null;
            if (local != null) {
                localAddress = new InetSocketAddress(local, 0);
            }
            if (this.log.isDebugEnabled()) {
                this.log.debug("Connecting to " + remoteAddress);
            }
            try {
                Socket connsock = sf.connectSocket(sock, remoteAddress, localAddress, params);
                conn.bind(connsock);
                return;
            } catch (ConnectException ex) {
                if (last) {
                    throw new HttpHostConnectException(host, ex);
                }
            } catch (ConnectTimeoutException ex) {
                if (last) {
                    throw ex;
                }
            }
            if (this.log.isDebugEnabled()) {
                this.log.debug("Connect to " + remoteAddress + " timed out. " +
                        "Connection will be retried using another IP address");
            }
        }
    }

    public void upgrade(
            final SocketClientConnection conn,
            final HttpHost host,
            final HttpContext context,
            final HttpParams params) throws IOException {
        SchemeRegistry registry = getSchemeRegistry(context);
        Scheme schm = registry.getScheme(host.getSchemeName());
        if (!(schm.getSchemeSocketFactory() instanceof SchemeLayeredSocketFactory)) {
            throw new IllegalArgumentException
                ("Target scheme (" + schm.getName() +
                 ") must have layered socket factory.");
        }
        SchemeLayeredSocketFactory lsf = (SchemeLayeredSocketFactory) schm.getSchemeSocketFactory();
        Socket sock;
        try {
            int port = schm.resolvePort(host.getPort());
            sock = lsf.createLayeredSocket(
                    conn.getSocket(), host.getHostName(), port, params);
        } catch (ConnectException ex) {
            throw new HttpHostConnectException(host, ex);
        }
        conn.bind(sock);
    }

}
