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
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Request;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RestClientRequestTest {

    private HttpServer server;
    private CloseableHttpAsyncClient httpClient;
    private URI baseUri;
    private final AtomicReference<String> lastMethod = new AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", this::handle);
        server.start();

        baseUri = new URI("http://localhost:" + server.getAddress().getPort());
        httpClient = HttpAsyncClients.createDefault();
        httpClient.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (httpClient != null) {
            httpClient.close();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    private void handle(final HttpExchange exchange) throws IOException {
        try {
            lastMethod.set(exchange.getRequestMethod());
            exchange.sendResponseHeaders(204, -1);
        } finally {
            exchange.close();
        }
    }

    @Test
    void syncRequestExposesHttpMethod() {
        final Api api = build();
        final Request request = api.fetch();
        assertEquals("GET", request.getMethod());
        assertEquals("GET", lastMethod.get());
    }

    @Test
    void syncPostExposesHttpMethod() {
        final Api api = build();
        assertEquals("POST", api.create().getMethod());
        assertEquals("POST", lastMethod.get());
    }

    @Test
    void completionStageRequestExposesHttpMethod() throws Exception {
        final Api api = build();
        final CompletionStage<Request> stage = api.removeAsync();
        assertEquals("DELETE", stage.toCompletableFuture().get(5, TimeUnit.SECONDS).getMethod());
        assertEquals("DELETE", lastMethod.get());
    }

    @Test
    void completableFutureRequestExposesHttpMethod() throws Exception {
        final Api api = build();
        final CompletableFuture<Request> future = api.fetchFuture();

        assertEquals("GET", future.get(5, TimeUnit.SECONDS).getMethod());
        assertEquals("GET", lastMethod.get());
    }

    @Test
    void serverOnlyMethodsThrowUnsupportedOperationException() {
        final RestClientRequest request = new RestClientRequest("GET");
        assertThrows(UnsupportedOperationException.class,
                () -> request.selectVariant(Collections.emptyList()));
        assertThrows(UnsupportedOperationException.class,
                () -> request.evaluatePreconditions());
        assertThrows(UnsupportedOperationException.class,
                () -> request.evaluatePreconditions((java.util.Date) null));
        assertThrows(UnsupportedOperationException.class,
                () -> request.evaluatePreconditions((jakarta.ws.rs.core.EntityTag) null));
        assertThrows(UnsupportedOperationException.class,
                () -> request.evaluatePreconditions(null, null));
    }

    private Api build() {
        return RestClientBuilder.newBuilder()
                .baseUri(baseUri)
                .httpClient(httpClient)
                .build(Api.class);
    }

    @Path("/")
    interface Api {

        @GET
        @Path("/x")
        Request fetch();

        @POST
        @Path("/x")
        Request create();

        @DELETE
        @Path("/x")
        CompletionStage<Request> removeAsync();

        @GET
        @Path("/x")
        CompletableFuture<Request> fetchFuture();
    }
}
