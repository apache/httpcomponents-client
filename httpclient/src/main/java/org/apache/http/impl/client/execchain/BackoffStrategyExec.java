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

package org.apache.http.impl.client.execchain;

import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;

import org.apache.http.HttpException;
import org.apache.http.annotation.Immutable;
import org.apache.http.client.BackoffManager;
import org.apache.http.client.ConnectionBackoffStrategy;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpExecutionAware;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.routing.HttpRoute;

/**
 * @since 4.3
 */
@Immutable
public class BackoffStrategyExec implements ClientExecChain {

    private final ClientExecChain requestExecutor;
    private final ConnectionBackoffStrategy connectionBackoffStrategy;
    private final BackoffManager backoffManager;

    public BackoffStrategyExec(
            final ClientExecChain requestExecutor,
            final ConnectionBackoffStrategy connectionBackoffStrategy,
            final BackoffManager backoffManager) {
        super();
        if (requestExecutor == null) {
            throw new IllegalArgumentException("HTTP client request executor may not be null");
        }
        if (connectionBackoffStrategy == null) {
            throw new IllegalArgumentException("Connection backoff strategy may not be null");
        }
        if (backoffManager == null) {
            throw new IllegalArgumentException("Backoff manager may not be null");
        }
        this.requestExecutor = requestExecutor;
        this.connectionBackoffStrategy = connectionBackoffStrategy;
        this.backoffManager = backoffManager;
    }

    public CloseableHttpResponse execute(
            final HttpRoute route,
            final HttpRequestWrapper request,
            final HttpClientContext context,
            final HttpExecutionAware execAware) throws IOException, HttpException {
        if (route == null) {
            throw new IllegalArgumentException("HTTP route may not be null");
        }
        if (request == null) {
            throw new IllegalArgumentException("HTTP request may not be null");
        }
        if (context == null) {
            throw new IllegalArgumentException("HTTP context may not be null");
        }
        CloseableHttpResponse out = null;
        try {
            out = this.requestExecutor.execute(route, request, context, execAware);
        } catch (Exception ex) {
            if (out != null) {
                out.close();
            }
            if (this.connectionBackoffStrategy.shouldBackoff(ex)) {
                this.backoffManager.backOff(route);
            }
            if (ex instanceof RuntimeException) throw (RuntimeException) ex;
            if (ex instanceof HttpException) throw (HttpException) ex;
            if (ex instanceof IOException) throw (IOException) ex;
            throw new UndeclaredThrowableException(ex);
        }
        if (this.connectionBackoffStrategy.shouldBackoff(out)) {
            this.backoffManager.backOff(route);
        } else {
            this.backoffManager.probe(route);
        }
        return out;
    }

}
