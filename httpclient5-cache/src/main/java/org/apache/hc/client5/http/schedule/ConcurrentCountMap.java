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
package org.apache.hc.client5.http.schedule;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.util.Args;

/**
 * Concurrent map of integer counts.
 *
 * @since 5.0
 *
 * @param <T> identifier used as a map key
 */
@Contract(threading = ThreadingBehavior.SAFE)
public final class ConcurrentCountMap<T> {

    private final ConcurrentMap<T, AtomicInteger> map;

    public ConcurrentCountMap() {
        this.map = new ConcurrentHashMap<>();
    }

    public int getCount(final T identifier) {
        Args.notNull(identifier, "Identifier");
        final AtomicInteger count = map.get(identifier);
        return count != null ? count.get() : 0;
    }

    public void resetCount(final T identifier) {
        Args.notNull(identifier, "Identifier");
        map.remove(identifier);
    }

    public int increaseCount(final T identifier) {
        Args.notNull(identifier, "Identifier");
        final AtomicInteger count = get(identifier);
        return count.incrementAndGet();
    }

    private AtomicInteger get(final T identifier) {
        AtomicInteger entry = map.get(identifier);
        if (entry == null) {
            final AtomicInteger newEntry = new AtomicInteger();
            entry = map.putIfAbsent(identifier, newEntry);
            if (entry == null) {
                entry = newEntry;
            }
        }
        return entry;
    }

}
