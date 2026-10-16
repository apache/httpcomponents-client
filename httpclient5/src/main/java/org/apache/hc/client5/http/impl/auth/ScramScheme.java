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
package org.apache.hc.client5.http.impl.auth;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.Principal;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.hc.client5.http.auth.AuthChallenge;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.AuthenticationException;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.MalformedChallengeException;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Experimental;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Strict HTTP SCRAM client implementing {@code SCRAM-SHA-256} per RFC&nbsp;7804
 * with SCRAM core per RFC&nbsp;5802/7677.
 * <p>HTTP SCRAM uses <em>no channel binding</em> (GS2 header {@code "n,,"}; {@code c=biws}).</p>
 * <p><strong>Experimental:</strong> This API is work in progress and may change without notice in a future release.</p>
 *
 * @since 5.6
 */
@Contract(threading = ThreadingBehavior.UNSAFE)
@Experimental
public final class ScramScheme implements AuthScheme {

    private static final Logger LOG = LoggerFactory.getLogger(ScramScheme.class);

    private static final int DEFAULT_WARN_MIN_ITERATIONS = 4096;
    private static final int DEFAULT_MAX_ITERATIONS_ALLOWED = 100000;

    // RFC 7804 / RFC 5802 fixed no-CB GS2 header and its base64 value for 'c='
    private static final String GS2_HEADER = "n,,";
    private static final String C_BIND_B64 = "biws"; // base64("n,,")

    private static final Base64.Encoder B64 = Base64.getEncoder();
    private static final Base64.Decoder B64D = Base64.getDecoder();

    private enum State {
        INIT,
        ANNOUNCED,            // after 401 challenge without data
        CLIENT_FIRST_SENT,    // after Authorization with client-first
        SERVER_FIRST_RCVD,    // after 401 with data (r,s,i)
        CLIENT_FINAL_SENT,    // after Authorization with client-final (p=...)
        COMPLETE,             // after 2xx with matching v
        FAILED
    }

    private final SecureRandom secureRandom;
    private final int warnMinIterations;
    private final int minIterationsRequired;
    private final int maxIterationsAllowed;

    private State state = State.INIT;
    private boolean complete;

    private String realm;
    private String sid;

    private String username;     // SASLprep (query)
    private char[] password;     // printable US-ASCII fallback, zeroed after use
    private Principal principal;

    private String clientNonce;
    private String clientFirstBare;
    private String serverFirstRaw;
    private String serverNonce;
    private byte[] salt;
    private int iterations;

    // Expected server signature (raw bytes) for constant-time check on Authentication-Info
    // (may appear on any final response status code)
    private byte[] expectedV;

    /**
     * Default policy: warn if {@code i < 4096}, no hard enforcement; SHA-256 only.
     *
     * @since 5.6
     */
    public ScramScheme() {
        this(DEFAULT_WARN_MIN_ITERATIONS, 0, DEFAULT_MAX_ITERATIONS_ALLOWED, null);
    }

    /**
     * Constructor with custom iteration policy.
     *
     * @param warnMinIterations     warn if iteration count is lower than this (0 disables warnings)
     * @param minIterationsRequired fail if iteration count is lower than this (0 disables enforcement)
     * @param rnd                   optional secure random source (null uses system default)
     * @since 5.6
     */
    public ScramScheme(final int warnMinIterations, final int minIterationsRequired, final SecureRandom rnd) {
        this(warnMinIterations, minIterationsRequired, DEFAULT_MAX_ITERATIONS_ALLOWED, rnd);
    }

    /**
     * Constructor with custom iteration policy.
     *
     * @param warnMinIterations     warn if iteration count is lower than this (0 disables warnings)
     * @param minIterationsRequired fail if iteration count is lower than this (0 disables enforcement)
     * @param maxIterationsAllowed  fail if iteration count is greater than this (must be positive)
     * @param rnd                   optional secure random source (null uses system default)
     * @since 5.6
     */
    public ScramScheme(
            final int warnMinIterations,
            final int minIterationsRequired,
            final int maxIterationsAllowed,
            final SecureRandom rnd) {
        this.warnMinIterations = Math.max(0, warnMinIterations);
        this.minIterationsRequired = Math.max(0, minIterationsRequired);
        this.maxIterationsAllowed = Args.positive(maxIterationsAllowed, "Max iterations allowed");
        this.secureRandom = rnd != null ? rnd : new SecureRandom();
    }

