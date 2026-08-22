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
import java.util.Collection;

import org.apache.hc.client5.http.entity.compress.CompressionDictionary;

/**
 * Selects the compression dictionary to advertise for an outgoing request, following the
 * Compression Dictionary Transport matching rules. Implementations decide which stored {@link CompressionDictionary},
 * if any, is eligible for a given request URI; the winner is later announced to the origin through
 * the {@code Available-Dictionary} header so it may return a {@code dcb} or {@code dcz} response.
 * <p>
 * Eligibility is governed by the {@code Use-As-Dictionary} metadata carried on each dictionary:
 * a candidate applies only when it is still fresh, shares the request's origin, and its stored
 * URL pattern matches the request path. When several candidates qualify the implementation is
 * expected to return the single most specific one.
 * <p>
 * Implementations are expected to be stateless and safe for concurrent use.
 */

interface CompressionDictionaryMatcher {

    /**
     * Selects the single dictionary to advertise for the given request, or {@code null} when
     * none is eligible. Only a candidate that is fresh, shares the request origin and whose
     * stored URL pattern matches the request qualifies; when several qualify the most specific
     * one is returned.
     *
     * @param requestUri the absolute request target. Dictionary transport is confined to secure
     *   origins, so a target whose scheme is not {@code https} matches nothing.
     * @param requestDestination the request destination the response is bound for, tested against
     *   each candidate's {@code match-dest}, or {@code null} when the caller does not support
     *   request destinations.
     * @param dictionaries the stored dictionaries to consider.
     * @return the winning dictionary, whose identifier the caller offers to the origin through
     *   {@code Available-Dictionary}, or {@code null} when no candidate is eligible.
     */
    CompressionDictionary match(
            URI requestUri,
            String requestDestination,
            Collection<CompressionDictionary> dictionaries);
}