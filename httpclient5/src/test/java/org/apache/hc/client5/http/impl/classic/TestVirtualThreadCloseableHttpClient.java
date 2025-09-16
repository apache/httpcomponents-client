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
package org.apache.hc.client5.http.impl.classic;

import static org.junit.jupiter.api.condition.JRE.JAVA_21;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;

@EnabledForJreRange(min = JAVA_21)
class TestVirtualThreadCloseableHttpClient {

    static final class StubClient extends CloseableHttpClient {
        ClassicHttpResponse next;
        IOException nextIo;
        RuntimeException nextRt;
        Error nextErr;

        @Override
        protected CloseableHttpResponse doExecute(final HttpHost target,
                                                  final ClassicHttpRequest request,
                                                  final HttpContext context) throws IOException {
            if (nextIo != null) {
                throw nextIo;
            }
            if (nextRt != null) {
                throw nextRt;
            }
            if (nextErr != null) {
                throw nextErr;
            }
            final ClassicHttpResponse r = next != null ? next : new BasicClassicHttpResponse(200);
            if (r.getEntity() == null) {
                r.setEntity(new StringEntity("ok", ContentType.TEXT_PLAIN));
            }
            return CloseableHttpResponse.adapt(r);
        }

        @Override
        public void close(final CloseMode closeMode) {
            // no-op for tests
        }

        @Override
        public void close() {
            // no-op
        }
    }

    static final class TrackExec extends AbstractExecutorService {
        volatile boolean shutdown;
        volatile boolean shutdownNow;
        volatile long awaitCalls;
        volatile Thread lastExecThread;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdownNow = true;
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return shutdown || shutdownNow;
        }

        @Override
        public boolean isTerminated() {
            return isShutdown();
        }

        @Override
        public boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException {
            awaitCalls++;
            return true;
        }

        @Override
        public void execute(final Runnable command) {
            lastExecThread = Thread.currentThread();
            command.run();
        }
    }

    static final class RejectingExec extends AbstractExecutorService {
        @Override
        public void shutdown() {
        }

        @Override
        public List<Runnable> shutdownNow() {
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(final long timeout, final TimeUnit unit) {
            return true;
        }

        @Override
        public void execute(final Runnable command) {
            throw new RejectedExecutionException("rejected");
        }
    }

    @Test
    void basic_execute_ok() throws Exception {
        final StubClient base = new StubClient();
        final TrackExec exec = new TrackExec();
        try (final CloseableHttpClient client = new VirtualThreadCloseableHttpClient(base, exec)) {
            final ClassicHttpRequest req = new BasicClassicHttpRequest("GET", "/");
            final Integer code = client.execute(req, null, response -> response.getCode());
            Assertions.assertEquals(200, code.intValue());
        }
    }

    @Test
    void io_exception_unwrapped() throws Exception {
        final StubClient base = new StubClient();
        base.nextIo = new IOException("boom");
        final ExecutorService exec = new TrackExec();
        try (final CloseableHttpClient client = new VirtualThreadCloseableHttpClient(base, exec)) {
            final ClassicHttpRequest req = new BasicClassicHttpRequest("GET", "/");
            Assertions.assertThrows(IOException.class, () ->
                    client.execute(req, null, r -> null));
        }
    }

    @Test
    void runtime_exception_propagates() throws Exception {
        final StubClient base = new StubClient();
        base.nextRt = new IllegalStateException("bad");
        final ExecutorService exec = new TrackExec();
        try (final CloseableHttpClient client = new VirtualThreadCloseableHttpClient(base, exec)) {
            final ClassicHttpRequest req = new BasicClassicHttpRequest("GET", "/");
            Assertions.assertThrows(IllegalStateException.class, () ->
                    client.execute(req, null, r -> null));
        }
    }

    @Test
    void error_propagates() throws Exception {
        final StubClient base = new StubClient();
        base.nextErr = new AssertionError("err");
        final ExecutorService exec = new TrackExec();
        try (final CloseableHttpClient client = new VirtualThreadCloseableHttpClient(base, exec)) {
            final ClassicHttpRequest req = new BasicClassicHttpRequest("GET", "/");
            Assertions.assertThrows(AssertionError.class, () ->
                    client.execute(req, null, r -> null));
        }
    }

    @Test
    void rejected_execution_bubbles_as_runtime() throws Exception {
        final CloseableHttpClient base = new StubClient();
        final ExecutorService rejecting = new RejectingExec();

        try (final CloseableHttpClient client =
                     new VirtualThreadCloseableHttpClient(base, rejecting)) {

            final ClassicHttpRequest req = ClassicRequestBuilder.get("http://localhost/").build();

            final IOException ex = Assertions.assertThrows(IOException.class, () ->
                    client.execute(req, response -> null));

            Assertions.assertTrue(ex.getCause() instanceof RejectedExecutionException);
            Assertions.assertEquals("client closed", ex.getMessage());
        }
    }

    @Test
    void close_immediate_shuts_down_now() throws Exception {
        final StubClient base = new StubClient();
        final TrackExec exec = new TrackExec();
        try (final VirtualThreadCloseableHttpClient client = new VirtualThreadCloseableHttpClient(base, exec)) {
            client.close(CloseMode.IMMEDIATE);
            Assertions.assertTrue(exec.shutdownNow);
        }
    }

    @Test
    void close_graceful_shuts_down_and_awaits() throws Exception {
        final StubClient base = new StubClient();
        final TrackExec exec = new TrackExec();
        try (final CloseableHttpClient client = new VirtualThreadCloseableHttpClient(base, exec)) {
            // rely on try-with-resources to call close() (graceful)
        }
        Assertions.assertTrue(exec.shutdown);
        Assertions.assertEquals(1, exec.awaitCalls);
    }

    @Test
    void createVirtualThreadDefault_builds_and_closes() throws Exception {
        try (final CloseableHttpClient client = HttpClients.createVirtualThreadDefault()) {
            Assertions.assertNotNull(client);
        }
    }

    @Test
    void createVirtualThreadSystem_builds_and_closes() throws Exception {
        try (final CloseableHttpClient client = HttpClients.createVirtualThreadSystem()) {
            Assertions.assertNotNull(client);
        }
    }

}
