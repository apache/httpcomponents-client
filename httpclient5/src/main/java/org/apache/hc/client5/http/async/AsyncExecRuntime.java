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
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
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
public interface AsyncExecRuntime {

    boolean isConnectionAcquired();

    void acquireConnection(
            HttpRoute route,
            Object state,
            HttpClientContext clientContext,
            FutureCallback<AsyncExecRuntime> callback);

    void releaseConnection();

    void discardConnection();

    boolean isConnected();

    void disconnect();

    void connect(
            HttpClientContext clientContext,
            FutureCallback<AsyncExecRuntime> callback);

    void upgradeTls(HttpClientContext context);

    /**
     * Initiates a message exchange using the given handler.
     */
    void execute(
            AsyncClientExchangeHandler exchangeHandler,
            HttpClientContext context);

    boolean validateConnection();

    boolean isConnectionReusable();

    void markConnectionReusable();

    void markConnectionNonReusable();

    void setConnectionState(Object state);

    void setConnectionValidFor(TimeValue duration);

}
