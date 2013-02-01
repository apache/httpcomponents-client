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

package org.apache.http.impl.client;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.annotation.ThreadSafe;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.ServiceUnavailableRetryStrategy;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

/**
 * {@link HttpClient} implementation that can automatically retry the request in case of
 * a non-2xx response using the {@link ServiceUnavailableRetryStrategy} interface.
 *
 * @since 4.2
 */
@ThreadSafe
public class AutoRetryHttpClient implements HttpClient {

    private final HttpClient backend;

    private final ServiceUnavailableRetryStrategy retryStrategy;

    private final Log log = LogFactory.getLog(getClass());

    public AutoRetryHttpClient(
            final HttpClient client, final ServiceUnavailableRetryStrategy retryStrategy) {
        super();
        if (client == null) {
            throw new IllegalArgumentException("HttpClient may not be null");
        }
        if (retryStrategy == null) {
            throw new IllegalArgumentException(
                    "ServiceUnavailableRetryStrategy may not be null");
        }
        this.backend = client;
        this.retryStrategy = retryStrategy;
    }

    /**
     * Constructs a {@code AutoRetryHttpClient} with default caching settings that
     * stores cache entries in memory and uses a vanilla
     * {@link DefaultHttpClient} for backend requests.
     */
    public AutoRetryHttpClient() {
        this(new DefaultHttpClient(), new DefaultServiceUnavailableRetryStrategy());
    }

    /**
     * Constructs a {@code AutoRetryHttpClient} with the given caching options that
     * stores cache entries in memory and uses a vanilla
     * {@link DefaultHttpClient} for backend requests.
     *
     * @param config
     *            retry configuration module options
     */
    public AutoRetryHttpClient(ServiceUnavailableRetryStrategy config) {
        this(new DefaultHttpClient(), config);
    }

    /**
     * Constructs a {@code AutoRetryHttpClient} with default caching settings that
     * stores cache entries in memory and uses the given {@link HttpClient} for
     * backend requests.
     *
     * @param client
     *            used to make origin requests
     */
    public AutoRetryHttpClient(HttpClient client) {
        this(client, new DefaultServiceUnavailableRetryStrategy());
    }

    public HttpResponse execute(HttpHost target, HttpRequest request)
            throws IOException {
        HttpContext defaultContext = null;
        return execute(target, request, defaultContext);
    }

    public <T> T execute(HttpHost target, HttpRequest request,
            ResponseHandler<? extends T> responseHandler) throws IOException {
        return execute(target, request, responseHandler, null);
    }

    public <T> T execute(HttpHost target, HttpRequest request,
            ResponseHandler<? extends T> responseHandler, HttpContext context)
            throws IOException {
        HttpResponse resp = execute(target, request, context);
        return responseHandler.handleResponse(resp);
    }

    public HttpResponse execute(HttpUriRequest request) throws IOException {
        HttpContext context = null;
        return execute(request, context);
    }

    public HttpResponse execute(HttpUriRequest request, HttpContext context)
            throws IOException {
        URI uri = request.getURI();
        HttpHost httpHost = new HttpHost(uri.getHost(), uri.getPort(),
                uri.getScheme());
        return execute(httpHost, request, context);
    }

    public <T> T execute(HttpUriRequest request,
            ResponseHandler<? extends T> responseHandler) throws IOException {
        return execute(request, responseHandler, null);
    }

    public <T> T execute(HttpUriRequest request,
            ResponseHandler<? extends T> responseHandler, HttpContext context)
            throws IOException {
        HttpResponse resp = execute(request, context);
        return responseHandler.handleResponse(resp);
    }

    public HttpResponse execute(HttpHost target, HttpRequest request,
            HttpContext context) throws IOException {
        for (int c = 1;; c++) {
            HttpResponse response = backend.execute(target, request, context);
            try {
                if (retryStrategy.retryRequest(response, c, context)) {
                    EntityUtils.consume(response.getEntity());
                    long nextInterval = retryStrategy.getRetryInterval();
                    try {
                        log.trace("Wait for " + nextInterval);
                        Thread.sleep(nextInterval);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new InterruptedIOException();
                    }
                } else {
                    return response;
                }
            } catch (RuntimeException ex) {
                try {
                    EntityUtils.consume(response.getEntity());
                } catch (IOException ioex) {
                    log.warn("I/O error consuming response content", ioex);
                }
                throw ex;
            }
        }
    }

    public ClientConnectionManager getConnectionManager() {
        return backend.getConnectionManager();
    }

    public HttpParams getParams() {
        return backend.getParams();
    }

}
