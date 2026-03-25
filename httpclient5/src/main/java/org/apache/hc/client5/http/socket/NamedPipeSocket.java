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

package org.apache.hc.client5.http.socket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

/**
 * A {@link Socket} adapter that wraps a Windows Named Pipe connection using JNA
 * and Win32 overlapped I/O.
 * <p>
 * Windows Named Pipes (e.g. {@code \\.\pipe\docker_engine}) are opened via the Win32
 * {@code CreateFile} API with {@code FILE_FLAG_OVERLAPPED}, enabling true asynchronous
 * I/O with real timeout support through {@code WaitForSingleObject} and cancellation
 * through {@code CancelIoEx}.
 * </p>
 * <h3>Timeout semantics</h3>
 * <p>
 * When {@link #setSoTimeout(int)} is set to a positive value, reads are executed as
 * overlapped operations guarded by {@code WaitForSingleObject} with the configured
 * timeout. On timeout, the pending I/O is cancelled via {@code CancelIoEx} and a
 * {@link SocketTimeoutException} is thrown &mdash; the same contract as
 * {@code Socket#setSoTimeout}. A timeout of zero (the default) means block
 * indefinitely.
 * </p>
 * <p>
 * This class requires JNA ({@code net.java.dev.jna:jna-platform}) on the classpath.
 * Use {@link NamedPipeSocketFactory#isAvailable()} to check availability at runtime.
 * </p>
 *
 * @since 5.7
 */
final class NamedPipeSocket extends Socket {

    /**
     * Custom Kernel32 binding with the signatures required for overlapped named pipe I/O.
     * <p>
     * The standard JNA {@code Kernel32} interface provides {@code ReadFile}/{@code WriteFile}
     * with {@code byte[]} buffers, which is unsafe for overlapped I/O (JNA frees the
     * temporary native memory when the call returns, but the OS may still be writing to it).
     * This interface uses {@link Pointer} buffers and adds {@code GetOverlappedResult} and
     * {@code CancelIoEx} which are not in the standard JNA Kernel32.
     * </p>
     */
    interface NpipeKernel32 extends StdCallLibrary {

        NpipeKernel32 INSTANCE = Native.load("kernel32", NpipeKernel32.class,
                W32APIOptions.DEFAULT_OPTIONS);

        WinNT.HANDLE CreateFile(String lpFileName, int dwDesiredAccess, int dwShareMode,
                                WinBase.SECURITY_ATTRIBUTES lpSecurityAttributes, int dwCreationDisposition,
                                int dwFlagsAndAttributes, WinNT.HANDLE hTemplateFile);

        boolean ReadFile(WinNT.HANDLE hFile, Pointer lpBuffer, int nNumberOfBytesToRead,
                         IntByReference lpNumberOfBytesRead, WinBase.OVERLAPPED lpOverlapped);

        boolean WriteFile(WinNT.HANDLE hFile, Pointer lpBuffer, int nNumberOfBytesToWrite,
                          IntByReference lpNumberOfBytesWritten, WinBase.OVERLAPPED lpOverlapped);

        /**
         * Uses {@link Pointer} instead of {@link WinBase.OVERLAPPED} to prevent
         * JNA auto-marshaling from overwriting OS-updated native OVERLAPPED fields
         * (Internal, InternalHigh) with stale Java-side values before the call.
         */
        boolean GetOverlappedResult(WinNT.HANDLE hFile, Pointer lpOverlapped,
                                    IntByReference lpNumberOfBytesTransferred, boolean bWait);

        boolean CancelIoEx(WinNT.HANDLE hFile, Pointer lpOverlapped);

        boolean CloseHandle(WinNT.HANDLE hObject);

        WinNT.HANDLE CreateEvent(WinBase.SECURITY_ATTRIBUTES lpEventAttributes,
                                 boolean bManualReset, boolean bInitialState, String lpName);

        boolean ResetEvent(WinNT.HANDLE hEvent);

        int WaitForSingleObject(WinNT.HANDLE hHandle, int dwMilliseconds);

        boolean WaitNamedPipe(String lpNamedPipeName, int nTimeOut);
    }

    private static final NpipeKernel32 KERNEL32 = NpipeKernel32.INSTANCE;
    private static final int BUFFER_SIZE = 8192;
    private static final int WAIT_FAILED = 0xFFFFFFFF;
    private static final int ERROR_PIPE_BUSY = 231;
    private static final int ERROR_SEM_TIMEOUT = 121;
    private static final int NMPWAIT_WAIT_FOREVER = -1; // 0xFFFFFFFF

