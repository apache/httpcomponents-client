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

package org.apache.hc.client5.http.classic;

import java.io.IOException;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.concurrent.CancellableDependency;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.util.TimeValue;

/**
 * Execution runtime that provides access to the underlying connection endpoint and helps
 * manager its life cycle.
 * <p>
 * This interface is considered internal and generally ought not be used or accessed
 * by custom request exec handlers.
 *
 * @since 5.0
 */
@Internal
public interface ExecRuntime {

    /**
     * Determines of the request execution has been aborted.
     *
     * @return {@code true} if the request execution has been acquired,
     * {@code false} otherwise.
     */
    boolean isExecutionAborted();

    /**
     * Determines of a connection endpoint has been acquired.
     *
     * @return {@code true} if an endpoint has been acquired, {@code false} otherwise.
     */
    boolean isEndpointAcquired();

    /**
     * Acquires a connection endpoint. Endpoints can leased from a pool
     * or unconnected new endpoint can be created.
     *
     * @param id unique operation ID or {@code null}.
     * @param route the connection route.
     * @param state the expected connection state. May be {@code null} if connection
     *              can be state-less or its state is irrelevant.
     * @param context the execution context.
     */
    void acquireEndpoint(
            String id,
            HttpRoute route,
            Object state,
            HttpClientContext context) throws IOException;

    /**
     * Releases the acquired endpoint potentially making it available for re-use.
     */
    void releaseEndpoint();

    /**
     * Shuts down and discards the acquired endpoint.
     */
    void discardEndpoint();

    /**
     * Determines of there the endpoint is connected to the initial hop (connection
     * target in case of a direct route or to the first proxy hop in case of a route
     * via a proxy or multiple proxies).
     *
     * @return {@code true} if the endpoint is connected, {@code false} otherwise.
     */
    boolean isEndpointConnected();

    /**
     * Disconnects the local endpoint from the initial hop in the connection route.
     */
    void disconnectEndpoint() throws IOException;

    /**
     * Connect the local endpoint to the initial hop (connection target in case
     * of a direct route or to the first proxy hop in case of a route via a proxy
     * or multiple proxies).
     *
     * @param context the execution context.
     */
    void connectEndpoint(HttpClientContext context) throws IOException;

    /**
     * Upgrades transport security of the active connection by using the TLS security protocol.
     *
     * @param context the execution context.
     */
    void upgradeTls(HttpClientContext context) throws IOException;

    /**
     * Executes HTTP request using the given context.
     *
     * @param id unique operation ID or {@code null}.
     * @param request the request message.
     * @param context the execution context.
     */
    ClassicHttpResponse execute(
            String id,
            ClassicHttpRequest request,
            HttpClientContext context) throws IOException, HttpException;

    /**
     * Determines of the connection is considered re-usable.
     *
     * @return {@code true} if the connection is re-usable, {@code false} otherwise.
     */
    boolean isConnectionReusable();

    /**
     * Marks the connection as potentially re-usable for the given period of time
     * and also marks it as stateful if the state representation is given.
     * @param state the connection state representation or {@code null} if stateless.
     * @param validityTime the period of time this connection is valid for.
     */
    void markConnectionReusable(Object state, TimeValue validityTime);

    /**
     * Marks the connection as non re-usable.
     */
    void markConnectionNonReusable();

    /**
     * Forks this runtime for parallel execution.
     *
     * @return another runtime with the same configuration.
     */
    ExecRuntime fork(CancellableDependency cancellableAware);

}
