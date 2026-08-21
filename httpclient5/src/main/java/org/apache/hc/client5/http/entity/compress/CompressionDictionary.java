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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.util.Args;

/**
 * An immutable compression dictionary used by Compression Dictionary Transport.
 * <p>
 * A dictionary carries the raw bytes that seed a Dictionary-Compressed Brotli ({@code dcb}) or
 * Dictionary-Compressed Zstandard ({@code dcz}) decoder, together with the metadata needed to
 * negotiate its use with an origin. The {@code match} pattern advertised in the origin's
 * {@code Use-As-Dictionary} response selects the requests a dictionary applies to; the SHA-256
 * hash is offered in the {@code Available-Dictionary} request header and echoed by the origin in
 * {@code Dictionary-ID}, so that both peers agree on which dictionary decoded the response.
 * <p>
 * Instances are value objects: the dictionary content is defensively copied on construction and
 * on every access, and the SHA-256 hash is computed once from that copy, so the observable state
 * never changes after construction.
 *
 * @since 5.7
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public final class CompressionDictionary {

    private final byte[] content;
    private final byte[] sha256;
    private final URI source;
    private final String match;
    private final List<String> matchDest;
    private final String id;
    private final String type;
    private final Instant storedAt;
    private final Instant validUntil;

    public CompressionDictionary(
            final byte[] content,
            final URI source,
            final String match,
            final List<String> matchDest,
            final String id,
            final String type,
            final Instant storedAt,
            final Instant validUntil) {
        this.content = Args.notNull(content, "Content").clone();
        this.sha256 = sha256(content);
        this.source = Args.notNull(source, "Source");
        this.match = Args.notBlank(match, "Match");
        this.matchDest = matchDest != null
                ? List.copyOf(matchDest)
                : Collections.emptyList();
        this.id = id != null ? id : "";
        this.type = type != null ? type : "raw";
        this.storedAt = Args.notNull(storedAt, "Stored at");
        this.validUntil = Args.notNull(validUntil, "Valid until");
    }

    public byte[] getContent() {
        return content.clone();
    }

    public byte[] getSha256() {
        return sha256.clone();
    }

    public URI getSource() {
        return source;
    }

    public String getMatch() {
        return match;
    }

    public List<String> getMatchDest() {
        return matchDest;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public Instant getStoredAt() {
        return storedAt;
    }

    public Instant getValidUntil() {
        return validUntil;
    }

    public boolean isFresh(final Instant now) {
        return now.isBefore(validUntil);
    }

    public boolean matchesHash(final byte[] hash) {
        return MessageDigest.isEqual(sha256, hash);
    }

    private static byte[] sha256(final byte[] content) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(content);
        } catch (final NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}