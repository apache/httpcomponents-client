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

package org.apache.hc.client5.http.examples;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecChainHandler;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.ChainElement;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * This example demonstrates how to insert custom request interceptor and an execution interceptor
 * to the request execution chain.
 */
public class ClientInterceptors {

    public static void main(final String[] args) throws Exception {
        try (final CloseableHttpClient httpclient = HttpClients.custom()

                // Add a simple request ID to each outgoing request

                .addRequestInterceptorFirst(new HttpRequestInterceptor() {

                    private final AtomicLong count = new AtomicLong(0);

                    @Override
                    public void process(
                            final HttpRequest request,
                            final EntityDetails entity,
                            final HttpContext context) throws HttpException, IOException {
                        request.setHeader("request-id", Long.toString(count.incrementAndGet()));
                    }
                })

                // Simulate a 404 response for some requests without passing the message down to the backend

                .addExecInterceptorAfter(ChainElement.PROTOCOL.name(), "custom", new ExecChainHandler() {

                    @Override
                    public ClassicHttpResponse execute(
                            final ClassicHttpRequest request,
                            final ExecChain.Scope scope,
                            final ExecChain chain) throws IOException, HttpException {

                        final Header idHeader = request.getFirstHeader("request-id");
                        if (idHeader != null && "13".equalsIgnoreCase(idHeader.getValue())) {
                            final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_NOT_FOUND, "Oppsie");
                            response.setEntity(new StringEntity("bad luck", ContentType.TEXT_PLAIN));
                            return response;
                        } else {
                            return chain.proceed(request, scope);
                        }
                    }

                })
                .build()) {

            for (int i = 0; i < 20; i++) {
                final HttpGet httpget = new HttpGet("http://httpbin.org/get");

                System.out.println("Executing request " + httpget.getMethod() + " " + httpget.getUri());

                try (final CloseableHttpResponse response = httpclient.execute(httpget)) {
                    System.out.println("----------------------------------------");
                    System.out.println(response.getCode() + " " + response.getReasonPhrase());
                    System.out.println(EntityUtils.toString(response.getEntity()));
                }
            }
        }
    }

}

