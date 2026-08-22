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

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;

import org.apache.hc.client5.http.entity.compress.CompressionDictionary;
import org.apache.hc.core5.util.Args;

/**
 * Default {@link CompressionDictionaryMatcher} that selects the compression dictionary
 * to advertise for an outgoing request, following the matching and selection rules of
 * Compression Dictionary Transport.
 * <p>
 * A candidate is eligible only when it is still fresh at the current instant, shares the
 * same origin as the request and its stored URL pattern matches the request path. Dictionary
 * transport is restricted to secure origins, so a request whose scheme is not {@code https}
 * never matches, and a candidate whose source is not an {@code https} URL is ignored.
 * <p>
 * When several candidates are eligible the most specific one wins: the longest pattern is
 * preferred, and ties are broken in favour of the most recently stored dictionary. The
 * winning dictionary is the value the caller offers to the origin through the
 * {@code Available-Dictionary} header.
 * <p>
 * Instances are immutable and stateless apart from the injected {@link Clock}, hence thread
 * safe and reusable across requests. The clock constructor exists for deterministic testing;
 * production code uses the system UTC clock.
 */
final class DefaultCompressionDictionaryMatcher
        implements CompressionDictionaryMatcher {

    private final Clock clock;
    private final CompressionDictionaryUrlPatternMatcher urlPatternMatcher;

    /**
     * Creates a matcher backed by the system UTC clock and the default URL pattern matcher.
     */
    DefaultCompressionDictionaryMatcher() {
        this(Clock.systemUTC(), new DefaultCompressionDictionaryUrlPatternMatcher());
    }

    /**
     * Creates a matcher with an explicit clock, used to make freshness evaluation deterministic
     * in tests.
     *
     * @param clock the clock supplying the instant against which candidate freshness is judged.
     */
    DefaultCompressionDictionaryMatcher(final Clock clock) {
        this(clock, new DefaultCompressionDictionaryUrlPatternMatcher());
    }

    /**
     * Creates a matcher with an explicit URL pattern matcher over the system UTC clock.
     *
     * @param urlPatternMatcher the strategy that validates and evaluates a candidate's stored
     *   {@code match} pattern against the request.
     */
    DefaultCompressionDictionaryMatcher(
            final CompressionDictionaryUrlPatternMatcher urlPatternMatcher) {
        this(Clock.systemUTC(), urlPatternMatcher);
    }

    /**
     * Creates a matcher with both collaborators supplied explicitly.
     *
     * @param clock the clock supplying the instant against which candidate freshness is judged.
     * @param urlPatternMatcher the strategy that validates and evaluates a candidate's stored
     *   {@code match} pattern against the request.
     * @throws NullPointerException if either argument is {@code null}.
     */
    DefaultCompressionDictionaryMatcher(
            final Clock clock,
            final CompressionDictionaryUrlPatternMatcher urlPatternMatcher) {
        this.clock = Args.notNull(clock, "Clock");
        this.urlPatternMatcher =
                Args.notNull(urlPatternMatcher, "URL pattern matcher");
    }

    /**
     * Convenience overload for callers that do not carry a request destination; equivalent to
     * {@link #match(URI, String, Collection)} with a {@code null} destination.
     *
     * @param requestUri the absolute request target.
     * @param dictionaries the stored dictionaries to consider.
     * @return the winning dictionary, or {@code null} when no candidate is eligible.
     */
    CompressionDictionary match(
            final URI requestUri,
            final Collection<CompressionDictionary> dictionaries) {
        return match(requestUri, null, dictionaries);
    }

    /**
     * {@inheritDoc}
     * <p>
     * A candidate survives only when it is fresh at the clock instant, is a {@code raw}
     * dictionary, shares the request origin, applies to the request destination and carries a
     * {@code match} pattern that is valid for its source and matches the request. Among the
     * survivors the winner is chosen by three keys in order: a {@code match-dest} that names the
     * request destination outranks a destination-agnostic one, then the longest {@code match}
     * pattern wins, and finally the most recently stored dictionary breaks any remaining tie.
     *
     * @throws NullPointerException if {@code requestUri} or {@code dictionaries} is {@code null}.
     */
    @Override
    public CompressionDictionary match(
            final URI requestUri,
            final String requestDestination,
            final Collection<CompressionDictionary> dictionaries) {

        Args.notNull(requestUri, "Request URI");
        Args.notNull(dictionaries, "Dictionaries");

        if (!"https".equalsIgnoreCase(requestUri.getScheme())
                || dictionaries.isEmpty()) {
            return null;
        }

        final Instant now = clock.instant();

        return dictionaries.stream()
                .filter(dictionary -> dictionary.isFresh(now))
                .filter(dictionary -> "raw".equals(dictionary.getType()))
                .filter(dictionary -> sameOrigin(
                        dictionary.getSource(), requestUri))
                .filter(dictionary -> destinationMatches(
                        dictionary, requestDestination))
                .filter(dictionary -> urlPatternMatcher.isValid(
                        dictionary.getMatch(), dictionary.getSource()))
                .filter(dictionary -> urlPatternMatcher.matches(
                        dictionary.getMatch(),
                        dictionary.getSource(),
                        requestUri))
                .max(
                        Comparator
                                .comparingInt(
                                        (CompressionDictionary dictionary) ->
                                                destinationPrecedence(
                                                        dictionary,
                                                        requestDestination))
                                .thenComparingInt(
                                        dictionary ->
                                                dictionary.getMatch().length())
                                .thenComparing(
                                        CompressionDictionary::getStoredAt))
                .orElse(null);
    }

    /**
     * Ranks a candidate for tie-breaking: a dictionary whose {@code match-dest} explicitly names
     * the request destination is preferred over one that matches irrespective of destination.
     * Returns {@code 1} for such an explicit destination match and {@code 0} otherwise, including
     * when the caller carries no destination or the candidate constrains none.
     */
    private static int destinationPrecedence(
            final CompressionDictionary dictionary,
            final String requestDestination) {

        if (requestDestination == null
                || dictionary.getMatchDest().isEmpty()) {
            return 0;
        }

        return dictionary.getMatchDest().contains(requestDestination)
                ? 1
                : 0;
    }

    /**
     * Tests whether a candidate's {@code match-dest} admits the request destination. An empty
     * {@code match-dest} places no constraint and admits every destination.
     */
    private static boolean destinationMatches(
            final CompressionDictionary dictionary,
            final String requestDestination) {

        if (requestDestination == null) {
            /*
             * Compression Dictionary Transport: a client that does not support request
             * destinations treats match-dest as an empty list, so the constraint is waived.
             */
            return true;
        }

        return dictionary.getMatchDest().isEmpty()
                || dictionary.getMatchDest().contains(requestDestination);
    }

    /**
     * Tests whether two URIs denote the same origin, that is the same scheme, host and effective
     * port. Dictionary transport is scoped to a single origin, so a candidate stored for one
     * origin is never advertised on a request to another.
     */
    private static boolean sameOrigin(
            final URI first,
            final URI second) {

        return equalsIgnoreCase(first.getScheme(), second.getScheme())
                && equalsIgnoreCase(first.getHost(), second.getHost())
                && effectivePort(first) == effectivePort(second);
    }

    private static boolean equalsIgnoreCase(
            final String first,
            final String second) {
        return first != null
                && second != null
                && first.equalsIgnoreCase(second);
    }

    /**
     * Resolves the port that participates in origin comparison, substituting the scheme default
     * when the URI carries no explicit port: {@code 443} for {@code https} and {@code 80} for
     * {@code http}. Any other scheme with no port yields {@code -1}.
     */
    private static int effectivePort(final URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme())
                ? 443
                : "http".equalsIgnoreCase(uri.getScheme())
                  ? 80
                  : -1;
    }
}
