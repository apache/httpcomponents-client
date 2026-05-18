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
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.ws.rs.core.Response;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityProducer;
import org.apache.hc.core5.http.nio.entity.DiscardingEntityConsumer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.jackson2.http.JsonNodeEntityFallbackConsumer;
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
    private final Map<Method, ResourceMethod> methodMap;

    RestInvocationHandler(final CloseableHttpAsyncClient client, final URI base,
                          final Map<Method, ResourceMethod> methodMap,
                          final ObjectMapper mapper) {
        this.httpClient = client;
        this.baseUri = base;
        this.objectMapper = mapper;
        this.methodMap = methodMap;
    }

    @Override
    public Object invoke(final Object proxy, final Method method,
                         final Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return handleObjectMethod(proxy, method, args);
        }
        final ResourceMethod resourceMethod = methodMap.get(method);
        if (resourceMethod == null) {
            throw new RestResourceException("No Jakarta REST mapping for " + method.getName());
        }
        return executeRequest(resourceMethod, args);
    }

    private Object executeRequest(final ResourceMethod rm,
                                  final Object[] args) {
        final ResourceParam[] params = rm.getParams();
        final Map<String, String> pathParams = new HashMap<>(params.length);
        final List<NameValuePair> queryParams = new ArrayList<>(params.length);
        final List<Header> headers = new ArrayList<>(params.length);
        Object bodyParam = null;

        if (args != null) {
            for (int i = 0; i < params.length; i++) {
                final ResourceParam param = params[i];
                final String paramName = param.getName();
                final Object arg = args[i];
                final String paramValue = arg != null ? paramToString(arg) : param.getDefaultValue();
                switch (param.getType()) {
                    case PATH:
                        Args.check(paramValue != null, "Path parameter '%s' must not be null", param.getName());
                        pathParams.put(paramName, paramValue);
                        break;
                    case QUERY:
                        queryParams.add(new BasicNameValuePair(paramName, paramValue));
                        break;
                    case HEADER:
                        headers.add(new BasicHeader(paramName, paramValue));
                        break;
                    case BODY:
                        bodyParam = arg;
                        break;
                }
            }
        }
        final URI requestUri;
        try {
            final URIBuilder uriBuilder = new URIBuilder(baseUri);
            final List<PathSegment> pathTemplates = rm.getPathSegments();
            final List<String> pathSegments = new ArrayList<>(pathTemplates.size());
            for (final PathSegment pathTemplate : pathTemplates) {
                if (pathTemplate.getType() == PathSegment.Type.PARAMETER) {
                    pathSegments.add(pathParams.get(pathTemplate.getSegment()));
                } else {
                    pathSegments.add(pathTemplate.getSegment());
                }
            }
            uriBuilder.appendPathSegments(pathSegments);
            uriBuilder.addParameters(queryParams);
            requestUri = uriBuilder.build();
        } catch (final URISyntaxException ex) {
            throw new RestResourceException("Invalid request URI", ex);
        }
        final BasicHttpRequest request = new BasicHttpRequest(rm.getHttpMethod(), requestUri);
        for (final Header header : headers) {
            request.addHeader(header);
        }
        final List<ContentType> consumesContentTypes = rm.getConsumesContentTypes();
        if (consumesContentTypes != null && !consumesContentTypes.isEmpty()) {
            request.setHeader(MessageSupport.headerOfTokens(
                    HttpHeaders.ACCEPT,
                    consumesContentTypes.stream()
                    .map(ContentType::getMimeType)
                    .collect(Collectors.toList())));
        }
        final AsyncEntityProducer entityProducer;
        if (bodyParam != null) {
            entityProducer = createEntityProducer(bodyParam,
                    consumesContentTypes != null && !consumesContentTypes.isEmpty() ? consumesContentTypes.get(0) : null);
        } else {
            entityProducer = null;
        }

        final BasicRequestProducer requestProducer = new BasicRequestProducer(request, entityProducer);

        final boolean isAsync = isAsync(rm.getMethod());
        final Class<?> rawType = resolveResponseType(rm.getMethod(), isAsync);
        final CompletableFuture<Object> future = dispatchAsync(rawType, requestProducer);
        if (isAsync) {
            return future;
        }
        return awaitSync(future);
    }

    private static boolean isAsync(final Method method) {
        final Class<?> rt = method.getReturnType();
        return rt == CompletionStage.class || rt == CompletableFuture.class;
    }

    private static Class<?> resolveResponseType(final Method method, final boolean async) {
        if (!async) {
            return method.getReturnType();
        }
        final Type generic = method.getGenericReturnType();
        if (generic instanceof ParameterizedType) {
            final Type inner = ((ParameterizedType) generic).getActualTypeArguments()[0];
            if (inner instanceof Class) {
                return (Class<?>) inner;
            }
            if (inner instanceof ParameterizedType) {
                final Type raw = ((ParameterizedType) inner).getRawType();
                if (raw instanceof Class) {
                    return (Class<?>) raw;
                }
            }
        }
        return Object.class;
    }

    private CompletableFuture<Object> dispatchAsync(final Class<?> rawType,
                                                    final BasicRequestProducer requestProducer) {
        if (rawType == void.class || rawType == Void.class) {
            return submit(requestProducer, new BasicResponseConsumer<>(new DiscardingEntityConsumer<>()))
                    .thenApply(result -> {
                        checkStatus(result.getHead(), null);
                        return null;
                    });
        }
        if (rawType == Response.class) {
            return submit(requestProducer, new BasicResponseConsumer<>(new JsonNodeEntityFallbackConsumer(objectMapper)))
                    .thenApply(result -> new RestClientResponse(result.getHead(), result.getBody(), objectMapper));
        }
        if (rawType == byte[].class) {
            return submit(requestProducer, new BasicResponseConsumer<>(new BasicAsyncEntityConsumer()))
                    .thenApply(result -> {
                        final byte[] body = result.getBody();
                        checkStatus(result.getHead(), body);
                        return body;
                    });
        }
        if (rawType == String.class) {
            return submit(requestProducer, new StringResponseConsumer())
                    .thenApply(result -> {
                        throwIfError(result);
                        return result.getBody();
                    });
        }
        @SuppressWarnings("unchecked") final Class<Object> objectType = (Class<Object>) rawType;
        return submit(requestProducer,
                JsonResponseConsumers.create(objectMapper, objectType, BasicAsyncEntityConsumer::new))
                .thenApply(result -> {
                    throwIfError(result);
                    return result.getBody();
                });
    }

    private <T> CompletableFuture<Message<HttpResponse, T>> submit(
            final BasicRequestProducer requestProducer,
            final AsyncResponseConsumer<Message<HttpResponse, T>> responseConsumer) {
        final CompletableFuture<Message<HttpResponse, T>> cf = new CompletableFuture<>();
        httpClient.execute(requestProducer, responseConsumer, null,
                new FutureCallback<Message<HttpResponse, T>>() {

                    @Override
                    public void completed(final Message<HttpResponse, T> result) {
                        cf.complete(result);
                    }

                    @Override
                    public void failed(final Exception ex) {
                        cf.completeExceptionally(ex);
                    }

                    @Override
                    public void cancelled() {
                        cf.cancel(false);
                    }
                });
        return cf;
    }

    private static Object awaitSync(final CompletableFuture<Object> future) {
        try {
            return future.get();
        } catch (final ExecutionException ex) {
            throw unwrap(ex.getCause());
        } catch (final CompletionException ex) {
            throw unwrap(ex.getCause());
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new UncheckedIOException(new IOException("Request interrupted", ex));
        }
    }

    private static RuntimeException unwrap(final Throwable cause) {
        if (cause instanceof RestClientResponseException) {
            return (RestClientResponseException) cause;
        }
        if (cause instanceof RuntimeException) {
            return (RuntimeException) cause;
        }
        if (cause instanceof IOException) {
            return new UncheckedIOException((IOException) cause);
        }
        return new UncheckedIOException(new IOException("Request execution failed", cause));
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

}