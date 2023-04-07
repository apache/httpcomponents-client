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
package org.apache.hc.client5.http.impl.async;

import org.apache.hc.core5.http.Chars;
import org.apache.hc.core5.reactor.IOSession;
import org.slf4j.Logger;

import java.io.IOException;

import java.nio.ByteBuffer;

public class DataLogger {
    public DataLogger() {
    }

    public void logData(final ByteBuffer data, final String prefix, final Logger wireLog, final IOSession session) throws IOException {
        final byte[] line = new byte[16];
        final StringBuilder buf = new StringBuilder();
        while (data.hasRemaining()) {
            buf.setLength(0);
            buf.append(session).append(" ").append(prefix);
            final int chunk = Math.min(data.remaining(), line.length);
            data.get(line, 0, chunk);

            for (int i = 0; i < chunk; i++) {
                final char ch = (char) line[i];
                if (ch > Chars.SP && ch <= Chars.DEL) {
                    buf.append(ch);
                } else if (Character.isWhitespace(ch)) {
                    buf.append(' ');
                } else {
                    buf.append('.');
                }
            }
            for (int i = chunk; i < 17; i++) {
                buf.append(' ');
            }

            for (int i = 0; i < chunk; i++) {
                buf.append(' ');
                final int b = line[i] & 0xff;
                final String s = Integer.toHexString(b);
                if (s.length() == 1) {
                    buf.append("0");
                }
                buf.append(s);
            }
            wireLog.debug(buf.toString());
        }
    }
}