    /**
     * Returns textual designation of the scheme.
     *
     * @since 5.6
     */
    @Override
    public String getName() {
        return StandardAuthScheme.SCRAM_SHA_256;
    }

    /**
     * SCRAM is per-request (no connection binding).
     *
     * @since 5.6
     */
    @Override
    public boolean isConnectionBased() {
        return false;
    }

    /**
     * SCRAM must inspect final responses to verify {@code v=} in {@code Authentication-Info}.
     *
     * @since 5.6
     */
    @Override
    public boolean isChallengeExpected() {
        return true;
    }

    /**
     * Legacy entry point: wraps {@link AuthenticationException} as {@link MalformedChallengeException}.
     *
     * @since 5.6
     */
    @Override
    public void processChallenge(final AuthChallenge authChallenge, final HttpContext context)
            throws MalformedChallengeException {
        try {
            processChallenge(null, true, authChallenge, context);
        } catch (final AuthenticationException ex) {
            throw new MalformedChallengeException(ex.getMessage(), ex);
        }
    }

    /**
     * Handles 401 challenges (with/without {@code data}) and final responses carrying
     * {@code Authentication-Info} (any status code).
     *
     * @since 5.6
     */
    @Override
    public void processChallenge(
            final HttpHost host,
            final boolean challenged,
            final AuthChallenge authChallenge,
            final HttpContext context) throws MalformedChallengeException, AuthenticationException {

        Args.notNull(context, "HTTP context");

        if (authChallenge == null) {
            if (!challenged && this.state == State.CLIENT_FINAL_SENT) {
                zeroAndClearExpectedV();
                this.state = State.FAILED;
                throw new AuthenticationException("Missing SCRAM Authentication-Info");
            }
            throw new MalformedChallengeException("Null SCRAM challenge");
        }
        final Map<String, String> params;
        try {
            params = toParamMap(authChallenge.getParams());
        } catch (final MalformedChallengeException ex) {
            zeroAndClearExpectedV();
            this.state = State.FAILED;
            throw ex;
        }

        if (challenged) {
            // --- 401 path (WWW-Authenticate) ---
            final String scheme = authChallenge.getSchemeName();
            if (scheme == null || !StandardAuthScheme.SCRAM_SHA_256.equalsIgnoreCase(scheme)) {
                throw new MalformedChallengeException("Unexpected scheme: " + scheme);
            }

            final String data = params.get("data");
            if (data == null) {
                // initial announce (no data)
                this.realm = params.get("realm");
                this.state = State.ANNOUNCED;
                this.complete = false;
                zeroAndClearExpectedV();
                return;
            }

            final String sid = params.get("sid");
            if (sid == null || sid.isEmpty()) {
                zeroAndClearExpectedV();
                this.state = State.FAILED;
                throw new MalformedChallengeException("SCRAM server-first missing sid");
            }
            if (!isToken(sid)) {
                zeroAndClearExpectedV();
                this.state = State.FAILED;
                throw new MalformedChallengeException("SCRAM server-first invalid sid");
            }

            // server-first (data present)
            final String decoded;
            try {
                decoded = b64ToString(data);
            } catch (final MalformedChallengeException ex) {
                zeroAndClearExpectedV();
                this.state = State.FAILED;
                throw ex;
            }
            this.serverFirstRaw = decoded;
            final Map<String, String> attrs;
            try {
                attrs = parseAttrs(decoded);
                validateServerFirstAttrs(attrs);
            } catch (final MalformedChallengeException ex) {
                zeroAndClearExpectedV();
                this.state = State.FAILED;
                throw ex;
            }
            final String r = attrs.get("r");
            final String s = attrs.get("s");
            final String i = attrs.get("i");
            if (!isPrintableNonce(r)) {
                this.state = State.FAILED;
                throw new MalformedChallengeException("Invalid SCRAM server nonce syntax");
            }
            if (this.clientNonce == null || !r.startsWith(this.clientNonce)
                    || r.length() <= this.clientNonce.length()) {
                this.state = State.FAILED;
                throw new AuthenticationException("SCRAM server nonce does not extend client nonce");
            }
            try {
                this.salt = decodeCanonicalBase64(s, "salt");
                if (this.salt.length == 0) {
                    throw new MalformedChallengeException("Empty SCRAM salt");
                }
            } catch (final MalformedChallengeException ex) {
                this.state = State.FAILED;
                throw ex;
            }
            if (!isPositiveNumber(i)) {
                this.state = State.FAILED;
                throw new MalformedChallengeException("Invalid SCRAM iteration count");
            }
            try {
                this.iterations = Integer.parseInt(i);
            } catch (final NumberFormatException e) {
                this.state = State.FAILED;
                throw new MalformedChallengeException("SCRAM iteration count is too large", e);
            }
            if (this.minIterationsRequired > 0 && this.iterations < this.minIterationsRequired) {
                this.state = State.FAILED;
                throw new AuthenticationException(
                        "SCRAM iteration count below required minimum: " + this.iterations + " < " + this.minIterationsRequired);
            }
            if (this.iterations > this.maxIterationsAllowed) {
                this.state = State.FAILED;
                throw new AuthenticationException(
                        "SCRAM iteration count above allowed maximum: " + this.iterations + " > " + this.maxIterationsAllowed);
            }
            if (this.warnMinIterations > 0 && this.iterations < this.warnMinIterations && LOG.isWarnEnabled()) {
                LOG.warn("SCRAM iteration count ({}) lower than recommended ({})", this.iterations, warnMinIterations);
            }

            this.sid = sid;
            this.serverNonce = r;
            this.state = State.SERVER_FIRST_RCVD;
            this.complete = false;
            zeroAndClearExpectedV();
            return;
        }

        if (this.state != State.CLIENT_FINAL_SENT) {
            final State currentState = this.state;
            zeroAndClearExpectedV();
            this.state = State.FAILED;
            throw new AuthenticationException("SCRAM final response out of sequence: " + currentState);
        }

        final String sid = params.get("sid");
        if (sid == null || sid.isEmpty()) {
            zeroAndClearExpectedV();
            this.state = State.FAILED;
            throw new MalformedChallengeException("SCRAM Authentication-Info missing sid");
        }
        if (!isToken(sid)) {
            zeroAndClearExpectedV();
            this.state = State.FAILED;
            throw new MalformedChallengeException("SCRAM Authentication-Info invalid sid");
        }
        if (this.sid == null || !this.sid.equals(sid)) {
            zeroAndClearExpectedV();
            this.state = State.FAILED;
            throw new AuthenticationException("SCRAM sid mismatch");
        }

        // --- final-response path (Authentication-Info on any status) ---
        // For Authentication-Info, RFC 7804 does NOT mandate a scheme token; do NOT enforce scheme name here.
        final String data = params.get("data");
        if (data == null) {
            zeroAndClearExpectedV();
            this.state = State.FAILED;
            throw new MalformedChallengeException("SCRAM Authentication-Info missing data");
        }
        final String decoded;
        try {
            decoded = b64ToString(data);
        } catch (final MalformedChallengeException ex) {
            zeroAndClearExpectedV();
            this.state = State.FAILED;
            throw ex;
        }
        final Map<String, String> attrs;
        try {
            attrs = parseAttrs(decoded);
            validateServerFinalAttrs(attrs);
        } catch (final MalformedChallengeException ex) {
            zeroAndClearExpectedV();
            this.state = State.FAILED;
            throw ex;
        }
        final String err = attrs.get("e");
        if (err != null) {
            zeroAndClearExpectedV();
            this.state = State.FAILED;
            throw new AuthenticationException("SCRAM server error: " + err);
        }
        final String vB64 = attrs.get("v");
        final byte[] expected = this.expectedV;
        this.expectedV = null;
        byte[] vBytes = null;
        boolean match;
        try {
            vBytes = decodeCanonicalBase64(vB64, "server signature");
            match = expected != null && MessageDigest.isEqual(expected, vBytes);
        } catch (final MalformedChallengeException ex) {
            match = false;
        } finally {
            zero(vBytes);
            zero(expected);
        }
        if (!match) {
            this.state = State.FAILED;
            throw new MalformedChallengeException("SCRAM server signature mismatch");
        }
        this.complete = true;
        this.state = State.COMPLETE;
    }

