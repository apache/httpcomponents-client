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

package org.apache.http.conn.socket;

import org.apache.http.HttpHost;
import org.apache.http.annotation.Contract;
import org.apache.http.annotation.ThreadingBehavior;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;

/**
 * The default class for creating plain (unencrypted) SOCKS sockets based on system properties 'socksProxyHost' and 'socksProxyPort'.
 *
 * @since 4.5
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class SOCKSConnectionSocketFactory implements ConnectionSocketFactory {

    public static final SOCKSConnectionSocketFactory INSTANCE = new SOCKSConnectionSocketFactory();

    public static SOCKSConnectionSocketFactory getSocketFactory() {
        return INSTANCE;
    }

    public SOCKSConnectionSocketFactory() {
        super();
    }

    @Override
    public Socket createSocket(final HttpContext context) throws IOException {
        final String socksProxyHost = System.getProperty("socksProxyHost");
        final String socksProxyPort = System.getProperty("socksProxyPort");
        final InetSocketAddress socksaddr = new InetSocketAddress(socksProxyHost, Integer.parseInt(socksProxyPort));
        final Proxy proxy = new Proxy(Proxy.Type.SOCKS, socksaddr);
        return new Socket(proxy);
    }

    @Override
    public Socket connectSocket(
            final int connectTimeout,
            final Socket socket,
            final HttpHost host,
            final InetSocketAddress remoteAddress,
            final InetSocketAddress localAddress,
            final HttpContext context) throws IOException {
        final Socket sock;
        if (socket != null) {
            sock = socket;
        } else {
            sock = createSocket(context);
        }
        if (localAddress != null) {
            sock.bind(localAddress);
        }
        sock.connect(remoteAddress, connectTimeout);
        return sock;
    }

}
