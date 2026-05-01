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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

/**
 * A test proxy server that bridges Windows Named Pipes to TCP connections.
 * <p>
 * Mirrors the architecture of {@link UnixDomainProxyServer}: each client
 * connection is accepted on a new pipe instance and proxied bidirectionally
 * to a TCP backend, without blocking the accept loop.
 * </p>
 * <p>
 * Uses overlapped I/O with {@link Pointer}-based buffers for pipe operations,
 * matching the I/O mode used by the production {@code NamedPipeSocket}.
 * </p>
 *
 * @since 5.7
 */
public final class NamedPipeProxyServer {

    /**
     * Custom Kernel32 binding with Pointer-based ReadFile/WriteFile for
     * overlapped I/O safety. Standard JNA Kernel32 uses byte[] buffers
     * which are unsafe with overlapped I/O (JNA frees the temporary native
     * memory before the async operation completes).
     */
    interface PipeKernel32 extends StdCallLibrary {

        PipeKernel32 INSTANCE = Native.load("kernel32", PipeKernel32.class,
                W32APIOptions.DEFAULT_OPTIONS);

        boolean ReadFile(WinNT.HANDLE hFile, Pointer lpBuffer, int nNumberOfBytesToRead,
                         IntByReference lpNumberOfBytesRead, WinBase.OVERLAPPED lpOverlapped);

        boolean WriteFile(WinNT.HANDLE hFile, Pointer lpBuffer, int nNumberOfBytesToWrite,
                          IntByReference lpNumberOfBytesWritten, WinBase.OVERLAPPED lpOverlapped);

        /**
         * Uses {@link Pointer} to prevent JNA auto-marshaling from overwriting
         * OS-updated native OVERLAPPED fields with stale Java-side values.
         */
        boolean GetOverlappedResult(WinNT.HANDLE hFile, Pointer lpOverlapped,
                                    IntByReference lpNumberOfBytesTransferred, boolean bWait);

        WinNT.HANDLE CreateEvent(WinBase.SECURITY_ATTRIBUTES lpEventAttributes,
                                 boolean bManualReset, boolean bInitialState, String lpName);

        boolean ResetEvent(WinNT.HANDLE hEvent);

        int WaitForSingleObject(WinNT.HANDLE hHandle, int dwMilliseconds);
    }

    private static final PipeKernel32 K32 = PipeKernel32.INSTANCE;

    private final int port;
    private final ExecutorService executorService;
    private final String pipeName;
    private final CountDownLatch serverReady = new CountDownLatch(1);
    private final AtomicInteger requestsReceived = new AtomicInteger(0);
    private volatile boolean running;
    private volatile WinNT.HANDLE waitingHandle;

    private static final int PIPE_ACCESS_DUPLEX = 0x00000003;
    private static final int FILE_FLAG_OVERLAPPED = 0x40000000;
    private static final int PIPE_TYPE_BYTE = 0x00000000;
    private static final int PIPE_READMODE_BYTE = 0x00000000;
    private static final int PIPE_WAIT = 0x00000000;
    private static final int PIPE_UNLIMITED_INSTANCES = 255;
    private static final int BUFFER_SIZE = 8192;

    // Win32 error codes — use constants to avoid dependency on WinError
    private static final int ERROR_IO_PENDING = 997;
    private static final int ERROR_BROKEN_PIPE = 109;
    private static final int ERROR_PIPE_NOT_CONNECTED = 233;
    private static final int ERROR_PIPE_CONNECTED = 535;

    public NamedPipeProxyServer(final int port) {
        this.port = port;
        this.executorService = Executors.newCachedThreadPool();
        this.pipeName = "\\\\.\\pipe\\httpclient5-test-" + UUID.randomUUID();
    }

