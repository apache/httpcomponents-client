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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.hc.client5.http.entity.compress.CompressionDictionary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TestDefaultCompressionDictionaryMatcher {

    private static final Instant NOW = Instant.parse("2026-08-21T12:00:00Z");

    private DefaultCompressionDictionaryMatcher matcher;

    @BeforeEach
    void setUp() {
        matcher = new DefaultCompressionDictionaryMatcher(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static CompressionDictionary dictionary(
            final String source,
            final String match,
            final Instant storedAt,
            final Instant validUntil) {
        return dictionary(source, match, Collections.<String>emptyList(), storedAt, validUntil);
    }

    private static CompressionDictionary dictionary(
            final String source,
            final String match,
            final List<String> matchDest,
            final Instant storedAt,
            final Instant validUntil) {
        return new CompressionDictionary(
                new byte[] {1, 2, 3},
                URI.create(source),
                match,
                matchDest,
                "id",
                "raw",
                storedAt,
                validUntil);
    }

    /**
     * A dictionary rooted at https://example.com, valid for one hour from the fixed clock.
     */
    private static CompressionDictionary fresh(final String match) {
        return dictionary(
                "https://example.com/",
                match,
                NOW.minus(Duration.ofMinutes(10)),
                NOW.plus(Duration.ofHours(1)));
    }

    private static Collection<CompressionDictionary> list(final CompressionDictionary... dictionaries) {
        return Arrays.asList(dictionaries);
    }

    @Test
    void nullRequestUriThrows() {
        assertThrows(NullPointerException.class,
                () -> matcher.match(null, Collections.<CompressionDictionary>emptyList()));
    }

    @Test
    void nullDictionariesThrows() {
        assertThrows(NullPointerException.class,
                () -> matcher.match(URI.create("https://example.com/app"), null));
    }

    @Test
    void nonHttpsRequestUriReturnsNull() {
        final CompressionDictionary dictionary = fresh("/*");
        assertNull(matcher.match(URI.create("http://example.com/app"), list(dictionary)));
    }

    @Test
    void httpsSchemeIsCaseInsensitive() {
        final CompressionDictionary dictionary = dictionary(
                "HTTPS://example.com/",
                "/app/*",
                NOW.minus(Duration.ofMinutes(1)),
                NOW.plus(Duration.ofHours(1)));
        assertSame(dictionary,
                matcher.match(URI.create("HTTPS://example.com/app/main.js"), list(dictionary)));
    }

    @Test
    void nonHttpsDictionarySourceIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> dictionary(
                "http://example.com/",
                "/*",
                NOW.minus(Duration.ofMinutes(1)),
                NOW.plus(Duration.ofHours(1))));
    }

    @Test
    void differentHostIsNotMatched() {
        final CompressionDictionary dictionary = dictionary(
                "https://other.com/",
                "/*",
                NOW.minus(Duration.ofMinutes(1)),
                NOW.plus(Duration.ofHours(1)));
        assertNull(matcher.match(URI.create("https://example.com/app"), list(dictionary)));
    }

    @Test
    void differentPortIsNotMatched() {
        final CompressionDictionary dictionary = dictionary(
                "https://example.com:8443/",
                "/*",
                NOW.minus(Duration.ofMinutes(1)),
                NOW.plus(Duration.ofHours(1)));
        assertNull(matcher.match(URI.create("https://example.com/app"), list(dictionary)));
    }

    @Test
    void defaultHttpsPortMatchesExplicit443() {
        final CompressionDictionary dictionary = dictionary(
                "https://example.com/",
                "/*",
                NOW.minus(Duration.ofMinutes(1)),
                NOW.plus(Duration.ofHours(1)));
        assertSame(dictionary,
                matcher.match(URI.create("https://example.com:443/app"), list(dictionary)));
    }

    @Test
    void expiredDictionaryIsExcluded() {
        final CompressionDictionary dictionary = dictionary(
                "https://example.com/",
                "/*",
                NOW.minus(Duration.ofHours(2)),
                NOW.minus(Duration.ofMinutes(1)));
        assertNull(matcher.match(URI.create("https://example.com/app"), list(dictionary)));
    }

    @Test
    void dictionaryExpiringAtTheClockInstantIsExcluded() {
        // isFresh uses instant.isBefore(validUntil); validUntil == now is not fresh.
        final CompressionDictionary dictionary = dictionary(
                "https://example.com/",
                "/*",
                NOW.minus(Duration.ofHours(2)),
                NOW);
        assertNull(matcher.match(URI.create("https://example.com/app"), list(dictionary)));
    }

    @Test
    void relativePatternUsesOutboundRequestAsBase() {
        final CompressionDictionary dictionary = fresh("app/*");
        assertNull(matcher.match(URI.create("https://example.com/app/x"), list(dictionary)));
    }

    @Test
    void relativePatternWithParentSegmentUsesOutboundRequestAsBase() {
        final CompressionDictionary dictionary = fresh("../app/*");
        assertSame(dictionary, matcher.match(URI.create("https://example.com/app/x"), list(dictionary)));
    }

    @Test
    void patternWithQueryMatchesQueryComponent() {
        final CompressionDictionary dictionary = fresh("/app?x");
        assertSame(dictionary, matcher.match(URI.create("https://example.com/app?x"), list(dictionary)));
    }

    @Test
    void wildcardQuestionMarkIsAGroupModifier() {
        final CompressionDictionary dictionary = fresh("https://example.com/*?foo");
        assertNull(matcher.match(URI.create("https://example.com/?foo"), list(dictionary)));
    }

    @Test
    void escapedQuestionMarkSeparatesTheSearchComponent() {
        final CompressionDictionary dictionary = fresh("https://example.com/*\\?foo");
        assertSame(dictionary,
                matcher.match(URI.create("https://example.com/?foo"), list(dictionary)));
    }

    @Test
    void patternWithFragmentMatchesFragmentComponent() {
        final CompressionDictionary dictionary = fresh("/app#x");
        assertSame(dictionary, matcher.match(URI.create("https://example.com/app#x"), list(dictionary)));
    }

    @Test
    void patternWithParenthesesNeverMatches() {
        assertNull(matcher.match(URI.create("https://example.com/app"), list(fresh("/app("))));
        assertNull(matcher.match(URI.create("https://example.com/app"), list(fresh("/app)"))));
    }

    @Test
    void rfcPathPrefixExampleMatches() {
        final CompressionDictionary dictionary = fresh("/product/*");
        assertSame(dictionary,
                matcher.match(URI.create("https://example.com/product/123"), list(dictionary)));
    }

    @Test
    void rfcVersionedDirectoriesExampleMatches() {
        final CompressionDictionary dictionary = fresh("/app/*/main.js");
        assertSame(dictionary,
                matcher.match(URI.create("https://example.com/app/v1/main.js"), list(dictionary)));
    }

    @Test
    void percentEncodedPathMatchesAtHttpLevel() {
        final CompressionDictionary dictionary = fresh("/d%C3%BCsseldorf");
        assertSame(dictionary,
                matcher.match(URI.create("https://example.com/d%C3%BCsseldorf"), list(dictionary)));
    }

    @Test
    void unbalancedPatternBracesNeverMatch() {
        assertNull(matcher.match(URI.create("https://example.com/app"), list(fresh("/app{"))));
        assertNull(matcher.match(URI.create("https://example.com/app"), list(fresh("/app}"))));
    }

    @Test
    void optionalBraceGroupMatches() {
        final CompressionDictionary dictionary = fresh("/app{/v1}?/*");
        assertSame(dictionary,
                matcher.match(URI.create("https://example.com/app/v1/main.js"), list(dictionary)));
        assertSame(dictionary,
                matcher.match(URI.create("https://example.com/app/main.js"), list(dictionary)));
    }

    @Test
    void patternWithColonNeverMatches() {
        final CompressionDictionary dictionary = fresh("/app:x");
        assertNull(matcher.match(URI.create("https://example.com/app"), list(dictionary)));
    }

    @Test
    void namedGroupMatchesOnePathSegment() {
        final CompressionDictionary dictionary = fresh("/app/:name");
        assertSame(dictionary,
                matcher.match(URI.create("https://example.com/app/main.js"), list(dictionary)));
        assertNull(matcher.match(URI.create("https://example.com/app/a/main.js"), list(dictionary)));
    }

    @Test
    void absoluteSameOriginPatternMatches() {
        final CompressionDictionary dictionary = fresh("https://example.com/app/*");
        assertSame(dictionary,
                matcher.match(URI.create("https://example.com/app/main.js?version=1"), list(dictionary)));
    }

    @Test
    void absoluteCrossOriginPatternNeverMatches() {
        final CompressionDictionary dictionary = fresh("https://other.example/app/*");
        assertNull(matcher.match(URI.create("https://example.com/app/main.js"), list(dictionary)));
    }

    @Test
    void absoluteCrossOriginPatternIsValidButCannotMatchDictionaryOrigin() {
        final CompressionDictionaryUrlPatternMatcher patternMatcher =
                new DefaultCompressionDictionaryUrlPatternMatcher();
        assertTrue(patternMatcher.isValid(
                "https://other.example/app/*", URI.create("https://example.com/dictionary")));
        assertNull(matcher.match(URI.create("https://example.com/app/main.js"),
                list(fresh("https://other.example/app/*"))));
    }

    @Test
    void patternedHostnameCanMatchWithinDictionaryOrigin() {
        final CompressionDictionary dictionary = dictionary(
                "https://www.example.com/",
                "https://*.example.com/app/*",
                NOW.minus(Duration.ofMinutes(1)),
                NOW.plus(Duration.ofHours(1)));
        assertSame(dictionary,
                matcher.match(URI.create("https://www.example.com/app/main.js"), list(dictionary)));
    }

    @Test
    void bracedAuthorityPatternMatchesWithinDictionaryOrigin() {
        final CompressionDictionary dictionary = fresh(
                "https://{sub.}?example{.com/}foo");
        assertSame(dictionary,
                matcher.match(URI.create("https://example.com/foo"), list(dictionary)));
    }

    @Test
    void escapedUserInfoDelimiterMatches() {
        final CompressionDictionary dictionary = dictionary(
                "https://foo:bar@example.com/dictionary",
                "https://foo\\:bar@example.com",
                NOW.minus(Duration.ofMinutes(1)),
                NOW.plus(Duration.ofHours(1)));
        assertSame(dictionary,
                matcher.match(URI.create("https://foo:bar@example.com"), list(dictionary)));
    }

    @Test
    void globPrefixMatchesSubPath() {
        final CompressionDictionary dictionary = fresh("/app/*");
        assertSame(dictionary,
                matcher.match(URI.create("https://example.com/app/main.js"), list(dictionary)));
    }

    @Test
    void globPrefixDoesNotMatchDifferentPrefix() {
        final CompressionDictionary dictionary = fresh("/app/*");
        assertNull(matcher.match(URI.create("https://example.com/static/main.js"), list(dictionary)));
    }

    @Test
    void rootGlobMatchesAnyPath() {
        final CompressionDictionary dictionary = fresh("/*");
        assertSame(dictionary,
                matcher.match(URI.create("https://example.com/anything/deep/path"), list(dictionary)));
    }

    @Test
    void exactPatternMatchesExactPath() {
        final CompressionDictionary dictionary = fresh("/app/main.js");
        assertSame(dictionary,
                matcher.match(URI.create("https://example.com/app/main.js"), list(dictionary)));
    }

    @Test
    void exactPatternDoesNotMatchLongerPath() {
        final CompressionDictionary dictionary = fresh("/app/main.js");
        assertNull(matcher.match(URI.create("https://example.com/app/main.js.map"), list(dictionary)));
    }

    @Test
    void emptyCollectionReturnsNull() {
        assertNull(matcher.match(URI.create("https://example.com/app"),
                Collections.<CompressionDictionary>emptyList()));
    }

    @Test
    void longestMatchWins() {
        final CompressionDictionary broad = fresh("/*");
        final CompressionDictionary narrow = fresh("/app/*");
        assertSame(narrow,
                matcher.match(URI.create("https://example.com/app/main.js"), list(broad, narrow)));
        // order independent
        assertSame(narrow,
                matcher.match(URI.create("https://example.com/app/main.js"), list(narrow, broad)));
    }

    @Test
    void matchingDestinationTakesPrecedence() {
        final CompressionDictionary destinationAgnostic = fresh("/app/*");
        final CompressionDictionary destinationSpecific = dictionary(
                "https://example.com/",
                "/*",
                Collections.singletonList("script"),
                NOW.minus(Duration.ofMinutes(10)),
                NOW.plus(Duration.ofHours(1)));
        assertSame(destinationSpecific,
                matcher.match(URI.create("https://example.com/app/main.js"), "script",
                        list(destinationAgnostic, destinationSpecific)));
    }

    @Test
    void nonMatchingDestinationIsExcluded() {
        final CompressionDictionary dictionary = dictionary(
                "https://example.com/",
                "/*",
                Collections.singletonList("document"),
                NOW.minus(Duration.ofMinutes(10)),
                NOW.plus(Duration.ofHours(1)));
        assertNull(matcher.match(
                URI.create("https://example.com/app/main.js"), "script", list(dictionary)));
    }

    @Test
    void unsupportedRequestDestinationsMatchAll() {
        final CompressionDictionary dictionary = dictionary(
                "https://example.com/",
                "/*",
                Collections.singletonList("document"),
                NOW.minus(Duration.ofMinutes(10)),
                NOW.plus(Duration.ofHours(1)));
        assertSame(dictionary,
                matcher.match(URI.create("https://example.com/app/main.js"), null, list(dictionary)));
    }

    @Test
    void equalLengthLaterStoredAtWins() {
        final CompressionDictionary older = dictionary(
                "https://example.com/",
                "/app/*",
                NOW.minus(Duration.ofHours(2)),
                NOW.plus(Duration.ofHours(1)));
        final CompressionDictionary newer = dictionary(
                "https://example.com/",
                "/app/*",
                NOW.minus(Duration.ofMinutes(5)),
                NOW.plus(Duration.ofHours(1)));
        assertSame(newer,
                matcher.match(URI.create("https://example.com/app/x"), list(older, newer)));
        assertSame(newer,
                matcher.match(URI.create("https://example.com/app/x"), list(newer, older)));
    }
}
