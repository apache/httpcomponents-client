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
package org.apache.hc.client5.http.impl.cache;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.apache.hc.client5.http.cache.RequestCacheControl;
import org.apache.hc.client5.http.cache.ResponseCacheControl;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.jupiter.api.Test;

public class CacheControlParserTest {

    private final CacheControlHeaderParser parser = CacheControlHeaderParser.INSTANCE;

    @Test
    public void testParseMaxAgeZero() {
        final Header header = new BasicHeader("Cache-Control", "max-age=0 , this = stuff;");
        final ResponseCacheControl cacheControl = parser.parseResponse(Collections.singletonList(header).iterator());
        assertEquals(0L, cacheControl.getMaxAge());
    }

    @Test
    public void testParseSMaxAge() {
        final Header header = new BasicHeader("Cache-Control", "s-maxage=3600");
        final ResponseCacheControl cacheControl = parser.parseResponse(Collections.singletonList(header).iterator());
        assertEquals(3600L, cacheControl.getSharedMaxAge());
    }

    @Test
    public void testParseInvalidCacheValue() {
        final Header header = new BasicHeader("Cache-Control", "max-age=invalid");
        final ResponseCacheControl cacheControl = parser.parseResponse(Collections.singletonList(header).iterator());
        assertEquals(0L, cacheControl.getMaxAge());
    }

    @Test
    public void testParseInvalidHeader() {
        final Header header = new BasicHeader("Cache-Control", "max-age");
        final ResponseCacheControl cacheControl = parser.parseResponse(Collections.singletonList(header).iterator());
        assertEquals(-1L, cacheControl.getMaxAge());
    }

    @Test
    public void testParseNullHeader() {
        final Header header = null;
        assertThrows(NullPointerException.class, () -> parser.parseResponse(Collections.singletonList(header).iterator()));
    }

    @Test
    public void testParseEmptyHeader() {
        final Header header = new BasicHeader("Cache-Control", "");
        final ResponseCacheControl cacheControl = parser.parseResponse(Collections.singletonList(header).iterator());
        assertEquals(-1L, cacheControl.getMaxAge());
    }

    @Test
    public void testParseCookieEmptyValue() {
        final Header header = new BasicHeader("Cache-Control", "max-age=,");
        final ResponseCacheControl cacheControl = parser.parseResponse(Collections.singletonList(header).iterator());
        assertEquals(-1L, cacheControl.getMaxAge());
    }

    @Test
    public void testParseNoCache() {
        final Header header = new BasicHeader(" Cache-Control", "no-cache");
        final ResponseCacheControl cacheControl = parser.parseResponse(Collections.singletonList(header).iterator());
        assertEquals(-1L, cacheControl.getMaxAge());
    }

    @Test
    public void testParseNoDirective() {
        final Header header = new BasicHeader(" Cache-Control", "");
        final ResponseCacheControl cacheControl = parser.parseResponse(Collections.singletonList(header).iterator());
        assertEquals(-1L, cacheControl.getMaxAge());
    }

    @Test
    public void testGarbage() {
        final Header header = new BasicHeader("Cache-Control", ",,= blah,");
        final ResponseCacheControl cacheControl = parser.parseResponse(Collections.singletonList(header).iterator());
        assertEquals(-1L, cacheControl.getMaxAge());
    }


    @Test
    public void testParseMultipleDirectives() {
        final Header header = new BasicHeader("Cache-Control", "max-age=604800, stale-while-revalidate=86400, s-maxage=3600, must-revalidate, private");
        final ResponseCacheControl cacheControl = parser.parseResponse(Collections.singletonList(header).iterator());

        assertAll("Must all pass",
                () -> assertEquals(604800L, cacheControl.getMaxAge()),
                () -> assertEquals(3600L, cacheControl.getSharedMaxAge()),
                () -> assertTrue(cacheControl.isMustRevalidate()),
                () -> assertTrue(cacheControl.isCachePrivate())
        );
    }

    @Test
    public void testParseMultipleDirectives2() {
        final Header header = new BasicHeader("Cache-Control", "max-age=604800, stale-while-revalidate=86400, must-revalidate, private, s-maxage=3600");
        final ResponseCacheControl cacheControl = parser.parseResponse(Collections.singletonList(header).iterator());

        assertAll("Must all pass",
                () -> assertEquals(604800L, cacheControl.getMaxAge()),
                () -> assertEquals(3600L, cacheControl.getSharedMaxAge()),
                () -> assertTrue(cacheControl.isMustRevalidate()),
                () -> assertTrue(cacheControl.isCachePrivate())
        );
    }

    @Test
    public void testParsePublic() {
        final Header header = new BasicHeader("Cache-Control", "public");
        final ResponseCacheControl cacheControl = parser.parseResponse(Collections.singletonList(header).iterator());

        assertTrue(cacheControl.isPublic());
    }

    @Test
    public void testParsePrivate() {
        final Header header = new BasicHeader("Cache-Control", "private");
        final ResponseCacheControl cacheControl = parser.parseResponse(Collections.singletonList(header).iterator());

        assertTrue(cacheControl.isCachePrivate());
    }

