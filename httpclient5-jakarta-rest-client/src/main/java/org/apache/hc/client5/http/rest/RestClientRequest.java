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
package org.apache.hc.client5.http.rest;

import java.util.Date;
import java.util.List;

import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Variant;

import org.apache.hc.core5.util.Args;

/**
 * Client-side {@link Request} implementation that exposes the dispatched
 * HTTP method.
 * <p>
 * Server-side JAX-RS operations such as variant selection and request
 * precondition evaluation are intentionally unsupported.
 *
 * @since 5.7
 */
final class RestClientRequest implements Request {

    private final String method;

    RestClientRequest(final String method) {
        this.method = Args.notBlank(method, "HTTP method");
    }

    @Override
    public String getMethod() {
        return method;
    }

    @Override
    public Variant selectVariant(final List<Variant> variants) {
        throw unsupported("selectVariant");
    }

    @Override
    public Response.ResponseBuilder evaluatePreconditions(final EntityTag eTag) {
        throw unsupported("evaluatePreconditions");
    }

    @Override
    public Response.ResponseBuilder evaluatePreconditions(final Date lastModified) {
        throw unsupported("evaluatePreconditions");
    }

    @Override
    public Response.ResponseBuilder evaluatePreconditions(final Date lastModified, final EntityTag eTag) {
        throw unsupported("evaluatePreconditions");
    }

    @Override
    public Response.ResponseBuilder evaluatePreconditions() {
        throw unsupported("evaluatePreconditions");
    }

    private static UnsupportedOperationException unsupported(final String operation) {
        return new UnsupportedOperationException(
                operation + " is a server-side JAX-RS operation and is not supported by the client proxy");
    }

}