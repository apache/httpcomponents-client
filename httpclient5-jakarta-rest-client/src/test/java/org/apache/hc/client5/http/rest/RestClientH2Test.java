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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.nio.AsyncRequestConsumer;
import org.apache.hc.core5.http.nio.AsyncServerRequestHandler;
import org.apache.hc.core5.http.nio.entity.DiscardingEntityConsumer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.support.BasicRequestConsumer;
import org.apache.hc.core5.http.nio.support.BasicResponseProducer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2ServerBootstrap;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the REST client proxy operates correctly over cleartext HTTP/2 (h2c).
 */
class RestClientH2Test {

    static HttpAsyncServer h2Server;
    static int h2Port;
    static CloseableHttpAsyncClient h2Client;

    // --- Test interfaces ---

    @Path("/echo")
    public interface EchoApi {

        @GET
        @Produces(MediaType.TEXT_PLAIN)
        String echo(@QueryParam("msg") String msg);
    }

    public static class Widget {

        public int id;
        public String name;

        public Widget() {
        }

        public Widget(final int id, final String name) {
            this.id = id;
            this.name = name;
        }
    }

    @Path("/json")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public interface JsonWidgetApi {

        @GET
        @Path("/{id}")
        Widget get(@PathParam("id") int id);

        @POST
        Widget create(Widget widget);
    }

    @Path("/jsonerror")
    @Produces(MediaType.APPLICATION_JSON)
    public interface JsonErrorApi {

        @GET
        @Path("/{code}")
        Widget getError(@PathParam("code") int code);
    }

    @Path("/widgets")
    public interface VoidApi {

        @DELETE
        @Path("/{id}")
        void delete(@PathParam("id") int id);
    }

    // --- Server setup ---

    @BeforeAll
    static void setUp() throws Exception {
        h2Server = H2ServerBootstrap.bootstrap()
                .setCanonicalHostName("localhost")
                .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_2)
                .register("/echo", echoHandler())
                .register("/json/*", jsonGetHandler())
                .register("/json", jsonPostHandler())
                .register("/jsonerror/*", jsonErrorHandler())
                .register("/widgets/*", voidHandler())
                .create();
        h2Server.start();
        final ListenerEndpoint endpoint = h2Server.listen(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                URIScheme.HTTP).get();
        h2Port = ((InetSocketAddress) endpoint.getAddress()).getPort();