    /**
     * @since 5.6
     */
    @Override
    public boolean isChallengeComplete() {
        return this.state == State.FAILED;
    }

    /**
     * @since 5.6
     */
    @Override
    public String getRealm() {
        return this.realm;
    }

    /**
     * Allow response when:
     * - INIT  (preemptive client-first) — only if creds have been prepared
     * - ANNOUNCED (401 without data)
     * - SERVER_FIRST_RCVD (ready to send client-final)
     *
     * @since 5.6
     */
    @Override
    public boolean isResponseReady(
            final HttpHost host,
            final CredentialsProvider credentialsProvider,
            final HttpContext context) throws AuthenticationException {

        Args.notNull(credentialsProvider, "Credentials provider");

        final HttpClientContext clientContext = HttpClientContext.cast(context);
        final AuthScope scope = new AuthScope(host, this.realm, getName());
        final Credentials creds = credentialsProvider.getCredentials(scope, clientContext);
        if (!(creds instanceof UsernamePasswordCredentials)) {
            return false;
        }
        final UsernamePasswordCredentials up = (UsernamePasswordCredentials) creds;

        // RFC 5802 prepares the username with SASLprep. RFC 7804 defines password
        // Normalize() using RFC 7613 OpaqueString and explicitly permits clients that do not
        // implement OpaqueString to reject non-US-ASCII passwords.
        final String preppedUser;
        final char[] passChars = up.getUserPassword() != null ? up.getUserPassword().clone() : null;
        try {
            preppedUser = SaslPrep.INSTANCE.prepAsQueryString(up.getUserName());
            if (preppedUser == null || preppedUser.isEmpty()) {
                throw new AuthenticationException("SCRAM username preparation produced an empty value");
            }
            if (passChars == null || passChars.length == 0) {
                throw new AuthenticationException("SCRAM password is missing or empty");
            }
            for (final char ch : passChars) {
                if (ch < 0x20 || ch > 0x7e) {
                    throw new AuthenticationException(
                            "SCRAM password contains characters outside printable US-ASCII; "
                                    + "RFC 7613 OpaqueString is not implemented");
                }
            }
            this.username = preppedUser;
            this.password = passChars.clone();
        } catch (final AuthenticationException ex) {
            throw ex;
        } catch (final Exception e) {
            throw new AuthenticationException("SASLprep failed for SCRAM username", e);
        } finally {
            if (passChars != null) {
                Arrays.fill(passChars, '\0');
            }
        }
        this.principal = new SimplePrincipal(this.username);

        switch (this.state) {
            case INIT: // allow preemptive
            case ANNOUNCED:
            case SERVER_FIRST_RCVD:
                return true;
            default:
                return false;
        }
    }

