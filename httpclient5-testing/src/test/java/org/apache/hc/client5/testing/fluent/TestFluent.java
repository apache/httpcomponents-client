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
package org.apache.hc.client5.testing.fluent;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.client5.http.fluent.Content;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.client5.testing.sync.extension.TestClientResources;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.testing.classic.ClassicTestServer;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class TestFluent {

    public static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    @RegisterExtension
    private TestClientResources testResources = new TestClientResources(URIScheme.HTTP, TIMEOUT);

    public HttpHost targetHost() {
        return testResources.targetHost();
    }

    @BeforeEach
    public void setUp() throws Exception {
        final ClassicTestServer server = testResources.startServer(null, null, null);
        server.registerHandler("/", (request, response, context) ->
                response.setEntity(new StringEntity("All is well", ContentType.TEXT_PLAIN)));
        server.registerHandler("/echo", (request, response, context) -> {
            HttpEntity responseEntity = null;
            final HttpEntity requestEntity = request.getEntity();
            if (requestEntity != null) {
                final String contentTypeStr = requestEntity.getContentType();
                final ContentType contentType = contentTypeStr == null ? ContentType.DEFAULT_TEXT : ContentType.parse(contentTypeStr);
                if (ContentType.TEXT_PLAIN.getMimeType().equals(contentType.getMimeType())) {
                    responseEntity = new StringEntity(
                            EntityUtils.toString(requestEntity), ContentType.TEXT_PLAIN);
                }
            }
            if (responseEntity == null) {
                responseEntity = new StringEntity("echo", ContentType.TEXT_PLAIN);
            }
            response.setEntity(responseEntity);
        });

        // Handler for large content large message
        server.registerHandler("/large-message", (request, response, context) -> {
            final String largeContent = generateLargeString(10000); // Large content string
            response.setEntity(new StringEntity(largeContent, ContentType.TEXT_PLAIN));
        });

        // Handler for large content large message with error
        server.registerHandler("/large-message-error", (request, response, context) -> {
            final String largeContent = generateLargeString(10000); // Large content string
            response.setCode(HttpStatus.SC_REDIRECTION);
            response.setEntity(new StringEntity(largeContent, ContentType.TEXT_PLAIN));
        });

    }

    @Test
    public void testGetRequest() throws Exception {
        final HttpHost target = targetHost();
        final String baseURL = "http://localhost:" + target.getPort();
        final String message = Request.get(baseURL + "/").execute().returnContent().asString();
        Assertions.assertEquals("All is well", message);
    }

    @Test
    public void testGetRequestByName() throws Exception {
        final HttpHost target = targetHost();
        final String baseURL = "http://localhost:" + target.getPort();
        final String message = Request.create("GET", baseURL + "/").execute().returnContent().asString();
        Assertions.assertEquals("All is well", message);
    }

    @Test
    public void testGetRequestByNameWithURI() throws Exception {
        final HttpHost target = targetHost();
        final String baseURL = "http://localhost:" + target.getPort();
        final String message = Request.create("GET", new URI(baseURL + "/")).execute().returnContent().asString();
        Assertions.assertEquals("All is well", message);
    }

    @Test
    public void testGetRequestFailure() throws Exception {
        final HttpHost target = targetHost();
        final String baseURL = "http://localhost:" + target.getPort();
        Assertions.assertThrows(ClientProtocolException.class, () ->
                Request.get(baseURL + "/boom").execute().returnContent().asString());
    }

    @Test
    public void testPostRequest() throws Exception {
        final HttpHost target = targetHost();
        final String baseURL = "http://localhost:" + target.getPort();
        final String message1 = Request.post(baseURL + "/echo")
                .bodyString("what is up?", ContentType.TEXT_PLAIN)
                .execute().returnContent().asString();
        Assertions.assertEquals("what is up?", message1);
        final String message2 = Request.post(baseURL + "/echo")
                .bodyByteArray(new byte[]{1, 2, 3}, ContentType.APPLICATION_OCTET_STREAM)
                .execute().returnContent().asString();
        Assertions.assertEquals("echo", message2);
    }

    @Test
    public void testContentAsStringWithCharset() throws Exception {
        final HttpHost target = targetHost();
        final String baseURL = "http://localhost:" + target.getPort();
        final Content content = Request.post(baseURL + "/echo").bodyByteArray("Ü".getBytes(StandardCharsets.UTF_8)).execute()
                .returnContent();
        Assertions.assertEquals((byte)-61, content.asBytes()[0]);
        Assertions.assertEquals((byte)-100, content.asBytes()[1]);
        Assertions.assertEquals("Ü", content.asString(StandardCharsets.UTF_8));
    }

    @Test
    public void testConnectionRelease() throws Exception {
        final HttpHost target = targetHost();
        final String baseURL = "http://localhost:" + target.getPort();
        for (int i = 0; i < 20; i++) {
            Request.get(baseURL + "/").execute().returnContent();
            Request.get(baseURL + "/").execute().returnResponse();
            Request.get(baseURL + "/").execute().discardContent();
            Request.get(baseURL + "/").execute().handleResponse(response -> null);
            final File tmpFile = File.createTempFile("test", ".bin");
            try {
                Request.get(baseURL + "/").execute().saveContent(tmpFile);
            } finally {
                tmpFile.delete();
            }
        }
    }

    private String generateLargeString(final int size) {
        final StringBuilder sb = new StringBuilder(size);
        for (int i = 0; i < size; i++) {
            sb.append("x");
        }
        return sb.toString();
    }

    @Test
    public void testLargeResponse() throws Exception {

        final HttpHost target = targetHost();
        final String baseURL = "http://localhost:" + target.getPort();

        final Content content = Request.get(baseURL + "/large-message").execute().returnContent();
        Assertions.assertEquals(10000, content.asBytes().length);
    }

    @Test
    public void testLargeResponseError() throws Exception {
        final HttpHost target = targetHost();
        final String baseURL = "http://localhost:" + target.getPort();

        try {
            Request.get(baseURL + "/large-message-error").execute().returnContent();
            Assertions.fail("Expected an HttpResponseException to be thrown");
        } catch (final HttpResponseException e) {
            // Check if the content of the exception is less than or equal to 256 bytes
            final byte[] contentBytes = e.getContentBytes();
            Assertions.assertNotNull(contentBytes, "Content bytes should not be null");
            Assertions.assertTrue(contentBytes.length <= 256, "Content length should be less or equal to 256 bytes");
        }
    }

}
