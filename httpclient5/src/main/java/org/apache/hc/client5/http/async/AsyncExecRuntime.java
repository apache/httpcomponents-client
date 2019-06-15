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

package org.apache.hc.client5.http.async;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.util.TimeValue;

/**
 * Execution runtime that provides access to the underlying connection endpoint and helps
 * manager its life cycle.
 * <p>
 * This interface is considered internal and generally ought not be used or accessed
 * by custom request exec handlers.
 * </p>
 *
 * @since 5.0
 */
@Internal
public interface AsyncExecRuntime {

    /**
     * Determines of a connection endpoint has been acquired.
     *
     * @return {@code true} if an endpoint has been acquired, {@code false} otherwise.
     */
    boolean isEndpointAcquired();

    /**
     * Initiates operation to acquire a connection endpoint. Endpoints can leased from a pool
     * or unconnected new endpoint can be created.
     *
     * @param id unique operation ID or {@code null}.
     * @param route the connection route.
     * @param state the expected connection state. May be {@code null} if connection
     *              can be state-less or its state is irrelevant.
     * @param context the execution context.
     * @param callback the result callback.
     * @return handle that can be used to cancel the operation.
     */
    Cancellable acquireEndpoint(
            String id,
            HttpRoute route,
            Object state,
            HttpClientContext context,
            FutureCallback<AsyncExecRuntime> callback);

    /**
     * Releases the acquired endpoint potentially making it available for re-use.
     */
    void releaseEndpoint();

    /**
     * Shuts down and discards the acquired endpoint.
     */
    void discardEndpoint();

    /**
     * Determines of there the endpoint is connected to the initial hop (connection target
     * in case of a direct route or to the first proxy hop in case of a route via a proxy
     * or multiple proxies).
     *
     * @return {@code true} if the endpoint is connected, {@code false} otherwise.
     */
    boolean isEndpointConnected();

    /**
     * Initiates operation to connect the local endpoint to the initial hop (connection
     * target in case of a direct route or to the first proxy hop in case of a route
     * via a proxy or multiple proxies).
     *
     * @param context the execution context.
     * @param callback the result callback.
     * @return handle that can be used to cancel the operation.
     */
    Cancellable connectEndpoint(
            HttpClientContext context,
            FutureCallback<AsyncExecRuntime> callback);

    /**
     * Upgrades transport security of the active connection by using the TLS security protocol.
     *
     * @param context the execution context.
     */
    void upgradeTls(HttpClientContext context);

    /**
     * Validates the connection making sure it can be used to execute requests.
     *
     * @return {@code true} if the connection is valid, {@code false}.
     */
    boolean validateConnection();

    /**
     * Initiates a message exchange using the given handler.
     *
     * @param id unique operation ID or {@code null}.
     * @param exchangeHandler the client message handler.
     * @param context the execution context.
     */
    Cancellable execute(
            String id,
            AsyncClientExchangeHandler exchangeHandler,
            HttpClientContext context);

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
    AsyncExecRuntime fork();

}