    public void start() {
        running = true;
        executorService.submit(this::runPipeServer);
        try {
            if (!serverReady.await(10, TimeUnit.SECONDS)) {
                throw new RuntimeException("Named Pipe proxy server did not start within 10 seconds");
            }
        } catch (final InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    public String getPipeName() {
        return pipeName;
    }

    public int getRequestsReceived() {
        return requestsReceived.get();
    }

    public void close() {
        running = false;
        // Close the pipe handle that is blocked on ConnectNamedPipe,
        // same as UDS closing the server socket to unblock accept().
        final WinNT.HANDLE h = waitingHandle;
        if (h != null) {
            Kernel32.INSTANCE.CloseHandle(h);
        }
        executorService.shutdownNow();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new RuntimeException("Failed to shut down");
            }
        } catch (final InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Creates a new overlapped pipe instance.
     */
    private WinNT.HANDLE createPipeInstance() {
        return Kernel32.INSTANCE.CreateNamedPipe(
                pipeName,
                PIPE_ACCESS_DUPLEX | FILE_FLAG_OVERLAPPED,
                PIPE_TYPE_BYTE | PIPE_READMODE_BYTE | PIPE_WAIT,
                PIPE_UNLIMITED_INSTANCES,
                BUFFER_SIZE,
                BUFFER_SIZE,
                0,
                null);
    }

    private void runPipeServer() {
        WinNT.HANDLE pipeHandle = createPipeInstance();
        if (WinBase.INVALID_HANDLE_VALUE.equals(pipeHandle)) {
            serverReady.countDown();
            throw new RuntimeException("CreateNamedPipe failed: error " + Native.getLastError());
        }
        // Event used to wait for overlapped ConnectNamedPipe
        final WinNT.HANDLE connectEvent = K32.CreateEvent(null, true, false, null);
        serverReady.countDown();

        while (running) {
            waitingHandle = pipeHandle;

            if (!waitForClient(pipeHandle, connectEvent)) {
                if (!running) {
                    break;
                }
                // Unrecoverable error — skip this instance
                Kernel32.INSTANCE.CloseHandle(pipeHandle);
                pipeHandle = createPipeInstance();
                continue;
            }
            waitingHandle = null;
            requestsReceived.incrementAndGet();

            final WinNT.HANDLE connectedHandle = pipeHandle;
            final Socket tcpSocket;
            try {
                tcpSocket = new Socket("localhost", port);
            } catch (final IOException ex) {
                Kernel32.INSTANCE.DisconnectNamedPipe(connectedHandle);
                Kernel32.INSTANCE.CloseHandle(connectedHandle);
                pipeHandle = createPipeInstance();
                continue;
            }

            // Cross-direction cancellation: named pipes don't support half-close,
            // so each direction must unblock the other when it exits.
            final CompletableFuture<Void> f1 = CompletableFuture.runAsync(() -> {
                try {
                    pipeToSocket(connectedHandle, tcpSocket);
                } finally {
                    // Pipe read ended — close TCP socket to unblock socketToPipe's in.read()
                    try {
                        tcpSocket.close();
                    } catch (final IOException ignore) {
                    }
                }
            }, executorService);
            final CompletableFuture<Void> f2 = CompletableFuture.runAsync(() -> {
                try {
                    socketToPipe(connectedHandle, tcpSocket);
                } finally {
                    // TCP read ended — disconnect pipe to unblock pipeToSocket's overlappedRead
                    Kernel32.INSTANCE.DisconnectNamedPipe(connectedHandle);
                }
            }, executorService);
            CompletableFuture.allOf(f1, f2).whenComplete((result, ex) -> {
                try {
                    tcpSocket.close();
                } catch (final IOException ignore) {
                }
                Kernel32.INSTANCE.DisconnectNamedPipe(connectedHandle);
                Kernel32.INSTANCE.CloseHandle(connectedHandle);
            });

            pipeHandle = createPipeInstance();
        }

        waitingHandle = null;
        Kernel32.INSTANCE.CloseHandle(pipeHandle);
        Kernel32.INSTANCE.CloseHandle(connectEvent);
    }

    /**
     * Waits for a client to connect using overlapped ConnectNamedPipe.
     * Overlapped mode is required because the pipe handle was created with
     * FILE_FLAG_OVERLAPPED (needed for concurrent read/write in the proxy).
     *
     * @return true if a client connected, false on error
     */
    private static boolean waitForClient(final WinNT.HANDLE pipeHandle,
                                         final WinNT.HANDLE connectEvent) {
        final WinBase.OVERLAPPED ol = new WinBase.OVERLAPPED();
        ol.hEvent = connectEvent;
        K32.ResetEvent(connectEvent);
        ol.write();

        if (Kernel32.INSTANCE.ConnectNamedPipe(pipeHandle, ol)) {
            return true; // Client connected synchronously
        }

        final int err = Native.getLastError();
        if (err == ERROR_PIPE_CONNECTED) {
            return true; // Client already connected
        }
        if (err != ERROR_IO_PENDING) {
            return false; // Real error
        }

        // Wait for a client to connect (or handle to be closed by close())
        K32.WaitForSingleObject(connectEvent, WinBase.INFINITE);
        final IntByReference dummy = new IntByReference();
        return K32.GetOverlappedResult(pipeHandle, ol.getPointer(), dummy, false);
    }

    private static void pipeToSocket(final WinNT.HANDLE pipeHandle, final Socket tcpSocket) {
        final WinNT.HANDLE event = K32.CreateEvent(null, true, false, null);
        if (event == null) {
            return;
        }
        final Memory buf = new Memory(BUFFER_SIZE);
        try {
            final OutputStream out = tcpSocket.getOutputStream();
            final byte[] javaBuf = new byte[BUFFER_SIZE];
            int n;
            while ((n = overlappedRead(pipeHandle, buf, BUFFER_SIZE, event)) > 0) {
                buf.read(0, javaBuf, 0, n);
                out.write(javaBuf, 0, n);
                out.flush();
            }
        } catch (final IOException ignore) {
        } finally {
            Kernel32.INSTANCE.CloseHandle(event);
        }
    }

    private static void socketToPipe(final WinNT.HANDLE pipeHandle, final Socket tcpSocket) {
        final WinNT.HANDLE event = K32.CreateEvent(null, true, false, null);
        if (event == null) {
            return;
        }
        final Memory buf = new Memory(BUFFER_SIZE);
        try {
            final InputStream in = tcpSocket.getInputStream();
            final byte[] javaBuf = new byte[BUFFER_SIZE];
            int len;
            while ((len = in.read(javaBuf)) != -1) {
                buf.write(0, javaBuf, 0, len);
                if (overlappedWrite(pipeHandle, buf, len, event) <= 0) {
                    break;
                }
            }
        } catch (final IOException ignore) {
        } finally {
            Kernel32.INSTANCE.CloseHandle(event);
        }
    }

    /**
     * Performs an overlapped read, waiting for completion.
     *
     * @return bytes read, or -1 on EOF/error
     */
    private static int overlappedRead(final WinNT.HANDLE pipe, final Memory buf,
                                      final int toRead, final WinNT.HANDLE event) {
        final WinBase.OVERLAPPED ol = new WinBase.OVERLAPPED();
        ol.hEvent = event;
        K32.ResetEvent(event);
        ol.write();

        final IntByReference bytesRead = new IntByReference();
        if (K32.ReadFile(pipe, buf, toRead, bytesRead, ol)) {
            final int n = bytesRead.getValue();
            return n == 0 ? -1 : n;
        }

        final int err = Native.getLastError();
        if (err == ERROR_BROKEN_PIPE || err == ERROR_PIPE_NOT_CONNECTED) {
            return -1;
        }
        if (err != ERROR_IO_PENDING) {
            return -1;
        }

        K32.WaitForSingleObject(event, WinBase.INFINITE);
        if (!K32.GetOverlappedResult(pipe, ol.getPointer(), bytesRead, false)) {
            return -1;
        }
        final int n = bytesRead.getValue();
        return n == 0 ? -1 : n;
    }

    /**
     * Performs an overlapped write, waiting for completion.
     *
     * @return bytes written, or -1 on error
     */
    private static int overlappedWrite(final WinNT.HANDLE pipe, final Memory buf,
                                       final int toWrite, final WinNT.HANDLE event) {
        final WinBase.OVERLAPPED ol = new WinBase.OVERLAPPED();
        ol.hEvent = event;
        K32.ResetEvent(event);
        ol.write();

        final IntByReference bytesWritten = new IntByReference();
        if (K32.WriteFile(pipe, buf, toWrite, bytesWritten, ol)) {
            return bytesWritten.getValue();
        }

        final int err = Native.getLastError();
        if (err != ERROR_IO_PENDING) {
            return -1;
        }

        K32.WaitForSingleObject(event, WinBase.INFINITE);
        if (!K32.GetOverlappedResult(pipe, ol.getPointer(), bytesWritten, false)) {
            return -1;
        }
        return bytesWritten.getValue();
    }
}
