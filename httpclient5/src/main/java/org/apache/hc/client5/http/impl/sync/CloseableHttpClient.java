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

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.hc.client5.http.protocol.ClientProtocolException;
import org.apache.hc.client5.http.sync.HttpClient;
import org.apache.hc.client5.http.utils.URIUtils;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.ResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Base implementation of {@link HttpClient} that also implements {@link Closeable}.
 *
 * @since 4.3
 */
@Contract(threading = ThreadingBehavior.SAFE)
public abstract class CloseableHttpClient implements HttpClient, Closeable {

    private final Logger log = LogManager.getLogger(getClass());

    protected abstract CloseableHttpResponse doExecute(HttpHost target, ClassicHttpRequest request,
                                                     HttpContext context) throws IOException;

    /**
     * {@inheritDoc}
     */
    @Override
    public CloseableHttpResponse execute(
            final HttpHost target,
            final ClassicHttpRequest request,
            final HttpContext context) throws IOException {
        return doExecute(target, request, context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CloseableHttpResponse execute(
            final ClassicHttpRequest request,
            final HttpContext context) throws IOException {
        Args.notNull(request, "HTTP request");
        return doExecute(determineTarget(request), request, context);
    }

    private static HttpHost determineTarget(final ClassicHttpRequest request) throws ClientProtocolException {
        // A null target may be acceptable if there is a default target.
        // Otherwise, the null target is detected in the director.
        HttpHost target = null;
        URI requestURI = null;
        try {
            requestURI = request.getUri();
        } catch (URISyntaxException ignore) {
        }
        if (requestURI != null && requestURI.isAbsolute()) {
            target = URIUtils.extractHost(requestURI);
            if (target == null) {
                throw new ClientProtocolException("URI does not specify a valid host name: "
                        + requestURI);
            }
        }
        return target;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CloseableHttpResponse execute(
            final ClassicHttpRequest request) throws IOException {
        return execute(request, (HttpContext) null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CloseableHttpResponse execute(
            final HttpHost target,
            final ClassicHttpRequest request) throws IOException {
        return doExecute(target, request, null);
    }

    /**
     * Executes a request using the default context and processes the
     * response using the given response handler. The content entity associated
     * with the response is fully consumed and the underlying connection is
     * released back to the connection manager automatically in all cases
     * relieving individual {@link ResponseHandler}s from having to manage
     * resource deallocation internally.
     *
     * @param request   the request to execute
     * @param responseHandler the response handler
     *
     * @return  the response object as generated by the response handler.
     * @throws IOException in case of a problem or the connection was aborted
     * @throws ClientProtocolException in case of an http protocol error
     */
    @Override
    public <T> T execute(final ClassicHttpRequest request,
            final ResponseHandler<? extends T> responseHandler) throws IOException {
        return execute(request, responseHandler, null);
    }

    /**
     * Executes a request using the default context and processes the
     * response using the given response handler. The content entity associated
     * with the response is fully consumed and the underlying connection is
     * released back to the connection manager automatically in all cases
     * relieving individual {@link ResponseHandler}s from having to manage
     * resource deallocation internally.
     *
     * @param request   the request to execute
     * @param responseHandler the response handler
     * @param context   the context to use for the execution, or
     *                  {@code null} to use the default context
     *
     * @return  the response object as generated by the response handler.
     * @throws IOException in case of a problem or the connection was aborted
     * @throws ClientProtocolException in case of an http protocol error
     */
    @Override
    public <T> T execute(final ClassicHttpRequest request,
            final ResponseHandler<? extends T> responseHandler, final HttpContext context)
            throws IOException {
        final HttpHost target = determineTarget(request);
        return execute(target, request, responseHandler, context);
    }

    /**
     * Executes a request using the default context and processes the
     * response using the given response handler. The content entity associated
     * with the response is fully consumed and the underlying connection is
     * released back to the connection manager automatically in all cases
     * relieving individual {@link ResponseHandler}s from having to manage
     * resource deallocation internally.
     *
     * @param target    the target host for the request.
     *                  Implementations may accept {@code null}
     *                  if they can still determine a route, for example
     *                  to a default target or by inspecting the request.
     * @param request   the request to execute
     * @param responseHandler the response handler
     *
     * @return  the response object as generated by the response handler.
     * @throws IOException in case of a problem or the connection was aborted
     * @throws ClientProtocolException in case of an http protocol error
     */
    @Override
    public <T> T execute(final HttpHost target, final ClassicHttpRequest request,
            final ResponseHandler<? extends T> responseHandler) throws IOException {
        return execute(target, request, responseHandler, null);
    }

    /**
     * Executes a request using the default context and processes the
     * response using the given response handler. The content entity associated
     * with the response is fully consumed and the underlying connection is
     * released back to the connection manager automatically in all cases
     * relieving individual {@link ResponseHandler}s from having to manage
     * resource deallocation internally.
     *
     * @param target    the target host for the request.
     *                  Implementations may accept {@code null}
     *                  if they can still determine a route, for example
     *                  to a default target or by inspecting the request.
     * @param request   the request to execute
     * @param responseHandler the response handler
     * @param context   the context to use for the execution, or
     *                  {@code null} to use the default context
     *
     * @return  the response object as generated by the response handler.
     * @throws IOException in case of a problem or the connection was aborted
     * @throws ClientProtocolException in case of an http protocol error
     */
    @Override
    public <T> T execute(final HttpHost target, final ClassicHttpRequest request,
            final ResponseHandler<? extends T> responseHandler, final HttpContext context) throws IOException {
        Args.notNull(responseHandler, "Response handler");

        try (final CloseableHttpResponse response = execute(target, request, context)) {
            try {
                final T result = responseHandler.handleResponse(response);
                final HttpEntity entity = response.getEntity();
                EntityUtils.consume(entity);
                return result;
            } catch (final HttpException t) {
                // Try to salvage the underlying connection in case of a protocol exception
                final HttpEntity entity = response.getEntity();
                try {
                    EntityUtils.consume(entity);
                } catch (final Exception t2) {
                    // Log this exception. The original exception is more
                    // important and will be thrown to the caller.
                    this.log.warn("Error consuming content after an exception.", t2);
                }
                throw new ClientProtocolException(t);
            }
        }
    }

}
