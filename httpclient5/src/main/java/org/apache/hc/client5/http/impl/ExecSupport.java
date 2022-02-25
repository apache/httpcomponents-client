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

/**
 * Request execution support methods.
 *
 * @since 5.0
 */
@Internal
public final class ExecSupport {

    private static final AtomicLong COUNT = new AtomicLong(0);

    public static long getNextExecNumber() {
        return COUNT.incrementAndGet();
    }

    public static String getNextExchangeId() {
        return createId(COUNT.incrementAndGet());
    }

    /**
     * Create an exchange ID.
     *
     * Hand rolled equivalent to `String.format("ex-%010d", value)` optimized to reduce
     * allocation and CPU overhead.
     */
    static String createId(final long value) {
        final String longString = Long.toString(value);
        return "ex-" + zeroPad(10 - longString.length()) + longString;
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
