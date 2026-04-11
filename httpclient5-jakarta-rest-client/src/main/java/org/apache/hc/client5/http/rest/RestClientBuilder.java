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
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.core5.util.Args;

/**
 * Builds type-safe REST client proxies from Jakarta REST annotated interfaces. The proxy
 * translates each method call into an HTTP request executed through the async
 * {@link CloseableHttpAsyncClient} transport, which supports both HTTP/1.1 and HTTP/2.
 *
 * <p>Minimal usage:</p>
 * <pre>
 * try (CloseableHttpAsyncClient client = HttpAsyncClients.createDefault()) {
 *     client.start();
 *     UserApi api = RestClientBuilder.newBuilder()
 *             .baseUri("<a href="http://api.example.com">...</a>")
 *             .httpClient(client)
 *             .build(UserApi.class);
 *     String json = api.getUser(42);
 * }
 * </pre>
 *
 * <p>Both {@code baseUri} and {@code httpClient} are required. The caller owns the
 * client lifecycle, including the call to {@code start()} before use.</p>
 *
 * <p>Methods may return {@code String}, {@code byte[]}, {@code void}, or any type
 * deserializable by the configured Jackson {@link ObjectMapper}. Request bodies may be
 * {@code String}, {@code byte[]}, or any type serializable by the ObjectMapper.
 * Non-2xx responses throw {@link RestClientResponseException}.</p>
 *
 * @since 5.7
 */
public final class RestClientBuilder {

    private URI baseUri;
    private CloseableHttpAsyncClient httpClient;
    private ObjectMapper objectMapper;

    private RestClientBuilder() {
    }

    /**
     * Creates a new builder instance.
     *
     * @return a fresh builder.
     */
    public static RestClientBuilder newBuilder() {
        return new RestClientBuilder();
    }

    /**
     * Sets the base URI for all requests.
     *
     * @param uri the base URI string, must not be {@code null}.
     * @return this builder for chaining.
     */
    public RestClientBuilder baseUri(final String uri) {
        Args.notBlank(uri, "Base URI");
        this.baseUri = URI.create(uri);
        return this;
    }

    /**
     * Sets the base URI for all requests.
     *
     * @param uri the base URI, must not be {@code null}.
     * @return this builder for chaining.
     */
    public RestClientBuilder baseUri(final URI uri) {
        Args.notNull(uri, "Base URI");
        this.baseUri = uri;
        return this;
    }

    /**
     * Sets the async HTTP client to use for requests. The caller owns the client
     * lifecycle, including the call to {@link CloseableHttpAsyncClient#start()}.
     *
     * @param client the async HTTP client, must not be {@code null}.
     * @return this builder for chaining.
     * @since 5.7
     */
    public RestClientBuilder httpClient(final CloseableHttpAsyncClient client) {
        Args.notNull(client, "HTTP client");
        this.httpClient = client;
        return this;
    }

    /**
     * Sets the Jackson {@link ObjectMapper} for JSON serialization and deserialization.
     * If not set, a default ObjectMapper is used.
     *
     * @param mapper the object mapper, must not be {@code null}.
     * @return this builder for chaining.
     * @since 5.7
     */
    public RestClientBuilder objectMapper(final ObjectMapper mapper) {
        Args.notNull(mapper, "Object mapper");
        this.objectMapper = mapper;
        return this;
    }

    /**
     * Scans the given interface for Jakarta REST annotations and creates a proxy that
     * implements it by dispatching HTTP requests through the configured async client.
     *
     * @param <T>   the interface type.
     * @param iface the Jakarta REST annotated interface class.
     * @return a proxy implementing the interface.
     * @throws IllegalArgumentException if the class is not an interface.
     * @throws IllegalStateException    if no base URI or client has been set, or if the
     *                                  interface has no Jakarta REST annotated methods.
     */
    @SuppressWarnings("unchecked")
    public <T> T build(final Class<T> iface) {
        Args.notNull(iface, "Interface class");
        if (!iface.isInterface()) {
            throw new IllegalArgumentException(iface.getName() + " is not an interface");
        }
        if (baseUri == null) {
            throw new IllegalStateException("baseUri is required");
        }
        if (httpClient == null) {
            throw new IllegalStateException("httpClient is required");
        }

        final List<ClientResourceMethod> methods = ClientResourceMethod.scan(iface);
        if (methods.isEmpty()) {
            throw new IllegalStateException(
                    "No Jakarta REST methods found on " + iface.getName());
        }
        final Map<Method, ClientResourceMethod> methodMap =
                new HashMap<>(methods.size());
        for (final ClientResourceMethod rm : methods) {
            methodMap.put(rm.getMethod(), rm);
        }

        final ObjectMapper mapper = objectMapper != null
                ? objectMapper : new ObjectMapper();

        return (T) Proxy.newProxyInstance(
                iface.getClassLoader(),
                new Class<?>[]{iface},
                new RestInvocationHandler(httpClient, baseUri, methodMap, mapper));
    }

}
