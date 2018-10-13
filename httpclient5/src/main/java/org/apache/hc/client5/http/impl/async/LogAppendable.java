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

import java.io.IOException;

import org.slf4j.Logger;

final class LogAppendable implements Appendable {

    private final Logger log;
    private final String prefix;
    private final StringBuilder buffer;

    public LogAppendable(final Logger log, final String prefix) {
        this.log = log;
        this.prefix = prefix;
        this.buffer = new StringBuilder();
    }


    @Override
    public Appendable append(final CharSequence text) throws IOException {
        return append(text, 0, text.length());
    }

    @Override
    public Appendable append(final CharSequence text, final int start, final int end) throws IOException {
        for (int i = start; i < end; i++) {
            append(text.charAt(i));
        }
        return this;
    }

    @Override
    public Appendable append(final char ch) throws IOException {
        if (ch == '\n') {
            log.debug(prefix + " " + buffer.toString());
            buffer.setLength(0);
        } else if (ch != '\r') {
            buffer.append(ch);
        }
        return this;
    }

    public void flush() {
        if (buffer.length() > 0) {
            log.debug(prefix + " " + buffer.toString());
            buffer.setLength(0);
        }
    }

}
