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

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

/**
 * Example demonstrating how to communicate with Docker Desktop on Windows
 * via Windows Named Pipes ({@code npipe://}).
 * <p>
 * Docker Desktop on Windows exposes its API through the named pipe
 * {@code \\.\pipe\docker_engine}. This example connects to that pipe
 * and sends an HTTP request, just like the Unix domain socket example
 * connects to {@code /var/run/docker.sock} on Linux/macOS.
 * </p>
 * <p>
 * The named pipe path is opened using {@link java.io.RandomAccessFile},
 * which internally maps to Win32 {@code CreateFile}/{@code ReadFile}/{@code WriteFile} —
 * no JNA/JNR/JNI is needed for the client side.
 * </p>
 *
 * @since 5.7
 */
public class WindowsNamedPipe {

    public static void main(final String[] args) throws IOException {
        if (args.length == 0 || "-h".equals(args[0]) || "--help".equals(args[0])) {
            usage(System.out);
            return;
        } else if (args.length != 2) {
            usage(System.err);
            return;
        }

        final String namedPipe = args[0];
        final String uri = args[1];

        try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
            final HttpGet httpGet = new HttpGet(uri);
            httpGet.setConfig(RequestConfig.custom().setNamedPipe(namedPipe).build());
            client.execute(httpGet, classicHttpResponse -> {
                final InputStream inputStream = classicHttpResponse.getEntity().getContent();
                final byte[] buf = new byte[8192];
                int len;
                while ((len = inputStream.read(buf)) > 0) {
                    System.out.write(buf, 0, len);
                }
                return null;
            });
        }
    }

    private static void usage(final PrintStream printStream) {
        printStream.println("Usage: WindowsNamedPipe [pipe-path] [uri]");
        printStream.println();
        printStream.println("Examples:");
        printStream.println("WindowsNamedPipe \\\\.\\pipe\\docker_engine 'http://localhost/info'");
        printStream.println("WindowsNamedPipe \\\\.\\pipe\\docker_engine 'http://localhost/containers/json?all=1'");
    }
}
