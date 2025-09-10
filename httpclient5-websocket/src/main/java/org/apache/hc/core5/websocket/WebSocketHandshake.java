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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TextUtils;

public final class WebSocketHandshake {

    private static final String SUPPORTED_VERSION = "13";

    private WebSocketHandshake() {
    }

    public static boolean isWebSocketUpgrade(final HttpRequest request) {
        if (request == null || request.getMethod() == null) {
            return false;
        }
        if (!Method.GET.isSame(request.getMethod())) {
            return false;
        }
        if (!containsToken(request, HttpHeaders.CONNECTION, "Upgrade")) {
            return false;
        }
        final Header upgradeHeader = request.getFirstHeader(HttpHeaders.UPGRADE);
        if (upgradeHeader == null || !"websocket".equalsIgnoreCase(upgradeHeader.getValue())) {
            return false;
        }
        final Header versionHeader = request.getFirstHeader(WebSocketConstants.SEC_WEBSOCKET_VERSION);
        if (versionHeader == null || !SUPPORTED_VERSION.equals(versionHeader.getValue())) {
            return false;
        }
        final Header keyHeader = request.getFirstHeader(WebSocketConstants.SEC_WEBSOCKET_KEY);
        return keyHeader != null && isValidClientKey(keyHeader.getValue());
    }

    public static String createAcceptKey(final String key) throws WebSocketException {
        try {
            Args.notBlank(key, "WebSocket key");
            final MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            final String acceptSource = key.trim() + WebSocketConstants.WEBSOCKET_GUID;
            final byte[] digest = sha1.digest(acceptSource.getBytes(StandardCharsets.ISO_8859_1));
            return Base64.getEncoder().encodeToString(digest);
        } catch (final Exception ex) {
            throw new WebSocketException("Unable to compute Sec-WebSocket-Accept", ex);
        }
    }

    public static List<String> parseSubprotocols(final Header header) {
        final List<String> protocols = new ArrayList<>();
        if (header == null) {
            return protocols;
        }
        for (final String token : MessageSupport.parseTokens(header)) {
            if (!TextUtils.isBlank(token)) {
                protocols.add(token);
            }
        }
        return protocols;
    }

    private static boolean containsToken(final HttpRequest request, final String headerName, final String token) {
        for (final Header hdr : request.getHeaders(headerName)) {
            for (final String value : MessageSupport.parseTokens(hdr)) {
                if (token.equalsIgnoreCase(value)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isValidClientKey(final String key) {
        if (TextUtils.isBlank(key)) {
            return false;
        }
        try {
            final byte[] nonce = Base64.getDecoder().decode(key.trim());
            return nonce.length == 16;
        } catch (final IllegalArgumentException ex) {
            return false;
        }
    }
}
