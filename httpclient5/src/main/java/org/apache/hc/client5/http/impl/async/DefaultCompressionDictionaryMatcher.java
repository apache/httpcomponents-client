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

    DefaultCompressionDictionaryMatcher(
            final CompressionDictionaryUrlPatternMatcher urlPatternMatcher) {
        this(Clock.systemUTC(), urlPatternMatcher);
    }

    DefaultCompressionDictionaryMatcher(
            final Clock clock,
            final CompressionDictionaryUrlPatternMatcher urlPatternMatcher) {
        this.clock = Args.notNull(clock, "Clock");
        this.urlPatternMatcher =
                Args.notNull(urlPatternMatcher, "URL pattern matcher");
    }

    @Override
    public CompressionDictionary match(
            final URI requestUri,
            final String requestDestination,
            final Collection<CompressionDictionary> dictionaries) {

        if (requestUri == null
                || !"https".equalsIgnoreCase(requestUri.getScheme())
                || dictionaries == null
                || dictionaries.isEmpty()) {
            return null;
        }

        final Instant now = clock.instant();

        return dictionaries.stream()
                .filter(dictionary -> dictionary.isFresh(now))
                .filter(dictionary -> "raw".equalsIgnoreCase(
                        dictionary.getType()))
                .filter(dictionary -> sameOrigin(
                        dictionary.getSource(), requestUri))
                .filter(dictionary -> destinationMatches(
                        dictionary, requestDestination))
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

    private static boolean destinationMatches(
            final CompressionDictionary dictionary,
            final String requestDestination) {

        if (requestDestination == null) {
            /*
             * RFC 9842: clients that do not support request destinations
             * MUST treat match-dest as an empty list.
             */
            return true;
        }

        return dictionary.getMatchDest().isEmpty()
                || dictionary.getMatchDest().contains(requestDestination);
    }

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