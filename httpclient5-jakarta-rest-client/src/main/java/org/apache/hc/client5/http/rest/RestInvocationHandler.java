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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityProducer;
import org.apache.hc.core5.http.nio.entity.DiscardingEntityConsumer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.jackson2.http.JsonObjectEntityProducer;
import org.apache.hc.core5.jackson2.http.JsonResponseConsumers;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.util.Args;

/**
 * {@link InvocationHandler} that translates interface method calls into HTTP requests
 * executed through the async {@link CloseableHttpAsyncClient} transport. Each method is
 * mapped to its HTTP verb, URI template and parameter bindings at proxy creation time.
 */
final class RestInvocationHandler implements InvocationHandler {

    private static final int ERROR_STATUS_THRESHOLD = 300;

    private final CloseableHttpAsyncClient httpClient;
    private final URI baseUri;
    private final ObjectMapper objectMapper;
    private final Map<Method, MethodInvoker> invokerMap;

    RestInvocationHandler(final CloseableHttpAsyncClient client, final URI base,
                          final Map<Method, ClientResourceMethod> methods,
                          final ObjectMapper mapper) {
        this.httpClient = client;
        this.baseUri = base;
        this.objectMapper = mapper;
        this.invokerMap = buildInvokers(methods);
    }

    private static Map<Method, MethodInvoker> buildInvokers(
            final Map<Method, ClientResourceMethod> methods) {
        final Map<Method, MethodInvoker> result = new HashMap<>(methods.size());
        for (final Map.Entry<Method, ClientResourceMethod> entry : methods.entrySet()) {
            final ClientResourceMethod rm = entry.getValue();
            final String acceptHeader = rm.getProduces().length > 0 ? joinMediaTypes(rm.getProduces()) : null;
            final ContentType consumeType = rm.getConsumes().length > 0 ? ContentType.parse(rm.getConsumes()[0]) : null;
            result.put(entry.getKey(), new MethodInvoker(rm, acceptHeader, consumeType));
        }
        return result;
    }

