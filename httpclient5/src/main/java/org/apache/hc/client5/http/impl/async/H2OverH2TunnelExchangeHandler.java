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
package org.apache.hc.client5.http.impl.async;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.StreamControl;
import org.apache.hc.core5.http.impl.BasicEntityDetails;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.RequestChannel;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.reactor.ssl.TransportSecurityLayer;
import org.apache.hc.core5.util.Timeout;

/**
 * Exchange handler that establishes an HTTP/2 CONNECT tunnel and exposes
 * the resulting data stream as a {@link ProtocolIOSession}.
 *
 * @since 5.7
 */
final class H2OverH2TunnelExchangeHandler implements AsyncClientExchangeHandler {

    private final IOSession physicalSession;
    private final NamedEndpoint targetEndpoint;
    private final Timeout connectTimeout;
    private final boolean secure;
    private final TlsStrategy tlsStrategy;
    private final HttpRequestInterceptor connectRequestInterceptor;
    private final IOEventHandlerFactory protocolStarter;
    private final FutureCallback<ProtocolIOSession> callback;

    private final AtomicBoolean done;

    private volatile DataStreamChannel dataChannel;
    private volatile CapacityChannel capacityChannel;
    private volatile StreamControl streamControl;
    private volatile H2TunnelProtocolIOSession tunnelSession;

    H2OverH2TunnelExchangeHandler(
            final IOSession physicalSession,
            final NamedEndpoint targetEndpoint,
            final Timeout connectTimeout,
            final boolean secure,
            final TlsStrategy tlsStrategy,
            final HttpRequestInterceptor connectRequestInterceptor,
            final IOEventHandlerFactory protocolStarter,
            final FutureCallback<ProtocolIOSession> callback) {
        this.physicalSession = physicalSession;
        this.targetEndpoint = targetEndpoint;
        this.connectTimeout = connectTimeout;
        this.secure = secure;
        this.tlsStrategy = tlsStrategy;
        this.connectRequestInterceptor = connectRequestInterceptor;
        this.protocolStarter = protocolStarter;
        this.callback = callback;
        this.done = new AtomicBoolean(false);
    }

    void initiated(final StreamControl streamControl) {
        this.streamControl = streamControl;
        final H2TunnelProtocolIOSession tunnel = this.tunnelSession;
        if (tunnel != null) {
            tunnel.bindStreamControl(streamControl);
        }
    }

    @Override
    public void releaseResources() {
    }

    @Override
    public void failed(final Exception cause) {
        fail(cause);
    }

    @Override
    public void cancel() {
        fail(new ConnectionClosedException("Tunnel setup cancelled"));
    }

    @Override
    public void produceRequest(final RequestChannel requestChannel, final HttpContext context) throws HttpException, IOException {
        final HttpRequest connectRequest = new BasicHttpRequest(Method.CONNECT.name(), (String) null);
        connectRequest.setAuthority(new URIAuthority(targetEndpoint));
        if (connectRequestInterceptor != null) {
            connectRequestInterceptor.process(connectRequest, null, context);
        }
        requestChannel.sendRequest(connectRequest, new BasicEntityDetails(-1, null), context);
    }

    @Override
    public int available() {
        final H2TunnelProtocolIOSession tunnel = this.tunnelSession;
        return tunnel != null ? tunnel.available() : 0;
    }

    @Override
    public void produce(final DataStreamChannel channel) throws IOException {
        this.dataChannel = channel;
        final H2TunnelProtocolIOSession tunnel = this.tunnelSession;
        if (tunnel != null) {
            tunnel.attachChannel(channel);
            tunnel.onOutputReady();
        }
    }

    @Override
    public void consumeInformation(final HttpResponse response, final HttpContext context) {
    }

    @Override
    public void consumeResponse(
            final HttpResponse response,
            final EntityDetails entityDetails,
            final HttpContext context) throws HttpException, IOException {

        final int status = response.getCode();
        if (status < 200 || status >= 300) {
            throw new TunnelRefusedException(response);
        }

        if (entityDetails == null) {
            throw new HttpException("CONNECT response does not provide a tunneled data stream");
        }

        if (this.tunnelSession != null) {
            return;
        }

        final H2TunnelProtocolIOSession tunnel =
                new H2TunnelProtocolIOSession(physicalSession, targetEndpoint, connectTimeout, streamControl);

        final DataStreamChannel currentChannel = this.dataChannel;
        if (currentChannel != null) {
            tunnel.attachChannel(currentChannel);
        }
        final CapacityChannel currentCapacity = this.capacityChannel;
        if (currentCapacity != null) {
            tunnel.updateCapacityChannel(currentCapacity);
        }
        this.tunnelSession = tunnel;

        if (secure) {
            tlsStrategy.upgrade(
                    tunnel,
                    targetEndpoint,
                    null,
                    connectTimeout,
                    new FutureCallback<TransportSecurityLayer>() {

                        @Override
                        public void completed(final TransportSecurityLayer transportSecurityLayer) {
                            try {
                                startProtocol(tunnel);
                                complete(tunnel);
                            } catch (final Exception ex) {
                                fail(ex);
                            }
                        }

                        @Override
                        public void failed(final Exception ex) {
                            fail(ex);
                        }

                        @Override
                        public void cancelled() {
                            fail(new ConnectionClosedException("Tunnel TLS upgrade cancelled"));
                        }
                    });
        } else {
            startProtocol(tunnel);
            complete(tunnel);
        }
    }

    private void startProtocol(final H2TunnelProtocolIOSession tunnel) throws IOException {
        if (protocolStarter == null) {
            return;
        }
        final IOEventHandler protocolHandler = protocolStarter.createHandler(tunnel, null);
        tunnel.upgrade(protocolHandler);
        protocolHandler.connected(tunnel);
    }

    @Override
    public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        this.capacityChannel = capacityChannel;
        final H2TunnelProtocolIOSession tunnel = this.tunnelSession;
        if (tunnel != null) {
            tunnel.updateCapacityChannel(capacityChannel);
        }
    }

    @Override
    public void consume(final ByteBuffer src) throws IOException {
        final H2TunnelProtocolIOSession tunnel = this.tunnelSession;
        if (tunnel != null && src != null && src.hasRemaining()) {
            tunnel.onInput(src);
        }
    }

    @Override
    public void streamEnd(final List<? extends Header> trailers) {
        final H2TunnelProtocolIOSession tunnel = this.tunnelSession;
        if (tunnel != null) {
            tunnel.onRemoteStreamEnd();
        } else {
            closeTransport(CloseMode.GRACEFUL);
            if (done.compareAndSet(false, true) && callback != null) {
                callback.failed(new ConnectionClosedException("Tunnel stream closed before establishment"));
            }
        }
    }

    private void closeTransport(final CloseMode closeMode) {
        final H2TunnelProtocolIOSession tunnel = this.tunnelSession;
        if (tunnel != null) {
            tunnel.close(closeMode);
            return;
        }
        final StreamControl currentStreamControl = this.streamControl;
        if (currentStreamControl != null) {
            currentStreamControl.cancel();
        }
    }

    private void fail(final Exception cause) {
        closeTransport(CloseMode.IMMEDIATE);
        if (done.compareAndSet(false, true) && callback != null) {
            callback.failed(cause);
        }
    }

    private void complete(final H2TunnelProtocolIOSession tunnel) {
        if (done.compareAndSet(false, true) && callback != null) {
            callback.completed(tunnel);
        }
    }
}