    private static boolean isNullHandle(final WinNT.HANDLE h) {
        return h == null || Pointer.nativeValue(h.getPointer()) == 0L;
    }

    /**
     * Opens a named pipe handle, retrying with {@code WaitNamedPipe} when all
     * server instances are busy ({@code ERROR_PIPE_BUSY}).
     *
     * @param pipeName          full pipe path (e.g. {@code \\.\pipe\docker_engine})
     * @param connectTimeoutMs  connect timeout in milliseconds; 0 means wait indefinitely
     * @return a valid pipe handle opened with {@code FILE_FLAG_OVERLAPPED}
     */
    private static WinNT.HANDLE openPipe(final String pipeName,
                                          final int connectTimeoutMs) throws IOException {
        final long deadline = connectTimeoutMs > 0
                ? System.currentTimeMillis() + connectTimeoutMs : 0;

        while (true) {
            final WinNT.HANDLE handle = KERNEL32.CreateFile(
                    pipeName,
                    WinNT.GENERIC_READ | WinNT.GENERIC_WRITE,
                    0,
                    null,
                    WinNT.OPEN_EXISTING,
                    WinNT.FILE_FLAG_OVERLAPPED,
                    null);

            if (!WinBase.INVALID_HANDLE_VALUE.equals(handle)) {
                return handle;
            }

            final int err = Native.getLastError();
            if (err != ERROR_PIPE_BUSY) {
                throw new IOException("Cannot open named pipe " + pipeName
                        + ": Windows error " + err);
            }

            // All server instances are busy — wait for one to become available
            final int waitMs;
            if (connectTimeoutMs > 0) {
                waitMs = (int) (deadline - System.currentTimeMillis());
                if (waitMs <= 0) {
                    throw new SocketTimeoutException(
                            "Connect to named pipe " + pipeName
                                    + " timed out after " + connectTimeoutMs + " ms");
                }
            } else {
                waitMs = NMPWAIT_WAIT_FOREVER;
            }

            if (!KERNEL32.WaitNamedPipe(pipeName, waitMs)) {
                final int waitErr = Native.getLastError();
                if (waitErr == ERROR_SEM_TIMEOUT) {
                    throw new SocketTimeoutException(
                            "Connect to named pipe " + pipeName
                                    + " timed out after " + connectTimeoutMs + " ms");
                }
                throw new IOException("WaitNamedPipe failed for " + pipeName
                        + ": Windows error " + waitErr);
            }
            // An instance became available — retry CreateFile
        }
    }

    private final WinNT.HANDLE hPipe;
    private final String pipeName;

    private final WinNT.HANDLE readEvent;
    private final Memory readBuffer;

    private final WinNT.HANDLE writeEvent;
    private final Memory writeBuffer;

    private final ReentrantLock readLock;
    private final ReentrantLock writeLock;

    private final InputStream inputStream;
    private final OutputStream outputStream;

    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile int soTimeoutMs;

    NamedPipeSocket(final String pipeName, final int connectTimeoutMs) throws IOException {
        this.pipeName = pipeName;
        this.hPipe = openPipe(pipeName, connectTimeoutMs);

        this.readEvent = KERNEL32.CreateEvent(null, true, false, null);
        this.writeEvent = KERNEL32.CreateEvent(null, true, false, null);
        if (isNullHandle(readEvent) || isNullHandle(writeEvent)) {
            KERNEL32.CloseHandle(hPipe);
            if (!isNullHandle(readEvent)) {
                KERNEL32.CloseHandle(readEvent);
            }
            if (!isNullHandle(writeEvent)) {
                KERNEL32.CloseHandle(writeEvent);
            }
            throw new IOException("Failed to create I/O events for " + pipeName
                    + ": Windows error " + Native.getLastError());
        }

        this.readBuffer = new Memory(BUFFER_SIZE);
        this.writeBuffer = new Memory(BUFFER_SIZE);
        this.readLock = new ReentrantLock();
        this.writeLock = new ReentrantLock();
        this.inputStream = new PipeInputStream();
        this.outputStream = new PipeOutputStream();
    }

    @Override
    public void connect(final SocketAddress endpoint, final int timeout) throws IOException {
        // Already connected at construction time via CreateFile.
    }

