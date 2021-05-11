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

import java.net.InetSocketAddress;
import java.util.concurrent.Future;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.core5.concurrent.CallbackContribution;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Resolver;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http2.nio.pool.H2ConnPool;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.ModalCloseable;
import org.apache.hc.core5.reactor.ConnectionInitiator;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

class InternalH2ConnPool implements ModalCloseable {

    private final H2ConnPool connPool;

    private volatile Resolver<HttpHost, ConnectionConfig> connectionConfigResolver;

    InternalH2ConnPool(final ConnectionInitiator connectionInitiator,
                       final Resolver<HttpHost, InetSocketAddress> addressResolver,
                       final TlsStrategy tlsStrategy) {
        this.connPool = new H2ConnPool(connectionInitiator, addressResolver, tlsStrategy);
    }

    public void close(final CloseMode closeMode) {
        connPool.close(closeMode);
    }

    public void close() {
        connPool.close();
    }

    private ConnectionConfig resolveConnectionConfig(final HttpHost httpHost) {
        final Resolver<HttpHost, ConnectionConfig> resolver = this.connectionConfigResolver;
        final ConnectionConfig connectionConfig = resolver != null ? resolver.resolve(httpHost) : null;
        return connectionConfig != null ? connectionConfig : ConnectionConfig.DEFAULT;
    }

    public Future<IOSession> getSession(
            final HttpHost endpoint,
            final Timeout connectTimeout,
            final FutureCallback<IOSession> callback) {
        final ConnectionConfig connectionConfig = resolveConnectionConfig(endpoint);
        return connPool.getSession(
                endpoint,
                connectTimeout != null ? connectTimeout : connectionConfig.getConnectTimeout(),
                new CallbackContribution<IOSession>(callback) {

                    @Override
                    public void completed(final IOSession ioSession) {
                        final Timeout socketTimeout = connectionConfig.getSocketTimeout();
                        if (socketTimeout != null) {
                            ioSession.setSocketTimeout(socketTimeout);
                        }
                        callback.completed(ioSession);
                    }

                });
    }

    public void closeIdle(final TimeValue idleTime) {
        connPool.closeIdle(idleTime);
    }

    public void setConnectionConfigResolver(final Resolver<HttpHost, ConnectionConfig> connectionConfigResolver) {
        this.connectionConfigResolver = connectionConfigResolver;
    }

}
