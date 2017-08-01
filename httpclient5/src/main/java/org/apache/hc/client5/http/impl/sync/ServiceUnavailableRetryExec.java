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

package org.apache.hc.client5.http.impl.sync;

import java.io.IOException;
import java.io.InterruptedIOException;

import org.apache.hc.client5.http.impl.ExecSupport;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.sync.ExecChain;
import org.apache.hc.client5.http.sync.ExecChainHandler;
import org.apache.hc.client5.http.sync.ServiceUnavailableRetryStrategy;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.util.Args;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Request executor in the request execution chain that is responsible
 * for making a decision whether a request that received a non-2xx response
 * from the target server should be re-executed.
 * <p>
 * Further responsibilities such as communication with the opposite
 * endpoint is delegated to the next executor in the request execution
 * chain.
 * </p>
 *
 * @since 4.3
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE_CONDITIONAL)
final class ServiceUnavailableRetryExec implements ExecChainHandler {

    private final Logger log = LogManager.getLogger(getClass());

    private final ServiceUnavailableRetryStrategy retryStrategy;

    public ServiceUnavailableRetryExec(
            final ServiceUnavailableRetryStrategy retryStrategy) {
        super();
        Args.notNull(retryStrategy, "Retry strategy");
        this.retryStrategy = retryStrategy;
    }

    @Override
    public ClassicHttpResponse execute(
            final ClassicHttpRequest request,
            final ExecChain.Scope scope,
            final ExecChain chain) throws IOException, HttpException {
        Args.notNull(request, "HTTP request");
        Args.notNull(scope, "Scope");
        final HttpClientContext context = scope.clientContext;
        ClassicHttpRequest currentRequest = request;
        for (int c = 1;; c++) {
            final ClassicHttpResponse response = chain.proceed(currentRequest, scope);
            try {
                final HttpEntity entity = request.getEntity();
                if (entity != null && !entity.isRepeatable()) {
                    return response;
                }
                if (this.retryStrategy.retryRequest(response, c, context)) {
                    response.close();
                    final long nextInterval = this.retryStrategy.getRetryInterval(response, context);
                    if (nextInterval > 0) {
                        try {
                            if (this.log.isDebugEnabled()) {
                                this.log.debug("Wait for " + ((double) nextInterval / 1000) + " seconds" );
                            }
                            Thread.sleep(nextInterval);
                        } catch (final InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new InterruptedIOException();
                        }
                    }
                    currentRequest = ExecSupport.copy(scope.originalRequest);
                } else {
                    return response;
                }
            } catch (final RuntimeException ex) {
                response.close();
                throw ex;
            }
        }
    }

}
