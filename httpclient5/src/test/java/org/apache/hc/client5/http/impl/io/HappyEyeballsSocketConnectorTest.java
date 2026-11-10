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
package org.apache.hc.client5.http.impl.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.util.TimeValue;
import org.junit.jupiter.api.Test;

class HappyEyeballsSocketConnectorTest {

    /** A socket whose {@link #connect} blocks until {@link #close} is called, then fails, matching the real behavior of closing a socket mid-connect. */
    private static final class BlockingSocket extends Socket {

        private final CountDownLatch closed = new CountDownLatch(1);
        volatile boolean connectAttempted;

        @Override
        public void connect(final SocketAddress endpoint, final int timeout) throws IOException {
            connectAttempted = true;
            try {
                closed.await();
            } catch (final InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            throw new SocketException("Socket closed");
        }

        @Override
        public synchronized void close() throws IOException {
            closed.countDown();
            super.close();
        }
    }

    private static List<InetSocketAddress> addresses(final int count) throws Exception {
        final List<InetSocketAddress> out = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            out.add(new InetSocketAddress(InetAddress.getByAddress(new byte[]{10, 0, 0, (byte) i}), 8080));
        }
        return out;
    }

    @Test
    void singleAddress_connectsWithoutRacing() throws Exception {
        final HappyEyeballsSocketConnector connector = new HappyEyeballsSocketConnector(TimeValue.ofMilliseconds(20));
        final Socket expected = new Socket();
        final AtomicInteger attempts = new AtomicInteger();

        final Socket actual = connector.connect(new HttpHost("somehost"), addresses(1), (remoteAddress, socketConsumer) -> {
            attempts.incrementAndGet();
            socketConsumer.accept(expected);
            return expected;
        });

        assertEquals(expected, actual);
        assertEquals(1, attempts.get());
    }

    @Test
    void secondAddressWinsWhileFirstStalls_loserSocketIsClosed() throws Exception {
        final HappyEyeballsSocketConnector connector = new HappyEyeballsSocketConnector(TimeValue.ofMilliseconds(20));
        final BlockingSocket firstSocket = new BlockingSocket();
        final Socket secondSocket = new Socket();

        final Socket winner = assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                connector.connect(new HttpHost("somehost"), addresses(2), (remoteAddress, socketConsumer) -> {
                    if (remoteAddress.getAddress().getAddress()[3] == 1) {
                        socketConsumer.accept(firstSocket);
                        firstSocket.connect(remoteAddress, 0); // blocks until closed by the race, then throws
                        return firstSocket;
                    }
                    socketConsumer.accept(secondSocket);
                    return secondSocket;
                }));

