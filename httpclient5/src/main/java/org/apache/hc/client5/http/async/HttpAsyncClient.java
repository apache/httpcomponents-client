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

import java.util.concurrent.Future;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * This interface represents only the most basic contract for HTTP request
 * execution. It imposes no restrictions or particular details on the request
 * execution process and leaves the specifics of state management,
 * authentication and redirect handling up to individual implementations.
 *
 * @since 4.0
 */
public interface HttpAsyncClient {

    /**
     * Leases {@link AsyncClientEndpoint} for the given {@link HttpHost}.
     * <p>
     * The endpoint MUST be released back when no longer used by calling
     * {@link AsyncClientEndpoint#releaseAndReuse()} or {@link AsyncClientEndpoint#releaseAndDiscard()}
     */
    Future<AsyncClientEndpoint> lease(
            HttpHost host,
            HttpContext context,
            FutureCallback<AsyncClientEndpoint> callback);

    /**
     * Initiates asynchronous HTTP request execution using the given context.
     * <p>
     * The request producer passed to this method will be used to generate
     * a request message and stream out its content without buffering it
     * in memory. The response consumer passed to this method will be used
     * to process a response message without buffering its content in memory.
     * <p>
     * Please note it may be unsafe to interact with the context instance
     * while the request is still being executed.
     *
     * @param <T> the result type of request execution.
     * @param requestProducer request producer callback.
     * @param responseConsumer response consumer callback.
     * @param context HTTP context
     * @param callback future callback.
     * @return future representing pending completion of the operation.
     */
    <T> Future<T> execute(
            AsyncRequestProducer requestProducer,
            AsyncResponseConsumer<T> responseConsumer,
            HttpContext context,
            FutureCallback<T> callback);

    /**
     * Registers {@link AsyncPushConsumer} for the given host and the URI pattern.
     *
     * @param hostname the name of the host this consumer intended for.
     *                 Can be {@code null} if applies to all hosts
     * @param uriPattern URI request pattern
     * @param supplier supplier that will be used to supply a consumer instance
     *                 for the given combination of hostname and URI pattern.
     */
    void register(String hostname, String uriPattern, Supplier<AsyncPushConsumer> supplier);

}