    /**
     * @since 5.6
     */
    @Override
    public String generateAuthResponse(
            final HttpHost host,
            final HttpRequest request,
            final HttpContext context) throws AuthenticationException {

        switch (this.state) {
            case INIT:
                // Allow preemptive only if credentials were prepared already
                if (this.username == null) {
                    this.state = State.FAILED;
                    throw new AuthenticationException("SCRAM state out of sequence: INIT without credentials");
                }
                return buildClientFirst();
            case ANNOUNCED:
                return buildClientFirst();
            case SERVER_FIRST_RCVD:
                return buildClientFinalAndExpectV();
            default:
                this.state = State.FAILED;
                throw new AuthenticationException("SCRAM state out of sequence: " + this.state);
        }
    }

    /**
     * Returns {@link Principal} whose credentials are used.
     *
     * @since 5.6
     */
    @Override
    public Principal getPrincipal() {
        return this.principal;
    }

    // ---------------- internals ----------------

    private String buildClientFirst() {
        this.clientNonce = genNonce();
        final String escUser = escapeUser(this.username);
        this.clientFirstBare = "n=" + escUser + ",r=" + this.clientNonce;

        // RFC 7804: data = base64(GS2 header + client-first-bare)
        final String data = stringToB64(GS2_HEADER + this.clientFirstBare);

        final StringBuilder sb = new StringBuilder(64);
        sb.append(StandardAuthScheme.SCRAM_SHA_256).append(' ');
        if (this.realm != null) {
            sb.append("realm=").append(quoteParam(this.realm)).append(", ");
        }
        sb.append("data=").append(quoteParam(data)); // quoted per RFC 7804
        this.state = State.CLIENT_FIRST_SENT;
        this.complete = false;
        zeroAndClearExpectedV();
        return sb.toString();
    }

