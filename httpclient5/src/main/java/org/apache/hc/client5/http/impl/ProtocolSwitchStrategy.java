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

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.FormattedHeader;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.ProtocolVersionParser;
import org.apache.hc.core5.http.ssl.TLS;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.CharArrayBuffer;
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

    @FunctionalInterface
    private interface HeaderConsumer {
        void accept(CharSequence buffer, Tokenizer.Cursor cursor) throws ProtocolException;
    }

    public ProtocolVersion switchProtocol(final HttpMessage response) throws ProtocolException {
        final AtomicReference<ProtocolVersion> tlsUpgrade = new AtomicReference<>();

        parseHeaders(response, HttpHeaders.UPGRADE, (buffer, cursor) -> {
            while (!cursor.atEnd()) {
                TOKENIZER.skipWhiteSpace(buffer, cursor);
                if (cursor.atEnd()) {
                    break;
                }
                final int tokenStart = cursor.getPos();
                TOKENIZER.parseToken(buffer, cursor, UPGRADE_TOKEN_DELIMITER);
                final int tokenEnd = cursor.getPos();
                if (tokenStart < tokenEnd) {
                    final ProtocolVersion version = parseProtocolToken(buffer, tokenStart, tokenEnd);
                    if (version != null && "TLS".equalsIgnoreCase(version.getProtocol())) {
                        tlsUpgrade.set(version);
                    }
                }
                if (!cursor.atEnd()) {
                    cursor.updatePos(cursor.getPos() + 1);
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

    private ProtocolVersion parseProtocolToken(final CharSequence buffer, final int start, final int end)
            throws ProtocolException {
        if (start >= end) {
            return null;
        }

        if (end - start == 3) {
            final char c0 = buffer.charAt(start);
            final char c1 = buffer.charAt(start + 1);
            final char c2 = buffer.charAt(start + 2);
            if ((c0 == 'T' || c0 == 't') &&
                    (c1 == 'L' || c1 == 'l') &&
                    (c2 == 'S' || c2 == 's')) {
                return TLS.V_1_2.getVersion();
            }
        }

        try {
            final Tokenizer.Cursor cursor = new Tokenizer.Cursor(start, end);
            final ProtocolVersion version = PROTOCOL_VERSION_PARSER.parse(buffer, cursor, null);

            if ("TLS".equalsIgnoreCase(version.getProtocol())) {
                return version;
            } else if (version.equals(HttpVersion.HTTP_1_1)) {
                return null;
            } else {
                throw new ProtocolException("Unsupported protocol or HTTP version: " + buffer.subSequence(start, end));
            }
        } catch (final ParseException ex) {
            throw new ProtocolException("Invalid protocol: " + buffer.subSequence(start, end), ex);
        }
    }

    private void parseHeaders(final HttpMessage message, final String name, final HeaderConsumer consumer)
            throws ProtocolException {
        Args.notNull(message, "Message headers");
        Args.notBlank(name, "Header name");
        final Iterator<Header> it = message.headerIterator(name);
        while (it.hasNext()) {
            parseHeader(it.next(), consumer);
        }
    }

    private void parseHeader(final Header header, final HeaderConsumer consumer) throws ProtocolException {
        Args.notNull(header, "Header");
        if (header instanceof FormattedHeader) {
            final CharArrayBuffer buf = ((FormattedHeader) header).getBuffer();
            final Tokenizer.Cursor cursor = new Tokenizer.Cursor(0, buf.length());
            cursor.updatePos(((FormattedHeader) header).getValuePos());
            consumer.accept(buf, cursor);
        } else {
            final String value = header.getValue();
            if (value == null) {
                return;
            }
            final Tokenizer.Cursor cursor = new Tokenizer.Cursor(0, value.length());
            consumer.accept(value, cursor);
        }
    }
}