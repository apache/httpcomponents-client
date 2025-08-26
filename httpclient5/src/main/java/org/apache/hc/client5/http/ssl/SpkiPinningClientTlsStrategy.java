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
            if (this == o) return true;
            if (!(o instanceof ByteArrayKey)) return false;
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
            Objects.requireNonNull(pattern, "pattern");
            final String norm = IDN.toASCII(pattern).toLowerCase(Locale.ROOT);
            this.pattern = norm;
            this.wildcard = norm.startsWith("*.");
            this.tail = wildcard ? norm.substring(1) /* ".example.com" */ : null;
            this.pins = Collections.unmodifiableSet(new HashSet<>(pins));
        }

        boolean matches(final String host) {
            if (wildcard) {
                // host must end with tail and have exactly one additional label before it
                if (!host.endsWith(tail)) {
                    return false;
                }
                final int boundary = host.length() - tail.length();
                if (boundary <= 1) {
                    return false; // need at least "x" + "." + tail
                }
                if (host.charAt(boundary - 1) != '.') {
                    return false;
                }
                // ensure there's no other dot before boundary -> exactly one extra label
                return host.indexOf('.', 0) == boundary - 1;
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
        // Canonicalize host: IDNA (Punycode) + lowercase for consistent matching.
        final String host = IDN.toASCII(hostname == null ? "" : hostname).toLowerCase(Locale.ROOT);
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

        // Keep diagnostics concise (no multi-line certificate subjects).
        throw new SSLException("SPKI pinning failure for " + hostname
                + "; peer pins: " + peerPinsForLog(peerSpkiHashes)
                + "; configured pins: " + configuredPinsFor(matched));
    }

    // --- Builder ---

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
         * @throws IllegalArgumentException if a pin is not {@code sha256/...} or invalid base64.
         */
        public Builder add(final String hostPattern, final String... pins) {
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
            final String prefix = "sha256/";
            if (s == null || !s.startsWith(prefix)) {
                throw new IllegalArgumentException("Only sha256 pins are supported: " + s);
            }
            final String b64 = s.substring(prefix.length());
            final byte[] raw = Base64.getDecoder().decode(b64);
            return new ByteArrayKey(raw);
        }
    }

    // --- Internals ---

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
                    final byte[] spki = c.getPublicKey().getEncoded();
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
                pins.add("sha256/" + Base64.getEncoder().encodeToString(k.v));
            }
        }
        return pins.toString();
    }

    private static String peerPinsForLog(final byte[][] hashes) {
        final List<String> pins = new ArrayList<>(hashes.length);
        for (int i = 0; i < hashes.length; i++) {
            pins.add("sha256/" + Base64.getEncoder().encodeToString(hashes[i]));
        }
        return pins.toString();
    }
}
