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

package org.apache.http.impl.client.builder;

import java.io.IOException;
import java.io.InterruptedIOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpException;
import org.apache.http.annotation.ThreadSafe;
import org.apache.http.client.ServiceUnavailableRetryStrategy;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpExecutionAware;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.routing.HttpRoute;

/**
 * {@link ClientExecChain} implementation that can automatically retry the request in case of
 * a non-2xx response using the {@link ServiceUnavailableRetryStrategy} interface.
 *
 * @since 4.3
 */
@ThreadSafe
class ServiceUnavailableRetryExec implements ClientExecChain {

    private final Log log = LogFactory.getLog(getClass());

    private final ClientExecChain requestExecutor;
    private final ServiceUnavailableRetryStrategy retryStrategy;

    public ServiceUnavailableRetryExec(
            final ClientExecChain requestExecutor, 
            final ServiceUnavailableRetryStrategy retryStrategy) {
        super();
        if (requestExecutor == null) {
            throw new IllegalArgumentException("HTTP request executor may not be null");
        }
        if (retryStrategy == null) {
            throw new IllegalArgumentException(
                    "ServiceUnavailableRetryStrategy may not be null");
        }
        this.requestExecutor = requestExecutor;
        this.retryStrategy = retryStrategy;
    }

    public CloseableHttpResponse execute(
            final HttpRoute route, 
            final HttpRequestWrapper request,
            final HttpClientContext context,
            final HttpExecutionAware execAware) throws IOException, HttpException {
        for (int c = 1;; c++) {
            CloseableHttpResponse response = this.requestExecutor.execute(
                    route, request, context, execAware);
            try {
                if (this.retryStrategy.retryRequest(response, c, context)) {
                    response.close();
                    long nextInterval = this.retryStrategy.getRetryInterval();
                    try {
                        this.log.trace("Wait for " + nextInterval);
                        Thread.sleep(nextInterval);
                    } catch (InterruptedException e) {
                        throw new InterruptedIOException(e.getMessage());
                    }
                } else {
                    return response;
                }
            } catch (RuntimeException ex) {
                response.close();
                throw ex;
            } catch (IOException ex) {
                response.close();
                throw ex;
            }
        }
    }

}
