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

package org.apache.hc.client5.http.impl.compat;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.Future;

import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Experimental;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.concurrent.CancellableDependency;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;

/**
 * {@link CloseableHttpClient} implementation backed by {@link CloseableHttpAsyncClient}
 * acting as a compatibility bridge with the classic APIs based on the standard
 * {@link java.io.InputStream} / {@link java.io.OutputStream} model.
 *
 * @since 5.5
 */
@Experimental
@Internal
@Contract(threading = ThreadingBehavior.SAFE_CONDITIONAL)
public class ClassicToAsyncAdaptor extends CloseableHttpClient {

    private final CloseableHttpAsyncClient client;
    private final Timeout operationTimeout;

    public ClassicToAsyncAdaptor(final CloseableHttpAsyncClient client, final Timeout operationTimeout) {
        super();
        this.client = client;
        this.operationTimeout = operationTimeout;
    }

    @Override
    protected CloseableHttpResponse doExecute(
            final HttpHost target,
            final ClassicHttpRequest request,
            final HttpContext context) throws IOException {
        final ClassicToAsyncRequestProducer requestProducer = new ClassicToAsyncRequestProducer(request, operationTimeout);
        final ClassicToAsyncResponseConsumer responseConsumer = new ClassicToAsyncResponseConsumer(operationTimeout);
        final Future<Void> resultFuture = client.execute(target, requestProducer, responseConsumer, null, context, null);
        if (request instanceof CancellableDependency) {
            ((CancellableDependency) request).setDependency(() -> resultFuture.cancel(true));
        }
        try {
            requestProducer.blockWaiting().execute();
            final ClassicHttpResponse response = responseConsumer.blockWaiting();
            return CloseableHttpResponse.create(response,
                    (closeable, closeMode) -> {
                        try {
                            if (closeMode == CloseMode.GRACEFUL) {
                                closeable.close();
                            }
                        } finally {
                            resultFuture.cancel(true);
                        }
                    });
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException();
        }
    }

    @Override
    public void close() throws IOException {
        client.close();
    }

    @Override
    public void close(final CloseMode closeMode) {
        client.close(closeMode);
    }

}
