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

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.util.Args;

/**
 * A thread safe incrementing identifier.
 *
 * @since 5.1.4
 */
@Internal
public final class IncrementingId {

    private final AtomicLong count = new AtomicLong(0);
    private final String prefix;
    private final int numberWidth;

    /**
     * Creates an incrementing identifier.
     * @param prefix string prefix for generated IDs
     * @param numberWidth width of ID number to be zero-padded
     */
    public IncrementingId(final String prefix, final int numberWidth) {
        Args.notNull(prefix, "prefix");
        Args.notNegative(numberWidth, "numberWidth");
        this.prefix = prefix;
        this.numberWidth = numberWidth;
    }

    public long getNextNumber() {
        return count.incrementAndGet();
    }

    public String getNextId() {
        return createId(count.incrementAndGet());
    }

    /**
     * Create an ID from this instance's prefix and zero padded specified value.
     *
     * Hand rolled equivalent to `String.format("ex-%010d", value)` optimized to reduce
     * allocation and CPU overhead.
     */
    String createId(final long value) {
        final String longString = Long.toString(value);
        return prefix + zeroPad(numberWidth - longString.length()) + longString;
    }

    /**
     * Hand rolled equivalent to JDK 11 `"0".repeat(count)` due to JDK 8 dependency
     */
    private static String zeroPad(final int leadingZeros) {
        if (leadingZeros <= 0) {
            return "";
        }
        final char[] zeros = new char[leadingZeros];
        Arrays.fill(zeros, '0');
        return new String(zeros);
    }
}
