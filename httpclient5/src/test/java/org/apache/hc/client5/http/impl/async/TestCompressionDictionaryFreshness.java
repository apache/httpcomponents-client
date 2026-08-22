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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.junit.jupiter.api.Test;

class TestCompressionDictionaryFreshness {

    private static final Instant REQUEST_TIME = Instant.parse("2026-08-21T11:59:58Z");
    private static final Instant RESPONSE_TIME = Instant.parse("2026-08-21T12:00:00Z");

    @Test
    void testMaxAgeUsesCorrectedInitialAge() {
        final BasicHttpResponse response = new BasicHttpResponse(200);
        response.addHeader(HttpHeaders.CACHE_CONTROL, "max-age=60");
        response.addHeader(HttpHeaders.DATE, "Fri, 21 Aug 2026 11:59:50 GMT");
        response.addHeader(HttpHeaders.AGE, "5");

        assertEquals(Instant.parse("2026-08-21T12:00:50Z"),
                CompressionDictionaryFreshness.determineValidUntil(
                        response, REQUEST_TIME, RESPONSE_TIME));
    }

    @Test
    void testExpiresSuppliesFreshnessLifetime() {
        final BasicHttpResponse response = new BasicHttpResponse(200);
        response.addHeader(HttpHeaders.DATE, "Fri, 21 Aug 2026 12:00:00 GMT");
        response.addHeader(HttpHeaders.EXPIRES, "Fri, 21 Aug 2026 12:01:00 GMT");

        assertEquals(Instant.parse("2026-08-21T12:00:58Z"),
                CompressionDictionaryFreshness.determineValidUntil(
                        response, REQUEST_TIME, RESPONSE_TIME));
    }

    @Test
    void testObsoleteHttpDateFormatsAreAccepted() {
        final BasicHttpResponse rfc850 = new BasicHttpResponse(200);
        rfc850.addHeader(HttpHeaders.DATE, "Friday, 21-Aug-26 12:00:00 GMT");
        rfc850.addHeader(HttpHeaders.EXPIRES, "Friday, 21-Aug-26 12:01:00 GMT");
        assertEquals(Instant.parse("2026-08-21T12:00:58Z"),
                CompressionDictionaryFreshness.determineValidUntil(
                        rfc850, REQUEST_TIME, RESPONSE_TIME));

        final BasicHttpResponse asctime = new BasicHttpResponse(200);
        asctime.addHeader(HttpHeaders.DATE, "Fri Aug 21 12:00:00 2026");
        asctime.addHeader(HttpHeaders.EXPIRES, "Fri Aug 21 12:01:00 2026");
        assertEquals(Instant.parse("2026-08-21T12:00:58Z"),
                CompressionDictionaryFreshness.determineValidUntil(
                        asctime, REQUEST_TIME, RESPONSE_TIME));
    }

    @Test
    void testNoStoreIsNotStorable() {
        final BasicHttpResponse response = new BasicHttpResponse(200);
        response.addHeader(HttpHeaders.CACHE_CONTROL, "max-age=60, no-store");

        assertNull(CompressionDictionaryFreshness.determineValidUntil(
                response, REQUEST_TIME, RESPONSE_TIME));
    }

    @Test
    void testNoCacheIsNotFresh() {
        final BasicHttpResponse response = new BasicHttpResponse(200);
        response.addHeader(HttpHeaders.CACHE_CONTROL, "max-age=60, no-cache");

        assertNull(CompressionDictionaryFreshness.determineValidUntil(
                response, REQUEST_TIME, RESPONSE_TIME));
    }

    @Test
    void testMalformedMaxAgeFailsClosed() {
        final BasicHttpResponse response = new BasicHttpResponse(200);
        response.addHeader(HttpHeaders.CACHE_CONTROL, "max-age=invalid");

        assertNull(CompressionDictionaryFreshness.determineValidUntil(
                response, REQUEST_TIME, RESPONSE_TIME));
    }

    @Test
    void testAlreadyStaleResponseIsNotEligible() {
        final BasicHttpResponse response = new BasicHttpResponse(200);
        response.addHeader(HttpHeaders.CACHE_CONTROL, "max-age=5");
        response.addHeader(HttpHeaders.DATE, "Fri, 21 Aug 2026 11:59:50 GMT");

        assertNull(CompressionDictionaryFreshness.determineValidUntil(
                response, REQUEST_TIME, RESPONSE_TIME));
    }

}