    private String buildClientFinalAndExpectV() throws AuthenticationException {
        byte[] salted = null, clientKey = null, storedKey = null, clientSignature = null,
                clientProof = null, serverKey = null, serverSignature = null;

        try {
            // HTTP SCRAM: no CB -> c=biws
            final String clientFinalNoProof = "c=" + C_BIND_B64 + ",r=" + this.serverNonce;
            final String authMessage = this.clientFirstBare + "," + this.serverFirstRaw + "," + clientFinalNoProof;

            salted = hiPBKDF2(this.password, this.salt, this.iterations, 32);
            clientKey = hmac(salted, "Client Key");
            storedKey = sha256(clientKey);
            clientSignature = hmac(storedKey, authMessage);
            clientProof = xor(clientKey, clientSignature);
            final String pB64 = B64.encodeToString(clientProof);

            serverKey = hmac(salted, "Server Key");
            serverSignature = hmac(serverKey, authMessage);

            // Stash expected v (raw) for constant-time check on 2xx
            zeroAndClearExpectedV();
            this.expectedV = serverSignature.clone();

            final String clientFinal = clientFinalNoProof + ",p=" + pB64;
            final String data = stringToB64(clientFinal);

            final StringBuilder sb = new StringBuilder(64);
            sb.append(StandardAuthScheme.SCRAM_SHA_256).append(' ');
            sb.append("sid=").append(this.sid).append(", ");
            sb.append("data=").append(quoteParam(data)); // quoted

            this.state = State.CLIENT_FINAL_SENT;
            this.complete = false;
            return sb.toString();
        } catch (final GeneralSecurityException e) {
            this.state = State.FAILED;
            throw new AuthenticationException("SCRAM crypto failure", e);
        } finally {
            if (this.password != null) {
                Arrays.fill(this.password, '\0');
                this.password = null;
            }
            zero(salted);
            zero(clientKey);
            zero(storedKey);
            zero(clientSignature);
            zero(clientProof);
            zero(serverKey);
            // clone held in expectedV
            zero(serverSignature);
        }
    }

    private static void zero(final byte[] a) {
        if (a != null) {
            Arrays.fill(a, (byte) 0);
        }
    }

    private void zeroAndClearExpectedV() {
        if (this.expectedV != null) {
            zero(this.expectedV);
            this.expectedV = null;
        }
    }
    private static Map<String, String> toParamMap(final List<NameValuePair> pairs)
            throws MalformedChallengeException {
        final Map<String, String> m = new HashMap<>();
        if (pairs != null) {
            for (final NameValuePair p : pairs) {
                if (p != null && p.getName() != null) {
                    final String name = p.getName().toLowerCase(Locale.ROOT);
                    if (m.putIfAbsent(name, p.getValue()) != null) {
                        throw new MalformedChallengeException("Duplicate SCRAM auth-param: " + name);
                    }
                }
            }
        }
        return m;
    }

