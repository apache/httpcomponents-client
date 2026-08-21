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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.entity.compress.BasicCompressionDictionaryStore;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;

/**
 * Async client example for RFC 9842 Compression Dictionary Transport.
 * <p>
 * The example uses {@code canicompress.com}, which publishes a new JavaScript
 * bundle every minute. The previous bundle is used as a compression dictionary
 * for the current bundle.
 * <p>
 * The client:
 * <ol>
 *   <li>Fetches the two most recent bundle URLs from the manifest.</li>
 *   <li>Fetches the previous bundle and stores it as a compression dictionary.</li>
 *   <li>Fetches the current bundle using the negotiated dictionary.</li>
 *   <li>Transparently decompresses the {@code dcz} response.</li>
 * </ol>
 */
public final class AsyncClientCompressionDictionary {

    private static final String ORIGIN = "https://canicompress.com";
    private static final URI MANIFEST_URI = URI.create(ORIGIN + "/manifest.json");

    public static void main(final String[] args) throws Exception {
        final BasicCompressionDictionaryStore dictionaryStore =
                new BasicCompressionDictionaryStore();

        try (final CloseableHttpAsyncClient client = HttpAsyncClients.custom()
                .setCompressionDictionaryStore(dictionaryStore)
                .build()) {

            client.start();

            final Message<HttpResponse, byte[]> manifestResponse =
                    execute(client, MANIFEST_URI);

            final String manifest = new String(
                    manifestResponse.getBody(),
                    StandardCharsets.UTF_8);

            final List<String> paths = extractDeployPaths(manifest);
            if (paths.size() < 2) {
                throw new IllegalStateException(
                        "Unable to find two recent deploys in manifest");
            }

            final long cacheBuster = System.currentTimeMillis();

            final URI dictionaryUri = URI.create(
                    ORIGIN + paths.get(1) + "?t=" + cacheBuster + "a");

            final URI resourceUri = URI.create(
                    ORIGIN + paths.get(0) + "?t=" + cacheBuster + "b");

            System.out.println("Dictionary:");
            System.out.println("  " + dictionaryUri);

            System.out.println();
            System.out.println("Resource:");
            System.out.println("  " + resourceUri);

            System.out.println();
            System.out.println("Fetching dictionary...");

            final Message<HttpResponse, byte[]> dictionaryResponse =
                    execute(client, dictionaryUri);

            final HttpResponse dictionaryHead = dictionaryResponse.getHead();

            System.out.println("Status            : " + dictionaryHead.getCode());

            final Header useAsDictionary =
                    dictionaryHead.getFirstHeader("Use-As-Dictionary");

            System.out.println("Use-As-Dictionary : "
                    + (useAsDictionary != null
                    ? useAsDictionary.getValue()
                    : "(none)"));

            System.out.println("Dictionary bytes  : "
                    + (dictionaryResponse.getBody() != null
                    ? dictionaryResponse.getBody().length
                    : 0));

            System.out.println("Stored dictionaries: "
                    + dictionaryStore.getByOrigin(dictionaryUri).size());

            System.out.println();
            System.out.println("Fetching current resource...");

            final Message<HttpResponse, byte[]> resourceResponse =
                    execute(client, resourceUri);

            final HttpResponse resourceHead = resourceResponse.getHead();

            System.out.println("Status            : " + resourceHead.getCode());

            final Header contentEncoding =
                    resourceHead.getFirstHeader(HttpHeaders.CONTENT_ENCODING);

            System.out.println("Content-Encoding  : "
                    + (contentEncoding != null
                    ? contentEncoding.getValue()
                    : "(none)"));

            final byte[] body = resourceResponse.getBody() != null
                    ? resourceResponse.getBody()
                    : new byte[0];

            System.out.println("Decoded bytes     : " + body.length);

            final String text = new String(body, StandardCharsets.UTF_8);

            System.out.println("Response prefix   : "
                    + text.substring(0, Math.min(text.length(), 120))
                    .replace('\n', ' '));
        }
    }

    private static Message<HttpResponse, byte[]> execute(
            final CloseableHttpAsyncClient client,
            final URI uri) throws Exception {

        final SimpleHttpRequest request = SimpleRequestBuilder.get(uri)
                .build();

        final Future<Message<HttpResponse, byte[]>> future = client.execute(
                new BasicRequestProducer(request, null),
                new BasicResponseConsumer<>(new BasicAsyncEntityConsumer()),
                null);

        return future.get();
    }

    private static List<String> extractDeployPaths(final String manifest) {
        final List<String> paths = new ArrayList<>();

        int offset = manifest.indexOf("\"recent_deploys\"");
        if (offset < 0) {
            return paths;
        }

        while (true) {
            final int name = manifest.indexOf("\"path\"", offset);
            if (name < 0) {
                break;
            }

            final int colon = manifest.indexOf(':', name);
            if (colon < 0) {
                break;
            }

            final int start = manifest.indexOf('"', colon + 1);
            if (start < 0) {
                break;
            }

            final int end = manifest.indexOf('"', start + 1);
            if (end < 0) {
                break;
            }

            final String path = manifest.substring(start + 1, end);
            if (!paths.contains(path)) {
                paths.add(path);
            }

            offset = end + 1;
        }

        return paths;
    }
}