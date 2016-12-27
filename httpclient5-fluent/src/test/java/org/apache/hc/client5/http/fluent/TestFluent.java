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
package org.apache.hc.client5.http.fluent;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;

import org.apache.hc.client5.http.localserver.LocalServerTestBase;
import org.apache.hc.client5.http.protocol.ClientProtocolException;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.ResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestFluent extends LocalServerTestBase {

    @Before @Override
    public void setUp() throws Exception {
        super.setUp();
        this.serverBootstrap.registerHandler("/", new HttpRequestHandler() {

            @Override
            public void handle(
                    final ClassicHttpRequest request,
                    final ClassicHttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                response.setEntity(new StringEntity("All is well", ContentType.TEXT_PLAIN));
            }

        });
        this.serverBootstrap.registerHandler("/echo", new HttpRequestHandler() {

            @Override
            public void handle(
                    final ClassicHttpRequest request,
                    final ClassicHttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                HttpEntity responseEntity = null;
                final HttpEntity requestEntity = request.getEntity();
                if (requestEntity != null) {
                    final ContentType contentType = EntityUtils.getContentTypeOrDefault(requestEntity);
                    if (ContentType.TEXT_PLAIN.getMimeType().equals(contentType.getMimeType())) {
                        responseEntity = new StringEntity(
                                EntityUtils.toString(requestEntity), ContentType.TEXT_PLAIN);
                    }
                }
                if (responseEntity == null) {
                    responseEntity = new StringEntity("echo", ContentType.TEXT_PLAIN);
                }
                response.setEntity(responseEntity);
            }

        });
    }

    @After @Override
    public void shutDown() throws Exception {
        Executor.closeIdleConnections();
        super.shutDown();
    }

    @Test
    public void testGetRequest() throws Exception {
        final HttpHost target = start();
        final String baseURL = "http://localhost:" + target.getPort();
        final String message = Request.Get(baseURL + "/").execute().returnContent().asString();
        Assert.assertEquals("All is well", message);
    }

    @Test
    public void testGetRequestByName() throws Exception {
        final HttpHost target = start();
        final String baseURL = "http://localhost:" + target.getPort();
        final String message = Request.create("GET", baseURL + "/").execute().returnContent().asString();
        Assert.assertEquals("All is well", message);
    }

    @Test
    public void testGetRequestByNameWithURI() throws Exception {
        final HttpHost target = start();
        final String baseURL = "http://localhost:" + target.getPort();
        final String message = Request.create("GET", new URI(baseURL + "/")).execute().returnContent().asString();
        Assert.assertEquals("All is well", message);
    }

    @Test(expected = ClientProtocolException.class)
    public void testGetRequestFailure() throws Exception {
        final HttpHost target = start();
        final String baseURL = "http://localhost:" + target.getPort();
        Request.Get(baseURL + "/boom").execute().returnContent().asString();
    }

    @Test
    public void testPostRequest() throws Exception {
        final HttpHost target = start();
        final String baseURL = "http://localhost:" + target.getPort();
        final String message1 = Request.Post(baseURL + "/echo")
                .bodyString("what is up?", ContentType.TEXT_PLAIN)
                .execute().returnContent().asString();
        Assert.assertEquals("what is up?", message1);
        final String message2 = Request.Post(baseURL + "/echo")
                .bodyByteArray(new byte[]{1, 2, 3}, ContentType.APPLICATION_OCTET_STREAM)
                .execute().returnContent().asString();
        Assert.assertEquals("echo", message2);
    }

    @Test
    public void testContentAsStringWithCharset() throws Exception {
        final HttpHost target = start();
        final String baseURL = "http://localhost:" + target.getPort();
        final Content content = Request.Post(baseURL + "/echo").bodyByteArray("Ü".getBytes("utf-8")).execute()
                .returnContent();
        Assert.assertEquals((byte)-61, content.asBytes()[0]);
        Assert.assertEquals((byte)-100, content.asBytes()[1]);
        Assert.assertEquals("Ü", content.asString(Charset.forName("utf-8")));
    }

    @Test
    public void testConnectionRelease() throws Exception {
        final HttpHost target = start();
        final String baseURL = "http://localhost:" + target.getPort();
        for (int i = 0; i < 20; i++) {
            Request.Get(baseURL + "/").execute().returnContent();
            Request.Get(baseURL + "/").execute().returnResponse();
            Request.Get(baseURL + "/").execute().discardContent();
            Request.Get(baseURL + "/").execute().handleResponse(new ResponseHandler<Object>() {

                @Override
                public Object handleResponse(
                        final ClassicHttpResponse response) throws IOException {
                    return null;
                }

            });
            final File tmpFile = File.createTempFile("test", ".bin");
            try {
                Request.Get(baseURL + "/").execute().saveContent(tmpFile);
            } finally {
                tmpFile.delete();
            }
        }
    }

}
