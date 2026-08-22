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
import java.util.ArrayList;
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
 * hash is offered in the {@code Available-Dictionary} request header. The optional server supplied
 * identifier is echoed independently in the {@code Dictionary-ID} request header.
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

    /**
     * Creates a dictionary with no {@code match-dest} restriction and the default {@code raw} format.
     *
     * @param content    the raw dictionary bytes; copied defensively.
     * @param source     the URI the dictionary was fetched from.
     * @param match      the {@code match} URL pattern that selects the requests the dictionary applies to.
     * @param id         the opaque server identifier echoed in the {@code Dictionary-ID} request header,
     *                   or {@code null} for none.
     * @param storedAt   the instant the dictionary was stored.
     * @param validUntil the instant at which the dictionary stops being fresh; freshness is a half-open
     *                   window ending strictly before this instant.
     * @since 5.7
     */
    public CompressionDictionary(
            final byte[] content,
            final URI source,
            final String match,
            final String id,
            final Instant storedAt,
            final Instant validUntil) {
        this(content, source, match, Collections.<String>emptyList(), id, "raw", storedAt, validUntil);
    }

    /**
     * Creates a dictionary with the full set of negotiation metadata.
     *
     * @param content    the raw dictionary bytes; copied defensively.
     * @param source     the URI the dictionary was fetched from.
     * @param match      the {@code match} URL pattern that selects the requests the dictionary applies to.
     * @param matchDest  the optional {@code match-dest} destinations that further constrain which requests
     *                   the dictionary applies to; copied defensively, {@code null} is treated as empty.
     * @param id         the opaque server identifier echoed in the {@code Dictionary-ID} request header,
     *                   or {@code null} for none.
     * @param type       the format token; only {@code raw} is honoured, {@code null} defaults to {@code raw}.
     * @param storedAt   the instant the dictionary was stored.
     * @param validUntil the instant at which the dictionary stops being fresh.
     * @throws NullPointerException     if {@code content}, {@code source}, {@code match}, {@code storedAt}
     *                                  or {@code validUntil} is {@code null}.
     * @throws IllegalArgumentException if {@code match} is blank or a {@code matchDest}
     *                                  element is {@code null}.
     * @since 5.7
     */
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
        this.sha256 = sha256(this.content);
        this.source = validateSource(Args.notNull(source, "Source"));
        this.match = validateString(Args.notBlank(match, "Match"), "Match", Integer.MAX_VALUE);
        this.matchDest = copyMatchDest(matchDest);
        this.id = validateString(id != null ? id : "", "ID", 1024);
        this.type = validateToken(type != null ? type : "raw");
        this.storedAt = Args.notNull(storedAt, "Stored at");
        this.validUntil = Args.notNull(validUntil, "Valid until");
    }

    /**
     * Returns a defensive copy of the raw dictionary bytes that seed the {@code dcb} or {@code dcz}
     * decoder.
     *
     * @return a fresh copy of the dictionary content.
     * @since 5.7
     */
    public byte[] getContent() {
        return content.clone();
    }

    /**
     * Returns a defensive copy of the SHA-256 digest of the dictionary content. The digest is offered
     * to the origin in the {@code Available-Dictionary} request header.
     *
     * @return a fresh copy of the SHA-256 digest.
     * @since 5.7
     */
    public byte[] getSha256() {
        return sha256.clone();
    }

    /**
     * Returns the URI the dictionary was fetched from.
     *
     * @return the source URI.
     * @since 5.7
     */
    public URI getSource() {
        return source;
    }

    /**
     * Returns the {@code match} URL pattern advertised in the origin's {@code Use-As-Dictionary}
     * response that selects the requests this dictionary applies to.
     *
     * @return the match pattern.
     * @since 5.7
     */
    public String getMatch() {
        return match;
    }

    /**
     * Returns the optional {@code match-dest} destinations that further constrain which requests the
     * dictionary applies to. The list is unmodifiable and empty when no destinations were advertised.
     *
     * @return the immutable list of match destinations.
     * @since 5.7
     */
    public List<String> getMatchDest() {
        return matchDest;
    }

    /**
     * Returns the opaque server identifier echoed back in the {@code Dictionary-ID} request header, or
     * an empty string when the origin supplied none.
     *
     * @return the dictionary identifier, never {@code null}.
     * @since 5.7
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the format token declared for this dictionary. Only {@code raw} is honoured.
     *
     * @return the format type token.
     * @since 5.7
     */
    public String getType() {
        return type;
    }

    /**
     * Returns the instant the dictionary was stored.
     *
     * @return the storage instant.
     * @since 5.7
     */
    public Instant getStoredAt() {
        return storedAt;
    }

    /**
     * Returns the instant at which the dictionary stops being fresh. Freshness ends strictly before
     * this instant.
     *
     * @return the freshness boundary.
     * @since 5.7
     */
    public Instant getValidUntil() {
        return validUntil;
    }

    /**
     * Tests whether the dictionary is still fresh at the given instant. The check is half-open: the
     * dictionary is fresh strictly before {@link #getValidUntil()} and stale from that instant onward.
     *
     * @param now the instant to test against.
     * @return {@code true} if {@code now} precedes the freshness boundary.
     * @since 5.7
     */
    public boolean isFresh(final Instant now) {
        return now.isBefore(validUntil);
    }

    /**
     * Tests whether the supplied hash equals the stored SHA-256 digest. The comparison uses
     * {@link MessageDigest#isEqual(byte[], byte[])} to avoid leaking timing information.
     *
     * @param hash the hash to compare, may be {@code null}.
     * @return {@code true} if {@code hash} is non-{@code null} and matches the stored digest.
     * @since 5.7
     */
    public boolean matchesHash(final byte[] hash) {
        return hash != null && MessageDigest.isEqual(sha256, hash);
    }

    private static byte[] sha256(final byte[] content) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(content);
        } catch (final NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static URI validateSource(final URI source) {
        if (!source.isAbsolute()
                || source.getHost() == null
                || !"https".equalsIgnoreCase(source.getScheme())) {
            throw new IllegalArgumentException("Source must be an absolute HTTPS URI");
        }
        return source;
    }

    private static List<String> copyMatchDest(final List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        final List<String> copy = new ArrayList<>(values.size());
        for (final String value : values) {
            if (value == null) {
                throw new IllegalArgumentException("Match destination must not be null");
            }
            copy.add(validateString(value, "Match destination", Integer.MAX_VALUE));
        }
        return Collections.unmodifiableList(copy);
    }

    private static String validateString(
            final String value,
            final String name,
            final int maxLength) {
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(name + " exceeds " + maxLength + " characters");
        }
        for (int i = 0; i < value.length(); i++) {
            final char ch = value.charAt(i);
            if (ch < 0x20 || ch > 0x7e) {
                throw new IllegalArgumentException(name + " is not an RFC 9651 String");
            }
        }
        return value;
    }

    private static String validateToken(final String value) {
        if (value.isEmpty() || !isTokenStart(value.charAt(0))) {
            throw new IllegalArgumentException("Type is not an RFC 9651 Token");
        }
        for (int i = 1; i < value.length(); i++) {
            if (!isTokenChar(value.charAt(i))) {
                throw new IllegalArgumentException("Type is not an RFC 9651 Token");
            }
        }
        return value;
    }

    private static boolean isTokenStart(final char ch) {
        return ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z' || ch == '*';
    }

    private static boolean isTokenChar(final char ch) {
        return isTokenStart(ch)
                || ch >= '0' && ch <= '9'
                || "!#$%&'*+-.^_`|~:/".indexOf(ch) >= 0;
    }
}
