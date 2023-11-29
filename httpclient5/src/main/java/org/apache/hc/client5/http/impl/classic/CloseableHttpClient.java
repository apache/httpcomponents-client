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

import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.routing.RoutingSupport;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.ModalCloseable;
import org.apache.hc.core5.util.Args;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base implementation of {@link HttpClient} that also implements {@link ModalCloseable}.
 *
 * @since 4.3
 */
@Contract(threading = ThreadingBehavior.SAFE)
public abstract class CloseableHttpClient implements HttpClient, ModalCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(CloseableHttpClient.class);

    protected abstract CloseableHttpResponse doExecute(HttpHost target, ClassicHttpRequest request,
                                                       HttpContext context) throws IOException;

    private static HttpHost determineTarget(final ClassicHttpRequest request) throws ClientProtocolException {
        try {
            return RoutingSupport.determineHost(request);
        } catch (final HttpException ex) {
            throw new ClientProtocolException(ex);
        }
    }

    /**
     * @deprecated It is strongly recommended to use execute methods with {@link HttpClientResponseHandler}
     * such as {@link #execute(HttpHost, ClassicHttpRequest, HttpContext, HttpClientResponseHandler)} in order
     * to ensure automatic resource deallocation by the client.
     * For special cases one can still use {@link #executeOpen(HttpHost, ClassicHttpRequest, HttpContext)}
     * to keep the response object open after the request execution.
     *
     * @see #execute(HttpHost, ClassicHttpRequest, HttpContext, HttpClientResponseHandler)
     * @see #executeOpen(HttpHost, ClassicHttpRequest, HttpContext)
     */
    @Deprecated
    @Override
    public CloseableHttpResponse execute(
            final HttpHost target,
            final ClassicHttpRequest request,
            final HttpContext context) throws IOException {
        return doExecute(target, request, context);
    }

    /**
     * @deprecated It is strongly recommended to use execute methods with {@link HttpClientResponseHandler}
     * such as {@link #execute(ClassicHttpRequest, HttpContext, HttpClientResponseHandler)} in order
     * to ensure automatic resource deallocation by the client.
     * For special cases one can still use {@link #executeOpen(HttpHost, ClassicHttpRequest, HttpContext)}
     * to keep the response object open after the request execution.
     *
     * @see #execute(ClassicHttpRequest, HttpContext, HttpClientResponseHandler)
     * @see #executeOpen(HttpHost, ClassicHttpRequest, HttpContext)
     */
    @Deprecated
    @Override
    public CloseableHttpResponse execute(
            final ClassicHttpRequest request,
            final HttpContext context) throws IOException {
        Args.notNull(request, "HTTP request");
        return doExecute(determineTarget(request), request, context);
    }

    /**
     * @deprecated It is strongly recommended to use execute methods with {@link HttpClientResponseHandler}
     * such as {@link #execute(ClassicHttpRequest, HttpClientResponseHandler)} in order
     * to ensure automatic resource deallocation by the client.
     * For special cases one can still use {@link #executeOpen(HttpHost, ClassicHttpRequest, HttpContext)}
     * to keep the response object open after the request execution.
     *
     * @see #execute(ClassicHttpRequest, HttpClientResponseHandler)
     * @see #executeOpen(HttpHost, ClassicHttpRequest, HttpContext)
     */
    @Deprecated
    @Override
    public CloseableHttpResponse execute(
            final ClassicHttpRequest request) throws IOException {
        return doExecute(determineTarget(request), request, null);
    }

    /**
     * @deprecated It is strongly recommended to use execute methods with {@link HttpClientResponseHandler}
     * such as {@link #execute(HttpHost, ClassicHttpRequest, HttpClientResponseHandler)} in order
     * to ensure automatic resource deallocation by the client.
     * For special cases one can still use {@link #executeOpen(HttpHost, ClassicHttpRequest, HttpContext)}
     * to keep the response object open after the request execution.
     *
     * @see #execute(HttpHost, ClassicHttpRequest, HttpClientResponseHandler)
     * @see #executeOpen(HttpHost, ClassicHttpRequest, HttpContext)
     */
    @Deprecated
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
     * relieving individual {@link HttpClientResponseHandler}s from having to manage
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
            final HttpClientResponseHandler<? extends T> responseHandler) throws IOException {
        return execute(request, null, responseHandler);
    }

    /**
     * Executes a request using the default context and processes the
     * response using the given response handler. The content entity associated
     * with the response is fully consumed and the underlying connection is
     * released back to the connection manager automatically in all cases
     * relieving individual {@link HttpClientResponseHandler}s from having to manage
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
    public <T> T execute(
            final ClassicHttpRequest request,
            final HttpContext context,
            final HttpClientResponseHandler<? extends T> responseHandler) throws IOException {
        final HttpHost target = determineTarget(request);
        return execute(target, request, context, responseHandler);
    }

    /**
     * Executes a request using the default context and processes the
     * response using the given response handler. The content entity associated
     * with the response is fully consumed and the underlying connection is
     * released back to the connection manager automatically in all cases
     * relieving individual {@link HttpClientResponseHandler}s from having to manage
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
            final HttpClientResponseHandler<? extends T> responseHandler) throws IOException {
        return execute(target, request, null, responseHandler);
    }

    /**
     * Executes a request using the default context and processes the
     * response using the given response handler. The content entity associated
     * with the response is fully consumed and the underlying connection is
     * released back to the connection manager automatically in all cases
     * relieving individual {@link HttpClientResponseHandler}s from having to manage
     * resource deallocation internally.
     *
     * @param target    the target host for the request.
     *                  Implementations may accept {@code null}
     *                  if they can still determine a route, for example
     *                  to a default target or by inspecting the request.
     * @param request   the request to execute
     * @param context   the context to use for the execution, or
     *                  {@code null} to use the default context
     * @param responseHandler the response handler
     *
     * @return  the response object as generated by the response handler.
     * @throws IOException in case of a problem or the connection was aborted
     * @throws ClientProtocolException in case of an http protocol error
     */
    @Override
    public <T> T execute(
            final HttpHost target,
            final ClassicHttpRequest request,
            final HttpContext context,
            final HttpClientResponseHandler<? extends T> responseHandler) throws IOException {
        Args.notNull(responseHandler, "Response handler");

        try (final ClassicHttpResponse response = doExecute(target, request, context)) {
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
                    LOG.warn("Error consuming content after an exception.", t2);
                }
                throw new ClientProtocolException(t);
            }
        }
    }

}
