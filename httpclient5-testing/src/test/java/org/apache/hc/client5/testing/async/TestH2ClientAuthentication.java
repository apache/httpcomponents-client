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
package org.apache.hc.client5.testing.async;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.client5.http.impl.auth.CredentialsProviderBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.testing.BasicTestAuthenticator;
import org.apache.hc.client5.testing.auth.BasicAuthenticationHandler;
import org.apache.hc.client5.testing.extension.async.ClientProtocolLevel;
import org.apache.hc.client5.testing.extension.async.ServerProtocolLevel;
import org.apache.hc.client5.testing.extension.async.TestAsyncClient;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.ResponseChannel;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityProducer;
import org.apache.hc.core5.http.nio.entity.FileEntityProducer;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.http2.H2StreamResetException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

abstract class TestH2ClientAuthentication extends AbstractHttpAsyncClientAuthenticationTest {

    public TestH2ClientAuthentication(final URIScheme scheme) {
        super(scheme, ClientProtocolLevel.H2_ONLY, ServerProtocolLevel.H2_ONLY);
    }

    @Test
    void testBasicAuthenticationReplayWithLargeFile(@TempDir final Path tempDir) throws Exception {
        final byte[] expected = new byte[2_000_000];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = (byte) (i * 31 + 7);
        }
        final Path source = tempDir.resolve("request.bin");
        Files.write(source, expected);

        configureServerWithBasicAuth(bootstrap -> bootstrap.register("*", AsyncEchoHandler::new));
        final HttpHost target = startServer();

        final TestAsyncClient client = startClient();
        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(CredentialsProviderBuilder.create()
                .add(target, "test", "test".toCharArray())
                .build());

        final Future<Message<HttpResponse, byte[]>> future = client.execute(
                new BasicRequestProducer(Method.PUT, target, "/", new FileEntityProducer(source.toFile())),
                new BasicResponseConsumer<>(new BasicAsyncEntityConsumer()), context, null);
        final Message<HttpResponse, byte[]> response = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());

        Assertions.assertEquals(HttpStatus.SC_OK, response.getHead().getCode());
        Assertions.assertArrayEquals(expected, response.getBody());
    }

    @Test
    void testBasicAuthenticationReplayAfterNoErrorReset() throws Exception {
        final byte[] expected = new byte[2_000_000];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = (byte) (i * 31 + 7);
        }

        final AtomicBoolean resetSent = new AtomicBoolean();
        configureServer(bootstrap -> bootstrap.register("*",
                () -> new ResettingBasicAuthHandler(new AsyncEchoHandler(), resetSent)));
        final HttpHost target = startServer();

        final TestAsyncClient client = startClient();
        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(CredentialsProviderBuilder.create()
                .add(target, "test", "test".toCharArray())
                .build());

        final Future<Message<HttpResponse, byte[]>> future = client.execute(
                new BasicRequestProducer(Method.PUT, target, "/", new BasicAsyncEntityProducer(expected)),
                new BasicResponseConsumer<>(new BasicAsyncEntityConsumer()), context, null);
        final Message<HttpResponse, byte[]> response = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());

        Assertions.assertTrue(resetSent.get());
        Assertions.assertEquals(HttpStatus.SC_OK, response.getHead().getCode());
        Assertions.assertArrayEquals(expected, response.getBody());
    }

    private static final class ResettingBasicAuthHandler implements AsyncServerExchangeHandler {

        private final AsyncServerExchangeHandler exchangeHandler;
        private final AtomicBoolean resetSent;
        private final BasicAuthenticationHandler authenticationHandler;
        private final BasicTestAuthenticator authenticator;
        private boolean authenticated;

        ResettingBasicAuthHandler(final AsyncServerExchangeHandler exchangeHandler, final AtomicBoolean resetSent) {
            this.exchangeHandler = exchangeHandler;
            this.resetSent = resetSent;
            this.authenticationHandler = new BasicAuthenticationHandler();
            this.authenticator = new BasicTestAuthenticator("test:test", "test realm");
        }

        @Override
        public void handleRequest(
                final HttpRequest request,
                final EntityDetails entityDetails,
                final ResponseChannel responseChannel,
                final HttpContext context) throws HttpException, IOException {
            final Header authorization = request.getFirstHeader(HttpHeaders.AUTHORIZATION);
            final String credentials = authorization != null
                    ? authenticationHandler.extractAuthToken(authorization.getValue())
                    : null;
            authenticated = authenticator.authenticate(request.getAuthority(), request.getRequestUri(), credentials);
            if (authenticated) {
                exchangeHandler.handleRequest(request, entityDetails, responseChannel, context);
            } else {
                final HttpResponse unauthorized = new BasicHttpResponse(HttpStatus.SC_UNAUTHORIZED);
                unauthorized.addHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"test realm\"");
                responseChannel.sendResponse(unauthorized, null, context);
            }
        }

        @Override
        public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
            if (authenticated) {
                exchangeHandler.updateCapacity(capacityChannel);
            } else {
                capacityChannel.update(Integer.MAX_VALUE);
            }
        }

        @Override
        public void consume(final ByteBuffer src) throws IOException {
            if (authenticated) {
                exchangeHandler.consume(src);
            } else {
                resetSent.set(true);
                throw new H2StreamResetException(H2Error.NO_ERROR, "Stop upload after authentication challenge");
            }
        }

        @Override
        public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
            if (authenticated) {
                exchangeHandler.streamEnd(trailers);
            }
        }

        @Override
        public int available() {
            return authenticated ? exchangeHandler.available() : 0;
        }

        @Override
        public void produce(final DataStreamChannel channel) throws IOException {
            if (authenticated) {
                exchangeHandler.produce(channel);
            }
        }

        @Override
        public void failed(final Exception cause) {
            exchangeHandler.failed(cause);
        }

        @Override
        public void releaseResources() {
            exchangeHandler.releaseResources();
        }

    }

}