    private static String joinMediaTypes(final String[] types) {
        if (types.length == 1) {
            return types[0];
        }
        final StringBuilder sb = new StringBuilder();
        for (final String type : types) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(type);
        }
        return sb.toString();
    }

    @Override
    public Object invoke(final Object proxy, final Method method,
                         final Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return handleObjectMethod(proxy, method, args);
        }
        final MethodInvoker invoker = invokerMap.get(method);
        if (invoker == null) {
            throw new UnsupportedOperationException(
                    "No Jakarta REST mapping for " + method.getName());
        }
        return executeRequest(invoker, args);
    }

    private Object executeRequest(final MethodInvoker invoker,
                                  final Object[] args) {
        final ClientResourceMethod rm = invoker.resourceMethod;
        final ClientResourceMethod.ParamInfo[] params = rm.getParameters();
        final Map<String, String> pathParams = rm.getPathParamCount() > 0
                ? new LinkedHashMap<>(rm.getPathParamCount()) : Collections.emptyMap();
        final Map<String, List<String>> queryParams = rm.getQueryParamCount() > 0
                ? new LinkedHashMap<>(rm.getQueryParamCount()) : Collections.emptyMap();
        final Map<String, String> headerParams = rm.getHeaderParamCount() > 0
                ? new LinkedHashMap<>(rm.getHeaderParamCount()) : Collections.emptyMap();
        Object bodyParam = null;

        if (args != null) {
            for (int i = 0; i < params.length; i++) {
                final ClientResourceMethod.ParamInfo pi = params[i];
                final Object val = args[i];
                final String strVal = val != null ? paramToString(val) : pi.getDefaultValue();
                switch (pi.getSource()) {
                    case PATH:
                        if (strVal == null) {
                            throw new IllegalArgumentException(
                                    "Path parameter \"" + pi.getName()
                                            + "\" must not be null");
                        }
                        pathParams.put(pi.getName(), strVal);
                        break;
                    case QUERY:
                        if (strVal != null) {
                            queryParams.computeIfAbsent(pi.getName(),
                                    k -> new ArrayList<>()).add(strVal);
                        }
                        break;
                    case HEADER:
                        if (strVal != null) {
                            headerParams.put(pi.getName(), strVal);
                        }
                        break;
                    case BODY:
                        bodyParam = val;
                        break;
                    default:
                        break;
                }
            }
        }

        final URI requestUri = buildRequestUri(rm.getPathTemplate(), pathParams, queryParams);
        final BasicHttpRequest request =
                new BasicHttpRequest(rm.getHttpMethod(), requestUri);

        if (invoker.acceptHeader != null) {
            request.addHeader(HttpHeaders.ACCEPT, invoker.acceptHeader);
        }
        for (final Map.Entry<String, String> entry : headerParams.entrySet()) {
            request.addHeader(entry.getKey(), entry.getValue());
        }

        final AsyncEntityProducer entityProducer;
        if (bodyParam != null) {
            entityProducer = createEntityProducer(bodyParam, invoker.consumeType);
        } else {
            entityProducer = null;
        }

        final Class<?> rawType = rm.getMethod().getReturnType();
        final BasicRequestProducer requestProducer =
                new BasicRequestProducer(request, entityProducer);
        try {
            if (rawType == void.class || rawType == Void.class) {
                final Message<HttpResponse, Void> result = awaitResult(
                        httpClient.execute(requestProducer,
                                new BasicResponseConsumer<>(
                                        new DiscardingEntityConsumer<>()),
                                null));
                checkStatus(result.getHead(), null);
                return null;
            }
            if (rawType == byte[].class) {
                final Message<HttpResponse, byte[]> result = awaitResult(
                        httpClient.execute(requestProducer,
                                new BasicResponseConsumer<>(
                                        new BasicAsyncEntityConsumer()),
                                null));
                final byte[] body = result.getBody();
                checkStatus(result.getHead(), body);
                return body;
            }
            if (rawType == String.class) {
                final Message<HttpResponse, String> result = awaitResult(
                        httpClient.execute(requestProducer,
                                new StringResponseConsumer(), null));
                throwIfError(result);
                return result.getBody();
            }
            @SuppressWarnings("unchecked") final Class<Object> objectType = (Class<Object>) rawType;
            final Message<HttpResponse, Object> result = awaitResult(
                    httpClient.execute(requestProducer,
                            JsonResponseConsumers.create(objectMapper, objectType,
                                    BasicAsyncEntityConsumer::new),
                            null));
            throwIfError(result);
            return result.getBody();
        } catch (final RestClientResponseException ex) {
            throw ex;
        } catch (final IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private URI buildRequestUri(final String pathTemplate,
                                final Map<String, String> pathParams,
                                final Map<String, List<String>> queryParams) {
        try {
            final URIBuilder uriBuilder = new URIBuilder(baseUri);
            final String[] segments = expandPathSegments(pathTemplate, pathParams);
            if (segments.length > 0) {
                uriBuilder.appendPathSegments(segments);
            }
            for (final Map.Entry<String, List<String>> entry : queryParams.entrySet()) {
                for (final String value : entry.getValue()) {
                    uriBuilder.addParameter(entry.getKey(), value);
                }
            }
            return uriBuilder.build();
        } catch (final URISyntaxException ex) {
            throw new IllegalStateException("Invalid URI: " + ex.getMessage(), ex);
        }
    }

    /**
     * Expands a path template by splitting it into segments, substituting template
     * variables with raw values. Encoding is deferred to {@link URIBuilder}.
     */
    static String[] expandPathSegments(final String template,
                                       final Map<String, String> variables) {
        if (template == null || template.isEmpty() || "/".equals(template)) {
            return new String[0];
        }
        final String[] rawSegments = template.split("/");
        final List<String> result = new ArrayList<>(rawSegments.length);
        for (final String segment : rawSegments) {
            if (segment.isEmpty()) {
                continue;
            }
            result.add(expandSegment(segment, variables));
        }
        return result.toArray(new String[0]);
    }

    /**
     * Expands template variables within a single path segment.
     */
    static String expandSegment(final String segment,
                                final Map<String, String> variables) {
        if (segment.indexOf('{') < 0) {
            return segment;
        }
        final StringBuilder sb = new StringBuilder(segment.length());
        int i = 0;
        while (i < segment.length()) {
            final char c = segment.charAt(i);
            if (c == '{') {
                final int close = segment.indexOf('}', i);
                if (close < 0) {
                    sb.append(segment, i, segment.length());
                    break;
                }
                final String name = segment.substring(i + 1, close);
                final String value = variables.get(name);
                if (value != null) {
                    sb.append(value);
                } else {
                    sb.append(segment, i, close + 1);
                }
                i = close + 1;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    private AsyncEntityProducer createEntityProducer(final Object body,
                                                     final ContentType consumeType) {
        if (body instanceof byte[]) {
            final ContentType ct = consumeType != null
                    ? consumeType : ContentType.APPLICATION_OCTET_STREAM;
            return new BasicAsyncEntityProducer((byte[]) body, ct);
        }
        if (body instanceof String) {
            final ContentType ct = consumeType != null
                    ? consumeType : ContentType.create("text/plain", StandardCharsets.UTF_8);
            return new StringAsyncEntityProducer((CharSequence) body, ct);
        }
        return new JsonObjectEntityProducer<>(body, objectMapper);
    }

    private static <T> T awaitResult(final Future<T> future) throws IOException {
        try {
            return future.get();
        } catch (final ExecutionException ex) {
            final Throwable cause = ex.getCause();
            if (cause instanceof RestClientResponseException) {
                throw (RestClientResponseException) cause;
            }
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new IOException("Request execution failed", cause);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", ex);
        }
    }

    private static void checkStatus(final HttpResponse response,
                                    final byte[] body) {
        if (response.getCode() >= ERROR_STATUS_THRESHOLD) {
            throw new RestClientResponseException(
                    response.getCode(), response.getReasonPhrase(),
                    body != null && body.length > 0 ? body : null);
        }
    }

    /**
     * Throws {@link RestClientResponseException} if the message carries an error.
     * Both {@link StringResponseConsumer} and the Jackson2 response consumer are
     * configured with {@link BasicAsyncEntityConsumer} on the error path, so the
     * error object is always {@code byte[]}.
     */
    private static void throwIfError(final Message<HttpResponse, ?> result) {
        final Object error = result.error();
        if (error != null) {
            if (!(error instanceof byte[])) {
                throw new IllegalStateException(
                        "Expected byte[] error body, got "
                                + error.getClass().getName());
            }
            final HttpResponse head = result.getHead();
            final byte[] errorBytes = (byte[]) error;
            throw new RestClientResponseException(
                    head.getCode(), head.getReasonPhrase(),
                    errorBytes.length > 0 ? errorBytes : null);
        }
    }

    /**
     * Converts a parameter value to its string representation for use in URI
     * path segments, query parameters or HTTP headers. Enums are converted using
     * {@link Enum#name()} to ensure round-trip compatibility with {@code valueOf}.
     */
    static String paramToString(final Object value) {
        Args.notNull(value, "Parameter value");
        if (value instanceof Enum) {
            return ((Enum<?>) value).name();
        }
        return value.toString();
    }

    private Object handleObjectMethod(final Object proxy, final Method method,
                                      final Object[] args) {
        final String name = method.getName();
        if ("toString".equals(name)) {
            return "RestProxy[" + baseUri + "]";
        }
        if ("hashCode".equals(name)) {
            return System.identityHashCode(proxy);
        }
        if ("equals".equals(name)) {
            return args[0] == proxy;
        }
        throw new UnsupportedOperationException(name);
    }

    static final class MethodInvoker {

        final ClientResourceMethod resourceMethod;
        final String acceptHeader;
        final ContentType consumeType;

        MethodInvoker(final ClientResourceMethod rm, final String accept,
                      final ContentType consume) {
            this.resourceMethod = rm;
            this.acceptHeader = accept;
            this.consumeType = consume;
        }
    }

}