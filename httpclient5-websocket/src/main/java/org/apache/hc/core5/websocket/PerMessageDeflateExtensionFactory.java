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

import java.util.Map;

/**
 * Factory for {@code permessage-deflate} extensions (RFC 7692).
 *
 * <p>An offer is accepted only when every parameter is recognised and well-formed. The
 * {@code *_no_context_takeover} parameters must appear without a value, and the window-bits
 * parameters must be exactly {@code 15} because the JDK {@link java.util.zip.Deflater} /
 * {@link java.util.zip.Inflater} only support a 15-bit window. {@code client_max_window_bits} may
 * also be offered without a value, in which case the client advertises support and the server
 * selects the window. Any unknown parameter, any value on a valueless parameter, and any window
 * size other than {@code 15} cause the offer to be declined (RFC 7692 section 7.1).</p>
 */
public final class PerMessageDeflateExtensionFactory implements WebSocketExtensionFactory {

    @Override
    public String getName() {
        return "permessage-deflate";
    }

    @Override
    public WebSocketExtension create(final WebSocketExtensionData request, final boolean server) {
        if (request == null || !"permessage-deflate".equals(request.getName())) {
            return null;
        }
        boolean serverNoContextTakeover = false;
        boolean clientNoContextTakeover = false;
        Integer clientMaxWindowBits = null;
        Integer serverMaxWindowBits = null;
        for (final Map.Entry<String, String> param : request.getParameters().entrySet()) {
            final String value = param.getValue();
            switch (param.getKey()) {
                case "server_no_context_takeover":
                    if (value != null) {
                        return null;
                    }
                    serverNoContextTakeover = true;
                    break;
                case "client_no_context_takeover":
                    if (value != null) {
                        return null;
                    }
                    clientNoContextTakeover = true;
                    break;
                case "client_max_window_bits":
                    // A valueless parameter advertises support; the server selects the window (15).
                    if (value != null && !isWindowBits15(value)) {
                        return null;
                    }
                    clientMaxWindowBits = 15;
                    break;
                case "server_max_window_bits":
                    // Must carry a value, and only 15 is supported.
                    if (!isWindowBits15(value)) {
                        return null;
                    }
                    serverMaxWindowBits = 15;
                    break;
                default:
                    // Unknown parameters make the offer invalid.
                    return null;
            }
        }
        return new PerMessageDeflateExtension(
                serverNoContextTakeover,
                clientNoContextTakeover,
                clientMaxWindowBits,
                serverMaxWindowBits);
    }

    /**
     * The only window size the JDK Deflater/Inflater supports. A valid value is exactly the two
     * decimal digits {@code "15"}; this rejects a leading zero ({@code "015"}), a sign
     * ({@code "+15"}) and any missing or non-numeric value, which {@link Integer#parseInt} would
     * otherwise accept.
     */
    private static boolean isWindowBits15(final String value) {
        return "15".equals(value);
    }
}
