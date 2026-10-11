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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.entity.compress.BasicCompressionDictionaryStore;
import org.apache.hc.client5.http.entity.compress.CompressionDictionary;
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie;
import org.junit.jupiter.api.Test;

class TestCompressionDictionaryCookieStore {

    @Test
    void testClearRemovesCookiesAndCompressionDictionaries() {
        final BasicCompressionDictionaryStore dictionaryStore =
                new BasicCompressionDictionaryStore();
        final CookieStore cookieStore = new CompressionDictionaryCookieStore(
                new BasicCookieStore(), dictionaryStore);
        cookieStore.addCookie(new BasicClientCookie("name", "value"));
        final Instant now = Instant.now();
        final URI source = URI.create("https://example.com/dictionary");
        dictionaryStore.add(cookieStore, new CompressionDictionary(
                "dictionary".getBytes(StandardCharsets.UTF_8),
                source,
                "/*",
                "",
                now,
                now.plusSeconds(60)));
        assertFalse(cookieStore.getCookies().isEmpty());

        cookieStore.clear();

        assertTrue(cookieStore.getCookies().isEmpty());
        assertTrue(dictionaryStore.getByOrigin(cookieStore, source).isEmpty());
    }

}
