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
package org.apache.hc.client5.http.sse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hc.client5.http.sse.impl.ExponentialJitterBackoff;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class ExponentialJitterBackoffTest {

    @Test
    void usesServerHintAndClamps() {
        final ExponentialJitterBackoff b = new ExponentialJitterBackoff(1000, 30000, 2.0, 250);
        long d = b.nextDelayMs(5, 0, 40L); // < min -> clamp to min
        assertEquals(250L, d);

        d = b.nextDelayMs(5, 0, 999999L); // > max -> clamp to max
        assertEquals(30000L, d);
    }

    @RepeatedTest(5)
    void jitterWithinRange() {
        final ExponentialJitterBackoff b = new ExponentialJitterBackoff(1000, 8000, 2.0, 250);
        final long d1 = b.nextDelayMs(1, 0, null); // cap=1000
        assertTrue(d1 >= 250 && d1 <= 1000, "attempt1 in [250,1000]");

        final long d2 = b.nextDelayMs(2, d1, null); // cap=2000
        assertTrue(d2 >= 250 && d2 <= 2000, "attempt2 in [250,2000]");

        final long d4 = b.nextDelayMs(4, d2, null); // cap=8000
        assertTrue(d4 >= 250 && d4 <= 8000, "attempt4 in [250,8000]");
    }
}
