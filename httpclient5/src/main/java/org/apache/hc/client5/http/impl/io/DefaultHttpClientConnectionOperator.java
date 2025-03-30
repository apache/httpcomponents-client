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
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.List;

import javax.net.ssl.SSLSocket;

import org.apache.hc.client5.http.ConnectExceptionSupport;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.UnsupportedSchemeException;
import org.apache.hc.client5.http.impl.ConnPoolSupport;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.io.DetachedSocketFactory;
import org.apache.hc.client5.http.io.HttpClientConnectionOperator;
import org.apache.hc.client5.http.io.ManagedHttpClientConnection;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.Closer;
import org.apache.hc.core5.net.NamedEndpoint;
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

    private static final Logger LOG = LoggerFactory.getLogger(DefaultHttpClientConnectionOperator.class);

    static final DetachedSocketFactory PLAIN_SOCKET_FACTORY = socksProxy -> socksProxy == null ? new Socket() : new Socket(socksProxy);

    private final DetachedSocketFactory detachedSocketFactory;
    private final Lookup<TlsSocketStrategy> tlsSocketStrategyLookup;
    private final SchemePortResolver schemePortResolver;
    private final DnsResolver dnsResolver;

    /**
     * @deprecated Provided for backward compatibility
     */
    @Deprecated
    static Lookup<TlsSocketStrategy> adapt(final Lookup<org.apache.hc.client5.http.socket.ConnectionSocketFactory> lookup) {

        return name -> {
            final org.apache.hc.client5.http.socket.ConnectionSocketFactory sf = lookup.lookup(name);
            return sf instanceof org.apache.hc.client5.http.socket.LayeredConnectionSocketFactory ? (socket, target, port, attachment, context) ->
                    (SSLSocket) ((org.apache.hc.client5.http.socket.LayeredConnectionSocketFactory) sf).createLayeredSocket(socket, target, port, attachment, context) : null;
        };

    }


    public DefaultHttpClientConnectionOperator(
            final DetachedSocketFactory detachedSocketFactory,
            final SchemePortResolver schemePortResolver,
            final DnsResolver dnsResolver,
            final Lookup<TlsSocketStrategy> tlsSocketStrategyLookup) {
        super();
        this.detachedSocketFactory = Args.notNull(detachedSocketFactory, "Plain socket factory");
        this.tlsSocketStrategyLookup = Args.notNull(tlsSocketStrategyLookup, "Socket factory registry");
        this.schemePortResolver = schemePortResolver != null ? schemePortResolver :
                DefaultSchemePortResolver.INSTANCE;
        this.dnsResolver = dnsResolver != null ? dnsResolver :
                SystemDefaultDnsResolver.INSTANCE;
    }

    /**
     * @deprecated Do not use.
     */
    @Deprecated
    public DefaultHttpClientConnectionOperator(
            final Lookup<org.apache.hc.client5.http.socket.ConnectionSocketFactory> socketFactoryRegistry,
            final SchemePortResolver schemePortResolver,
            final DnsResolver dnsResolver) {
        this(PLAIN_SOCKET_FACTORY, schemePortResolver, dnsResolver, adapt(socketFactoryRegistry));
    }

    public DefaultHttpClientConnectionOperator(
            final SchemePortResolver schemePortResolver,
            final DnsResolver dnsResolver,
            final Lookup<TlsSocketStrategy> tlsSocketStrategyLookup) {
        this(PLAIN_SOCKET_FACTORY, schemePortResolver, dnsResolver, tlsSocketStrategyLookup);
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
        connect(conn, host, null, localAddress, timeout, socketConfig, null, context);
    }

    @Override
    public void connect(
            final ManagedHttpClientConnection conn,
            final HttpHost endpointHost,
            final NamedEndpoint endpointName,
            final InetSocketAddress localAddress,
            final Timeout connectTimeout,
            final SocketConfig socketConfig,
            final Object attachment,
            final HttpContext context) throws IOException {

        Args.notNull(conn, "Connection");
        Args.notNull(endpointHost, "Host");
        Args.notNull(socketConfig, "Socket config");
        Args.notNull(context, "Context");

        final Timeout soTimeout = socketConfig.getSoTimeout();
        final SocketAddress socksProxyAddress = socketConfig.getSocksProxyAddress();
        final Proxy socksProxy = socksProxyAddress != null ? new Proxy(Proxy.Type.SOCKS, socksProxyAddress) : null;

        final List<InetSocketAddress> remoteAddresses;
        if (endpointHost.getAddress() != null) {
            remoteAddresses = Collections.singletonList(
                    new InetSocketAddress(endpointHost.getAddress(), this.schemePortResolver.resolve(endpointHost.getSchemeName(), endpointHost)));
        } else {
            final int port = this.schemePortResolver.resolve(endpointHost.getSchemeName(), endpointHost);
            remoteAddresses = this.dnsResolver.resolve(endpointHost.getHostName(), port);
        }
        for (int i = 0; i < remoteAddresses.size(); i++) {
            final InetSocketAddress remoteAddress = remoteAddresses.get(i);
            final boolean last = i == remoteAddresses.size() - 1;
            onBeforeSocketConnect(context, endpointHost);
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} connecting {}->{} ({})", endpointHost, localAddress, remoteAddress, connectTimeout);
            }
            final Socket socket = detachedSocketFactory.create(endpointHost.getSchemeName(), socksProxy);
            try {
                // Always bind to the local address if it's provided.
                if (localAddress != null) {
                    socket.bind(localAddress);
                }
                conn.bind(socket);
                if (soTimeout != null) {
                    socket.setSoTimeout(soTimeout.toMillisecondsIntBound());
                }
                socket.setReuseAddress(socketConfig.isSoReuseAddress());
                socket.setTcpNoDelay(socketConfig.isTcpNoDelay());
                socket.setKeepAlive(socketConfig.isSoKeepAlive());
                if (socketConfig.getRcvBufSize() > 0) {
                    socket.setReceiveBufferSize(socketConfig.getRcvBufSize());
                }
                if (socketConfig.getSndBufSize() > 0) {
                    socket.setSendBufferSize(socketConfig.getSndBufSize());
                }

                final int linger = socketConfig.getSoLinger().toMillisecondsIntBound();
                if (linger >= 0) {
                    socket.setSoLinger(true, linger);
                }
                socket.connect(remoteAddress, TimeValue.isPositive(connectTimeout) ? connectTimeout.toMillisecondsIntBound() : 0);
                conn.bind(socket);
                onAfterSocketConnect(context, endpointHost);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("{} {} connected {}->{}", ConnPoolSupport.getId(conn), endpointHost, conn.getLocalAddress(), conn.getRemoteAddress());
                }
                conn.setSocketTimeout(soTimeout);
                final TlsSocketStrategy tlsSocketStrategy = tlsSocketStrategyLookup != null ? tlsSocketStrategyLookup.lookup(endpointHost.getSchemeName()) : null;
                if (tlsSocketStrategy != null) {
                    final NamedEndpoint tlsName = endpointName != null ? endpointName : endpointHost;
                    onBeforeTlsHandshake(context, endpointHost);
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("{} {} upgrading to TLS", ConnPoolSupport.getId(conn), tlsName);
                    }
                    final SSLSocket sslSocket = tlsSocketStrategy.upgrade(socket, tlsName.getHostName(), tlsName.getPort(), attachment, context);
                    conn.bind(sslSocket, socket);
                    onAfterTlsHandshake(context, endpointHost);
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("{} {} upgraded to TLS", ConnPoolSupport.getId(conn), tlsName);
                    }
                }
                return;
            } catch (final RuntimeException ex) {
                Closer.closeQuietly(socket);
                throw ex;
            } catch (final IOException ex) {
                Closer.closeQuietly(socket);
                if (last) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("{} connection to {} failed ({}); terminating operation", endpointHost, remoteAddress, ex.getClass());
                    }
                    throw ConnectExceptionSupport.enhance(ex, endpointHost);
                }
                if (LOG.isDebugEnabled()) {
                    LOG.debug("{} connection to {} failed ({}); retrying connection to the next address", endpointHost, remoteAddress, ex.getClass());
                }
            }
        }
    }

    @Override
    public void upgrade(
            final ManagedHttpClientConnection conn,
            final HttpHost host,
            final HttpContext context) throws IOException {
        upgrade(conn, host, null, null, context);
    }

    @Override
    public void upgrade(
            final ManagedHttpClientConnection conn,
            final HttpHost endpointHost,
            final NamedEndpoint endpointName,
            final Object attachment,
            final HttpContext context) throws IOException {
        final Socket socket = conn.getSocket();
        if (socket == null) {
            throw new ConnectionClosedException("Connection is closed");
        }
        final String newProtocol = URIScheme.HTTP.same(endpointHost.getSchemeName()) ? URIScheme.HTTPS.id : endpointHost.getSchemeName();
        final TlsSocketStrategy tlsSocketStrategy = tlsSocketStrategyLookup != null ? tlsSocketStrategyLookup.lookup(newProtocol) : null;
        if (tlsSocketStrategy != null) {
            final NamedEndpoint tlsName = endpointName != null ? endpointName : endpointHost;
            onBeforeTlsHandshake(context, endpointHost);
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} upgrading to TLS {}:{}", ConnPoolSupport.getId(conn), tlsName.getHostName(), tlsName.getPort());
            }
            final SSLSocket upgradedSocket = tlsSocketStrategy.upgrade(socket, tlsName.getHostName(), tlsName.getPort(), attachment, context);
            conn.bind(upgradedSocket, socket);
            onAfterTlsHandshake(context, endpointHost);
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} upgraded to TLS {}:{}", ConnPoolSupport.getId(conn), tlsName.getHostName(), tlsName.getPort());
            }
        } else {
            throw new UnsupportedSchemeException(newProtocol + " protocol is not supported");
        }
    }

    protected void onBeforeSocketConnect(final HttpContext httpContext, final HttpHost endpointHost) {
    }

    protected void onAfterSocketConnect(final HttpContext httpContext, final HttpHost endpointHost) {
    }

    protected void onBeforeTlsHandshake(final HttpContext httpContext, final HttpHost endpointHost) {
    }

    protected void onAfterTlsHandshake(final HttpContext httpContext, final HttpHost endpointHost) {
    }

}
