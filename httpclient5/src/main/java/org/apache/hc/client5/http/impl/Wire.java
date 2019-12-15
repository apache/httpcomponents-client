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

import java.nio.ByteBuffer;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.util.Args;
import org.slf4j.Logger;

@Internal
public class Wire {

    private static final int MAX_STRING_BUILDER_SIZE = 2048;

    private static final ThreadLocal<StringBuilder> THREAD_LOCAL = new ThreadLocal<>();

    /**
     * Returns a {@code StringBuilder} that this Layout implementation can use to write the formatted log event to.
     *
     * @return a {@code StringBuilder}
     */
    private static StringBuilder getStringBuilder() {
        StringBuilder result = THREAD_LOCAL.get();
        if (result == null) {
            result = new StringBuilder(MAX_STRING_BUILDER_SIZE);
            THREAD_LOCAL.set(result);
        }
        trimToMaxSize(result, MAX_STRING_BUILDER_SIZE);
        result.setLength(0);
        return result;
    }

    /**
     * Ensures that the char[] array of the specified StringBuilder does not exceed the specified number of characters.
     * This method is useful to ensure that excessively long char[] arrays are not kept in memory forever.
     *
     * @param stringBuilder the StringBuilder to check
     * @param maxSize the maximum number of characters the StringBuilder is allowed to have
     */
    private static void trimToMaxSize(final StringBuilder stringBuilder, final int maxSize) {
        if (stringBuilder != null && stringBuilder.capacity() > maxSize) {
            stringBuilder.setLength(maxSize);
            stringBuilder.trimToSize();
        }
    }

    private final Logger log;
    private final String id;

    public Wire(final Logger log, final String id) {
        super();
        this.log = log;
        this.id = id;
    }

    private void wire(final String header, final byte[] b, final int pos, final int off) {
        final StringBuilder buffer = getStringBuilder();
        for (int i = 0; i < off; i++) {
            final int ch = b[pos + i];
            if (ch == 13) {
                buffer.append("[\\r]");
            } else if (ch == 10) {
                    buffer.append("[\\n]\"");
                    buffer.insert(0, "\"");
                    buffer.insert(0, header);
                    this.log.debug(this.id + " " + buffer.toString());
                    buffer.setLength(0);
            } else if ((ch < 32) || (ch >= 127)) {
                buffer.append("[0x");
                buffer.append(Integer.toHexString(ch));
                buffer.append("]");
            } else {
                buffer.append((char) ch);
            }
        }
        if (buffer.length() > 0) {
            buffer.append('\"');
            buffer.insert(0, '\"');
            buffer.insert(0, header);
            this.log.debug(this.id + " " + buffer.toString());
        }
    }


    public boolean isEnabled() {
        return this.log.isDebugEnabled();
    }

    public void output(final byte[] b, final int pos, final int off) {
        Args.notNull(b, "Output");
        wire(">> ", b, pos, off);
    }

    public void input(final byte[] b, final int pos, final int off) {
        Args.notNull(b, "Input");
        wire("<< ", b, pos, off);
    }

    public void output(final byte[] b) {
        Args.notNull(b, "Output");
        output(b, 0, b.length);
    }

    public void input(final byte[] b) {
        Args.notNull(b, "Input");
        input(b, 0, b.length);
    }

    public void output(final int b) {
        output(new byte[] {(byte) b});
    }

    public void input(final int b) {
        input(new byte[] {(byte) b});
    }

    public void output(final String s) {
        Args.notNull(s, "Output");
        output(s.getBytes());
    }

    public void input(final String s) {
        Args.notNull(s, "Input");
        input(s.getBytes());
    }

    public void output(final ByteBuffer b) {
        Args.notNull(b, "Output");
        if (b.hasArray()) {
            output(b.array(), b.arrayOffset() + b.position(), b.remaining());
        } else {
            final byte[] tmp = new byte[b.remaining()];
            b.get(tmp);
            output(tmp);
        }
    }

    public void input(final ByteBuffer b) {
        Args.notNull(b, "Input");
        if (b.hasArray()) {
            input(b.array(), b.arrayOffset() + b.position(), b.remaining());
        } else {
            final byte[] tmp = new byte[b.remaining()];
            b.get(tmp);
            input(tmp);
        }
    }

}
