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

package org.apache.hc.client5.http.classic;

import java.io.IOException;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * This interface represents only the most basic contract for HTTP request
 * execution. It imposes no restrictions or particular details on the request
 * execution process and leaves the specifics of state management,
 * authentication and redirect handling up to individual implementations.
 *
 * @since 4.0
 */
public interface HttpClient {

    /**
     * Executes HTTP request using the default context.
     *
     * @param request   the request to execute
     *
     * @return  the response to the request. This is always a final response,
     *          never an intermediate response with an 1xx status code.
     *          Whether redirects or authentication challenges will be returned
     *          or handled automatically depends on the implementation and
     *          configuration of this client.
     * @throws IOException in case of a problem or the connection was aborted
     */
    HttpResponse execute(ClassicHttpRequest request) throws IOException;

    /**
     * Executes HTTP request using the given context.
     *
     * @param request   the request to execute
     * @param context   the context to use for the execution, or
     *                  {@code null} to use the default context
     *
     * @return  the response to the request. This is always a final response,
     *          never an intermediate response with an 1xx status code.
     *          Whether redirects or authentication challenges will be returned
     *          or handled automatically depends on the implementation and
     *          configuration of this client.
     * @throws IOException in case of a problem or the connection was aborted
     */
    HttpResponse execute(ClassicHttpRequest request, HttpContext context) throws IOException;

    /**
     * Executes HTTP request using the default context.
     *
     * @param target    the target host for the request.
     *                  Implementations may accept {@code null}
     *                  if they can still determine a route, for example
     *                  to a default target or by inspecting the request.
     * @param request   the request to execute
     *
     * @return  the response to the request. This is always a final response,
     *          never an intermediate response with an 1xx status code.
     *          Whether redirects or authentication challenges will be returned
     *          or handled automatically depends on the implementation and
     *          configuration of this client.
     * @throws IOException in case of a problem or the connection was aborted
     */
     ClassicHttpResponse execute(HttpHost target, ClassicHttpRequest request) throws IOException;

    /**
     * Executes HTTP request using the given context.
     *
     * @param target    the target host for the request.
     *                  Implementations may accept {@code null}
     *                  if they can still determine a route, for example
     *                  to a default target or by inspecting the request.
     * @param request   the request to execute
     * @param context   the context to use for the execution, or
     *                  {@code null} to use the default context
     *
     * @return  the response to the request. This is always a final response,
     *          never an intermediate response with an 1xx status code.
     *          Whether redirects or authentication challenges will be returned
     *          or handled automatically depends on the implementation and
     *          configuration of this client.
     * @throws IOException in case of a problem or the connection was aborted
     */
    HttpResponse execute(HttpHost target, ClassicHttpRequest request, HttpContext context) throws IOException;

    /**
     * Executes HTTP request using the default context and processes the
     * response using the given response handler.
     * <p>
     * Implementing classes are required to ensure that the content entity
     * associated with the response is fully consumed and the underlying
     * connection is released back to the connection manager automatically
     * in all cases relieving individual {@link HttpClientResponseHandler}s from
     * having to manage resource deallocation internally.
     * </p>
     *
     * @param request   the request to execute
     * @param responseHandler the response handler
     *
     * @return  the response object as generated by the response handler.
     * @throws IOException in case of a problem or the connection was aborted
     */
    <T> T execute(ClassicHttpRequest request, HttpClientResponseHandler<? extends T> responseHandler) throws IOException;

    /**
     * Executes HTTP request using the given context and processes the
     * response using the given response handler.
     * <p>
     * Implementing classes are required to ensure that the content entity
     * associated with the response is fully consumed and the underlying
     * connection is released back to the connection manager automatically
     * in all cases relieving individual {@link HttpClientResponseHandler}s from
     * having to manage resource deallocation internally.
     * </p>
     *
     * @param request   the request to execute
     * @param context   the context to use for the execution, or
     *                  {@code null} to use the default context
     * @param responseHandler the response handler
     *
     * @return  the response object as generated by the response handler.
     * @throws IOException in case of a problem or the connection was aborted
     */
    <T> T execute(
            ClassicHttpRequest request,
            HttpContext context,
            HttpClientResponseHandler<? extends T> responseHandler) throws IOException;

    /**
     * Executes HTTP request to the target using the default context and
     * processes the response using the given response handler.
     * <p>
     * Implementing classes are required to ensure that the content entity
     * associated with the response is fully consumed and the underlying
     * connection is released back to the connection manager automatically
     * in all cases relieving individual {@link HttpClientResponseHandler}s from
     * having to manage resource deallocation internally.
     * </p>
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
     */
    <T> T execute(
            HttpHost target,
            ClassicHttpRequest request,
            HttpClientResponseHandler<? extends T> responseHandler) throws IOException;

    /**
     * Executes HTTP request to the target using the given context and
     * processes the response using the given response handler.
     * <p>
     * Implementing classes are required to ensure that the content entity
     * associated with the response is fully consumed and the underlying
     * connection is released back to the connection manager automatically
     * in all cases relieving individual {@link HttpClientResponseHandler}s from
     * having to manage resource deallocation internally.
     * </p>
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
     */
    <T> T execute(
            HttpHost target,
            ClassicHttpRequest request,
            HttpContext context,
            HttpClientResponseHandler<? extends T> responseHandler) throws IOException;

}