    /** Generic SCRAM attribute parser. Message-specific order and semantics are validated separately. */
    private static Map<String, String> parseAttrs(final String s) throws MalformedChallengeException {
        final Map<String, String> out = new LinkedHashMap<>();
        if (s == null || s.isEmpty()) {
            throw new MalformedChallengeException("Empty SCRAM message");
        }
        final String[] parts = s.split(",", -1);
        for (final String part : parts) {
            if (part.length() < 2 || part.charAt(1) != '=' || !isAlpha(part.charAt(0))) {
                throw new MalformedChallengeException("Bad SCRAM attribute: " + part);
            }
            final String key = part.substring(0, 1);
            final String value = part.substring(2);
            for (int i = 0; i < value.length(); i++) {
                if (value.charAt(i) == '\0') {
                    throw new MalformedChallengeException("NUL is not permitted in SCRAM attribute values");
                }
            }
            if (out.containsKey(key)) {
                if (isReservedScramAttribute(key)) {
                    throw new MalformedChallengeException("Duplicate SCRAM attribute: " + key);
                }
                if (value.isEmpty()) {
                    throw new MalformedChallengeException("Empty SCRAM extension attribute: " + key);
                }
                continue;
            }
            out.put(key, value);
        }
        return out;
    }

    private static void validateServerFirstAttrs(final Map<String, String> attrs)
            throws MalformedChallengeException {
        if (attrs.containsKey("m")) {
            throw new MalformedChallengeException("Unsupported mandatory SCRAM extension 'm'");
        }
        if (!attrs.containsKey("r") || !attrs.containsKey("s") || !attrs.containsKey("i")) {
            throw new MalformedChallengeException("SCRAM server-first missing r/s/i");
        }
        final Iterator<String> it = attrs.keySet().iterator();
        if (!it.hasNext() || !"r".equals(it.next())
                || !it.hasNext() || !"s".equals(it.next())
                || !it.hasNext() || !"i".equals(it.next())) {
            throw new MalformedChallengeException("Invalid SCRAM server-first attribute order; expected r,s,i");
        }
        while (it.hasNext()) {
            final String key = it.next();
            if (isReservedScramAttribute(key)) {
                throw new MalformedChallengeException("SCRAM attribute out of position: " + key);
            }
            if (attrs.get(key).isEmpty()) {
                throw new MalformedChallengeException("Empty SCRAM extension attribute: " + key);
            }
        }
        if (attrs.get("r").isEmpty() || attrs.get("s").isEmpty() || attrs.get("i").isEmpty()) {
            throw new MalformedChallengeException("SCRAM server-first missing r/s/i value");
        }
    }

    private static void validateServerFinalAttrs(final Map<String, String> attrs)
            throws MalformedChallengeException {
        if (attrs.containsKey("m")) {
            throw new MalformedChallengeException("Unsupported mandatory SCRAM extension 'm'");
        }
        final Iterator<String> it = attrs.keySet().iterator();
        if (!it.hasNext()) {
            throw new MalformedChallengeException("Empty SCRAM server-final message");
        }
        final String first = it.next();
        if (!"e".equals(first) && !"v".equals(first)) {
            throw new MalformedChallengeException("SCRAM server-final must start with e or v");
        }
        if (attrs.get(first).isEmpty()) {
            throw new MalformedChallengeException("Empty SCRAM server-final " + first + " value");
        }
        while (it.hasNext()) {
            final String key = it.next();
            if (isReservedScramAttribute(key)) {
                throw new MalformedChallengeException("SCRAM attribute out of position: " + key);
            }
            if (attrs.get(key).isEmpty()) {
                throw new MalformedChallengeException("Empty SCRAM extension attribute: " + key);
            }
        }
    }

    private static boolean isReservedScramAttribute(final String key) {
        return "a".equals(key) || "c".equals(key) || "e".equals(key) || "i".equals(key)
                || "m".equals(key) || "n".equals(key) || "p".equals(key) || "r".equals(key)
                || "s".equals(key) || "v".equals(key);
    }

    private static boolean isAlpha(final char ch) {
        return ch >= 'A' && ch <= 'Z' || ch >= 'a' && ch <= 'z';
    }

