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
package org.apache.http.client.fluent;

import java.util.concurrent.Future;

import org.apache.http.client.ResponseHandler;
import org.apache.http.concurrent.BasicFuture;
import org.apache.http.concurrent.FutureCallback;

public class Async {

    private Executor executor;
    private java.util.concurrent.Executor concurrentExec;

    public static Async newInstance() {
        return new Async();
    }

    Async() {
        super();
    }

    public Async use(final Executor executor) {
        this.executor = executor;
        return this;
    }

    public Async use(final java.util.concurrent.Executor concurrentExec) {
        this.concurrentExec = concurrentExec;
        return this;
    }

    static class ExecRunnable<T> implements Runnable {

        private final BasicFuture<T> future;
        private final Request request;
        private final Executor executor;
        private final ResponseHandler<T> handler;

        ExecRunnable(
                final BasicFuture<T> future,
                final Request request,
                final Executor executor,
                final ResponseHandler<T> handler) {
            super();
            this.future = future;
            this.request = request;
            this.executor = executor;
            this.handler = handler;
        }

        @Override
        public void run() {
            try {
                final Response response = this.executor.execute(this.request);
                final T result = response.handleResponse(this.handler);
                this.future.completed(result);
            } catch (final Exception ex) {
                this.future.failed(ex);
            }
        }

    }

    public <T> Future<T> execute(
            final Request request, final ResponseHandler<T> handler, final FutureCallback<T> callback) {
        final BasicFuture<T> future = new BasicFuture<T>(callback);
        final ExecRunnable<T> runnable = new ExecRunnable<T>(
                future,
                request,
                this.executor != null ? this.executor : Executor.newInstance(),
                handler);
        if (this.concurrentExec != null) {
            this.concurrentExec.execute(runnable);
        } else {
            final Thread t = new Thread(runnable);
            t.setDaemon(true);
            t.start();
        }
        return future;
    }

    public <T> Future<T> execute(final Request request, final ResponseHandler<T> handler) {
        return execute(request, handler, null);
    }

    public Future<Content> execute(final Request request, final FutureCallback<Content> callback) {
        return execute(request, new ContentResponseHandler(), callback);
    }

    public Future<Content> execute(final Request request) {
        return execute(request, new ContentResponseHandler(), null);
    }

}
