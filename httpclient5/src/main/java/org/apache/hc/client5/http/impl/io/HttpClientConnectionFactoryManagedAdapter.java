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

import org.apache.hc.client5.http.io.ManagedHttpClientConnection;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.io.HttpClientConnection;
import org.apache.hc.core5.http.io.HttpConnectionFactory;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;

import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;

/** Adapter to use convert basic http client connection factories into managed http client connection factories. */
public final class HttpClientConnectionFactoryManagedAdapter implements HttpConnectionFactory<ManagedHttpClientConnection> {

    private final HttpConnectionFactory<HttpClientConnection> delegate;

    public HttpClientConnectionFactoryManagedAdapter(final HttpConnectionFactory<HttpClientConnection> delegate) {
        this.delegate = delegate;
    }

    @Override
    public ManagedHttpClientConnection createConnection(final Socket socket) throws IOException {
        return new ManagedHttpClientConnectionAdapter(delegate.createConnection(socket));
    }

    @Override
    public String toString() {
        return "HttpClientConnectionFactoryManagedAdapter{" + delegate + '}';
    }

    private static final class ManagedHttpClientConnectionAdapter implements ManagedHttpClientConnection {

        private final HttpClientConnection delegate;
        private final AtomicBoolean closed = new AtomicBoolean();
        private Timeout socketTimeout;
        private volatile Socket currentSocket;

        ManagedHttpClientConnectionAdapter(final HttpClientConnection delegate) {
            this.delegate = delegate;
        }

        // ManagedHttpClientConnection methods not provided by HttpClientConnection

        @Override
        public void bind(final Socket socket) throws IOException {
            if (this.closed.get()) {
                final Socket socketSnapshot = currentSocket;
                if (socketSnapshot != null) {
                    socketSnapshot.close(); // allow this to throw...
                    // ...but if it doesn't, explicitly throw one ourselves.
                }
                throw new InterruptedIOException("Connection already shutdown");
            }
            currentSocket = socket;
            socketTimeout = Timeout.ofMilliseconds(socket.getSoTimeout());
        }

        @Override
        public Socket getSocket() {
            return currentSocket;
        }

        @Override
        public SSLSession getSSLSession() {
            return delegate.getSSLSession();
        }

        @Override
        public void passivate() {
            delegate.setSocketTimeout(Timeout.ZERO_MILLISECONDS);
        }

        @Override
        public void activate() {
            delegate.setSocketTimeout(socketTimeout);
        }

        // HttpClientConnection methods

        @Override
        public boolean isConsistent() {
            return delegate.isConsistent();
        }

        @Override
        public void sendRequestHeader(final ClassicHttpRequest request) throws HttpException, IOException {
            delegate.sendRequestHeader(request);
        }

        @Override
        public void terminateRequest(final ClassicHttpRequest request) throws HttpException, IOException {
            delegate.terminateRequest(request);
        }

        @Override
        public void sendRequestEntity(final ClassicHttpRequest request) throws HttpException, IOException {
            delegate.sendRequestEntity(request);
        }

        @Override
        public ClassicHttpResponse receiveResponseHeader() throws HttpException, IOException {
            return delegate.receiveResponseHeader();
        }

        @Override
        public void receiveResponseEntity(final ClassicHttpResponse response) throws HttpException, IOException {
            delegate.receiveResponseEntity(response);
        }

        @Override
        public boolean isDataAvailable(final Timeout timeout) throws IOException {
            return delegate.isDataAvailable(timeout);
        }

        @Override
        public boolean isStale() throws IOException {
            return delegate.isStale();
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            if (!closed.getAndSet(true)) {
                delegate.close();
            }
        }

        @Override
        public EndpointDetails getEndpointDetails() {
            return delegate.getEndpointDetails();
        }

        @Override
        public SocketAddress getLocalAddress() {
            return delegate.getLocalAddress();
        }

        @Override
        public SocketAddress getRemoteAddress() {
            return delegate.getRemoteAddress();
        }

        @Override
        public ProtocolVersion getProtocolVersion() {
            return delegate.getProtocolVersion();
        }

        @Override
        public boolean isOpen() {
            return delegate.isOpen();
        }

        @Override
        public Timeout getSocketTimeout() {
            return delegate.getSocketTimeout();
        }

        @Override
        public void setSocketTimeout(final Timeout timeout) {
            delegate.setSocketTimeout(timeout);
        }

        @Override
        public void close(final CloseMode closeMode) {
            if (!closed.getAndSet(true)) {
                delegate.close(closeMode);
            }
        }

        @Override
        public String toString() {
            return "ManagedHttpClientConnectionAdapter{delegate=" + delegate + ", socketTimeout=" + socketTimeout + '}';
        }
    }
}