    private static boolean isPositiveNumber(final String value) {
        if (value == null || value.isEmpty() || value.charAt(0) < '1' || value.charAt(0) > '9') {
            return false;
        }
        for (int i = 1; i < value.length(); i++) {
            final char ch = value.charAt(i);
            if (ch < '0' || ch > '9') {
                return false;
            }
        }
        return true;
    }

    private static boolean isPrintableNonce(final String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            final char ch = value.charAt(i);
            if (ch < 0x21 || ch > 0x7e || ch == ',') {
                return false;
            }
        }
        return true;
    }

    private static boolean isToken(final String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            final char ch = value.charAt(i);
            final boolean alphaNum = ch >= '0' && ch <= '9' || ch >= 'A' && ch <= 'Z' || ch >= 'a' && ch <= 'z';
            final boolean tchar = alphaNum || ch == '!' || ch == '#' || ch == '$' || ch == '%' || ch == '&'
                    || ch == '\'' || ch == '*' || ch == '+' || ch == '-' || ch == '.' || ch == '^'
                    || ch == '_' || ch == '`' || ch == '|' || ch == '~';
            if (!tchar) {
                return false;
            }
        }
        return true;
    }

    private static byte[] decodeCanonicalBase64(final String value, final String what)
            throws MalformedChallengeException {
        if (value == null) {
            throw new MalformedChallengeException("Missing SCRAM " + what);
        }
        try {
            final byte[] decoded = B64D.decode(value);
            if (!B64.encodeToString(decoded).equals(value)) {
                throw new MalformedChallengeException("Non-canonical base64 SCRAM " + what);
            }
            return decoded;
        } catch (final IllegalArgumentException ex) {
            throw new MalformedChallengeException("Invalid base64 SCRAM " + what, ex);
        }
    }

    private String genNonce() {
        final byte[] buf = new byte[16];
        this.secureRandom.nextBytes(buf);
        final StringBuilder sb = new StringBuilder(buf.length * 2);
        for (final byte b : buf) {
            final int v = b & 0xff;
            if (v < 0x10) {
                sb.append('0');
            }
            sb.append(Integer.toHexString(v));
        }
        return sb.toString();
    }

    private static String escapeUser(final String user) {
        if (user == null) {
            // Defensive for tests that skip isResponseReady() before client-first
            return "";
        }
        final StringBuilder sb = new StringBuilder(user.length() + 8);
        for (int i = 0; i < user.length(); i++) {
            final char c = user.charAt(i);
            if (c == ',') {
                sb.append("=2C");
            } else if (c == '=') {
                sb.append("=3D");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String quoteParam(final String v) {
        if (v == null) {
            return "\"\"";
        }
        final StringBuilder sb = new StringBuilder(v.length() + 2);
        sb.append('"');
        for (int i = 0; i < v.length(); i++) {
            final char c = v.charAt(i);
            if (c == '\\' || c == '"') {
                sb.append('\\');
            }
            sb.append(c);
        }
        sb.append('"');
        return sb.toString();
    }

    private static byte[] hiPBKDF2(final char[] password, final byte[] salt, final int iterations, final int dkLen)
            throws GeneralSecurityException {
        final PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, dkLen * 8);
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    }

    private static byte[] hmac(final byte[] key, final String msg) throws GeneralSecurityException {
        final Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(msg.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] sha256(final byte[] in) throws GeneralSecurityException {
        return MessageDigest.getInstance("SHA-256").digest(in);
    }

    private static byte[] xor(final byte[] a, final byte[] b) {
        final int len = Math.min(a.length, b.length);
        final byte[] out = new byte[len];
        for (int i = 0; i < len; i++) {
            out[i] = (byte) (a[i] ^ b[i]);
        }
        return out;
    }

    private static String stringToB64(final String s) {
        return B64.encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String b64ToString(final String b64) throws MalformedChallengeException {
        final byte[] decoded = decodeCanonicalBase64(b64, "data value");
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(decoded))
                    .toString();
        } catch (final CharacterCodingException ex) {
            throw new MalformedChallengeException("Invalid UTF-8 in SCRAM data value", ex);
        }
    }

    private static final class SimplePrincipal implements Principal {
        private final String name;

        private SimplePrincipal(final String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }

}
