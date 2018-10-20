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

package org.apache.hc.client5.http.ssl;

import java.util.BitSet;

import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.message.ParserCursor;
import org.apache.hc.core5.http.message.TokenParser;

final class TlsVersionParser {

    public final static TlsVersionParser INSTANCE = new TlsVersionParser();

    private final TokenParser tokenParser;

    TlsVersionParser() {
        this.tokenParser = TokenParser.INSTANCE;
    }

    ProtocolVersion parse(
            final CharSequence buffer,
            final ParserCursor cursor,
            final BitSet delimiters) throws ParseException {
        final int lowerBound = cursor.getLowerBound();
        final int upperBound = cursor.getUpperBound();

        int pos = cursor.getPos();
        if (pos + 4 > cursor.getUpperBound()) {
            throw new ParseException("Invalid TLS protocol version", buffer, lowerBound, upperBound, pos);
        }
        if (buffer.charAt(pos) != 'T' || buffer.charAt(pos + 1) != 'L' || buffer.charAt(pos + 2) != 'S'
                || buffer.charAt(pos + 3) != 'v') {
            throw new ParseException("Invalid TLS protocol version", buffer, lowerBound, upperBound, pos);
        }
        pos = pos + 4;
        cursor.updatePos(pos);
        if (cursor.atEnd()) {
            throw new ParseException("Invalid TLS version", buffer, lowerBound, upperBound, pos);
        }
        final String s = this.tokenParser.parseToken(buffer, cursor, delimiters);
        final int idx = s.indexOf('.');
        if (idx == -1) {
            final int major;
            try {
                major = Integer.parseInt(s);
            } catch (final NumberFormatException e) {
                throw new ParseException("Invalid TLS major version", buffer, lowerBound, upperBound, pos);
            }
            return new ProtocolVersion("TLS", major, 0);
        } else {
            final String s1 = s.substring(0, idx);
            final int major;
            try {
                major = Integer.parseInt(s1);
            } catch (final NumberFormatException e) {
                throw new ParseException("Invalid TLS major version", buffer, lowerBound, upperBound, pos);
            }
            final String s2 = s.substring(idx + 1);
            final int minor;
            try {
                minor = Integer.parseInt(s2);
            } catch (final NumberFormatException e) {
                throw new ParseException("Invalid TLS minor version", buffer, lowerBound, upperBound, pos);
            }
            return new ProtocolVersion("TLS", major, minor);
        }
    }

    ProtocolVersion parse(final String s) throws ParseException {
        if (s == null) {
            return null;
        }
        final ParserCursor cursor = new ParserCursor(0, s.length());
        return parse(s, cursor, null);
    }

}

