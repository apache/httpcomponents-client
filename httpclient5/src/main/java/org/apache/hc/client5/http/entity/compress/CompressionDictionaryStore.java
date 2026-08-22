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

import java.net.URI;
import java.util.List;

import org.apache.hc.client5.http.cookie.CookieStore;

/**
 * Repository of {@link CompressionDictionary} instances available to the client for
 * decoding dictionary-compressed Brotli ({@code dcb}) and dictionary-compressed
 * Zstandard ({@code dcz}) responses under Compression Dictionary Transport.
 * <p>
 * Entries are keyed by privacy partition, request origin and the SHA-256 hash of their content.
 * The privacy partition is represented by the {@link CookieStore} used for the exchange, ensuring
 * dictionary state is never shared more broadly than cookie state.
 * The hash-keyed lookup resolves the exact dictionary a response was encoded against,
 * named by the hash carried in a {@code dcb} or {@code dcz} frame or advertised to the
 * origin in the {@code Available-Dictionary} header. The origin-scoped lookup yields the
 * candidates whose {@code Use-As-Dictionary} match pattern applies to an outbound request,
 * from which the client selects what to advertise.
 * <p>
 * Implementations are expected to be thread-safe, since a store is shared across
 * concurrent exchanges. The store keeps entries as supplied; enforcing freshness against
 * {@link CompressionDictionary#getValidUntil()} is the caller's responsibility.
 *
 * @since 5.7
 */
public interface CompressionDictionaryStore {

    /**
     * Stores a dictionary, replacing any entry already held under the same origin and
     * SHA-256 hash.
     *
     * @param partition the cookie storage partition; must not be {@code null}.
     * @param dictionary the dictionary to store; must not be {@code null}.
     * @since 5.7
     */
    void add(CookieStore partition, CompressionDictionary dictionary);

    /**
     * Resolves the dictionary a response was encoded against, identified by the SHA-256
     * hash carried in a {@code dcb} or {@code dcz} frame or advertised in the
     * {@code Available-Dictionary} header, scoped to the response origin.
     *
     * @param partition the cookie storage partition; must not be {@code null}.
     * @param origin the origin the dictionary is associated with; must not be {@code null}.
     * @param sha256 the SHA-256 hash of the dictionary content; must not be {@code null}.
     * @return the matching dictionary, or {@code null} if none is held for the given
     *   origin and hash.
     * @since 5.7
     */
    CompressionDictionary getByHash(CookieStore partition, URI origin, byte[] sha256);

    /**
     * Returns the dictionaries associated with the given request origin, that is the
     * candidates eligible to be advertised through {@code Available-Dictionary} for a
     * request to that origin.
     *
     * @param partition the cookie storage partition; must not be {@code null}.
     * @param origin the request origin to look up; must not be {@code null}.
     * @return the matching dictionaries, never {@code null}; an empty list if none apply.
     * @since 5.7
     */
    List<CompressionDictionary> getByOrigin(CookieStore partition, URI origin);

    /**
     * Removes all dictionaries associated with a cookie storage partition.
     *
     * @param partition the cookie storage partition; must not be {@code null}.
     * @since 5.7
     */
    void clear(CookieStore partition);

    /**
     * Removes all stored dictionaries.
     *
     * @since 5.7
     */
    void clear();
}
