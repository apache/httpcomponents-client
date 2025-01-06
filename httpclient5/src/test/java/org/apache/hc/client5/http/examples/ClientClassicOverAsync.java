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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.annotation.Experimental;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.util.Timeout;

/**
 * This example demonstrates create an {@link CloseableHttpClient} adaptor over
 * {@link CloseableHttpAsyncClient} providing compatibility with the classic APIs
 * based on the {@link java.io.InputStream} / {@link java.io.OutputStream} model
 */
@Experimental
public class ClientClassicOverAsync {

    public static void main(final String[] args) throws Exception {
        try (final CloseableHttpClient httpclient = HttpAsyncClients.classic(
                HttpAsyncClients.createDefault(),
                Timeout.ofMinutes(1))) {
            final HttpGet httpget = new HttpGet("http://httpbin.org/get");
            System.out.println("Executing request " + httpget.getMethod() + " " + httpget.getUri());
            httpclient.execute(httpget, response -> {
                System.out.println("----------------------------------------");
                System.out.println(httpget + "->" + new StatusLine(response));
                final HttpEntity entity = response.getEntity();
                if (entity != null) {
                    final ContentType contentType = ContentType.parseLenient(entity.getContentType());
                    final Charset charset = ContentType.getCharset(contentType, StandardCharsets.UTF_8);
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(entity.getContent(), charset))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            System.out.println(line);
                        }
                    }
                }
                return null;
            });
        }
    }

}

