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
package org.apache.hc.client5.http.impl;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.net.PercentCodec;
import org.apache.hc.core5.util.Args;

/**
 * Codec for the HTTP {@code ALPN} header field (RFC 7639).
 *
 * @since 5.7
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
@Internal
public final class AlpnHeaderSupport {

    private AlpnHeaderSupport() {
    }

    /**
     * Formats a list of raw ALPN protocol IDs into a single {@code ALPN} header.
     */
    public static Header formatValue(final List<String> protocolIds) {
        Args.notEmpty(protocolIds, "protocolIds");
        return MessageSupport.headerOfTokens(HttpHeaders.ALPN, protocolIds, AlpnHeaderSupport::encodeId);
    }

    /**
     * Parses an {@code ALPN} header into decoded protocol IDs.
     *
     * @throws ProtocolException if a token is not a well-formed percent-encoded protocol ID.
     */
    public static List<String> parseValue(final Header header) throws ProtocolException {
        final List<String> tokens = new ArrayList<>();
        MessageSupport.parseTokens(header, tokens::add);
        final List<String> out = new ArrayList<>(tokens.size());
        for (final String token : tokens) {
            out.add(decodeId(token));
        }
        return out;
    }

    /**
     * Encodes a single raw protocol ID to canonical token form using the HTTP token codec
     * from core, which keeps RFC 7230 {@code tchar} octets literal and percent-encodes the
     * rest (including {@code '%'}) with uppercase hexadecimal.
     */
    public static String encodeId(final String id) {
        Args.notBlank(id, "id");
        return PercentCodec.HTTP_TOKEN.encode(id);
    }

    /**
     * Decodes a percent-encoded token to a raw protocol ID using UTF-8.
     * <p>
     * A {@code '%'} that is not followed by two hexadecimal digits is a malformed
     * token and is rejected as a protocol error.
     *
     * @throws ProtocolException if the token contains malformed percent-encoding.
     */
    public static String decodeId(final String token) throws ProtocolException {
        Args.notBlank(token, "token");
        for (int i = 0; i < token.length(); i++) {
            if (token.charAt(i) == '%') {
                if (i + 2 >= token.length()
                        || Character.digit(token.charAt(i + 1), 16) < 0
                        || Character.digit(token.charAt(i + 2), 16) < 0) {
                    throw new ProtocolException("Malformed percent-encoding in ALPN protocol id: " + token);
                }
                i += 2;
            }
        }
        return PercentCodec.decode(token, StandardCharsets.UTF_8);
    }

}
