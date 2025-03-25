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

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.ProtocolVersionParser;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.http.ssl.TLS;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.apache.hc.core5.util.Tokenizer;

/**
 * Protocol switch handler.
 *
 * @since 5.4
 */
@Internal
public final class ProtocolSwitchStrategy {

    private static final ProtocolVersionParser PARSER = ProtocolVersionParser.INSTANCE;

    public ProtocolVersion switchProtocol(final HttpMessage response) throws ProtocolException {
        final Iterator<String> it = MessageSupport.iterateTokens(response, HttpHeaders.UPGRADE);
        ProtocolVersion tlsUpgrade = null;
        ProtocolVersion httpUpgrade = null;

        while (it.hasNext()) {
            final String token = it.next().trim();
            try {
                if (token.startsWith("TLS") || token.startsWith("HTTP/")) {
                    final ProtocolVersion version = parseToken(token);
                    if (token.startsWith("TLS")) {
                        tlsUpgrade = version;
                    } else {
                        httpUpgrade = version;
                    }
                } else {
                    throw new ProtocolException("Unsupported protocol: " + token);
                }
            } catch (final ParseException ex) {
                if (token.startsWith("TLS")) {
                    throw new ProtocolException("Invalid TLS protocol: " + token, ex);
                } else if (token.startsWith("HTTP/")) {
                    throw new ProtocolException("Invalid HTTP protocol: " + token, ex);
                } else {
                    throw new ProtocolException("Unsupported protocol: " + token, ex);
                }
            }
        }

        if (tlsUpgrade != null) {
            return tlsUpgrade;
        } else if (httpUpgrade != null) {
            return httpUpgrade;
        } else {
            throw new ProtocolException("Invalid protocol switch response");
        }
    }

    private ProtocolVersion parseToken(final String token) throws ParseException {
        if (token.equals("TLS")) {
            return TLS.V_1_2.getVersion();
        }
        final CharArrayBuffer buffer = new CharArrayBuffer(token.length());
        buffer.append(token);
        final Tokenizer.Cursor cursor = new Tokenizer.Cursor(0, buffer.length());
        return PARSER.parse(buffer, cursor, null);
    }
}