    @Override
    public void connect(final SocketAddress endpoint) throws IOException {
        // Already connected at construction time via CreateFile.
    }

    @Override
    public boolean isConnected() {
        return !closed.get();
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        if (closed.get()) {
            throw new SocketException("Socket is closed");
        }
        return inputStream;
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        if (closed.get()) {
            throw new SocketException("Socket is closed");
        }
        return outputStream;
    }

    @Override
    public void close() throws IOException {
        if (closed.compareAndSet(false, true)) {
            // Cancel all pending overlapped I/O on this handle
            KERNEL32.CancelIoEx(hPipe, null);
            // Acquire both I/O locks so that no overlapped operation is still
            // referencing the pipe handle or event objects when we close them.
            // CancelIoEx above ensures any blocked WaitForSingleObject returns
            // promptly, so these locks will not block for long.
            readLock.lock();
            writeLock.lock();
            try {
                KERNEL32.CloseHandle(hPipe);
                KERNEL32.CloseHandle(readEvent);
                KERNEL32.CloseHandle(writeEvent);
            } finally {
                writeLock.unlock();
                readLock.unlock();
            }
        }
    }

    @Override
    public int getSoTimeout() throws SocketException {
        return soTimeoutMs;
    }

    @Override
    public void setSoTimeout(final int timeout) throws SocketException {
        if (timeout < 0) {
            throw new IllegalArgumentException("timeout < 0");
        }
        this.soTimeoutMs = timeout;
    }

    @Override
    public void setReceiveBufferSize(final int size) throws SocketException {
        // No-op: named pipes do not expose kernel buffer tuning.
    }

    @Override
    public int getReceiveBufferSize() throws SocketException {
        return BUFFER_SIZE;
    }

    @Override
    public void setSendBufferSize(final int size) throws SocketException {
        // No-op: named pipes do not expose kernel buffer tuning.
    }

    @Override
    public int getSendBufferSize() throws SocketException {
        return BUFFER_SIZE;
    }

    @Override
    public void setSoLinger(final boolean on, final int linger) throws SocketException {
        // No-op for named pipes.
    }

    @Override
    public int getSoLinger() throws SocketException {
        return -1;
    }

    @Override
    public String toString() {
        return "NamedPipeSocket[" + pipeName + (closed.get() ? ", closed" : "") + "]";
    }

    /**
     * Performs an overlapped read on the pipe handle with timeout support.
     *
     * @param buf       pre-allocated native buffer
     * @param event     manual-reset event for the overlapped operation
     * @param toRead    number of bytes to read
     * @param timeoutMs SO_TIMEOUT value; 0 or negative means infinite
     * @return number of bytes read, or -1 on EOF / broken pipe
     */
    private int overlappedRead(final Memory buf, final WinNT.HANDLE event,
                               final int toRead, final int timeoutMs) throws IOException {
        final WinBase.OVERLAPPED overlapped = new WinBase.OVERLAPPED();
        overlapped.hEvent = event;
        KERNEL32.ResetEvent(event);
        overlapped.write();

        final IntByReference bytesRead = new IntByReference();
        final boolean ok = KERNEL32.ReadFile(hPipe, buf, toRead, bytesRead, overlapped);
        if (ok) {
            // Completed synchronously
            final int n = bytesRead.getValue();
            return n == 0 ? -1 : n;
        }

        final int err = Native.getLastError();
        if (err == WinError.ERROR_BROKEN_PIPE) {
            return -1;
        }
        if (err != WinError.ERROR_IO_PENDING) {
            throw new IOException("ReadFile on " + pipeName + " failed: Windows error " + err);
        }

        // I/O is pending — wait with timeout
        final int waitMs = timeoutMs > 0 ? timeoutMs : WinBase.INFINITE;
        final int waitResult = KERNEL32.WaitForSingleObject(event, waitMs);

        if (waitResult == WAIT_FAILED) {
            throw new IOException("WaitForSingleObject failed on read from " + pipeName
                    + ": Windows error " + Native.getLastError());
        }

        if (waitResult == WinError.WAIT_TIMEOUT) {
            KERNEL32.CancelIoEx(hPipe, overlapped.getPointer());
            // Wait for cancellation to complete so the OVERLAPPED struct is safe to discard
            KERNEL32.GetOverlappedResult(hPipe, overlapped.getPointer(), bytesRead, true);
            throw new SocketTimeoutException("Read timed out after " + timeoutMs + " ms on " + pipeName);
        }

        if (!KERNEL32.GetOverlappedResult(hPipe, overlapped.getPointer(), bytesRead, false)) {
            final int resultErr = Native.getLastError();
            if (resultErr == WinError.ERROR_BROKEN_PIPE) {
                return -1;
            }
            throw new IOException("ReadFile on " + pipeName + " failed: Windows error " + resultErr);
        }
        final int n = bytesRead.getValue();
        return n == 0 ? -1 : n;
    }

