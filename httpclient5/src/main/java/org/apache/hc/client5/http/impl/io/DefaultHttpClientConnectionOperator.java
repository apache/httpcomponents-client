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
package org.apache.hc.client5.http.impl.io;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;

import org.apache.hc.client5.http.ConnectExceptionSupport;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.UnsupportedSchemeException;
import org.apache.hc.client5.http.impl.ConnPoolSupport;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.io.HttpClientConnectionOperator;
import org.apache.hc.client5.http.io.ManagedHttpClientConnection;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.LayeredConnectionSocketFactory;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of {@link HttpClientConnectionOperator} used as default in Http client,
 * when no instance provided by user to {@link BasicHttpClientConnectionManager} or {@link
 * PoolingHttpClientConnectionManager} constructor.
 *
 * @since 4.4
 */
@Internal
@Contract(threading = ThreadingBehavior.STATELESS)
public class DefaultHttpClientConnectionOperator implements HttpClientConnectionOperator {

    static final String SOCKET_FACTORY_REGISTRY = "http.socket-factory-registry";

    private static final Logger LOG = LoggerFactory.getLogger(DefaultHttpClientConnectionOperator.class);

    private final Lookup<ConnectionSocketFactory> socketFactoryRegistry;
    private final SchemePortResolver schemePortResolver;
    private final DnsResolver dnsResolver;

    public DefaultHttpClientConnectionOperator(
            final Lookup<ConnectionSocketFactory> socketFactoryRegistry,
            final SchemePortResolver schemePortResolver,
            final DnsResolver dnsResolver) {
        super();
        Args.notNull(socketFactoryRegistry, "Socket factory registry");
        this.socketFactoryRegistry = socketFactoryRegistry;
        this.schemePortResolver = schemePortResolver != null ? schemePortResolver :
            DefaultSchemePortResolver.INSTANCE;
        this.dnsResolver = dnsResolver != null ? dnsResolver :
            SystemDefaultDnsResolver.INSTANCE;
    }

    @SuppressWarnings("unchecked")
    private Lookup<ConnectionSocketFactory> getSocketFactoryRegistry(final HttpContext context) {
        Lookup<ConnectionSocketFactory> reg = (Lookup<ConnectionSocketFactory>) context.getAttribute(
                SOCKET_FACTORY_REGISTRY);
        if (reg == null) {
            reg = this.socketFactoryRegistry;
        }
        return reg;
    }

    @Override
    public void connect(
            final ManagedHttpClientConnection conn,
            final HttpHost host,
            final InetSocketAddress localAddress,
            final TimeValue connectTimeout,
            final SocketConfig socketConfig,
            final HttpContext context) throws IOException {
        final Timeout timeout = connectTimeout != null ? Timeout.of(connectTimeout.getDuration(), connectTimeout.getTimeUnit()) : null;
        connect(conn, host, localAddress, timeout, socketConfig, null, context);
    }

    @Override
    public void connect(
            final ManagedHttpClientConnection conn,
            final HttpHost host,
            final InetSocketAddress localAddress,
            final Timeout connectTimeout,
            final SocketConfig socketConfig,
            final Object attachment,
            final HttpContext context) throws IOException {
        Args.notNull(conn, "Connection");
        Args.notNull(host, "Host");
        Args.notNull(socketConfig, "Socket config");
        Args.notNull(context, "Context");
        final Lookup<ConnectionSocketFactory> registry = getSocketFactoryRegistry(context);
        final ConnectionSocketFactory sf = registry.lookup(host.getSchemeName());
        if (sf == null) {
            throw new UnsupportedSchemeException(host.getSchemeName() + " protocol is not supported");
        }
        final InetAddress[] remoteAddresses;
        if (host.getAddress() != null) {
            remoteAddresses = new InetAddress[] { host.getAddress() };
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} resolving remote address", host.getHostName());
            }

            remoteAddresses = this.dnsResolver.resolve(host.getHostName());

