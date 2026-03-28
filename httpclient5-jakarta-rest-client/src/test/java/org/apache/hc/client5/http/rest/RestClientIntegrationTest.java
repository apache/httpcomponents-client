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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RestClientIntegrationTest {

    private HttpServer server;
    private CloseableHttpAsyncClient httpClient;
    private URI baseUri;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/books", this::handleBooks);
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
    void executesRealRoundTripAgainstLocalServer() throws Exception {
        final BookResource client = RestClientBuilder.newBuilder()
                .baseUri(baseUri)
                .httpClient(httpClient)
                .build(BookResource.class);

        final Book created = client.createBook("abc-123", "en", "token-1",
                new Book(null, "HttpComponents in Action"));

        assertNotNull(created);
        assertEquals("abc-123", created.id);
        assertEquals("HttpComponents in Action", created.title);
        assertEquals("en", created.lang);

        final Book fetched = client.getBook("abc-123", "en", "token-1");

        assertNotNull(fetched);
        assertEquals("abc-123", fetched.id);
        assertEquals("HttpComponents in Action", fetched.title);
        assertEquals("en", fetched.lang);
    }

    private void handleBooks(final HttpExchange exchange) throws IOException {
        try {
            final String method = exchange.getRequestMethod();
            final String path = exchange.getRequestURI().getPath();
            final String query = exchange.getRequestURI().getRawQuery();
            final String auth = exchange.getRequestHeaders().getFirst("X-Auth");

            if (!"token-1".equals(auth)) {
                send(exchange, 401, "text/plain", "Unauthorized");
                return;
            }

            if ("GET".equals(method) && path.startsWith("/books/")) {
                final String id = path.substring("/books/".length());
                final String lang = extractQueryParam(query, "lang");

                final Book book = new Book(id, "HttpComponents in Action");
                book.lang = lang;

                sendJson(exchange, 200, book);
                return;
            }

            if ("POST".equals(method) && "/books".equals(path)) {
                final String id = extractQueryParam(query, "id");
                final String lang = extractQueryParam(query, "lang");

                final Book incoming = readJson(exchange.getRequestBody(), Book.class);
                incoming.id = id;
                incoming.lang = lang;

                sendJson(exchange, 200, incoming);
                return;
            }

            send(exchange, 404, "text/plain", "Not found");
        } finally {
            exchange.close();
        }
    }

    private <T> T readJson(final InputStream inputStream, final Class<T> type) throws IOException {
        return objectMapper.readValue(inputStream, type);
    }

    private void sendJson(final HttpExchange exchange, final int statusCode, final Object payload)
            throws IOException {
        final byte[] body = objectMapper.writeValueAsBytes(payload);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, body.length);
        try (final OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static void send(final HttpExchange exchange, final int statusCode,
                             final String contentType, final String body) throws IOException {
        final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", contentType + "; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (final OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String extractQueryParam(final String query, final String name) {
        if (query == null || query.isEmpty()) {
            return null;
        }
        final String prefix = name + "=";
        for (final String pair : query.split("&")) {
            if (pair.startsWith(prefix)) {
                return pair.substring(prefix.length());
            }
        }
        return null;
    }

    @Path("/books")
    interface BookResource {

        @GET
        @Path("/{id}")
        @Produces("application/json")
        Book getBook(@PathParam("id") String id,
                     @QueryParam("lang") String lang,
                     @HeaderParam("X-Auth") String auth);

        @POST
        @Consumes("application/json")
        @Produces("application/json")
        Book createBook(@QueryParam("id") String id,
                        @QueryParam("lang") String lang,
                        @HeaderParam("X-Auth") String auth,
                        Book book);
    }

    static final class Book {

        public String id;
        public String title;
        public String lang;

        Book() {
        }

        Book(final String id, final String title) {
            this.id = id;
            this.title = title;
        }
    }

}