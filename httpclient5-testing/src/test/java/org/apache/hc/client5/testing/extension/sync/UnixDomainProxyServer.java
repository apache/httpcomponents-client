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

package org.apache.hc.client5.testing.extension.sync;

import org.newsclub.net.unix.AFUNIXServerSocket;
import org.newsclub.net.unix.AFUNIXSocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.CompletableFuture.supplyAsync;

public final class UnixDomainProxyServer {
    private final int port;
    private final ExecutorService executorService;
    private final Path socketPath;
    private final CountDownLatch serverReady = new CountDownLatch(1);
    private final AtomicInteger requestsReceived = new AtomicInteger(0);
    private volatile AFUNIXServerSocket serverSocket;

    public UnixDomainProxyServer(final int port) {
        this.port = port;
        this.executorService = Executors.newCachedThreadPool();
        this.socketPath = createTempSocketPath();
    }

    public void start() {
        executorService.submit(this::runUdsProxy);
        try {
            serverReady.await();
        } catch (final InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    public Path getSocketPath() {
        return socketPath;
    }

    public int getRequestsReceived() {
        return requestsReceived.get();
    }

    public void close() {
        try {
            serverSocket.close();
            executorService.shutdownNow();
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new RuntimeException("Failed to shut down");
            }
            Files.deleteIfExists(socketPath);
        } catch (final InterruptedException | IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void runUdsProxy() {
        try {
            Files.deleteIfExists(socketPath);
        } catch (final IOException ignore) {
        }

        try {
            try (final AFUNIXServerSocket server = AFUNIXServerSocket.bindOn(socketPath, true)) {
                this.serverSocket = server;
                serverReady.countDown();
                serveRequests(server);
            } catch (final Throwable ignore) {
            }
        } catch (final Throwable t) {
            serverReady.countDown();
            throw t;
        }
    }

    private void serveRequests(final AFUNIXServerSocket server) throws IOException {
        while (true) {
            final AFUNIXSocket udsClient = server.accept();
            requestsReceived.incrementAndGet();
            final Socket tcpSocket = new Socket("localhost", port);
            final CompletableFuture<Void> f1 = supplyAsync(() -> pipe(udsClient, tcpSocket), executorService);
            final CompletableFuture<Void> f2 = supplyAsync(() -> pipe(tcpSocket, udsClient), executorService);
            CompletableFuture.allOf(f1, f2).whenComplete((result, ex) -> {
                try {
                    udsClient.close();
                    tcpSocket.close();
                } catch (final IOException ignore) {
                }
            });
        }
    }

    private static Path createTempSocketPath() {
        try {
            final Path tempFile = Files.createTempFile("uds", ".sock");
            Files.deleteIfExists(tempFile);
            return tempFile;
        } catch (final IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private Void pipe(final Socket inputSocket, final Socket outputSocket) {
        try (
            final InputStream in = inputSocket.getInputStream();
            final OutputStream out = outputSocket.getOutputStream()
        ) {
            final byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
                out.flush();
            }
        } catch (final IOException ignore) {
        }
        return null;
    }
}
