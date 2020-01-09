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
package org.apache.hc.client5.http;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Arrays;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.net.NamedEndpoint;

/**
 * Connect exception support methods.
 *
 * @since 5.0
 */
@Internal
public final class ConnectExceptionSupport {

    public static ConnectTimeoutException createConnectTimeoutException(
            final IOException cause, final NamedEndpoint namedEndpoint, final InetAddress... remoteAddresses) {
        final String message = "Connect to " +
                (namedEndpoint != null ? namedEndpoint : "remote endpoint") +
                (remoteAddresses != null && remoteAddresses.length > 0 ? " " + Arrays.asList(remoteAddresses) : "") +
                ((cause != null && cause.getMessage() != null) ? " failed: " + cause.getMessage() : " timed out");
        return new ConnectTimeoutException(message, namedEndpoint);
    }

    public static HttpHostConnectException createHttpHostConnectException(
            final IOException cause,
            final NamedEndpoint namedEndpoint,
            final InetAddress... remoteAddresses) {
        final String message = "Connect to " +
                (namedEndpoint != null ? namedEndpoint : "remote endpoint") +
                (remoteAddresses != null && remoteAddresses.length > 0 ? " " + Arrays.asList(remoteAddresses) : "") +
                ((cause != null && cause.getMessage() != null) ? " failed: " + cause.getMessage() : " refused");
        return new HttpHostConnectException(message, namedEndpoint);
    }

    public static IOException enhance(
            final IOException cause, final NamedEndpoint namedEndpoint, final InetAddress... remoteAddresses) {
        if (cause instanceof SocketTimeoutException) {
            final IOException ex = createConnectTimeoutException(cause, namedEndpoint, remoteAddresses);
            ex.setStackTrace(cause.getStackTrace());
            return ex;
        } else if (cause instanceof ConnectException) {
            if ("Connection timed out".equals(cause.getMessage())) {
                final IOException ex = createConnectTimeoutException(cause, namedEndpoint, remoteAddresses);
                ex.initCause(cause);
                return ex;
            }
            final IOException ex = createHttpHostConnectException(cause, namedEndpoint, remoteAddresses);
            ex.setStackTrace(cause.getStackTrace());
            return ex;
        } else {
            return cause;
        }
    }

}
