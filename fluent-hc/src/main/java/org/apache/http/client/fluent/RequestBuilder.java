/*
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * ====================================================================
 */

package org.apache.http.client.fluent;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.http.HttpEntity;

public class RequestBuilder {

    private FluentHttpMethod method;
    private URI uri;
    private HttpEntity entity;

    /**
     * Build an instance of
     * <code><a href="FunRequest.html">FunRequest</a></code>
     *
     * @param uri
     *            the URI of the request
     * @return an instance of
     *         <code><a href="FunRequest.html">FunRequest</a></code>
     * @throws IllegalArgumentException
     *             if the uri is invalid.
     */
    @Deprecated
    public static FluentRequest request(final String uri) {
        URI uriObj;
        try {
            uriObj = new URI(uri);
            return request(uriObj);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Build an instance of
     * <code><a href="FunRequest.html">FunRequest</a></code>
     *
     * @param uri
     *            the URI of the request
     * @return an instance of
     *         <code><a href="FunRequest.html">FunRequest</a></code>
     */
    @Deprecated
    public static FluentRequest request(final URI uri) {
        return FluentRequest.build(uri, FluentHttpMethod.GET_METHOD);
    }

    public RequestBuilder() {
        method = FluentHttpMethod.GET_METHOD;
    }

    public FluentRequest build() {
        if (uri != null) {
            FluentRequest req = FluentRequest.build(uri, method);
            if (entity != null)
                req.setEntity(entity);
            return req;
        } else
            throw new IllegalStateException(
                    "too less information provided to build FluentRequest");
    }

    public static FluentRequest build(final String uri) {
        return build(uri, FluentHttpMethod.GET_METHOD);
    }

    public static FluentRequest build(final String uri,
            final FluentHttpMethod method) {
        try {
            URI uriObj;
            uriObj = new URI(uri);
            return build(uriObj, method);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static FluentRequest build(final URI uri) {
        return build(uri, FluentHttpMethod.GET_METHOD);
    }

    public static FluentRequest build(final URI uri,
            final FluentHttpMethod method) {
        return FluentRequest.build(uri, method);
    }

    public RequestBuilder by(final FluentHttpMethod method) {
        this.method = method;
        return this;
    }

    public RequestBuilder req(final String uri) throws URISyntaxException {
        URI uriObj = new URI(uri);
        req(uriObj);
        return this;
    }

    public RequestBuilder req(final URI uri) {
        this.uri = uri;
        return this;
    }

    public RequestBuilder with(final HttpEntity entity) {
        this.entity = entity;
        return this;
    }

    public RequestBuilder removeEntity() {
        this.entity = null;
        return this;
    }

    public RequestBuilder set(final Object obj) throws IllegalArgumentException {
        try {
            if (obj instanceof String)
                return this.req((String) obj);
            if (obj instanceof URI)
                return this.req((URI) obj);
            if (obj instanceof FluentHttpMethod)
                return this.by((FluentHttpMethod) obj);
            if (obj instanceof HttpEntity)
                return this.with((HttpEntity) obj);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(obj.toString()
                    + " is an illegal URI value");
        }
        throw new IllegalArgumentException(obj.toString()
                + " is an illegal parameter");
    }
}
