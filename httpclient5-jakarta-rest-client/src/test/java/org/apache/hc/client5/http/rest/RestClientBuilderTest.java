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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.charset.StandardCharsets;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.client.ResponseProcessingException;
import jakarta.ws.rs.core.MediaType;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class RestClientBuilderTest {

    static HttpServer server;
    static int port;
    static CloseableHttpAsyncClient httpClient;

    // --- Test interfaces ---

    @Path("/widgets")
    @Produces(MediaType.APPLICATION_JSON)
    public interface WidgetApi {

        @GET
        String list();

        @GET
        @Path("/{id}")
        String get(@PathParam("id") int id);

        @POST
        @Consumes(MediaType.APPLICATION_JSON)
        String create(String body);

        @PUT
        @Path("/{id}")
        @Consumes(MediaType.APPLICATION_JSON)
        String update(@PathParam("id") int id, String body);

        @DELETE
        @Path("/{id}")
        void delete(@PathParam("id") int id);
    }

    @Path("/echo")
    public interface EchoApi {

        @GET
        @Produces(MediaType.TEXT_PLAIN)
        String echo(@QueryParam("msg") String msg);

        @GET
        @Path("/multi")
        @Produces(MediaType.TEXT_PLAIN)
        String echoMulti(@QueryParam("tag") String tag1,
                         @QueryParam("tag") String tag2);

        @GET
        @Path("/header")
        @Produces(MediaType.TEXT_PLAIN)
        String echoHeader(@HeaderParam("X-Tag") String tag);
    }

    @Path("/status")
    public interface StatusApi {

        @GET
        @Path("/{code}")
        @Produces(MediaType.TEXT_PLAIN)
        String getStatus(@PathParam("code") int code);
    }

    @Path("/echopath")
    public interface EchoPathApi {

        @GET
        @Path("/{value}")
        @Produces(MediaType.TEXT_PLAIN)
        String echoPath(@PathParam("value") String value);
    }

    @Path("/defaults")
    public interface DefaultsApi {

        @GET
        @Produces(MediaType.TEXT_PLAIN)
        String withDefault(
                @QueryParam("color") @DefaultValue("red") String color);
    }

    @Path("/bytes")
    public interface BytesApi {

        @GET
        @Produces(MediaType.APPLICATION_OCTET_STREAM)
        byte[] getBytes();
    }

    @Path("/inspect")
    public interface InspectApi {

        @POST
        @Path("/string")
        @Produces(MediaType.TEXT_PLAIN)
        String postString(String body);

        @POST
        @Path("/bytes")
        @Consumes(MediaType.TEXT_PLAIN)
        String postBytes(byte[] body);
    }

    @Path("/echobody")
    public interface EchoBodyApi {

        @POST
        @Consumes("text/plain; charset=ISO-8859-1")
        @Produces(MediaType.APPLICATION_OCTET_STREAM)
        byte[] post(String body);
    }

    @Path("/noproduce")
    public interface NoProduceApi {

        @GET
        String get();
    }

    @Path("/charset")
    public interface CharsetApi {

        @GET
        @Produces(MediaType.TEXT_PLAIN)
        String get();
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

    @Path("/inspect")
    public interface InspectJsonApi {

        @POST
        @Path("/json")
        @Consumes("application/vnd.api+json")
        @Produces(MediaType.TEXT_PLAIN)
        String postJson(Widget widget);
    }

    @Path("/charstatus")
    public interface CharStatusApi {

        @GET
        @Path("/{code}")
        @Produces(MediaType.TEXT_PLAIN)
        String getStatus(@PathParam("code") int code);
    }

    public enum Color { RED, GREEN, BLUE }

    @Path("/echo")
    public interface EnumQueryApi {

        @GET
        @Produces(MediaType.TEXT_PLAIN)
        String echo(@QueryParam("msg") Color color);
    }

    @Path("/bad")
    public interface BadMultiBodyApi {

        @POST
        String post(String body1, String body2);
    }

    @Path("/mismatch")
    public interface BadPathParamApi {

        @GET
        @Path("/{id}")
        String get(@PathParam("userId") int id);
    }

    @Path("/missing")
    public interface MissingPathParamApi {

        @GET
        @Path("/{id}/{version}")
        String get(@PathParam("id") int id);
    }

    @Path("/multi")
    public interface MultiConsumesBodyApi {

        @POST
        @Consumes({"application/json", "application/xml"})
        String post(String body);
    }

    // --- Server setup ---

    @BeforeAll
    static void setUp() throws Exception {
        server = ServerBootstrap.bootstrap()
                .setCanonicalHostName("localhost")
                .register("/widgets", (request, response, context) -> {
                    final String method = request.getMethod();
                    if ("GET".equals(method)) {
                        response.setCode(200);
                        response.setEntity(new StringEntity(
                                "[{\"id\":1,\"name\":\"A\"},"
                                        + "{\"id\":2,\"name\":\"B\"}]",
                                ContentType.APPLICATION_JSON));
                    } else if ("POST".equals(method)) {
                        response.setCode(201);
                        response.setEntity(new StringEntity(
                                "{\"id\":99,\"name\":\"Created\"}",
                                ContentType.APPLICATION_JSON));
                    }
                })
                .register("/widgets/*", (request, response, context) -> {
                    final String path = request.getRequestUri();
                    final String idStr =
                            path.substring(path.lastIndexOf('/') + 1);
                    final String method = request.getMethod();
                    if ("GET".equals(method)) {
                        response.setCode(200);
                        response.setEntity(new StringEntity(
                                "{\"id\":" + idStr
                                        + ",\"name\":\"W-" + idStr + "\"}",
                                ContentType.APPLICATION_JSON));
                    } else if ("PUT".equals(method)) {
                        response.setCode(200);
                        response.setEntity(new StringEntity(
                                "{\"id\":" + idStr
                                        + ",\"name\":\"Updated\"}",
                                ContentType.APPLICATION_JSON));
                    } else if ("DELETE".equals(method)) {
                        response.setCode(204);
                    }
                })
                .register("/echo", (request, response, context) -> {
                    final String uri = request.getRequestUri();
                    final int qi = uri.indexOf("msg=");
                    final String msg = qi >= 0 ? uri.substring(qi + 4) : "";
                    response.setCode(200);
                    response.setEntity(
                            new StringEntity(msg, ContentType.TEXT_PLAIN));
                })
                .register("/echo/multi",
                        (request, response, context) -> {
                            final String uri = request.getRequestUri();
                            final int qi = uri.indexOf('?');
                            final String query = qi >= 0
                                    ? uri.substring(qi + 1) : "";
                            response.setCode(200);
                            response.setEntity(new StringEntity(
                                    query, ContentType.TEXT_PLAIN));
                        })
                .register("/echo/header",
                        (request, response, context) -> {
                            final String tag =
                                    request.getFirstHeader("X-Tag") != null
                                    ? request.getFirstHeader("X-Tag")
                                            .getValue()
                                    : "none";
                            response.setCode(200);
                            response.setEntity(new StringEntity(
                                    tag, ContentType.TEXT_PLAIN));
                        })
                .register("/echopath/*",
                        (request, response, context) -> {
                            final String path = request.getRequestUri();
                            final String raw = path.substring(
                                    path.lastIndexOf('/') + 1);
                            response.setCode(200);
                            response.setEntity(new StringEntity(
                                    raw, ContentType.TEXT_PLAIN));
                        })
                .register("/defaults",
                        (request, response, context) -> {
                            final String uri = request.getRequestUri();
                            final int qi = uri.indexOf("color=");
                            final String color = qi >= 0
                                    ? uri.substring(qi + 6) : "none";
                            response.setCode(200);
                            response.setEntity(new StringEntity(
                                    color, ContentType.TEXT_PLAIN));
                        })
                .register("/inspect/*",
                        (request, response, context) -> {
                            final String ct =
                                    request.getFirstHeader("Content-Type")
                                            != null
                                    ? request.getFirstHeader("Content-Type")
                                            .getValue()
                                    : "none";
                            response.setCode(200);
                            response.setEntity(new StringEntity(
                                    ct, ContentType.TEXT_PLAIN));
                        })
                .register("/echobody",
                        (request, response, context) -> {
                            final byte[] body =
                                    EntityUtils.toByteArray(request.getEntity());
                            response.setCode(200);
                            response.setEntity(new ByteArrayEntity(body,
                                    ContentType.APPLICATION_OCTET_STREAM));
                        })
                .register("/noproduce",
                        (request, response, context) -> {
                            final String accept =
                                    request.getFirstHeader("Accept") != null
                                    ? request.getFirstHeader("Accept")
                                            .getValue()
                                    : "none";
                            response.setCode(200);
                            response.setEntity(new StringEntity(
                                    accept, ContentType.TEXT_PLAIN));
                        })
                .register("/bytes",
                        (request, response, context) -> {
                            response.setCode(200);
                            response.setEntity(new StringEntity(
                                    "binary-data",
                                    ContentType.APPLICATION_OCTET_STREAM));
                        })
                .register("/json",
                        (request, response, context) -> {
                            final String body =
                                    EntityUtils.toString(request.getEntity());
                            response.setCode(201);
                            response.setEntity(new StringEntity(
                                    body, ContentType.APPLICATION_JSON));
                        })
                .register("/json/*",
                        (request, response, context) -> {
                            final String path = request.getRequestUri();
                            final String idStr = path.substring(
                                    path.lastIndexOf('/') + 1);
                            response.setCode(200);
                            response.setEntity(new StringEntity(
                                    "{\"id\":" + idStr
                                            + ",\"name\":\"W-" + idStr + "\"}",
                                    ContentType.APPLICATION_JSON));
                        })
                .register("/jsonerror/*",
                        (request, response, context) -> {
                            final String path = request.getRequestUri();
                            final int code = Integer.parseInt(
                                    path.substring(
                                            path.lastIndexOf('/') + 1));
                            response.setCode(code);
                            response.setEntity(new StringEntity(
                                    "{\"error\":\"status " + code + "\"}",
                                    ContentType.APPLICATION_JSON));
                        })
                .register("/charset",
                        (request, response, context) -> {
                            final byte[] bytes = "caf\u00e9"
                                    .getBytes(StandardCharsets.ISO_8859_1);
                            response.setCode(200);
                            response.setEntity(new ByteArrayEntity(bytes,
                                    ContentType.create("text/plain",
                                            "ISO-8859-1")));
                        })
                .register("/charstatus/*",
                        (request, response, context) -> {
                            final String path = request.getRequestUri();
                            final int code = Integer.parseInt(
                                    path.substring(
                                            path.lastIndexOf('/') + 1));
                            final byte[] bytes = "caf\u00e9"
                                    .getBytes(StandardCharsets.ISO_8859_1);
                            response.setCode(code);
                            response.setEntity(new ByteArrayEntity(bytes,
                                    ContentType.create("text/plain",
                                            "ISO-8859-1")));
                        })
                .register("/status/*",
                        (request, response, context) -> {
                            final String path = request.getRequestUri();
                            final int code = Integer.parseInt(path.substring(
                                    path.lastIndexOf('/') + 1));
                            response.setCode(code);
                            response.setEntity(new StringEntity(
                                    "status:" + code,
                                    ContentType.TEXT_PLAIN));
                        })
                .create();
        server.start();
        port = server.getLocalPort();
        httpClient = HttpAsyncClients.createDefault();
        httpClient.start();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (httpClient != null) {
            httpClient.close();
        }
        if (server != null) {
            server.close();
        }
    }

    private <T> T proxy(final Class<T> iface) {
        return RestClientBuilder.newBuilder()
                .baseUri("http://localhost:" + port)
                .httpClient(httpClient)
                .build(iface);
    }

    // --- Tests ---

    @Test
    void testGetSingleWidget() {
        final WidgetApi api = proxy(WidgetApi.class);
        final String json = api.get(42);
        assertThat(json.contains("\"id\":42")).isTrue();
        assertThat(json.contains("\"name\":\"W-42\"")).isTrue();
    }

    @Test
    void testGetWidgetList() {
        final WidgetApi api = proxy(WidgetApi.class);
        final String json = api.list();
        assertThat(json.contains("\"name\":\"A\"")).isTrue();
        assertThat(json.contains("\"name\":\"B\"")).isTrue();
    }

    @Test
    void testPostWidget() {
        final WidgetApi api = proxy(WidgetApi.class);
        final String json = api.create("{\"id\":0,\"name\":\"New\"}");
        assertThat(json.contains("\"id\":99")).isTrue();
        assertThat(json.contains("\"name\":\"Created\"")).isTrue();
    }

    @Test
    void testPutWidget() {
        final WidgetApi api = proxy(WidgetApi.class);
        final String json = api.update(7, "{\"id\":0,\"name\":\"Up\"}");
        assertThat(json.contains("\"id\":7")).isTrue();
        assertThat(json.contains("\"name\":\"Updated\"")).isTrue();
    }

    @Test
    void testDeleteWidget() {
        final WidgetApi api = proxy(WidgetApi.class);
        api.delete(1);
    }

    @Test
    void testQueryParam() {
        final EchoApi api = proxy(EchoApi.class);
        assertThat(api.echo("hello")).isEqualTo("hello");
    }

    @Test
    void testMultiValueQueryParam() {
        final EchoApi api = proxy(EchoApi.class);
        final String result = api.echoMulti("alpha", "beta");
        assertThat(result.contains("tag=alpha")).isTrue();
        assertThat(result.contains("tag=beta")).isTrue();
    }

    @Test
    void testHeaderParam() {
        final EchoApi api = proxy(EchoApi.class);
        assertThat(api.echoHeader("myTag")).isEqualTo("myTag");
    }

    @Test
    void testErrorThrowsException() {
        final StatusApi api = proxy(StatusApi.class);
        final ResponseProcessingException ex = assertThatExceptionOfType(ResponseProcessingException.class)
                .isThrownBy(() ->
                        api.getStatus(404)).actual();
        assertThat(ex.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void testPathParamEncoding() {
        final EchoPathApi api = proxy(EchoPathApi.class);
        assertThat(api.echoPath("hello world")).isEqualTo("hello%20world");
        assertThat(api.echoPath("~user")).isEqualTo("~user");
    }

    @Test
    void testDefaultValue() {
        final DefaultsApi api = proxy(DefaultsApi.class);
        assertThat(api.withDefault(null)).isEqualTo("red");
        assertThat(api.withDefault("blue")).isEqualTo("blue");
    }

    @Test
    void testByteArrayReturn() {
        final BytesApi api = proxy(BytesApi.class);
        final byte[] data = api.getBytes();
        assertThat(new String(data,
                StandardCharsets.UTF_8)).isEqualTo("binary-data");
    }

    @Test
    void testProxyToString() {
        final EchoApi api = proxy(EchoApi.class);
        assertThat(api.toString().startsWith("RestProxy[")).isTrue();
    }

    @Test
    void testInferredContentTypeForStringBody() {
        final InspectApi api = proxy(InspectApi.class);
        final String ct = api.postString("hello");
        assertThat(ct.startsWith("text/plain")).as(ct).isTrue();
    }

    @Test
    void testInferredContentTypeForByteArrayBody() {
        final InspectApi api = proxy(InspectApi.class);
        final String ct = api.postBytes(new byte[]{1, 2, 3});
        assertThat(ct.startsWith("text/plain")).as(ct).isTrue();
    }

    @Test
    void testNoAcceptHeaderWithoutProduces() {
        final NoProduceApi api = proxy(NoProduceApi.class);
        assertThat(api.get()).isEqualTo("none");
    }

    @Test
    void testJsonPojoGet() {
        final JsonWidgetApi api = proxy(JsonWidgetApi.class);
        final Widget w = api.get(42);
        assertThat(w.id).isEqualTo(42);
        assertThat(w.name).isEqualTo("W-42");
    }

    @Test
    void testJsonPojoPostRoundTrip() {
        final JsonWidgetApi api = proxy(JsonWidgetApi.class);
        final Widget w = api.create(new Widget(0, "New"));
        assertThat(w.id).isEqualTo(0);
        assertThat(w.name).isEqualTo("New");
    }

    @Test
    void testJsonPojoErrorResponse() {
        final JsonErrorApi api = proxy(JsonErrorApi.class);
        final ResponseProcessingException ex = assertThatExceptionOfType(ResponseProcessingException.class)
                .isThrownBy(() ->
                        api.getError(500)).actual();
        assertThat(ex.getResponse().getStatus()).isEqualTo(500);
        assertThat(ex.getResponse().hasEntity()).isTrue();
    }

    @Disabled("Custom JSON content types for POJO bodies not supported by current core API")
    void testPojoBodyHonorsConsumesMediaType() {
        final InspectJsonApi api = proxy(InspectJsonApi.class);
        final String ct = api.postJson(new Widget(1, "test"));
        assertThat(ct.startsWith("application/vnd.api+json")).as(ct).isTrue();
    }

    @Test
    void testNonUtf8ErrorResponsePreservesBytes() {
        final CharStatusApi api = proxy(CharStatusApi.class);
        final ResponseProcessingException ex = assertThatExceptionOfType(ResponseProcessingException.class)
                .isThrownBy(() ->
                        api.getStatus(500)).actual();
        assertThat(ex.getResponse().getStatus()).isEqualTo(500);
        assertThat(ex.getResponse().readEntity(String.class)).contains("caf\u00e9");
    }

    // --- Validation tests ---

    @Test
    void testRejectsNonInterface() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                RestClientBuilder.newBuilder()
                        .baseUri("http://localhost")
                        .httpClient(httpClient)
                        .build(String.class));
    }

    @Test
    void testRequiresBaseUri() {
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() ->
                RestClientBuilder.newBuilder()
                        .httpClient(httpClient)
                        .build(EchoApi.class));
    }

    @Test
    void testRequiresHttpClient() {
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() ->
                RestClientBuilder.newBuilder()
                        .baseUri("http://localhost")
                        .build(EchoApi.class));
    }

    @Test
    void testRejectsMultipleBodyParams() {
        assertThatExceptionOfType(RestResourceException.class).isThrownBy(() ->
                RestClientBuilder.newBuilder()
                        .baseUri("http://localhost")
                        .httpClient(httpClient)
                        .build(BadMultiBodyApi.class));
    }

    @Test
    void testRejectsPathParamMismatch() {
        assertThatExceptionOfType(RestResourceException.class).isThrownBy(() ->
                RestClientBuilder.newBuilder()
                        .baseUri("http://localhost")
                        .httpClient(httpClient)
                        .build(BadPathParamApi.class));
    }

    @Test
    void testRejectsMissingPathParam() {
        assertThatExceptionOfType(RestResourceException.class).isThrownBy(() ->
                RestClientBuilder.newBuilder()
                        .baseUri("http://localhost")
                        .httpClient(httpClient)
                        .build(MissingPathParamApi.class));
    }

    @Test
    void testRejectsMultipleConsumesWithBody() {
        assertThatExceptionOfType(RestResourceException.class).isThrownBy(() ->
                RestClientBuilder.newBuilder()
                        .baseUri("http://localhost")
                        .httpClient(httpClient)
                        .build(MultiConsumesBodyApi.class));
    }

    @Test
    void testNullPathParamThrows() {
        final EchoPathApi api = proxy(EchoPathApi.class);
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> api.echoPath(null));
    }

    @Test
    void testQueryParamSpaceEncodedAsPercent20() {
        final EchoApi api = proxy(EchoApi.class);
        final String result = api.echo("hello world");
        assertThat(result).isEqualTo("hello%20world");
    }

    @Test
    void testQueryParamLiteralPlusEncodedAsPercent2B() {
        final EchoApi api = proxy(EchoApi.class);
        final String result = api.echo("a+b");
        assertThat(result).isEqualTo("a%2Bb");
    }

    @Test
    void testStringResponseRespectsEntityCharset() {
        final CharsetApi api = proxy(CharsetApi.class);
        assertThat(api.get()).isEqualTo("caf\u00e9");
    }

    @Test
    void testStringBodyEncodedWithConsumesCharset() {
        final EchoBodyApi api = proxy(EchoBodyApi.class);
        final byte[] result = api.post("caf\u00e9");
        assertThat(result).containsExactly("caf\u00e9".getBytes(StandardCharsets.ISO_8859_1));
    }

    @Test
    void testEnumQueryParamUsesName() {
        final EnumQueryApi api = proxy(EnumQueryApi.class);
        assertThat(api.echo(Color.GREEN)).isEqualTo("GREEN");
    }

}
