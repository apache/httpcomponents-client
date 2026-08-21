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

/**
 * Repository of {@link CompressionDictionary} instances available to the client for
 * decoding dictionary-compressed Brotli ({@code dcb}) and dictionary-compressed
 * Zstandard ({@code dcz}) responses under Compression Dictionary Transport.
 * <p>
 * Dictionaries are indexed both by their SHA-256 hash, which the origin echoes back in
 * the {@code Dictionary-ID} header to identify the dictionary a response was encoded
 * against, and by request origin, so that the client can select the candidates whose
 * match pattern applies to an outbound request and advertise them through the
 * {@code Available-Dictionary} header.
 * <p>
 * Implementations are expected to be thread-safe, since a store is shared across
 * concurrent exchanges. The store keeps entries as supplied; enforcing freshness against
 * {@link CompressionDictionary#getValidUntil()} is the caller's responsibility.
 *
 * @since 5.7
 */

public interface CompressionDictionaryStore {

    void add(CompressionDictionary dictionary);

    CompressionDictionary getByHash(URI origin, byte[] sha256);

    List<CompressionDictionary> getByOrigin(URI origin);

    void clear();
}