    @Test
    public void testParseNoStore() {
        final Header header = new BasicHeader("Cache-Control", "no-store");
        final ResponseCacheControl cacheControl = parser.parseResponse(Collections.singletonList(header).iterator());

        assertTrue(cacheControl.isNoStore());
    }

    @Test
    public void testParseStaleWhileRevalidate() {
        final Header header = new BasicHeader("Cache-Control", "max-age=3600, stale-while-revalidate=120");
        final ResponseCacheControl cacheControl = parser.parseResponse(Collections.singletonList(header).iterator());

        assertEquals(120, cacheControl.getStaleWhileRevalidate());
    }

    @Test
    public void testParseNoCacheFields() {
        final Header header = new BasicHeader("Cache-Control", "no-cache=\"Set-Cookie, Content-Language\", stale-while-revalidate=120");
        final ResponseCacheControl cacheControl = parser.parseResponse(Collections.singletonList(header).iterator());

        assertTrue(cacheControl.isNoCache());
        assertEquals(2, cacheControl.getNoCacheFields().size());
        assertTrue(cacheControl.getNoCacheFields().contains("Set-Cookie"));
        assertTrue(cacheControl.getNoCacheFields().contains("Content-Language"));
        assertEquals(120, cacheControl.getStaleWhileRevalidate());
    }

    @Test
    public void testParseNoCacheFieldsNoQuote() {
        final Header header = new BasicHeader("Cache-Control", "no-cache=Set-Cookie, Content-Language, stale-while-revalidate=120");
        final ResponseCacheControl cacheControl = parser.parseResponse(Collections.singletonList(header).iterator());

        assertTrue(cacheControl.isNoCache());
        assertEquals(1, cacheControl.getNoCacheFields().size());
        assertTrue(cacheControl.getNoCacheFields().contains("Set-Cookie"));
        assertEquals(120, cacheControl.getStaleWhileRevalidate());
    }

    @Test
    public void testParseNoCacheFieldsMessy() {
        final Header header = new BasicHeader("Cache-Control", "no-cache=\"  , , ,,, Set-Cookie  , , Content-Language ,   \", stale-while-revalidate=120");
        final ResponseCacheControl cacheControl = parser.parseResponse(Collections.singletonList(header).iterator());

        assertTrue(cacheControl.isNoCache());
        assertEquals(2, cacheControl.getNoCacheFields().size());
        assertTrue(cacheControl.getNoCacheFields().contains("Set-Cookie"));
        assertTrue(cacheControl.getNoCacheFields().contains("Content-Language"));
        assertEquals(120, cacheControl.getStaleWhileRevalidate());
    }


    @Test
    public void testParseMultipleHeaders() {
        // Create headers
        final Header header1 = new BasicHeader("Cache-Control", "max-age=3600, no-store");
        final Header header2 = new BasicHeader("Cache-Control", "private, must-revalidate");
        final Header header3 = new BasicHeader("Cache-Control", "max-age=3600");
        final Header header4 = new BasicHeader("Cache-Control", "no-store");
        final Header header5 = new BasicHeader("Cache-Control", "private");
        final Header header6 = new BasicHeader("Cache-Control", "must-revalidate");

        // Parse headers
        final ResponseCacheControl cacheControl1 = parser.parseResponse(Arrays.asList(header1, header2).iterator());
        final ResponseCacheControl cacheControl2 = parser.parseResponse(Arrays.asList(header3, header4, header5, header6).iterator());

        // Validate Cache-Control directives
        assertEquals(cacheControl1.getMaxAge(), cacheControl2.getMaxAge());
        assertEquals(cacheControl1.isNoStore(), cacheControl2.isNoStore());
        assertEquals(cacheControl1.isCachePrivate(), cacheControl2.isCachePrivate());
        assertEquals(cacheControl1.isMustRevalidate(), cacheControl2.isMustRevalidate());
    }

    @Test
    public void testParseRequestMultipleDirectives() {
        final Header header = new BasicHeader("Cache-Control", "blah, max-age=1111, max-stale=2222, " +
                "min-fresh=3333, no-cache, no-store, no-cache, no-stuff, only-if-cached, only-if-cached-or-maybe-not");
        final RequestCacheControl cacheControl = parser.parseRequest(Collections.singletonList(header).iterator());

        assertAll("Must all pass",
                () -> assertEquals(1111L, cacheControl.getMaxAge()),
                () -> assertEquals(2222L, cacheControl.getMaxStale()),
                () -> assertEquals(3333L, cacheControl.getMinFresh()),
                () -> assertTrue(cacheControl.isNoCache()),
                () -> assertTrue(cacheControl.isNoCache()),
                () -> assertTrue(cacheControl.isOnlyIfCached())
        );
    }

    @Test
    public void testParseIsImmutable() {
        final Header header = new BasicHeader("Cache-Control", "max-age=0 , immutable");
        final ResponseCacheControl cacheControl = parser.parseResponse(Collections.singletonList(header).iterator());
        assertTrue(cacheControl.isImmutable());
    }

    @Test
    public void testParseMultipleIsImmutable() {
        final Header header = new BasicHeader("Cache-Control", "immutable, nmax-age=0 , immutable");
        final ResponseCacheControl cacheControl = parser.parseResponse(Collections.singletonList(header).iterator());
        assertTrue(cacheControl.isImmutable());
    }

}
