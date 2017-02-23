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
package org.apache.hc.client5.http.io;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * Represents a manager of persistent client connections.
 * <p>
 * The purpose of an HTTP connection manager is to serve as a factory for new
 * HTTP connections, manage persistent connections and synchronize access to
 * persistent connections making sure that only one thread of execution can
 * have access to a connection at a time.
 * <p>
 * Implementations of this interface must be thread-safe. Access to shared
 * data must be synchronized as methods of this interface may be executed
 * from multiple threads.
 *
 * @since 4.3
 */
public interface HttpClientConnectionManager extends Closeable {

    /**
     * Returns a {@link LeaseRequest} object which can be used to obtain
     * a {@link ConnectionEndpoint} to cancel the request by calling
     * {@link LeaseRequest#cancel()}.
     * <p>
     * Please note that newly allocated endpoints can be leased
     * {@link ConnectionEndpoint#isConnected() disconnected}. The consumer of the endpoint
     * is responsible for fully establishing the route to the endpoint target
     * by calling {@link #connect(ConnectionEndpoint, long, TimeUnit, HttpContext)}
     * in order to connect directly to the target or to the first proxy hop,
     * and optionally calling {@link #upgrade(ConnectionEndpoint, HttpContext)} method
     * to upgrade the underlying transport to Transport Layer Security after having
     * executed a {@code CONNECT} method to all intermediate proxy hops.
     *
     * @param route HTTP route of the requested connection.
     * @param state expected state of the connection or {@code null}
     *              if the connection is not expected to carry any state.
     */
    LeaseRequest lease(HttpRoute route, Object state);

    /**
     * Releases the endpoint back to the manager making it potentially
     * re-usable by other consumers. Optionally, the maximum period
     * of how long the manager should keep the connection alive can be
     * defined using {@code validDuration} and {@code timeUnit}
     * parameters.
     *
     * @param endpoint      the managed endpoint.
     * @param newState      the new connection state of {@code null} if state-less.
     * @param validDuration the duration of time this connection is valid for reuse.
     * @param timeUnit the time unit.
     *
     * @see #closeExpired()
     */
    void release(ConnectionEndpoint endpoint, Object newState, long validDuration, TimeUnit timeUnit);

    /**
     * Connects the endpoint to the initial hop (connection target in case
     * of a direct route or to the first proxy hop in case of a route via a proxy
     * or multiple proxies).
     *
     * @param endpoint      the managed endpoint.
     * @param connectTimeout connect timeout.
     * @param timeUnit the time unit.
     * @param context the actual HTTP context.
     * @throws IOException
     */
    void connect(
            ConnectionEndpoint endpoint,
            long connectTimeout,
            TimeUnit timeUnit,
            HttpContext context) throws IOException;

    /**
     * Upgrades the endpoint's underlying transport to Transport Layer Security.
     *
     * @param endpoint      the managed endpoint.
     * @param context the actual HTTP context.
     * @throws IOException
     */
    void upgrade(
            ConnectionEndpoint endpoint,
            HttpContext context) throws IOException;

}
