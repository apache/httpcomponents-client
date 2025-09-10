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
package org.apache.hc.core5.websocket;

/**
 * Factory for {@code permessage-deflate} extensions (RFC 7692).
 *
 * <p>Note: the JDK {@link java.util.zip.Deflater} / {@link java.util.zip.Inflater}
 * only support a 15-bit window size. This factory therefore accepts
 * {@code client_max_window_bits} / {@code server_max_window_bits} only when
 * they are either absent or explicitly set to {@code 15}. Other values are
 * rejected during negotiation.</p>
 */
public final class PerMessageDeflateExtensionFactory implements WebSocketExtensionFactory {

    @Override
    public String getName() {
        return "permessage-deflate";
    }

    @Override
    public WebSocketExtension create(final WebSocketExtensionData request, final boolean server) {
        if (request == null) {
            return null;
        }
        final String name = request.getName();
        if (!"permessage-deflate".equals(name)) {
            return null;
        }
        final boolean serverNoContextTakeover = request.getParameters().containsKey("server_no_context_takeover");
        final boolean clientNoContextTakeover = request.getParameters().containsKey("client_no_context_takeover");
        final boolean clientMaxBitsPresent = request.getParameters().containsKey("client_max_window_bits");
        final boolean serverMaxBitsPresent = request.getParameters().containsKey("server_max_window_bits");
        Integer clientMaxWindowBits = parseWindowBits(request.getParameters().get("client_max_window_bits"));
        Integer serverMaxWindowBits = parseWindowBits(request.getParameters().get("server_max_window_bits"));
        if (clientMaxBitsPresent && clientMaxWindowBits == null) {
            clientMaxWindowBits = 15;
        }
        if (serverMaxBitsPresent && serverMaxWindowBits == null) {
            serverMaxWindowBits = 15;
        }

        if (!PerMessageDeflateExtension.isValidWindowBits(clientMaxWindowBits)
                || !PerMessageDeflateExtension.isValidWindowBits(serverMaxWindowBits)) {
            return null;
        }
        // JDK Deflater/Inflater only supports 15-bit window size.
        if (!isSupportedWindowBits(clientMaxWindowBits) || !isSupportedWindowBits(serverMaxWindowBits)) {
            return null;
        }
        return new PerMessageDeflateExtension(
                serverNoContextTakeover,
                clientNoContextTakeover,
                clientMaxWindowBits,
                serverMaxWindowBits);
    }

    private static Integer parseWindowBits(final String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (final NumberFormatException ignore) {
            return null;
        }
    }

    private static boolean isSupportedWindowBits(final Integer bits) {
        return bits == null || bits == 15;
    }
}
