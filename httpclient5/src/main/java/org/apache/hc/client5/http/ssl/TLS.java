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

import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.message.ParserCursor;

/**
 * Supported {@code TLS} protocol versions.
 *
 * @since 5.0
 */
public enum TLS {

    V_1_0("TLSv1",   new ProtocolVersion("TLS", 1, 0)),
    V_1_1("TLSv1.1", new ProtocolVersion("TLS", 1, 1)),
    V_1_2("TLSv1.2", new ProtocolVersion("TLS", 1, 2)),
    V_1_3("TLSv1.3", new ProtocolVersion("TLS", 1, 3));

    public final String ident;
    public final ProtocolVersion version;

    TLS(final String ident, final ProtocolVersion version) {
        this.ident = ident;
        this.version = version;
    }

    public boolean isSame(final ProtocolVersion protocolVersion) {
        return version.equals(protocolVersion);
    }

    public boolean isComparable(final ProtocolVersion protocolVersion) {
        return version.isComparable(protocolVersion);
    }

    public boolean greaterEquals(final ProtocolVersion protocolVersion) {
        return version.greaterEquals(protocolVersion);
    }

    public boolean lessEquals(final ProtocolVersion protocolVersion) {
        return version.lessEquals(protocolVersion);
    }

    public static ProtocolVersion parse(final String s) throws ParseException {
        if (s == null) {
            return null;
        }
        final ParserCursor cursor = new ParserCursor(0, s.length());
        return TlsVersionParser.INSTANCE.parse(s, cursor, null);
    }

}
