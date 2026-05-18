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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.http.message.ParserCursor;
import org.apache.hc.core5.net.URLEncodedUtils;
import org.apache.hc.core5.util.Args;

final class ResourceIfaceScanner {

    static List<ResourceMethod> scan(final Class<?> iface) {
        final Path classPath = iface.getAnnotation(Path.class);
        final List<PathSegment> classPathSegments = parsePathSegments(classPath != null ? classPath.value() : null);
        final Produces classProduces = iface.getAnnotation(Produces.class);
        final List<ContentType> classProducesTypes = parseMediaTypes(classProduces != null ? classProduces.value() : null);
        final Consumes classConsumes = iface.getAnnotation(Consumes.class);
        final List<ContentType> classConsumesTypes = parseMediaTypes(classConsumes != null ? classConsumes.value() : null);
        final Method[] methods = iface.getMethods();
        final List<ResourceMethod> definitions = new ArrayList<>(methods.length);
        for (final Method m : methods) {
            final Path methodPath = m.getAnnotation(Path.class);
            final HttpMethod httpMethod = resolveHttpMethod(m);
            if (httpMethod == null && methodPath == null) {
                continue;
            }
            final List<PathSegment> methodPathSegments = parsePathSegments(methodPath != null ? methodPath.value() : null);
            final Produces methodProduces = m.getAnnotation(Produces.class);
            final List<ContentType> methodProducesTypes = parseMediaTypes(methodProduces != null ? methodProduces.value() : null);
            final Consumes methodConsumes = m.getAnnotation(Consumes.class);
            final List<ContentType> methodConsumesTypes = parseMediaTypes(methodConsumes != null ? methodConsumes.value() : null);

            final List<PathSegment> fullPathSegments = join(classPathSegments, methodPathSegments);

            final Annotation[][] annotations = m.getParameterAnnotations();
            final ResourceParam[] params = new ResourceParam[annotations.length];
            for (int i = 0; i < annotations.length; i++) {
                params[i] = resolveParam(annotations[i]);
            }
            validateParams(m, params, fullPathSegments);
            final ResourceMethod definition = new ResourceMethod(
                    m,
                    httpMethod != null ? httpMethod.value() : "GET",
                    fullPathSegments,
                    methodProducesTypes != null ? methodProducesTypes : classProducesTypes,
                    methodConsumesTypes != null ? methodConsumesTypes : classConsumesTypes,
                    params
            );
            definitions.add(definition);
        }
        return definitions;
    }

    private static HttpMethod resolveHttpMethod(final Method m) {
        for (final Annotation a : m.getAnnotations()) {
            final HttpMethod hm = a.annotationType().getAnnotation(HttpMethod.class);
            if (hm != null) {
                return hm;
            }
        }
        return null;
    }

    // URLEncodedUtils to be replaced by a utility class from core
    @SuppressWarnings("deprecated")
    static List<PathSegment> parsePathSegments(final String value) {
        if (value == null) {
            return Collections.emptyList();
        }
        return URLEncodedUtils.parsePathSegments(value).stream()
                .map(e -> {
                    if (e.startsWith("{") && e.endsWith("}")) {
                        final String param = e.substring(1, e.length() - 1);
                        if (param.contains(":")) {
                            throw new RestResourceException("Path parameters with regex not supported");
                        }
                        return new PathSegment(param, PathSegment.Type.PARAMETER);
                    } else {
                        return new PathSegment(e, PathSegment.Type.VALUE);
                    }
                }).collect(Collectors.toList());
    }

    static List<ContentType> parseMediaTypes(final String... mediaTypes) {
        if (mediaTypes == null || mediaTypes.length == 0) {
            return null;
        }
        final List<ContentType> contentTypes = new ArrayList<>(mediaTypes.length);
        for (final String s : mediaTypes) {
            final ParserCursor cursor = new ParserCursor(0, s.length());
            MessageSupport.parseElements(s, cursor, elem -> {
                final String mimeType = elem.getName();
                if (!Args.isEmpty(mimeType)) {
                    final ContentType contentType = ContentType.create(mimeType, elem.getParameters());
                    if (!contentType.isSameMimeType(ContentType.APPLICATION_JSON) &&
                            !contentType.isSameMimeType(ContentType.TEXT_PLAIN) &&
                            !contentType.isSameMimeType(ContentType.APPLICATION_OCTET_STREAM)) {
                        throw new RestResourceException("Unsupported media type: " + contentType);
                    }
                    contentTypes.add(contentType);
                }
            });
        }
        return contentTypes;
    }

    static List<PathSegment> join(
            final List<PathSegment> list1,
            final List<PathSegment> list2) {
        final LinkedList<PathSegment> joint = new LinkedList<>();
        if (list1 != null && !list1.isEmpty()) {
            joint.addAll(list1);
        }
        if (list2 != null && !list2.isEmpty()) {
            final PathSegment lastSegment = joint.peekLast();
            if (lastSegment != null && lastSegment.getSegment().isEmpty()) {
                joint.removeLast();
            }
            joint.addAll(list2);
        }
        return joint;
    }

    private static ResourceParam resolveParam(final Annotation[] annotations) {
        String defaultValue = null;
        for (final Annotation a : annotations) {
            if (a instanceof DefaultValue) {
                defaultValue = ((DefaultValue) a).value();
            }
        }
        for (final Annotation a : annotations) {
            if (a instanceof PathParam) {
                return new ResourceParam(((PathParam) a).value(), ResourceParam.Type.PATH, defaultValue);
            }
            if (a instanceof QueryParam) {
                return new ResourceParam(((QueryParam) a).value(), ResourceParam.Type.QUERY, defaultValue);
            }
            if (a instanceof HeaderParam) {
                return new ResourceParam(((HeaderParam) a).value(), ResourceParam.Type.HEADER, defaultValue);
            }
        }
        return new ResourceParam("body", ResourceParam.Type.BODY, null);
    }

    private static void validateParams(
            final Method m,
            final ResourceParam[] params,
            final List<PathSegment> pathSegments) {
        int bodyCount = 0;
        final Set<String> pathParams = new HashSet<>(params.length);
        for (final ResourceParam param : params) {
            if (param.getType() == ResourceParam.Type.BODY) {
                bodyCount++;
            }
            if (param.getType() == ResourceParam.Type.PATH) {
                pathParams.add(param.getName());
            }
        }
        if (bodyCount > 1) {
            throw new RestResourceException("Method '" + m.getName()
                    + "': there are " + bodyCount + " unannotated (body) parameters;"
                    + " at most one is allowed");
        }
        final Set<String> templateVars = new HashSet<>(params.length);
        for (final PathSegment pathSegment : pathSegments) {
            if (pathSegment.getType() == PathSegment.Type.PARAMETER) {
                templateVars.add(pathSegment.getSegment());
            }
        }
        for (final String pathParam : pathParams) {
            if (!templateVars.contains(pathParam)) {
                throw new RestResourceException("Method '" + m.getName()
                        + "': path parameter '" + pathParam + "' has no matching annotated method argument");
            }
        }
        for (final String templateVar : templateVars) {
            if (!pathParams.contains(templateVar)) {
                throw new RestResourceException("Method '" + m.getName()
                        + "': there is no path parameter '" + templateVar + "' matching annotated method argument");
            }
        }
    }

}
