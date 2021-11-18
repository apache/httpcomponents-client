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
package org.apache.hc.client5.testing.sync;

import java.io.IOException;
import java.net.Socket;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.impl.io.DefaultBHttpServerConnection;
import org.apache.hc.core5.http.io.HttpConnectionFactory;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestMalformedServerResponse {

    static class BrokenServerConnection extends DefaultBHttpServerConnection {

        public BrokenServerConnection(final Http1Config h1Config) {
            super(null, h1Config);
        }

        @Override
        public void sendResponseHeader(final ClassicHttpResponse response) throws HttpException, IOException {
            super.sendResponseHeader(response);
            if (response.getCode() == HttpStatus.SC_NO_CONTENT) {
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
            final BrokenServerConnection conn = new BrokenServerConnection(Http1Config.DEFAULT);
            conn.bind(socket);
            return conn;
        }
    }

    @Test
    public void testNoContentResponseWithGarbage() throws Exception {
        try (final HttpServer server = ServerBootstrap.bootstrap()
                .setConnectionFactory(new BrokenServerConnectionFactory())
                .register("/nostuff", (request, response, context) -> response.setCode(HttpStatus.SC_NO_CONTENT))
                .register("/stuff", (request, response, context) -> {
                    response.setCode(HttpStatus.SC_OK);
                    response.setEntity(new StringEntity("Some important stuff"));
                })
                .create()) {
            server.start();
            final HttpHost target = new HttpHost("localhost", server.getLocalPort());
            try (final CloseableHttpClient httpclient = HttpClientBuilder.create().build()) {
                final HttpGet get1 = new HttpGet("/nostuff");
                httpclient.execute(target, get1, response -> {
                    Assertions.assertEquals(HttpStatus.SC_NO_CONTENT, response.getCode());
                    EntityUtils.consume(response.getEntity());
                    return null;
                });
                final HttpGet get2 = new HttpGet("/stuff");
                httpclient.execute(target, get2, response -> {
                    Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
                    EntityUtils.consume(response.getEntity());
                    return null;
                });
            }
        }
    }

}
