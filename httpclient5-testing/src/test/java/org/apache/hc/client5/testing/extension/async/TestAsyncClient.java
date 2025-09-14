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

package org.apache.hc.client5.testing.extension.async;

import java.io.IOException;
import java.util.concurrent.Future;

import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.nio.AsyncClientConnectionManager;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorStatus;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;

public class TestAsyncClient extends CloseableHttpAsyncClient {

    private final CloseableHttpAsyncClient client;
    private final AsyncClientConnectionManager connectionManager;

    public TestAsyncClient(final CloseableHttpAsyncClient client,
                           final AsyncClientConnectionManager connectionManager) {
        this.client = Args.notNull(client, "Client");
        this.connectionManager = connectionManager;
    }

    @Override
    public void close(final CloseMode closeMode) {
        client.close(closeMode);
    }

    @Override
    public void close() throws IOException {
        client.close();
    }

    @Override
    public void start() {
        client.start();
    }

    @Override
    public IOReactorStatus getStatus() {
        return client.getStatus();
    }

    @Override
    public void awaitShutdown(final TimeValue waitTime) throws InterruptedException {
        client.awaitShutdown(waitTime);
    }

    @Override
    public void initiateShutdown() {
        client.initiateShutdown();
    }

    @Override
    protected <T> Future<T> doExecute(final HttpHost target,
                                      final AsyncRequestProducer requestProducer,
                                      final AsyncResponseConsumer<T> responseConsumer,
                                      final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
                                      final HttpContext context,
                                      final FutureCallback<T> callback) {
        return client.execute(target, requestProducer, responseConsumer, pushHandlerFactory, context, callback);
    }

    /**
     * @deprecated Do not use.
     */
    @Deprecated
    @Override
    public void register(final String hostname,
                         final String uriPattern,
                         final Supplier<AsyncPushConsumer> supplier) {
        client.register(hostname, uriPattern, supplier);
    }

    @SuppressWarnings("unchecked")
    public <T extends CloseableHttpAsyncClient> T getImplementation() {
        return (T) client;
    }

    @SuppressWarnings("unchecked")
    public <T extends AsyncClientConnectionManager> T getConnectionManager() {
        return (T) connectionManager;
    }

}
