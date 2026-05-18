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

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import org.apache.hc.core5.http.ContentType;

final class ResourceMethod {

    private final Method method;
    private final String httpMethod;
    private final List<PathSegment> pathSegments;
    private final List<ContentType> producesContentTypes;
    private final List<ContentType> consumesContentTypes;
    private final ResourceParam[] params;

    ResourceMethod(final Method method,
                   final String httpMethod,
                   final List<PathSegment> pathSegments,
                   final List<ContentType> producesContentTypes,
                   final List<ContentType> consumesContentTypes,
                   final ResourceParam[] params) {
        this.method = method;
        this.httpMethod = httpMethod;
        this.pathSegments = pathSegments;
        this.producesContentTypes = producesContentTypes;
        this.consumesContentTypes = consumesContentTypes;
        this.params = params;
    }

    public Method getMethod() {
        return method;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public List<PathSegment> getPathSegments() {
        return pathSegments;
    }

    public List<ContentType> getProducesContentTypes() {
        return producesContentTypes;
    }

    public List<ContentType> getConsumesContentTypes() {
        return consumesContentTypes;
    }

    public ResourceParam[] getParams() {
        return params;
    }

    @Override
    public String toString() {
        return "ResourceMethod{" +
                "method=" + method +
                ", pathSegments=" + pathSegments +
                ", producesContentTypes=" + producesContentTypes +
                ", consumesContentTypes=" + consumesContentTypes +
                ", params=" + Arrays.toString(params) +
                '}';
    }

}