    /**
     * Performs an overlapped write on the pipe handle, blocking until complete.
     *
     * @return number of bytes actually written
     */
    private int overlappedWrite(final Memory buf, final WinNT.HANDLE event,
                                final int toWrite) throws IOException {
        final WinBase.OVERLAPPED overlapped = new WinBase.OVERLAPPED();
        overlapped.hEvent = event;
        KERNEL32.ResetEvent(event);
        overlapped.write();

        final IntByReference bytesWritten = new IntByReference();
        final boolean ok = KERNEL32.WriteFile(hPipe, buf, toWrite, bytesWritten, overlapped);
        if (ok) {
            return bytesWritten.getValue();
        }

        final int err = Native.getLastError();
        if (err != WinError.ERROR_IO_PENDING) {
            throw new IOException("WriteFile on " + pipeName + " failed: Windows error " + err);
        }

        // Wait for write to complete
        final int waitResult = KERNEL32.WaitForSingleObject(event, WinBase.INFINITE);
        if (waitResult == WAIT_FAILED) {
            throw new IOException("WaitForSingleObject failed on write to " + pipeName
                    + ": Windows error " + Native.getLastError());
        }
        if (!KERNEL32.GetOverlappedResult(hPipe, overlapped.getPointer(), bytesWritten, false)) {
            throw new IOException("WriteFile on " + pipeName
                    + " failed: Windows error " + Native.getLastError());
        }
        return bytesWritten.getValue();
    }

    private final class PipeInputStream extends InputStream {

        @Override
        public int read() throws IOException {
            final byte[] b = new byte[1];
            final int n = read(b, 0, 1);
            return n == -1 ? -1 : (b[0] & 0xFF);
        }

        @Override
        public int read(final byte[] b) throws IOException {
            return read(b, 0, b.length);
        }

        @Override
        public int read(final byte[] b, final int off, final int len) throws IOException {
            if (closed.get()) {
                throw new SocketException("Socket is closed");
            }
            if (len == 0) {
                return 0;
            }

            readLock.lock();
            try {
                if (closed.get()) {
                    throw new SocketException("Socket is closed");
                }
                final int toRead = Math.min(len, BUFFER_SIZE);
                final int n = overlappedRead(readBuffer, readEvent, toRead, soTimeoutMs);
                if (n > 0) {
                    readBuffer.read(0, b, off, n);
                }
                return n;
            } finally {
                readLock.unlock();
            }
        }

        @Override
        public void close() throws IOException {
            NamedPipeSocket.this.close();
        }
    }

    private final class PipeOutputStream extends OutputStream {

        @Override
        public void write(final int b) throws IOException {
            write(new byte[]{(byte) b}, 0, 1);
        }

        @Override
        public void write(final byte[] b) throws IOException {
            write(b, 0, b.length);
        }

        @Override
        public void write(final byte[] b, final int off, final int len) throws IOException {
            if (closed.get()) {
                throw new SocketException("Socket is closed");
            }

            writeLock.lock();
            try {
                if (closed.get()) {
                    throw new SocketException("Socket is closed");
                }
                int offset = off;
                int remaining = len;
                while (remaining > 0) {
                    final int chunk = Math.min(remaining, BUFFER_SIZE);
                    writeBuffer.write(0, b, offset, chunk);
                    final int written = overlappedWrite(writeBuffer, writeEvent, chunk);
                    if (written <= 0) {
                        throw new IOException("WriteFile on " + pipeName + " wrote 0 bytes");
                    }
                    offset += written;
                    remaining -= written;
                }
            } finally {
                writeLock.unlock();
            }
        }

        @Override
        public void flush() throws IOException {
            // No-op: named pipes deliver data as soon as WriteFile returns.
        }

        @Override
        public void close() throws IOException {
            NamedPipeSocket.this.close();
        }
    }
}
