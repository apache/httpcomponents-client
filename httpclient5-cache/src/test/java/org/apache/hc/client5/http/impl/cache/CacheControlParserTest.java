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

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CacheControlParserTest {

    private final CacheControlHeaderParser parser = CacheControlHeaderParser.INSTANCE;

    @Test
    public void testParseMaxAgeZero() {
        final Header header = new BasicHeader("Cache-Control", "max-age=0 , this = stuff;");
        final CacheControl cacheControl = parser.parse(header);
        assertEquals(0L, cacheControl.getMaxAge());
    }

    @Test
    public void testParseSMaxAge() {
        final Header header = new BasicHeader("Cache-Control", "s-maxage=3600");
        final CacheControl cacheControl = parser.parse(header);
        assertEquals(3600L, cacheControl.getSharedMaxAge());
    }

    @Test
    public void testParseInvalidCacheValue() {
        final Header header = new BasicHeader("Cache-Control", "max-age=invalid");
        final CacheControl cacheControl = parser.parse(header);
        assertEquals(-1L, cacheControl.getMaxAge());
    }

    @Test
    public void testParseInvalidHeader() {
        final Header header = new BasicHeader("Cache-Control", "max-age");
        final CacheControl cacheControl = parser.parse(header);
        assertEquals(-1L, cacheControl.getMaxAge());
    }

    @Test
    public void testParseNullHeader() {
        final Header header = null;
        assertThrows(NullPointerException.class, () -> parser.parse(header));
    }

    @Test
    public void testParseEmptyHeader() {
        final Header header = new BasicHeader("Cache-Control", "");
        final CacheControl cacheControl = parser.parse(header);
        assertEquals(-1L, cacheControl.getMaxAge());
    }

    @Test
    public void testParseCookieEmptyValue() {
        final Header header = new BasicHeader("Cache-Control", "max-age=;");
        final CacheControl cacheControl = parser.parse(header);
        assertEquals(-1L, cacheControl.getMaxAge());
    }

    @Test
    public void testParseNoCache() {
        final Header header = new BasicHeader(" Cache-Control", "no-cache");
        final CacheControl cacheControl = parser.parse(header);
        assertEquals(-1L, cacheControl.getMaxAge());
    }

    @Test
    public void testParseNoDirective() {
        final Header header = new BasicHeader(" Cache-Control", "");
        final CacheControl cacheControl = parser.parse(header);
        assertEquals(-1L, cacheControl.getMaxAge());
    }

    @Test
    public void testGarbage() {
        final Header header = new BasicHeader("Cache-Control", ",,= blah,");
        final CacheControl cacheControl = parser.parse(header);
        assertEquals(-1L, cacheControl.getMaxAge());
    }

}
