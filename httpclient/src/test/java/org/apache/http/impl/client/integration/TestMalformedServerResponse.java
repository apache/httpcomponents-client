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
package org.apache.http.impl.client.integration;

import java.io.IOException;
import java.net.Socket;

import org.apache.http.HttpConnectionFactory;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.DefaultBHttpServerConnection;
import org.apache.http.localserver.LocalServerTestBase;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.junit.Test;

public class TestMalformedServerResponse extends LocalServerTestBase {

    static class BrokenServerConnection extends DefaultBHttpServerConnection {

        public BrokenServerConnection(final int buffersize) {
            super(buffersize);
        }

        @Override
        public void sendResponseHeader(final HttpResponse response) throws HttpException, IOException {
            super.sendResponseHeader(response);
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NO_CONTENT) {
                response.setEntity(new StringEntity(
                        "garbage\ngarbage\n" +
                        "garbage\ngarbage\n" +
                        "garbage\ngarbage\n" +
                        "garbage\ngarbage\n" +
                        "garbage\ngarbage\n" +
                        "garbage\ngarbage\n" +
                        "garbage\ngarbage\n" +
                        "garbage\ngarbage\n" +
                        "garbage\ngarbage\n"));
                sendResponseEntity(response);
            }
        }
    }

    static class BrokenServerConnectionFactory implements HttpConnectionFactory<DefaultBHttpServerConnection> {

        @Override
        public DefaultBHttpServerConnection createConnection(final Socket socket) throws IOException {
            final BrokenServerConnection conn = new BrokenServerConnection(4096);
            conn.bind(socket);
            return conn;
        }
    }

    @Test
    public void testNoContentResponseWithGarbage() throws Exception {
        this.serverBootstrap.setConnectionFactory(new BrokenServerConnectionFactory());
        this.serverBootstrap.registerHandler("/nostuff", new HttpRequestHandler() {

            @Override
            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                response.setStatusCode(HttpStatus.SC_NO_CONTENT);
            }

        });
        this.serverBootstrap.registerHandler("/stuff", new HttpRequestHandler() {

            @Override
            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                response.setStatusCode(HttpStatus.SC_OK);
                response.setEntity(new StringEntity("Some important stuff"));
            }

        });

        final HttpHost target = start();
        final HttpGet get1 = new HttpGet("/nostuff");
        final CloseableHttpResponse response1 = this.httpclient.execute(target, get1);
        try {
            Assert.assertEquals(HttpStatus.SC_NO_CONTENT, response1.getStatusLine().getStatusCode());
            EntityUtils.consume(response1.getEntity());
        } finally {
            response1.close();
        }
        final HttpGet get2 = new HttpGet("/stuff");
        final CloseableHttpResponse response2 = this.httpclient.execute(target, get2);
        try {
            Assert.assertEquals(HttpStatus.SC_OK, response2.getStatusLine().getStatusCode());
            EntityUtils.consume(response2.getEntity());
        } finally {
            response2.close();
        }
    }

}