            if (LOG.isDebugEnabled()) {
                LOG.debug("{} resolved to {}", host.getHostName(), Arrays.asList(remoteAddresses));
            }
        }

        final Timeout soTimeout = socketConfig.getSoTimeout();

        final int port = this.schemePortResolver.resolve(host);
        for (int i = 0; i < remoteAddresses.length; i++) {
            final InetAddress address = remoteAddresses[i];
            final boolean last = i == remoteAddresses.length - 1;

            Socket sock = sf.createSocket(context);
            if (soTimeout != null) {
                sock.setSoTimeout(soTimeout.toMillisecondsIntBound());
            }
            sock.setReuseAddress(socketConfig.isSoReuseAddress());
            sock.setTcpNoDelay(socketConfig.isTcpNoDelay());
            sock.setKeepAlive(socketConfig.isSoKeepAlive());
            if (socketConfig.getRcvBufSize() > 0) {
                sock.setReceiveBufferSize(socketConfig.getRcvBufSize());
            }
            if (socketConfig.getSndBufSize() > 0) {
                sock.setSendBufferSize(socketConfig.getSndBufSize());
            }

            final int linger = socketConfig.getSoLinger().toMillisecondsIntBound();
            if (linger >= 0) {
                sock.setSoLinger(true, linger);
            }
            conn.bind(sock);

            final InetSocketAddress remoteAddress = new InetSocketAddress(address, port);
            if (LOG.isDebugEnabled()) {
                LOG.debug("{}:{} connecting {}->{} ({})",
                        host.getHostName(), host.getPort(), localAddress, remoteAddress, connectTimeout);
            }
            try {
                sock = sf.connectSocket(sock, host, remoteAddress, localAddress, connectTimeout, attachment, context);
                conn.bind(sock);
                conn.setSocketTimeout(soTimeout);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("{}:{} connected {}->{} as {}",
                            host.getHostName(), host.getPort(), localAddress, remoteAddress, ConnPoolSupport.getId(conn));
                }
                return;
            } catch (final IOException ex) {
                if (last) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("{}:{} connection to {} failed ({}); terminating operation",
                                host.getHostName(), host.getPort(), remoteAddress, ex.getClass());
                    }
                    throw ConnectExceptionSupport.enhance(ex, host, remoteAddresses);
                } else {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("{}:{} connection to {} failed ({}); retrying connection to the next address",
                                host.getHostName(), host.getPort(), remoteAddress, ex.getClass());
                    }
                }
            }
        }
    }

    @Override
    public void upgrade(
            final ManagedHttpClientConnection conn,
            final HttpHost host,
            final HttpContext context) throws IOException {
        upgrade(conn, host, null, context);
    }

    @Override
    public void upgrade(
            final ManagedHttpClientConnection conn,
            final HttpHost host,
            final Object attachment,
            final HttpContext context) throws IOException {
        final HttpClientContext clientContext = HttpClientContext.adapt(context);
        final Lookup<ConnectionSocketFactory> registry = getSocketFactoryRegistry(clientContext);
        final ConnectionSocketFactory sf = registry.lookup(host.getSchemeName());
        if (sf == null) {
            throw new UnsupportedSchemeException(host.getSchemeName() +
                    " protocol is not supported");
        }
        if (!(sf instanceof LayeredConnectionSocketFactory)) {
            throw new UnsupportedSchemeException(host.getSchemeName() +
                    " protocol does not support connection upgrade");
        }
        final LayeredConnectionSocketFactory lsf = (LayeredConnectionSocketFactory) sf;
        Socket sock = conn.getSocket();
        if (sock == null) {
            throw new ConnectionClosedException("Connection is closed");
        }
        final int port = this.schemePortResolver.resolve(host);
        sock = lsf.createLayeredSocket(sock, host.getHostName(), port, attachment, context);
        conn.bind(sock);
    }

}
