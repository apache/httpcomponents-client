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

import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.ProtocolVersionParser;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.http.message.ParserCursor;
import org.apache.hc.core5.http.ssl.TLS;
import org.apache.hc.core5.util.Tokenizer;

/**
 * Protocol switch handler.
 *
 * @since 5.4
 */
@Internal
public final class ProtocolSwitchStrategy {

    private static final ProtocolVersionParser PROTOCOL_VERSION_PARSER = ProtocolVersionParser.INSTANCE;
    private static final Tokenizer TOKENIZER = Tokenizer.INSTANCE;
    private static final Tokenizer.Delimiter UPGRADE_TOKEN_DELIMITER = Tokenizer.delimiters(',');
    private static final Tokenizer.Delimiter LAX_PROTO_DELIMITER = Tokenizer.delimiters('/', ',');

    @FunctionalInterface
    private interface HeaderConsumer {

        void accept(CharSequence buffer, ParserCursor cursor) throws ProtocolException;

    }

    private static class InternalProtocolException extends RuntimeException {

        public InternalProtocolException(final ProtocolException cause) {
            super(cause);
        }

    }

    public ProtocolVersion switchProtocol(final HttpMessage response) throws ProtocolException {
        final AtomicReference<ProtocolVersion> tlsUpgrade = new AtomicReference<>();

        parseHeaders(response, HttpHeaders.UPGRADE, (buffer, cursor) -> {
            final ProtocolVersion protocolVersion = parseProtocolVersion(buffer, cursor);
            if (protocolVersion != null) {
                if ("TLS".equalsIgnoreCase(protocolVersion.getProtocol())) {
                    tlsUpgrade.set(protocolVersion);
                } else if (!protocolVersion.equals(HttpVersion.HTTP_1_1)) {
                    throw new ProtocolException("Unsupported protocol or HTTP version: " + protocolVersion);
                }
            }
        });

        final ProtocolVersion result = tlsUpgrade.get();
        if (result != null) {
            return result;
        } else {
            throw new ProtocolException("Invalid protocol switch response: no TLS version found");
        }
    }

    private ProtocolVersion parseProtocolVersion(final CharSequence buffer, final ParserCursor cursor) throws ProtocolException {
        TOKENIZER.skipWhiteSpace(buffer, cursor);
        final String proto = TOKENIZER.parseToken(buffer, cursor, LAX_PROTO_DELIMITER);
        if (!cursor.atEnd()) {
            final char ch = buffer.charAt(cursor.getPos());
            if (ch == '/') {
                if (proto.isEmpty()) {
                    throw new ParseException("Invalid protocol", buffer, cursor.getLowerBound(), cursor.getUpperBound(), cursor.getPos());
                }
                cursor.updatePos(cursor.getPos() + 1);
                return PROTOCOL_VERSION_PARSER.parse(proto, null, buffer, cursor, UPGRADE_TOKEN_DELIMITER);
            }
        }
        if (proto.isEmpty()) {
            return null;
        } else if (proto.equalsIgnoreCase("TLS")) {
            return TLS.V_1_2.getVersion();
        } else {
            throw new ProtocolException("Unsupported or invalid protocol: " + proto);
        }
    }

    //TODO To be replaced by MessageSupport method that can propagate ProtocolException
    private void parseHeaders(final HttpMessage message,
                              final String name,
                              final HeaderConsumer consumer) throws ProtocolException {
        try {
            MessageSupport.parseHeaders(
                    message,
                    name,
                    (cs1, c1) ->
                            MessageSupport.parseElementList(
                                    cs1,
                                    c1,
                                    (cs2, c2) -> {
                                        try {
                                            consumer.accept(cs2, c2);
                                        } catch (final ProtocolException ex) {
                                            throw new InternalProtocolException(ex);
                                        }
                                    }));
        } catch (final InternalProtocolException ex) {
            throw (ProtocolException) ex.getCause();
        }
    }

}