        h2Client = HttpAsyncClients.createHttp2Default();
        h2Client.start();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (h2Client != null) {
            h2Client.close();
        }
        if (h2Server != null) {
            h2Server.close(CloseMode.GRACEFUL);
        }
    }

    private <T> T proxy(final Class<T> iface) {
        return RestClientBuilder.newBuilder()
                .baseUri("http://localhost:" + h2Port)
                .httpClient(h2Client)
                .build(iface);
    }

    // --- Tests ---

    @Test
    void testStringGetOverH2() {
        final EchoApi api = proxy(EchoApi.class);
        assertEquals("hello", api.echo("hello"));
    }

    @Test
    void testJsonPojoGetOverH2() {
        final JsonWidgetApi api = proxy(JsonWidgetApi.class);
        final Widget w = api.get(42);
        assertEquals(42, w.id);
        assertEquals("W-42", w.name);
    }

    @Test
    void testJsonPojoPostOverH2() {
        final JsonWidgetApi api = proxy(JsonWidgetApi.class);
        final Widget w = api.create(new Widget(7, "Created"));
        assertEquals(7, w.id);
        assertEquals("Created", w.name);
    }

    @Test
    void testErrorResponseOverH2() {
        final JsonErrorApi api = proxy(JsonErrorApi.class);
        final RestClientResponseException ex = assertThrows(
                RestClientResponseException.class,
                () -> api.getError(500));
        assertEquals(500, ex.getStatusCode());
        assertNotNull(ex.getResponseBody());
    }

    @Test
    void testVoidDeleteOverH2() {
        final VoidApi api = proxy(VoidApi.class);
        api.delete(1);
    }

    // --- Async server handlers ---

    private static AsyncServerRequestHandler<Message<HttpRequest, Void>> echoHandler() {
        return new AsyncServerRequestHandler<Message<HttpRequest, Void>>() {
            @Override
            public AsyncRequestConsumer<Message<HttpRequest, Void>> prepare(
                    final HttpRequest request, final EntityDetails entityDetails,
                    final HttpContext context) {
                return new BasicRequestConsumer<>(new DiscardingEntityConsumer<>());
            }

            @Override
            public void handle(final Message<HttpRequest, Void> message,
                               final ResponseTrigger responseTrigger,
                               final HttpContext context) throws HttpException, IOException {
                final String uri = message.getHead().getRequestUri();
                final int qi = uri.indexOf("msg=");
                final String msg = qi >= 0 ? uri.substring(qi + 4) : "";
                responseTrigger.submitResponse(
                        new BasicResponseProducer(200, msg, ContentType.TEXT_PLAIN),
                        context);
            }
        };
    }

    private static AsyncServerRequestHandler<Message<HttpRequest, Void>> jsonGetHandler() {
        return new AsyncServerRequestHandler<Message<HttpRequest, Void>>() {
            @Override
            public AsyncRequestConsumer<Message<HttpRequest, Void>> prepare(
                    final HttpRequest request, final EntityDetails entityDetails,
                    final HttpContext context) {
                return new BasicRequestConsumer<>(new DiscardingEntityConsumer<>());
            }

            @Override
            public void handle(final Message<HttpRequest, Void> message,
                               final ResponseTrigger responseTrigger,
                               final HttpContext context) throws HttpException, IOException {
                final String path = message.getHead().getRequestUri();
                final String idStr = path.substring(path.lastIndexOf('/') + 1);
                responseTrigger.submitResponse(
                        new BasicResponseProducer(200,
                                "{\"id\":" + idStr + ",\"name\":\"W-" + idStr + "\"}",
                                ContentType.APPLICATION_JSON),
                        context);
            }
        };
    }

    private static AsyncServerRequestHandler<Message<HttpRequest, String>> jsonPostHandler() {
        return new AsyncServerRequestHandler<Message<HttpRequest, String>>() {
            @Override
            public AsyncRequestConsumer<Message<HttpRequest, String>> prepare(
                    final HttpRequest request, final EntityDetails entityDetails,
                    final HttpContext context) {
                return new BasicRequestConsumer<>(new StringAsyncEntityConsumer());
            }

            @Override
            public void handle(final Message<HttpRequest, String> message,
                               final ResponseTrigger responseTrigger,
                               final HttpContext context) throws HttpException, IOException {
                responseTrigger.submitResponse(
                        new BasicResponseProducer(201, message.getBody(),
                                ContentType.APPLICATION_JSON),
                        context);
            }
        };
    }

    private static AsyncServerRequestHandler<Message<HttpRequest, Void>> jsonErrorHandler() {
        return new AsyncServerRequestHandler<Message<HttpRequest, Void>>() {
            @Override
            public AsyncRequestConsumer<Message<HttpRequest, Void>> prepare(
                    final HttpRequest request, final EntityDetails entityDetails,
                    final HttpContext context) {
                return new BasicRequestConsumer<>(new DiscardingEntityConsumer<>());
            }

            @Override
            public void handle(final Message<HttpRequest, Void> message,
                               final ResponseTrigger responseTrigger,
                               final HttpContext context) throws HttpException, IOException {
                final String path = message.getHead().getRequestUri();
                final int code = Integer.parseInt(
                        path.substring(path.lastIndexOf('/') + 1));
                responseTrigger.submitResponse(
                        new BasicResponseProducer(code,
                                "{\"error\":\"status " + code + "\"}",
                                ContentType.APPLICATION_JSON),
                        context);
            }
        };
    }

    private static AsyncServerRequestHandler<Message<HttpRequest, Void>> voidHandler() {
        return new AsyncServerRequestHandler<Message<HttpRequest, Void>>() {
            @Override
            public AsyncRequestConsumer<Message<HttpRequest, Void>> prepare(
                    final HttpRequest request, final EntityDetails entityDetails,
                    final HttpContext context) {
                return new BasicRequestConsumer<>(new DiscardingEntityConsumer<>());
            }

            @Override
            public void handle(final Message<HttpRequest, Void> message,
                               final ResponseTrigger responseTrigger,
                               final HttpContext context) throws HttpException, IOException {
                responseTrigger.submitResponse(
                        new BasicResponseProducer(new BasicHttpResponse(204)),
                        context);
            }
        };
    }

}
