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

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.ProtocolFamily;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;

/**
 * A factory for Unix domain sockets.
 * <p>
 * This implementation supports both the JDK16+ standard library implementation (JEP 380) and the JUnixSocket library.
 * It will automatically detect which implementation is available and use it; if both are available, the standard
 * library implementation is preferred.
 * </p>
 *
 * @since 5.6
 */
@Contract(threading = ThreadingBehavior.STATELESS)
@Internal
public final class UnixDomainSocketFactory {
    private static final Logger LOG = LoggerFactory.getLogger(UnixDomainSocketFactory.class);

    private static final String JDK_UNIX_SOCKET_ADDRESS_CLASS = "java.net.UnixDomainSocketAddress";
    private static final String JUNIXSOCKET_SOCKET_CLASS = "org.newsclub.net.unix.AFUNIXSocket";
    private static final String JUNIXSOCKET_ADDRESS_CLASS = "org.newsclub.net.unix.AFUNIXSocketAddress";

    private enum Implementation {
        JDK,
        JUNIXSOCKET,
        NONE
    }

    private static final Implementation IMPLEMENTATION = detectImplementation();

    private static Implementation detectImplementation() {
        try {
            Class.forName(JDK_UNIX_SOCKET_ADDRESS_CLASS);
            LOG.debug("Using JDK Unix Domain Socket implementation");
            return Implementation.JDK;
        } catch (final ClassNotFoundException e) {
            try {
                Class.forName(JUNIXSOCKET_SOCKET_CLASS);
                LOG.debug("Using JUnixSocket Unix Domain Socket implementation");
                return Implementation.JUNIXSOCKET;
            } catch (final ClassNotFoundException e2) {
                LOG.debug("No Unix Domain Socket implementation found");
                return Implementation.NONE;
            }
        }
    }

    /**
     * Checks if Unix Domain Socket support is available.
     *
     * @return true if Unix Domain Socket support is available, false otherwise
     */
    public static boolean isAvailable() {
        return IMPLEMENTATION != Implementation.NONE;
    }

    /**
     * Default instance of {@link UnixDomainSocketFactory}.
     */
    private static final UnixDomainSocketFactory INSTANCE = new UnixDomainSocketFactory();

    /**
     * Gets the singleton instance of {@link UnixDomainSocketFactory}.
     *
     * @return the singleton instance
     */
    public static UnixDomainSocketFactory getSocketFactory() {
        return INSTANCE;
    }

    public SocketAddress createSocketAddress(final Path socketPath) {
        if (!isAvailable()) {
            throw new UnsupportedOperationException("Unix Domain Socket support is not available");
        }
        Args.notNull(socketPath, "Unix domain socket path");

        try {
            if (IMPLEMENTATION == Implementation.JDK) {
                // JDK implementation
                final Class<?> addressClass = Class.forName(JDK_UNIX_SOCKET_ADDRESS_CLASS);
                final Method ofMethod = addressClass.getMethod("of", Path.class);
                return (SocketAddress) ofMethod.invoke(null, socketPath);
            } else {
                // JUnixSocket implementation
                final Class<?> addressClass = Class.forName(JUNIXSOCKET_ADDRESS_CLASS);
                final Method ofMethod = addressClass.getMethod("of", Path.class);
                return (SocketAddress) ofMethod.invoke(null, socketPath);
            }
        } catch (final ReflectiveOperationException ex) {
            throw new RuntimeException("Could not create UDS SocketAddress", ex);
        }
    }

    public Socket createSocket() throws IOException {
        if (!isAvailable()) {
            throw new UnsupportedOperationException("Unix Domain Socket support is not available");
        }

        try {
            if (IMPLEMENTATION == Implementation.JDK) {
                // Java 16+ only supports UDS through the SocketChannel API, but the sync client is coupled to the
                // legacy Socket API. To facilitate this, we use an adapter, similar to the one provided by JUnixSocket.
                try {
                    final SocketChannel channel = (SocketChannel) SocketChannel.class.getMethod("open",
                            ProtocolFamily.class)
                        .invoke(null, StandardProtocolFamily.valueOf("UNIX"));
                    return new Jep380SocketChannelAdapter(channel);
                } catch (final ReflectiveOperationException ex) {
                    throw new UnsupportedOperationException("JEP 380 Unix domain sockets are not supported; use "
                        + "JUnixSocket", ex);
                }
            } else {
                // JUnixSocket implementation
                final Class<?> socketClass = Class.forName(JUNIXSOCKET_SOCKET_CLASS);
                final Method newInstanceMethod = socketClass.getMethod("newInstance");
                return (Socket) newInstanceMethod.invoke(null);
            }
        } catch (final Exception e) {
            throw new IOException("Failed to create Unix domain socket", e);
        }
    }

    public Socket connectSocket(
        final Socket socket,
        final Path socketPath,
        final TimeValue connectTimeout
    ) throws IOException {
        Args.notNull(socketPath, "Unix domain socket path");

        final Socket sock = socket != null ? socket : createSocket();
        final SocketAddress address = createSocketAddress(socketPath);
        final int connTimeoutMs = TimeValue.isPositive(connectTimeout) ? connectTimeout.toMillisecondsIntBound() : 0;

        try {
            sock.connect(address, connTimeoutMs);
            return sock;
        } catch (final IOException ex) {
            try {
                sock.close();
            } catch (final IOException ignore) {
            }
            throw ex;
        }
    }
}
