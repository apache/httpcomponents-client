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

import java.util.concurrent.atomic.AtomicLong;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.util.Args;

/**
 * A thread safe incrementing identifier.
 *
 * @since 5.1.4
 */
@Internal
public final class PrefixedIncrementingId {

    private final AtomicLong count = new AtomicLong(0);
    private final String prefix0;
    private final String prefix1;
    private final String prefix2;
    private final String prefix3;
    private final String prefix4;
    private final String prefix5;
    private final String prefix6;
    private final String prefix7;
    private final String prefix8;
    private final String prefix9;

    /**
     * Creates an incrementing identifier.
     * @param prefix string prefix for generated IDs
     */
    public PrefixedIncrementingId(final String prefix) {
        this.prefix0 = Args.notNull(prefix, "prefix");
        this.prefix1 = prefix0 + '0';
        this.prefix2 = prefix1 + '0';
        this.prefix3 = prefix2 + '0';
        this.prefix4 = prefix3 + '0';
        this.prefix5 = prefix4 + '0';
        this.prefix6 = prefix5 + '0';
        this.prefix7 = prefix6 + '0';
        this.prefix8 = prefix7 + '0';
        this.prefix9 = prefix8 + '0';
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
        switch (longString.length()) {
            case 1:
                return prefix9 + longString;
            case 2:
                return prefix8 + longString;
            case 3:
                return prefix7 + longString;
            case 4:
                return prefix6 + longString;
            case 5:
                return prefix5 + longString;
            case 6:
                return prefix4 + longString;
            case 7:
                return prefix3 + longString;
            case 8:
                return prefix2 + longString;
            case 9:
                return prefix1 + longString;
            default:
                return prefix0 + longString;
        }
    }
}
