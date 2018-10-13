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

import java.io.IOException;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.concurrent.CancellableDependency;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.util.Args;

/**
 * Represents a single element in the client side asynchronous request execution chain.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public interface AsyncExecChain {

    /**
     * Request execution scope that includes the unique message exchange ID,
     * the connection route, the original request message, the execution
     * context and the internal execution runtime.
     */
    final class Scope {

        public final String exchangeId;
        public final HttpRoute route;
        public final HttpRequest originalRequest;
        public final CancellableDependency cancellableDependency;
        public final HttpClientContext clientContext;
        public final AsyncExecRuntime execRuntime;

        public Scope(
                final String exchangeId,
                final HttpRoute route,
                final HttpRequest originalRequest,
                final CancellableDependency cancellableDependency,
                final HttpClientContext clientContext,
                final AsyncExecRuntime execRuntime) {
            this.exchangeId = Args.notBlank(exchangeId, "Exchange id");
            this.route = Args.notNull(route, "Route");
            this.originalRequest = Args.notNull(originalRequest, "Original request");
            this.cancellableDependency = Args.notNull(cancellableDependency, "Dependency");
            this.clientContext = clientContext != null ? clientContext : HttpClientContext.create();
            this.execRuntime = Args.notNull(execRuntime, "Exec runtime");
        }

    }

    /**
     * Proceeds to the next element in the request execution chain.
     *
     * @param request the actual request.
     * @param entityProducer the request entity producer or {@code null} if the request
     *                      does not enclose an entity.
     * @param scope the execution scope .
     * @param asyncExecCallback the execution callback.
     */
    void proceed(
            HttpRequest request,
            AsyncEntityProducer entityProducer,
            Scope scope,
            AsyncExecCallback asyncExecCallback) throws HttpException, IOException;

}
