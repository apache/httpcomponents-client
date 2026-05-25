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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RestClientResponseTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private HttpServer server;
    private CloseableHttpAsyncClient httpClient;
    private URI baseUri;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/echo", this::handleEcho);
        server.createContext("/error", this::handleError);
        server.createContext("/empty", this::handleEmpty);
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

    @Test
    void successResponseExposesStatusBodyAndHeaders() {
        final EchoApi api = build();
        try (Response response = api.echo("abc")) {
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getStatusInfo().toEnum()).isEqualTo(Response.Status.OK);
            assertThat(response.getStatusInfo().getReasonPhrase()).isEqualTo("OK");
            assertThat(response.getMediaType()).isNotNull();
            assertThat(response.getMediaType().getType()).isEqualTo("application");
            assertThat(response.getMediaType().getSubtype()).isEqualTo("json");
            assertThat(response.getHeaderString("X-Custom")).isEqualTo("custom-value");
            assertThat(response.hasEntity()).isTrue();
            assertThat(response.readEntity(String.class)).isEqualTo("{\"id\":\"abc\"}");
        }
    }

    @Test
    void readEntityDecodesJsonPojo() {
        final EchoApi api = build();
        try (Response response = api.echo("xyz")) {
            final Echo echo = response.readEntity(Echo.class);
            assertThat(echo.id).isEqualTo("xyz");
        }
    }

    @Test
    void readEntityReturnsJsonNodeForJsonContent() {
        final EchoApi api = build();
        try (Response response = api.echo("abc")) {
            final JsonNode node = response.readEntity(JsonNode.class);
            assertThat(node).isNotNull();
            assertThat(node.isObject()).isTrue();
            assertThat(node.get("id").asText()).isEqualTo("abc");
        }
    }

    @Test
    void repeatedReadEntityReusesJsonTree() {
        final EchoApi api = build();
        try (Response response = api.echo("abc")) {
            final JsonNode first = response.readEntity(JsonNode.class);
            final Echo pojo = response.readEntity(Echo.class);
            final JsonNode second = response.readEntity(JsonNode.class);

            assertThat(pojo.id).isEqualTo("abc");
            assertThat(first.get("id").asText()).isEqualTo("abc");
            assertThat(second).isSameAs(first);
        }
    }

    @Test
    void responseJsonEntityIsBackedByJsonNode() {
        final BasicHttpResponse httpResponse = new BasicHttpResponse(200);
        httpResponse.addHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString());

        final JsonNode node = OBJECT_MAPPER.createObjectNode().put("id", "abc");

        try (Response response = new RestClientResponse(OBJECT_MAPPER, httpResponse, node, ContentType.APPLICATION_JSON, -1)) {
            assertThat(response.readEntity(JsonNode.class)).isSameAs(node);
            assertThat(response.readEntity(Echo.class).id).isEqualTo("abc");
            assertThat(response.readEntity(String.class)).isEqualTo("{\"id\":\"abc\"}");
        }
    }

    @Test
    void errorResponsesAreReturnedNotThrown() {
        final EchoApi api = build();
        try (Response response = api.failing()) {
            assertThat(response.getStatus()).isEqualTo(418);
            assertThat(response.getStatusInfo().getFamily()).isEqualTo(Response.Status.Family.CLIENT_ERROR);
            assertThat(response.readEntity(String.class)).isEqualTo("nope");
        }
    }

    @Test
    void emptyBodyHasNoEntity() {
        final EchoApi api = build();
        try (Response response = api.empty()) {
            assertThat(response.getStatus()).isEqualTo(204);
            assertThat(response.hasEntity()).isFalse();
            assertThat(response.getAllowedMethods().contains("GET")).isTrue();
        }
    }

    @Test
    void completionStageOfResponseDelivers2xx() throws Exception {
        final EchoApi api = build();
        final CompletionStage<Response> stage = api.echoAsync("abc");
        try (Response response = stage.toCompletableFuture().get(5, TimeUnit.SECONDS)) {
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.readEntity(Echo.class).id).isEqualTo("abc");
        }
    }

    @Test
    void completionStageOfResponseDeliversNon2xxAsValue() throws Exception {
        final EchoApi api = build();
        try (Response response = api.failingAsync().toCompletableFuture().get(5, TimeUnit.SECONDS)) {
            assertThat(response.getStatus()).isEqualTo(418);
            assertThat(response.readEntity(String.class)).isEqualTo("nope");
        }
    }

    @Test
    void completableFutureOfResponseDelivers2xx() throws Exception {
        final EchoApi api = build();

        try (Response response = api.echoFuture("abc").get(5, TimeUnit.SECONDS)) {
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.readEntity(Echo.class).id).isEqualTo("abc");
        }
    }

    private EchoApi build() {
        return RestClientBuilder.newBuilder()
                .baseUri(baseUri)
                .httpClient(httpClient)
                .build(EchoApi.class);
    }

    private void handleEcho(final HttpExchange exchange) throws IOException {
        try {
            final String path = exchange.getRequestURI().getPath();
            final String id = path.substring("/echo/".length());
            final byte[] body = ("{\"id\":\"" + id + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.getResponseHeaders().add("X-Custom", "custom-value");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        } finally {
            exchange.close();
        }
    }

    private void handleError(final HttpExchange exchange) throws IOException {
        try {
            final byte[] body = "nope".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(418, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        } finally {
            exchange.close();
        }
    }

    private void handleEmpty(final HttpExchange exchange) throws IOException {
        try {
            exchange.getResponseHeaders().add("Allow", "GET, HEAD");
            exchange.sendResponseHeaders(204, -1);
        } finally {
            exchange.close();
        }
    }

    @Path("/")
    interface EchoApi {

        @GET
        @Path("/echo/{id}")
        Response echo(@PathParam("id") String id);

        @GET
        @Path("/echo/{id}")
        CompletionStage<Response> echoAsync(@PathParam("id") String id);

        @GET
        @Path("/echo/{id}")
        CompletableFuture<Response> echoFuture(@PathParam("id") String id);

        @GET
        @Path("/error")
        Response failing();

        @GET
        @Path("/error")
        CompletionStage<Response> failingAsync();

        @GET
        @Path("/empty")
        Response empty();
    }

    static final class Echo {
        public String id;
    }
}