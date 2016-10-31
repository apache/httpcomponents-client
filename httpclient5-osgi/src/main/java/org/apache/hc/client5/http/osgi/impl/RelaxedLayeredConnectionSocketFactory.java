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
package org.apache.hc.client5.http.osgi.impl;

import static org.apache.hc.client5.http.osgi.impl.HostMatcher.HostMatcherFactory.createMatcher;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import org.apache.hc.client5.http.osgi.services.TrustedHostsConfiguration;
import org.apache.hc.client5.http.socket.LayeredConnectionSocketFactory;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.protocol.HttpContext;

final class RelaxedLayeredConnectionSocketFactory implements LayeredConnectionSocketFactory {

    private final TrustedHostsConfiguration trustedHostsConfiguration;

    private final LayeredConnectionSocketFactory defaultSocketFactory;

    public RelaxedLayeredConnectionSocketFactory(final TrustedHostsConfiguration trustedHostsConfiguration,
                                                 final LayeredConnectionSocketFactory defaultSocketFactory) {
        this.trustedHostsConfiguration = trustedHostsConfiguration;
        this.defaultSocketFactory = defaultSocketFactory;
    }

    @Override
    public Socket createLayeredSocket(final Socket socket,
                                      final String target,
                                      final int port,
                                      final HttpContext context) throws IOException {
        if (trustedHostsConfiguration.isEnabled()) {
            // if trust all there is no check to perform
            if (trustedHostsConfiguration.trustAll()) {
                return socket;
            }

            // blindly verify the host if in the trust list
            for (String trustedHost : trustedHostsConfiguration.getTrustedHosts()) {
                if (createMatcher(trustedHost).matches(target)) {
                    return socket;
                }
            }
        }

        // fall back to the default behavior
        return defaultSocketFactory.createLayeredSocket(socket, target, port, context);
    }

    @Override
    public Socket createSocket(final HttpContext context) throws IOException {
        return defaultSocketFactory.createSocket(context);
    }

    @Override
    public Socket connectSocket(final int connectTimeout,
                                final Socket sock,
                                final HttpHost host,
                                final InetSocketAddress remoteAddress,
                                final InetSocketAddress localAddress,
                                final HttpContext context) throws IOException {
        return defaultSocketFactory.connectSocket(connectTimeout, sock, host, remoteAddress, localAddress, context);
    }

}