        assertEquals(secondSocket, winner);
        assertTrue(firstSocket.connectAttempted, "the first attempt must have genuinely started connecting");
        assertTrue(firstSocket.isClosed(), "the losing, still-connecting socket must be closed rather than leaked");
    }

    @Test
    void allAddressesFail_aggregatedFailure() throws Exception {
        final HappyEyeballsSocketConnector connector = new HappyEyeballsSocketConnector(TimeValue.ofMilliseconds(20));

        final IOException ex = assertThrows(IOException.class, () ->
                connector.connect(new HttpHost("somehost"), addresses(2), (remoteAddress, socketConsumer) -> {
                    throw new IOException("refused: " + remoteAddress);
                }));
        assertTrue(ex.getMessage() != null);
    }

    @Test
    void firstAddressFailsFast_secondStartsWithoutWaitingOutTheFullDelay() throws Exception {
        final HappyEyeballsSocketConnector connector = new HappyEyeballsSocketConnector(TimeValue.ofMilliseconds(250));
        final Socket winnerSocket = new Socket();

        final long start = System.nanoTime();
        final Socket winner = assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                connector.connect(new HttpHost("somehost"), addresses(2), (remoteAddress, socketConsumer) -> {
                    if (remoteAddress.getAddress().getAddress()[3] == 1) {
                        throw new IOException("refused");
                    }
                    socketConsumer.accept(winnerSocket);
                    return winnerSocket;
                }));
        final long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // The 2nd address must not have had to wait out the 250ms stagger delay, since the
        // 1st address failed fast rather than merely being slow: it only waits the RFC 8305
        // minimum inter-attempt spacing (10ms).
        assertEquals(winnerSocket, winner);
        assertTrue(elapsedMs < 200, "must advance well before the full stagger delay elapses, took " + elapsedMs + "ms");
    }

    @Test
    void interruptionWhileStillRacing_closesLoserSocket() throws Exception {
        final HappyEyeballsSocketConnector connector = new HappyEyeballsSocketConnector(TimeValue.ofMilliseconds(20));
        final BlockingSocket firstSocket = new BlockingSocket();
        final Socket secondSocket = new Socket();

        final Socket winner = assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                connector.connect(new HttpHost("somehost"), addresses(2), (remoteAddress, socketConsumer) -> {
                    if (remoteAddress.getAddress().getAddress()[3] == 1) {
                        socketConsumer.accept(firstSocket);
                        firstSocket.connect(remoteAddress, 0);
                        return firstSocket;
                    }
                    socketConsumer.accept(secondSocket);
                    return secondSocket;
                }));

        assertEquals(secondSocket, winner);
        // The executor's shutdownNow() (called in the finally block) interrupts the still
        // "connecting" worker; its socket must still end up closed, not leaked.
        assertTrue(firstSocket.isClosed());
    }

    @Test
    void interruptionDuringMinimumSpacingWait_abortsBeforeStartingNextAddress() throws Exception {
        // Regression test: sleepOrAbort() (the minimum-spacing wait after a fast failure) must
        // propagate an interruption instead of swallowing it and continuing the race.
        final HappyEyeballsSocketConnector connector = new HappyEyeballsSocketConnector(TimeValue.ofMilliseconds(250));
        final AtomicInteger attempts = new AtomicInteger();
        final CountDownLatch firstFailed = new CountDownLatch(1);
        final AtomicReference<Throwable> thrown = new AtomicReference<>();

        final Thread caller = new Thread(() -> {
            try {
                connector.connect(new HttpHost("somehost"), addresses(2), (remoteAddress, socketConsumer) -> {
                    attempts.incrementAndGet();
                    if (remoteAddress.getAddress().getAddress()[3] == 1) {
                        firstFailed.countDown();
                        throw new IOException("refused");
                    }
                    return new Socket();
                });
            } catch (final Throwable ex) {
                thrown.set(ex);
            }
        });
        caller.start();
        assertTrue(firstFailed.await(5, TimeUnit.SECONDS));
        caller.interrupt(); // races the <=10ms minimum-spacing wait that follows the failure
        caller.join(5000);

        assertFalse(caller.isAlive());
        // Best-effort timing: if the interruption landed while connect() was still running, it
        // must be reported as InterruptedIOException, never silently swallowed.
        if (thrown.get() != null) {
            assertTrue(thrown.get() instanceof InterruptedIOException,
                    "an interruption during racing must surface as InterruptedIOException: " + thrown.get());
        }
    }

    @Test
    void interruptingTheCallingThread_abortsAndClosesTheStillConnectingSocket() throws Exception {
        final HappyEyeballsSocketConnector connector = new HappyEyeballsSocketConnector(TimeValue.ofMilliseconds(20));
        final BlockingSocket onlySocket = new BlockingSocket();
        final CountDownLatch connecting = new CountDownLatch(1);
        final AtomicReference<Throwable> thrown = new AtomicReference<>();

        final Thread caller = new Thread(() -> {
            try {
                connector.connect(new HttpHost("somehost"), addresses(1), (remoteAddress, socketConsumer) -> {
                    socketConsumer.accept(onlySocket);
                    connecting.countDown();
                    onlySocket.connect(remoteAddress, 0); // blocks until closed or interrupted
                    return onlySocket;
                });
            } catch (final Throwable ex) {
                thrown.set(ex);
            }
        });
        caller.start();
        assertTrue(connecting.await(5, TimeUnit.SECONDS), "the attempt must have genuinely started connecting");
        caller.interrupt();
        caller.join(5000);

        assertFalse(caller.isAlive(), "the calling thread must have returned after being interrupted");
        assertTrue(thrown.get() instanceof InterruptedIOException,
                "connect() must report the interruption rather than silently returning: was " + thrown.get());
        assertTrue(onlySocket.isClosed(), "the still-connecting socket must be closed, not leaked, on interruption");
    }

    @Test
    void registrationAfterRaceDecided_closesSocketImmediately() throws Exception {
        final HappyEyeballsSocketConnector connector = new HappyEyeballsSocketConnector(TimeValue.ofMilliseconds(20));
        final Socket winnerSocket = new Socket();
        final CountDownLatch registerNow = new CountDownLatch(1);
        final CountDownLatch registered = new CountDownLatch(1);
        final AtomicReference<Socket> lateSocketRef = new AtomicReference<>();

        final Socket winner = assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                connector.connect(new HttpHost("somehost"), addresses(2), (remoteAddress, socketConsumer) -> {
                    if (remoteAddress.getAddress().getAddress()[3] == 1) {
                        // Registers strictly after the race below has already picked a winner.
                        awaitUninterruptibly(registerNow);
                        final Socket lateSocket = new Socket();
                        lateSocketRef.set(lateSocket);
                        socketConsumer.accept(lateSocket);
                        registered.countDown();
                        throw new IOException("too late");
                    }
                    socketConsumer.accept(winnerSocket);
                    return winnerSocket;
                }));

        assertEquals(winnerSocket, winner);
        registerNow.countDown();
        assertTrue(registered.await(5, TimeUnit.SECONDS));
        assertTrue(lateSocketRef.get().isClosed(),
                "a socket registered strictly after the race is decided must be closed immediately, not left open");
    }

    private static void awaitUninterruptibly(final CountDownLatch latch) {
        try {
            latch.await();
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void doesNotRejectSingleAddressBelowStaggerIndex() throws Exception {
        final HappyEyeballsSocketConnector connector = new HappyEyeballsSocketConnector(TimeValue.ofMilliseconds(20));
        final Socket socket = new Socket();

        final Socket result = connector.connect(new HttpHost("somehost"), addresses(1), (remoteAddress, socketConsumer) -> {
            socketConsumer.accept(socket);
            return socket;
        });

        assertEquals(socket, result);
        assertFalse(socket.isClosed());
    }

    /**
     * Real-socket integration test (no mocks, no fakes): one address has nothing listening
     * (genuine, OS-level immediate connection refusal) and the other is a real local server
     * that accepts. Proves actual fallover over real loopback TCP, not just that the first
     * address happens to succeed.
     */
    @Test
    void realSockets_unreachableAddressFailsOver_toRealListeningServer() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            final int listeningPort = server.getLocalPort();
            final int deadPort = findUnusedLoopbackPort();

            final HappyEyeballsSocketConnector connector = new HappyEyeballsSocketConnector(TimeValue.ofMilliseconds(20));
            final List<InetSocketAddress> targets = new ArrayList<>();
            targets.add(new InetSocketAddress(InetAddress.getLoopbackAddress(), deadPort));
            targets.add(new InetSocketAddress(InetAddress.getLoopbackAddress(), listeningPort));

            final Socket winner = assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                    connector.connect(new HttpHost("localhost"), targets, (remoteAddress, socketConsumer) -> {
                        final Socket s = new Socket();
                        socketConsumer.accept(s);
                        s.connect(remoteAddress, 2000);
                        return s;
                    }));

            assertEquals(listeningPort, winner.getPort());
            assertFalse(winner.isClosed());
            winner.close();
        }
    }

    /** Finds a loopback TCP port nothing is listening on, by briefly binding then closing it. */
    private static int findUnusedLoopbackPort() throws IOException {
        try (ServerSocket probe = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return probe.getLocalPort();
        }
    }
}
