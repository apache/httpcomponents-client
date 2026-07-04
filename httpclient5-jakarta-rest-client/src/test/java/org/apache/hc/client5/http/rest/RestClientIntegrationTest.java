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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

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
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        assertThat(created).isNotNull();
        assertThat(created.id).isEqualTo("abc-123");
        assertThat(created.title).isEqualTo("HttpComponents in Action");
        assertThat(created.lang).isEqualTo("en");

        final Book fetched = client.getBook("abc-123", "en", "token-1");

        assertThat(fetched).isNotNull();
        assertThat(fetched.id).isEqualTo("abc-123");
        assertThat(fetched.title).isEqualTo("HttpComponents in Action");
        assertThat(fetched.lang).isEqualTo("en");
    }

    @Test
    void sendsFormUrlEncodedFromNameValuePairs() throws Exception {
        final BookResource client = RestClientBuilder.newBuilder()
                .baseUri(baseUri)
                .httpClient(httpClient)
                .build(BookResource.class);

        final List<NameValuePair> form = Arrays.asList(
                new BasicNameValuePair("title", "HttpComponents in Action"),
                new BasicNameValuePair("lang", "en & fr"));

        final FormEcho echo = client.submitForm("token-1", form);

        assertThat(echo.contentType).startsWith("application/x-www-form-urlencoded");
        assertThat(echo.body).isEqualTo("title=HttpComponents+in+Action&lang=en+%26+fr");
    }

    @Test
    void nameValuePairListStaysJsonWhenConsumesIsJson() throws Exception {
        final BookResource client = RestClientBuilder.newBuilder()
                .baseUri(baseUri)
                .httpClient(httpClient)
                .build(BookResource.class);

        final List<NameValuePair> form = Arrays.asList(
                new BasicNameValuePair("title", "HttpComponents in Action"),
                new BasicNameValuePair("lang", "en"));

        final FormEcho echo = client.submitJsonList("token-1", form);

        assertThat(echo.contentType).startsWith("application/json");
        assertThat(echo.body).doesNotContain("title=HttpComponents");
    }

    @Test
    void readsFormUrlEncodedResponseAsNameValuePairs() throws Exception {
        final BookResource client = RestClientBuilder.newBuilder()
                .baseUri(baseUri)
                .httpClient(httpClient)
                .build(BookResource.class);

        final List<NameValuePair> pairs = client.readForm("token-1");

        assertThat(pairs).hasSize(2);
        assertThat(pairs.get(0).getName()).isEqualTo("greeting");
        assertThat(pairs.get(0).getValue()).isEqualTo("hello world");
        assertThat(pairs.get(1).getName()).isEqualTo("lang");
        assertThat(pairs.get(1).getValue()).isEqualTo("en");
    }

    @Test
    void rejectsNonFormResponseForNameValuePairReturnType() {
        final BookResource client = RestClientBuilder.newBuilder()
                .baseUri(baseUri)
                .httpClient(httpClient)
                .build(BookResource.class);

        assertThatThrownBy(() -> client.readNotForm("token-1"))
                .isInstanceOf(RestResourceException.class)
                .hasMessageContaining("application/x-www-form-urlencoded");
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

            if ("GET".equals(method) && "/books/not-form".equals(path)) {
                sendJson(exchange, 200, new Book("x", "not a form"));
                return;
            }

            if ("GET".equals(method) && "/books/form-echo".equals(path)) {
                send(exchange, 200, "application/x-www-form-urlencoded", "greeting=hello+world&lang=en");
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

            if ("POST".equals(method)) {
                final FormEcho echo = new FormEcho();
                echo.contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                echo.body = readString(exchange.getRequestBody());
                sendJson(exchange, 200, echo);
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

    private static String readString(final InputStream inputStream) throws IOException {
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        final byte[] tmp = new byte[1024];
        int n;
        while ((n = inputStream.read(tmp)) != -1) {
            buffer.write(tmp, 0, n);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
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

        @POST
        @Path("/form")
        @Consumes("application/x-www-form-urlencoded")
        @Produces("application/json")
        FormEcho submitForm(@HeaderParam("X-Auth") String auth,
                            List<NameValuePair> form);

        @POST
        @Path("/echo")
        @Produces("application/json")
        @Consumes("application/json")
        FormEcho submitJsonList(@HeaderParam("X-Auth") String auth,
                                List<NameValuePair> form);

        @GET
        @Path("/form-echo")
        @Produces("application/x-www-form-urlencoded")
        List<NameValuePair> readForm(@HeaderParam("X-Auth") String auth);

        @GET
        @Path("/not-form")
        @Produces("application/x-www-form-urlencoded")
        List<NameValuePair> readNotForm(@HeaderParam("X-Auth") String auth);
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

    static final class FormEcho {

        public String contentType;
        public String body;

        FormEcho() {
        }
    }

}