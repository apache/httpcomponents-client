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

package org.apache.hc.client5.testing.sync.compress;

import java.util.Arrays;
import java.util.List;

import org.apache.hc.client5.http.entity.CompressorFactory;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.testing.classic.ClassicTestServer;


/**
 * Demonstrates handling of HTTP responses with content compression using Apache HttpClient.
 * <p>
 * This example sets up a local test server that simulates compressed HTTP responses. It then
 * creates a custom HttpClient configured to handle compression. The client makes a request to
 * the test server, receives a compressed response, and decompresses the content to verify the
 * process.
 * <p>
 * The main focus of this example is to illustrate the use of a custom HttpClient that can
 * handle compressed HTTP responses transparently, simulating a real-world scenario where
 * responses from a server might be compressed to reduce bandwidth usage.
 */
public class CompressedResponseHandlingExample {

    public static void main(final String[] args) {

        final ClassicTestServer server = new ClassicTestServer();
        try {
            server.register("/compressed", (request, response, context) -> {
                final String uncompressedContent = "This is the uncompressed response content";
                response.setEntity(compress(uncompressedContent, "gzip"));
                response.addHeader(HttpHeaders.CONTENT_ENCODING, "gzip");
            });

            server.start();

            final HttpHost target = new HttpHost("localhost", server.getPort());

            final List<String> encodingList = Arrays.asList("gz", "deflate");

            try (final CloseableHttpClient httpclient = HttpClients
                    .custom()
                    .setEncodings(encodingList)
                    .build()) {
                final ClassicHttpRequest httpGet = ClassicRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath("/compressed")
                        .build();

                System.out.println("Executing request " + httpGet.getMethod() + " " + httpGet.getUri());
                httpclient.execute(httpGet, response -> {
                    System.out.println("----------------------------------------");
                    System.out.println(httpGet + "->" + response.getCode() + " " + response.getReasonPhrase());

                    final HttpEntity responseEntity = response.getEntity();
                    final String responseBody = EntityUtils.toString(responseEntity);
                    System.out.println("Response content: " + responseBody);

                    return null;
                });
            }

        } catch (final Exception e) {
            e.printStackTrace();
        } finally {
            server.shutdown(CloseMode.GRACEFUL);
        }
    }


    private static HttpEntity compress(final String data, final String name) {
        final StringEntity originalEntity = new StringEntity(data, ContentType.TEXT_PLAIN);
        return CompressorFactory.INSTANCE.compressEntity(originalEntity, name);
    }

}
