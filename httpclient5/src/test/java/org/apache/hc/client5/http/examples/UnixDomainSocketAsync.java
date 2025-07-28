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

import io.reactivex.rxjava3.core.Observable;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.reactive.ReactiveResponseConsumer;
import org.reactivestreams.Publisher;

import java.io.File;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class UnixDomainSocketAsync {
    public static void main(final String[] args) throws Exception {
        if (args.length == 0 || "-h".equals(args[0]) || "--help".equals(args[0])) {
            usage(System.out);
            return;
        } else if (args.length != 2) {
            usage(System.err);
            return;
        }

        final Path unixDomainSocket = new File(args[0]).toPath();
        final String uri = args[1];

        final RequestConfig requestConfig = RequestConfig.custom().setUnixDomainSocket(unixDomainSocket).build();
        try (CloseableHttpAsyncClient client = HttpAsyncClientBuilder.create()
                .setDefaultRequestConfig(requestConfig)
                .build()) {
            client.start();

            final SimpleHttpRequest httpGet = SimpleHttpRequest.create(Method.GET.name(), uri);
            httpGet.setConfig(requestConfig);

            final ReactiveResponseConsumer consumer = new ReactiveResponseConsumer();
            client.execute(SimpleRequestProducer.create(httpGet), consumer, null).get(10, TimeUnit.SECONDS);
            final Message<HttpResponse, Publisher<ByteBuffer>> message = consumer.getResponseFuture()
                .get(10, TimeUnit.SECONDS);
            final List<ByteBuffer> bufs = Observable.fromPublisher(message.getBody())
                .collectInto(new ArrayList<ByteBuffer>(), List::add)
                .blockingGet();
            for (final ByteBuffer buf : bufs) {
                final byte[] bytes = new byte[buf.remaining()];
                buf.get(bytes);
                System.out.write(bytes);
            }
        }
    }

    private static void usage(final PrintStream printStream) {
        printStream.println("Usage: UnixDomainSocketAsync [path] [uri]");
        printStream.println();
        printStream.println("Examples:");
        printStream.println("UnixDomainSocketAsync /var/run/docker.sock 'http://localhost/info'");
        printStream.println("UnixDomainSocketAsync /var/run/docker.sock 'http://localhost/containers/json?all=1'");
    }
}
