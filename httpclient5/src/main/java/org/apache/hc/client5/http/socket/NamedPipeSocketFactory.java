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
import java.lang.reflect.Constructor;
import java.net.Socket;
import java.net.SocketTimeoutException;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A factory for Windows Named Pipe sockets.
 * <p>
 * Windows Named Pipes (e.g. {@code \\.\pipe\docker_engine}) provide an IPC mechanism on Windows
 * similar to Unix domain sockets. This factory creates {@link Socket} instances that connect to
 * Named Pipes using the Win32 {@code CreateFile} API via JNA with overlapped I/O, enabling real
 * timeout enforcement and cancellation.
 * </p>
 * <p>
 * Named Pipe support requires:
 * <ul>
 *   <li>Windows OS</li>
 *   <li>JNA ({@code net.java.dev.jna:jna-platform}) on the classpath</li>
 * </ul>
 * Use {@link #isAvailable()} to check at runtime whether both conditions are met.
 * </p>
 * <h3>Connect timeout</h3>
 * <p>
 * Opening a named pipe can block when all server instances are busy. When a positive connect
 * timeout is specified, the implementation uses the Win32 {@code WaitNamedPipe} API with
 * the given deadline. If all instances remain busy past the deadline, a
 * {@link java.net.SocketTimeoutException} is thrown. A timeout of zero means wait indefinitely.
 * </p>
 *
 * @since 5.7
 */
@Contract(threading = ThreadingBehavior.STATELESS)
@Internal
public final class NamedPipeSocketFactory {

    private static final Logger LOG = LoggerFactory.getLogger(NamedPipeSocketFactory.class);

    /**
     * Check for a JNA platform class that does NOT trigger native library loading.
     * {@code Kernel32.class} must not be used here because its static initializer
     * calls {@code Native.load("kernel32")} which fails on non-Windows platforms.
     */
    private static final String JNA_PLATFORM_CLASS = "com.sun.jna.platform.win32.WinBase";

    private static final boolean IS_WINDOWS = System.getProperty("os.name", "")
            .toLowerCase(java.util.Locale.ROOT).contains("win");

    private static final boolean JNA_AVAILABLE = detectJna();

    private static final NamedPipeSocketFactory INSTANCE = new NamedPipeSocketFactory();

    private static boolean detectJna() {
        if (!IS_WINDOWS) {
            return false;
        }
        try {
            Class.forName(JNA_PLATFORM_CLASS);
            LOG.debug("JNA detected for Windows Named Pipe support");
            return true;
        } catch (final ClassNotFoundException e) {
            LOG.debug("JNA not available; Windows Named Pipe support disabled");
            return false;
        }
    }

    /**
     * Checks if Windows Named Pipe support is available on the current platform.
     * Requires both Windows OS and JNA on the classpath.
     *
     * @return true if Named Pipe support is available, false otherwise
     */
    public static boolean isAvailable() {
        return IS_WINDOWS && JNA_AVAILABLE;
    }

    /**
     * Gets the singleton instance of {@link NamedPipeSocketFactory}.
     *
     * @return the singleton instance
     */
    public static NamedPipeSocketFactory getSocketFactory() {
        return INSTANCE;
    }

    /**
     * Connects to a Windows Named Pipe and returns a {@link Socket} wrapping the connection.
     *
     * @param pipeName       the full pipe path (e.g. {@code \\.\pipe\docker_engine})
     * @param connectTimeout the connect timeout; a positive value enables deadline enforcement,
     *                       zero or {@code null} means block indefinitely
     * @return a connected {@link Socket} wrapping the Named Pipe
     * @throws SocketTimeoutException if the pipe could not be opened within the timeout
     * @throws IOException if the pipe cannot be opened
     * @throws UnsupportedOperationException if the current platform is not Windows or JNA is missing
     */
    public Socket connectSocket(
            final String pipeName,
            final TimeValue connectTimeout) throws IOException {
        Args.notNull(pipeName, "Named pipe path");

        if (!isAvailable()) {
            throw new UnsupportedOperationException(
                    "Windows Named Pipes require Windows OS and JNA (jna-platform) on the classpath; "
                            + "Windows=" + IS_WINDOWS + ", JNA=" + JNA_AVAILABLE);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Connecting to named pipe: {} (timeout: {})", pipeName, connectTimeout);
        }

        final int timeoutMs = TimeValue.isPositive(connectTimeout)
                ? connectTimeout.toMillisecondsIntBound() : 0;

        try {
            return createNamedPipeSocket(pipeName, timeoutMs);
        } catch (final IOException ex) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Failed to connect to named pipe {}: {}", pipeName, ex.getMessage());
            }
            throw ex;
        }
    }

    /**
     * Creates a {@link NamedPipeSocket} via reflection to avoid a hard class-loading
     * dependency on JNA from this factory class.
     */
    private static Socket createNamedPipeSocket(final String pipeName,
                                                final int connectTimeoutMs) throws IOException {
        try {
            final Class<?> socketClass = Class.forName(
                    "org.apache.hc.client5.http.socket.NamedPipeSocket");
            final Constructor<?> ctor = socketClass.getDeclaredConstructor(String.class, int.class);
            ctor.setAccessible(true);
            return (Socket) ctor.newInstance(pipeName, connectTimeoutMs);
        } catch (final ReflectiveOperationException ex) {
            final Throwable cause = ex.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new IOException("Failed to create named pipe socket for " + pipeName, ex);
        }
    }
}
