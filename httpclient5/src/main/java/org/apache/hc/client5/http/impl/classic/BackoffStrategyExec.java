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

package org.apache.hc.client5.http.impl.classic;

import java.io.IOException;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.classic.BackoffManager;
import org.apache.hc.client5.http.classic.ConnectionBackoffStrategy;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecChainHandler;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.util.Args;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Request execution handler in the classic request execution chain
 * that is responsible for execution of an {@link ConnectionBackoffStrategy}.
 * <p>
 * Further responsibilities such as communication with the opposite
 * endpoint is delegated to the next executor in the request execution
 * chain.
 * </p>
 *
 * @since 4.3
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public final class BackoffStrategyExec implements ExecChainHandler {

    private static final Logger LOG = LoggerFactory.getLogger(BackoffStrategyExec.class);

    private final ConnectionBackoffStrategy connectionBackoffStrategy;
    private final BackoffManager backoffManager;

    /**
     * Constructs a {@code BackoffStrategyExec} with the specified
     * {@link ConnectionBackoffStrategy} and {@link BackoffManager}.
     *
     * @param connectionBackoffStrategy the strategy to determine whether
     *                                  to backoff based on the response or exception
     * @param backoffManager            the manager responsible for applying backoff
     *                                  and probing actions to the HTTP routes
     */
    public BackoffStrategyExec(
            final ConnectionBackoffStrategy connectionBackoffStrategy,
            final BackoffManager backoffManager) {
        super();
        Args.notNull(connectionBackoffStrategy, "Connection backoff strategy");
        Args.notNull(backoffManager, "Backoff manager");
        this.connectionBackoffStrategy = connectionBackoffStrategy;
        this.backoffManager = backoffManager;
    }

    @Override
    public ClassicHttpResponse execute(
            final ClassicHttpRequest request,
            final ExecChain.Scope scope,
            final ExecChain chain) throws IOException, HttpException {
        Args.notNull(request, "HTTP request");
        Args.notNull(scope, "Scope");
        final HttpRoute route = scope.route;

        final ClassicHttpResponse response;
        try {
            response = chain.proceed(request, scope);
        } catch (final IOException | HttpException ex) {
            if (this.connectionBackoffStrategy.shouldBackoff(ex)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Backing off route {} due to exception: {}", route, ex.getMessage());
                }
                this.backoffManager.backOff(route);
            }
            throw ex;
        }
        if (this.connectionBackoffStrategy.shouldBackoff(response)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Backing off route {} due to response status: {}", route, response.getCode());
            }
            this.backoffManager.backOff(route);
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Probing route: {}", route);
            }
            this.backoffManager.probe(route);
        }
        return response;
    }

}
