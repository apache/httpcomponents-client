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
package org.apache.hc.client5.http.entity.compress;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

class TestCompressionDictionary {

    private static final byte[] CONTENT = "the quick brown fox".getBytes(StandardCharsets.UTF_8);
    private static final URI SOURCE = URI.create("https://example.com/dict.bin");
    private static final String MATCH = "/*";
    private static final String ID = "dict-1";
    private static final Instant STORED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant VALID_UNTIL = Instant.parse("2026-02-01T00:00:00Z");

    private static CompressionDictionary newDictionary() {
        return new CompressionDictionary(CONTENT, SOURCE, MATCH, ID, STORED_AT, VALID_UNTIL);
    }

    private static byte[] sha256(final byte[] content) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(content);
        } catch (final NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Test
    void constructorRejectsNullContent() {
        assertThrows(NullPointerException.class, () ->
                new CompressionDictionary(null, SOURCE, MATCH, ID, STORED_AT, VALID_UNTIL));
    }

    @Test
    void constructorRejectsNullSource() {
        assertThrows(NullPointerException.class, () ->
                new CompressionDictionary(CONTENT, null, MATCH, ID, STORED_AT, VALID_UNTIL));
    }

    @Test
    void constructorRejectsNullMatch() {
        assertThrows(NullPointerException.class, () ->
                new CompressionDictionary(CONTENT, SOURCE, null, ID, STORED_AT, VALID_UNTIL));
    }

    @Test
    void constructorRejectsBlankMatch() {
        assertThrows(IllegalArgumentException.class, () ->
                new CompressionDictionary(CONTENT, SOURCE, "   ", ID, STORED_AT, VALID_UNTIL));
    }

    @Test
    void constructorRejectsNonHttpsOrRelativeSource() {
        assertThrows(IllegalArgumentException.class, () ->
                new CompressionDictionary(CONTENT, URI.create("http://example.com/dict"),
                        MATCH, ID, STORED_AT, VALID_UNTIL));
        assertThrows(IllegalArgumentException.class, () ->
                new CompressionDictionary(CONTENT, URI.create("/dict"),
                        MATCH, ID, STORED_AT, VALID_UNTIL));
    }

    @Test
    void constructorRejectsInvalidStructuredFieldStrings() {
        assertThrows(IllegalArgumentException.class, () ->
                new CompressionDictionary(CONTENT, SOURCE, "/caf\u00e9", ID, STORED_AT, VALID_UNTIL));
        assertThrows(IllegalArgumentException.class, () ->
                new CompressionDictionary(CONTENT, SOURCE, MATCH, "bad\nid", STORED_AT, VALID_UNTIL));
        assertThrows(IllegalArgumentException.class, () ->
                new CompressionDictionary(CONTENT, SOURCE, MATCH,
                        Arrays.asList("document", null), ID, "raw", STORED_AT, VALID_UNTIL));
    }

    @Test
    void constructorRejectsOversizedId() {
        final char[] chars = new char[1025];
        Arrays.fill(chars, 'x');
        assertThrows(IllegalArgumentException.class, () ->
                new CompressionDictionary(CONTENT, SOURCE, MATCH,
                        new String(chars), STORED_AT, VALID_UNTIL));
    }

    @Test
    void constructorRejectsInvalidTypeToken() {
        assertThrows(IllegalArgumentException.class, () ->
                new CompressionDictionary(CONTENT, SOURCE, MATCH,
                        null, ID, "raw type", STORED_AT, VALID_UNTIL));
    }

    @Test
    void constructorRejectsNullStoredAt() {
        assertThrows(NullPointerException.class, () ->
                new CompressionDictionary(CONTENT, SOURCE, MATCH, ID, null, VALID_UNTIL));
    }

    @Test
    void constructorRejectsNullValidUntil() {
        assertThrows(NullPointerException.class, () ->
                new CompressionDictionary(CONTENT, SOURCE, MATCH, ID, STORED_AT, null));
    }

    @Test
    void nullIdBecomesEmptyString() {
        final CompressionDictionary dict =
                new CompressionDictionary(CONTENT, SOURCE, MATCH, null, STORED_AT, VALID_UNTIL);
        assertEquals("", dict.getId());
    }

    @Test
    void gettersReturnPassedValues() {
        final CompressionDictionary dict = newDictionary();
        assertEquals(SOURCE, dict.getSource());
        assertEquals(MATCH, dict.getMatch());
        assertEquals(ID, dict.getId());
        assertEquals(STORED_AT, dict.getStoredAt());
        assertEquals(VALID_UNTIL, dict.getValidUntil());
        assertArrayEquals(CONTENT, dict.getContent());
    }

    @Test
    void constructorClonesContent() {
        final byte[] mutable = CONTENT.clone();
        final CompressionDictionary dict =
                new CompressionDictionary(mutable, SOURCE, MATCH, ID, STORED_AT, VALID_UNTIL);
        mutable[0] = (byte) (mutable[0] ^ 0xFF);
        assertArrayEquals(CONTENT, dict.getContent());
    }

    @Test
    void getContentReturnsDefensiveCopy() {
        final CompressionDictionary dict = newDictionary();
        final byte[] first = dict.getContent();
        first[0] = (byte) (first[0] ^ 0xFF);
        assertArrayEquals(CONTENT, dict.getContent());
    }

    @Test
    void getContentReturnsDistinctArrays() {
        final CompressionDictionary dict = newDictionary();
        final byte[] first = dict.getContent();
        final byte[] second = dict.getContent();
        assertNotSame(first, second);
        assertArrayEquals(first, second);
    }

    @Test
    void getSha256ReturnsDefensiveCopy() {
        final CompressionDictionary dict = newDictionary();
        final byte[] first = dict.getSha256();
        first[0] = (byte) (first[0] ^ 0xFF);
        assertArrayEquals(sha256(CONTENT), dict.getSha256());
    }

    @Test
    void getSha256ReturnsDistinctArrays() {
        final CompressionDictionary dict = newDictionary();
        final byte[] first = dict.getSha256();
        final byte[] second = dict.getSha256();
        assertNotSame(first, second);
        assertArrayEquals(first, second);
    }

    @Test
    void sha256MatchesFreshlyComputedDigest() {
        final CompressionDictionary dict = newDictionary();
        assertArrayEquals(sha256(CONTENT), dict.getSha256());
    }

    @Test
    void matchesHashTrueForSha256() {
        final CompressionDictionary dict = newDictionary();
        assertTrue(dict.matchesHash(sha256(CONTENT)));
    }

    @Test
    void matchesHashFalseForOtherHash() {
        final CompressionDictionary dict = newDictionary();
        assertFalse(dict.matchesHash(sha256("different".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void isFreshBeforeValidUntil() {
        final CompressionDictionary dict = newDictionary();
        assertTrue(dict.isFresh(VALID_UNTIL.minusSeconds(1)));
    }

    @Test
    void isFreshAtValidUntil() {
        final CompressionDictionary dict = newDictionary();
        assertFalse(dict.isFresh(VALID_UNTIL));
    }

    @Test
    void isFreshAfterValidUntil() {
        final CompressionDictionary dict = newDictionary();
        assertFalse(dict.isFresh(VALID_UNTIL.plusSeconds(1)));
    }
}
