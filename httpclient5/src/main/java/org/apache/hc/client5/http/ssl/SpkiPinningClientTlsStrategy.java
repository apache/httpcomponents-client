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

package org.apache.hc.client5.http.ssl;

import java.net.IDN;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;

/**
 * <p><strong>SPKI pinning decorator</strong> for client-side TLS.</p>
 *
 * <p>This strategy enforces one or more {@code sha256/<base64(SPKI)>} pins for a given
 * host or single-label wildcard (e.g. {@code *.example.com}) <em>after</em> the standard
 * trust manager and hostname verification succeed. Pins are matched against the
 * {@code SubjectPublicKeyInfo} (SPKI) of any certificate in the peer chain.</p>
 *
 * <p>Host matching is performed on the IDNA ASCII (Punycode) lowercase form.
 * Wildcards are <em>single-label only</em> (e.g. {@code *.example.com} matches
 * {@code a.example.com} but not {@code a.b.example.com}).</p>
 *
 * <p><strong>Warning:</strong> Certificate pinning increases operational risk.
 * Always ship at least two pins (active + backup) and keep
 * normal PKI + hostname verification enabled.</p>
 *
 * <p>Thread-safety: immutable and thread-safe.</p>
 *
 * @since 5.6
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public final class SpkiPinningClientTlsStrategy extends DefaultClientTlsStrategy {

    private static final String PIN_PREFIX = "sha256/";
    private static final int SHA256_LEN = 32;

    /**
     * Byte-array key with constant-time equality for use in sets/maps.
     */
    private static final class ByteArrayKey {
        final byte[] v;
        private final int hash;

        ByteArrayKey(final byte[] v) {
            this.v = Objects.requireNonNull(v, "bytes");
            int h = 1;
            for (int i = 0; i < v.length; i++) {
                h = 31 * h + (v[i] & 0xff);
            }
            this.hash = h;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ByteArrayKey)) {
                return false;
            }
            return MessageDigest.isEqual(v, ((ByteArrayKey) o).v);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    /**
     * Match rule for a host or single-label wildcard.
     */
    private static final class Rule {
        final String pattern;      // normalized: IDNA ASCII + lowercase
        final boolean wildcard;    // true if pattern starts with "*."
        final String tail;         // for wildcard, ".example.com"; otherwise null
        final Set<ByteArrayKey> pins; // unmodifiable set of 32-byte SHA-256 hashes

        Rule(final String pattern, final Set<ByteArrayKey> pins) {
            if (pattern == null) {
                throw new IllegalArgumentException("Host pattern must not be null");
            }
            final String norm;
            try {
                norm = IDN.toASCII(pattern).toLowerCase(Locale.ROOT);
            } catch (final IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid IDN host pattern: " + pattern, e);
            }
            if (norm.isEmpty()) {
                throw new IllegalArgumentException("Empty host pattern");
            }
            final boolean wc = norm.startsWith("*.");
            if (wc && norm.indexOf('.', 2) < 0) { // require "*.<label>"
                throw new IllegalArgumentException("Wildcard must be single-label: *.example.com");
            }
            if (pins == null || pins.isEmpty()) {
                throw new IllegalArgumentException("At least one SPKI pin is required for " + pattern);
            }
            this.pattern = norm;
            this.wildcard = wc;
            this.tail = wc ? norm.substring(1) : null; // ".example.com"
            this.pins = Collections.unmodifiableSet(new HashSet<>(pins));
        }

        // In Rule
        boolean matches(final String host) {
            if (host == null || host.isEmpty()) {
                return false;
            }
            if (wildcard) {
                if (!host.endsWith(tail)) {
                    return false;
                }
                final int boundary = host.length() - tail.length();
                if (boundary < 1) {
                    return false;
                }
                if (host.charAt(boundary) != '.') {
                    return false;
                }
                return host.indexOf('.', 0) == boundary;
            }
            return host.equals(pattern);
        }

    }

    private final List<Rule> rules;

    private SpkiPinningClientTlsStrategy(final SSLContext sslContext, final List<Rule> rules) {
        super(sslContext);
        this.rules = Collections.unmodifiableList(new ArrayList<>(rules));
    }

    /**
     * Invoked after the default trust and hostname checks. If one or more rules match the
     * {@code hostname}, at least one pin must match any SPKI in the peer chain.
     */
    @Override
    protected void verifySession(final String hostname, final SSLSession sslSession) throws SSLException {
        final String host;
        try {
            // Canonicalize host: IDNA (Punycode) + lowercase for consistent matching.
            host = IDN.toASCII(hostname == null ? "" : hostname).toLowerCase(Locale.ROOT);
        } catch (final IllegalArgumentException e) {
            throw new SSLException("Invalid IDN host: " + hostname, e);
        }
        super.verifySession(host, sslSession);
        enforcePins(host, sslSession);
    }

    /**
     * Enforce SPKI pins for the given hostname and session.
     * Package-private for testing.
     */
    void enforcePins(final String hostname, final SSLSession sslSession) throws SSLException {
        final List<Rule> matched = matchedRules(hostname);
        if (matched.isEmpty()) {
            return; // No pins configured for this host.
        }

        final byte[][] peerSpkiHashes = chainSpkiSha256(sslSession);
        for (int i = 0; i < peerSpkiHashes.length; i++) {
            final ByteArrayKey key = new ByteArrayKey(peerSpkiHashes[i]);
            for (int r = 0; r < matched.size(); r++) {
                if (matched.get(r).pins.contains(key)) {
                    return; // match found
                }
            }
        }

        throw new SSLException("SPKI pinning failure for " + hostname
                + "; peer pins: " + peerPinsForLog(peerSpkiHashes)
                + "; configured pins: " + configuredPinsFor(matched));
    }


    /**
     * Create a new builder.
     *
     * @param sslContext SSL context used for handshakes (trust + keys).
     * @return builder
     */
    public static Builder newBuilder(final SSLContext sslContext) {
        return new Builder(sslContext);
    }

    /**
     * Builder for {@link SpkiPinningClientTlsStrategy}.
     */
    public static final class Builder {
        private final SSLContext sslContext;
        private final List<Rule> rules = new ArrayList<>();

        private Builder(final SSLContext sslContext) {
            this.sslContext = Objects.requireNonNull(sslContext, "sslContext");
        }

        /**
         * Add pins for a host pattern.
         *
         * @param hostPattern exact host (e.g. {@code api.example.com}) or single-label wildcard
         *                    (e.g. {@code *.example.com}).
         * @param pins        one or more pins in the form {@code sha256/BASE64}.
         * @return this
         * @throws IllegalArgumentException if a pin is not {@code sha256/...}, has invalid Base64, or wrong length.
         */
        public Builder add(final String hostPattern, final String... pins) {
            if (pins == null || pins.length == 0) {
                throw new IllegalArgumentException("No pins supplied for " + hostPattern);
            }
            final Set<ByteArrayKey> set = new HashSet<>(pins.length);
            for (int i = 0; i < pins.length; i++) {
                set.add(parsePin(pins[i]));
            }
            rules.add(new Rule(hostPattern, set));
            return this;
        }

        /**
         * Build an immutable {@link SpkiPinningClientTlsStrategy}.
         */
        public SpkiPinningClientTlsStrategy build() {
            return new SpkiPinningClientTlsStrategy(sslContext, rules);
        }

        private static ByteArrayKey parsePin(final String s) {
            if (s == null) {
                throw new IllegalArgumentException("Pin must not be null");
            }
            final String t = s.trim();
            if (!t.regionMatches(true, 0, PIN_PREFIX, 0, PIN_PREFIX.length())) {
                throw new IllegalArgumentException("Only sha256 pins are supported: " + s);
            }
            final String b64 = t.substring(PIN_PREFIX.length()).trim();
            final byte[] raw;
            try {
                raw = Base64.getDecoder().decode(b64);
            } catch (final IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid Base64 in SPKI pin: " + s, e);
            }
            if (raw.length != SHA256_LEN) {
                throw new IllegalArgumentException("SPKI pin must be 32 bytes (SHA-256): " + s);
            }
            return new ByteArrayKey(raw);
        }
    }


    private List<Rule> matchedRules(final String host) {
        final List<Rule> out = new ArrayList<>();
        for (int i = 0; i < rules.size(); i++) {
            final Rule r = rules.get(i);
            if (r.matches(host)) {
                out.add(r);
            }
        }
        return out;
    }

    private static byte[][] chainSpkiSha256(final SSLSession session) throws SSLException {
        final Certificate[] chain = session.getPeerCertificates();
        try {
            final MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            final List<byte[]> out = new ArrayList<>(chain.length);
            for (int i = 0; i < chain.length; i++) {
                final Certificate c = chain[i];
                if (c instanceof X509Certificate) {
                    final byte[] spki = ((X509Certificate) c).getPublicKey().getEncoded();
                    out.add(sha256.digest(spki));
                }
            }
            if (out.isEmpty()) {
                throw new SSLException("No X509Certificate in peer chain");
            }
            return out.toArray(new byte[out.size()][]);
        } catch (final SSLException e) {
            throw e;
        } catch (final Exception e) {
            throw new SSLException("Cannot compute SPKI sha256", e);
        }
    }

    private static String configuredPinsFor(final List<Rule> rules) {
        final List<String> pins = new ArrayList<>();
        for (int i = 0; i < rules.size(); i++) {
            for (final ByteArrayKey k : rules.get(i).pins) {
                pins.add(PIN_PREFIX + Base64.getEncoder().encodeToString(k.v));
            }
        }
        return pins.toString();
    }

    private static String peerPinsForLog(final byte[][] hashes) {
        final List<String> pins = new ArrayList<>(hashes.length);
        for (int i = 0; i < hashes.length; i++) {
            pins.add(PIN_PREFIX + Base64.getEncoder().encodeToString(hashes[i]));
        }
        return pins.toString();
    }
}
