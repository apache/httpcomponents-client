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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Principal;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

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
import org.apache.hc.client5.http.utils.ByteArrayBuilder;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicHeaderValueFormatter;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.net.PercentCodec;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.apache.hc.core5.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Digest authentication scheme.
 * Both MD5 (default) and MD5-sess are supported.
 * Currently only qop=auth or no qop is supported. qop=auth-int
 * is unsupported. If auth and auth-int are provided, auth is
 * used.
 * <p>
 * Since the digest username is included as clear text in the generated
 * Authentication header, the charset of the username must be compatible
 * with the HTTP element charset used by the connection.
 * </p>
 *
 * @since 4.0
 */
public class DigestScheme implements AuthScheme, Serializable {

    private static final long serialVersionUID = 3883908186234566916L;

    private static final Logger LOG = LoggerFactory.getLogger(DigestScheme.class);

    /**
     * Hexa values used when creating 32 character long digest in HTTP DigestScheme
     * in case of authentication.
     *
     * @see #formatHex(byte[])
     */
    private static final char[] HEXADECIMAL = {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd',
        'e', 'f'
    };

    /**
     * Represent the possible values of quality of protection.
     */
    private enum QualityOfProtection {
        UNKNOWN, MISSING, AUTH_INT, AUTH
    }

    private transient Charset defaultCharset;
    private final Map<String, String> paramMap;
    private boolean complete;
    private transient ByteArrayBuilder buffer;

    /**
     * Flag indicating whether username hashing is supported.
     * <p>
     * This flag is used to determine if the server supports hashing of the username
     * as part of the Digest Access Authentication process. When set to {@code true},
     * the client is expected to hash the username using the same algorithm used for
     * hashing the credentials. This is in accordance with Section 3.4.4 of RFC 7616.
     * </p>
     * <p>
     * The default value is {@code false}, indicating that username hashing is not
     * supported. If the server requires username hashing (indicated by the
     * {@code userhash} parameter in the  a header set to {@code true}),
     * this flag should be set to {@code true} to comply with the server's requirements.
     * </p>
     */
    private boolean userhashSupported = false;


    private String lastNonce;
    private long nounceCount;
    private String cnonce;
    private byte[] a1;
    private byte[] a2;

    private UsernamePasswordCredentials credentials;

    public DigestScheme() {
        this.defaultCharset = StandardCharsets.UTF_8;
        this.paramMap = new HashMap<>();
        this.complete = false;
    }

    /**
     * @deprecated This constructor is deprecated to enforce the use of {@link StandardCharsets#UTF_8} encoding
     * in compliance with RFC 7616 for HTTP Digest Access Authentication. Use the default constructor {@link #DigestScheme()} instead.
     *
     * @param charset the {@link Charset} set to be used for encoding credentials. This parameter is ignored as UTF-8 is always used.
     */
    @Deprecated
    public DigestScheme(final Charset charset) {
        this();
    }

    public void initPreemptive(final Credentials credentials, final String cnonce, final String realm) {
        Args.notNull(credentials, "Credentials");
        Args.check(credentials instanceof UsernamePasswordCredentials,
                "Unsupported credential type: " + credentials.getClass());
        this.credentials = (UsernamePasswordCredentials) credentials;
        this.paramMap.put("cnonce", cnonce);
        this.paramMap.put("realm", realm);
    }

    @Override
    public String getName() {
        return StandardAuthScheme.DIGEST;
    }

    @Override
    public boolean isConnectionBased() {
        return false;
    }

    @Override
    public String getRealm() {
        return this.paramMap.get("realm");
    }

    @Override
    public void processChallenge(
            final AuthChallenge authChallenge,
            final HttpContext context) throws MalformedChallengeException {
        Args.notNull(authChallenge, "AuthChallenge");
        this.paramMap.clear();
        final List<NameValuePair> params = authChallenge.getParams();
        if (params != null) {
            for (final NameValuePair param: params) {
                this.paramMap.put(param.getName().toLowerCase(Locale.ROOT), param.getValue());
            }
        }
        if (this.paramMap.isEmpty()) {
            throw new MalformedChallengeException("Missing digest auth parameters");
        }

        final String userHashValue = this.paramMap.get("userhash");
        this.userhashSupported = "true".equalsIgnoreCase(userHashValue);

        this.complete = true;
    }

    @Override
    public boolean isChallengeComplete() {
        final String s = this.paramMap.get("stale");
        return !"true".equalsIgnoreCase(s) && this.complete;
    }

    @Override
    public boolean isResponseReady(
            final HttpHost host,
            final CredentialsProvider credentialsProvider,
            final HttpContext context) throws AuthenticationException {

        Args.notNull(host, "Auth host");
        Args.notNull(credentialsProvider, "CredentialsProvider");

        final AuthScope authScope = new AuthScope(host, getRealm(), getName());
        final Credentials credentials = credentialsProvider.getCredentials(
                authScope, context);
        if (credentials instanceof UsernamePasswordCredentials) {
            this.credentials = (UsernamePasswordCredentials) credentials;
            return true;
        }

        if (LOG.isDebugEnabled()) {
            final HttpClientContext clientContext = HttpClientContext.cast(context);
            final String exchangeId = clientContext.getExchangeId();
            LOG.debug("{} No credentials found for auth scope [{}]", exchangeId, authScope);
        }
        this.credentials = null;
        return false;
    }

    @Override
    public Principal getPrincipal() {
        return null;
    }

    @Override
            public String generateAuthResponse(
            final HttpHost host,
            final HttpRequest request,
            final HttpContext context) throws AuthenticationException {

        Args.notNull(request, "HTTP request");
        if (this.paramMap.get("realm") == null) {
            throw new AuthenticationException("missing realm");
        }
        if (this.paramMap.get("nonce") == null) {
            throw new AuthenticationException("missing nonce");
        }

        if (context != null) {
            final HttpClientContext clientContext = HttpClientContext.cast(context);
            final String nextNonce = clientContext.getNextNonce();
            if (!TextUtils.isBlank(nextNonce)) {
                this.paramMap.put("nonce", nextNonce);
                clientContext.setNextNonce(null);
            }
        }

        return createDigestResponse(request);
    }

    private static MessageDigest createMessageDigest(
            final String digAlg) throws UnsupportedDigestAlgorithmException {
        try {
            return MessageDigest.getInstance(digAlg);
        } catch (final Exception e) {
            throw new UnsupportedDigestAlgorithmException(
              "Unsupported algorithm in HTTP Digest authentication: "
               + digAlg);
        }
    }

    private String createDigestResponse(final HttpRequest request) throws AuthenticationException {
        if (credentials == null) {
            throw new AuthenticationException("User credentials have not been provided");
        }
        final String uri = request.getRequestUri();
        final String method = request.getMethod();
        final String realm = this.paramMap.get("realm");
        final String nonce = this.paramMap.get("nonce");
        final String opaque = this.paramMap.get("opaque");
        final String algorithm = this.paramMap.get("algorithm");

        final Set<String> qopset = new HashSet<>(8);
        QualityOfProtection qop = QualityOfProtection.UNKNOWN;
        final String qoplist = this.paramMap.get("qop");
        if (qoplist != null) {
            final StringTokenizer tok = new StringTokenizer(qoplist, ",");
            while (tok.hasMoreTokens()) {
                final String variant = tok.nextToken().trim();
                qopset.add(variant.toLowerCase(Locale.ROOT));
            }
            final HttpEntity entity = request instanceof ClassicHttpRequest ? ((ClassicHttpRequest) request).getEntity() : null;
            if (entity != null && qopset.contains("auth-int")) {
                qop = QualityOfProtection.AUTH_INT;
            } else if (qopset.contains("auth")) {
                qop = QualityOfProtection.AUTH;
            } else if (qopset.contains("auth-int")) {
                qop = QualityOfProtection.AUTH_INT;
            }
        } else {
            qop = QualityOfProtection.MISSING;
        }

        if (qop == QualityOfProtection.UNKNOWN) {
            throw new AuthenticationException("None of the qop methods is supported: " + qoplist);
        }

        final Charset charset = AuthSchemeSupport.parseCharset(paramMap.get("charset"), defaultCharset);

        // If an algorithm is not specified, default to MD5.

        DigestAlgorithm digAlg = null;

        final MessageDigest digester;
        try {
            digAlg = DigestAlgorithm.fromString(algorithm == null ? "MD5" : algorithm);
            digester = createMessageDigest(digAlg.getBaseAlgorithm());
        } catch (final UnsupportedDigestAlgorithmException ex) {
            throw new AuthenticationException("Unsupported digest algorithm: " + digAlg);
        }

        if (nonce.equals(this.lastNonce)) {
            nounceCount++;
        } else {
            nounceCount = 1;
            cnonce = null;
            lastNonce = nonce;
        }

        final StringBuilder sb = new StringBuilder(8);
        try (final Formatter formatter = new Formatter(sb, Locale.ROOT)) {
            formatter.format("%08x", nounceCount);
        }
        final String nc = sb.toString();

        if (cnonce == null) {
            cnonce = formatHex(createCnonce(digAlg));
        }

        if (buffer == null) {
            buffer = new ByteArrayBuilder(128);
        } else {
            buffer.reset();
        }
        buffer.charset(charset);

        a1 = null;
        a2 = null;


        // Extract username and username*
        String username = credentials.getUserName();
        String encodedUsername = null;
        // Check if 'username' has invalid characters and use 'username*'
        if (username != null && containsInvalidABNFChars(username)) {
            encodedUsername = "UTF-8''" + PercentCodec.RFC5987.encode(username);
        }

        final String usernameForDigest;
        if (this.userhashSupported) {
            final String usernameRealm = username + ":" + realm;
            final byte[] hashedBytes = digester.digest(usernameRealm.getBytes(StandardCharsets.UTF_8));
            usernameForDigest = formatHex(hashedBytes); // Use hashed username for digest
            username = usernameForDigest;
        } else if (encodedUsername != null) {
            usernameForDigest = encodedUsername; // Use encoded username for digest
        } else {
            usernameForDigest = username; // Use regular username for digest
        }

        // 3.2.2.2: Calculating digest
        if (digAlg.isSessionBased()) {
            // H( unq(username-value) ":" unq(realm-value) ":" passwd )
            //      ":" unq(nonce-value)
            //      ":" unq(cnonce-value)

            // calculated one per session
            buffer.append(username).append(":").append(realm).append(":").append(credentials.getUserPassword());
            final String checksum = formatHex(digester.digest(this.buffer.toByteArray()));
            buffer.reset();
            buffer.append(checksum).append(":").append(nonce).append(":").append(cnonce);
        } else {
            // unq(username-value) ":" unq(realm-value) ":" passwd
            buffer.append(username).append(":").append(realm).append(":").append(credentials.getUserPassword());
        }
        a1 = buffer.toByteArray();

        final String hasha1 = formatHex(digester.digest(a1));
        buffer.reset();

        if (qop == QualityOfProtection.AUTH) {
            // Method ":" digest-uri-value
            a2 = buffer.append(method).append(":").append(uri).toByteArray();
        } else if (qop == QualityOfProtection.AUTH_INT) {
            // Method ":" digest-uri-value ":" H(entity-body)
            final HttpEntity entity = request instanceof ClassicHttpRequest ? ((ClassicHttpRequest) request).getEntity() : null;
            if (entity != null && !entity.isRepeatable()) {
                // If the entity is not repeatable, try falling back onto QOP_AUTH
                if (qopset.contains("auth")) {
                    qop = QualityOfProtection.AUTH;
                    a2 = buffer.append(method).append(":").append(uri).toByteArray();
                } else {
                    throw new AuthenticationException("Qop auth-int cannot be used with " +
                            "a non-repeatable entity");
                }
            } else {
                final HttpEntityDigester entityDigester = new HttpEntityDigester(digester);
                try {
                    if (entity != null) {
                        entity.writeTo(entityDigester);
                    }
                    entityDigester.close();
                } catch (final IOException ex) {
                    throw new AuthenticationException("I/O error reading entity content", ex);
                }
                a2 = buffer.append(method).append(":").append(uri)
                        .append(":").append(formatHex(entityDigester.getDigest())).toByteArray();
            }
        } else {
            a2 = buffer.append(method).append(":").append(uri).toByteArray();
        }

        final String hasha2 = formatHex(digester.digest(a2));
        buffer.reset();

        // 3.2.2.1

        final byte[] digestInput;
        if (qop == QualityOfProtection.MISSING) {
            buffer.append(hasha1).append(":").append(nonce).append(":").append(hasha2);
        } else {
            buffer.append(hasha1).append(":").append(nonce).append(":").append(nc).append(":")
                .append(cnonce).append(":").append(qop == QualityOfProtection.AUTH_INT ? "auth-int" : "auth")
                .append(":").append(hasha2);
        }
        digestInput = buffer.toByteArray();
        buffer.reset();

        final String digest = formatHex(digester.digest(digestInput));

        final CharArrayBuffer buffer = new CharArrayBuffer(128);
        buffer.append(StandardAuthScheme.DIGEST + " ");

        final List<BasicNameValuePair> params = new ArrayList<>(20);
        if (this.userhashSupported) {
            // Use hashed username for the 'username' parameter
            params.add(new BasicNameValuePair("username", usernameForDigest));
            params.add(new BasicNameValuePair("userhash", "true"));
        } else if (encodedUsername != null) {
            // Use encoded 'username*' parameter
            params.add(new BasicNameValuePair("username*", encodedUsername));
        } else {
            // Use regular 'username' parameter
            params.add(new BasicNameValuePair("username", username));
        }
        params.add(new BasicNameValuePair("realm", realm));
        params.add(new BasicNameValuePair("nonce", nonce));
        params.add(new BasicNameValuePair("uri", uri));
        params.add(new BasicNameValuePair("response", digest));

        if (qop != QualityOfProtection.MISSING) {
            params.add(new BasicNameValuePair("qop", qop == QualityOfProtection.AUTH_INT ? "auth-int" : "auth"));
            params.add(new BasicNameValuePair("nc", nc));
            params.add(new BasicNameValuePair("cnonce", cnonce));
            params.add(new BasicNameValuePair("rspauth", hasha2));
        }
        if (algorithm != null) {
            params.add(new BasicNameValuePair("algorithm", algorithm));
        }
        if (opaque != null) {
            params.add(new BasicNameValuePair("opaque", opaque));
        }

        for (int i = 0; i < params.size(); i++) {
            final BasicNameValuePair param = params.get(i);
            if (i > 0) {
                buffer.append(", ");
            }
            final String name = param.getName();
            final boolean noQuotes = "nc".equals(name) || "qop".equals(name)
                    || "algorithm".equals(name);
            BasicHeaderValueFormatter.INSTANCE.formatNameValuePair(buffer, param, !noQuotes);
        }
        return buffer.toString();
    }

    @Internal
    public String getNonce() {
        return lastNonce;
    }

    @Internal
    public long getNounceCount() {
        return nounceCount;
    }

    @Internal
    public String getCnonce() {
        return cnonce;
    }

    String getA1() {
        return a1 != null ? new String(a1, StandardCharsets.US_ASCII) : null;
    }

    String getA2() {
        return a2 != null ? new String(a2, StandardCharsets.US_ASCII) : null;
    }

    /**
     * Encodes a byte array digest into a hexadecimal string.
     * <p>
     * This method supports digests of various lengths, such as 16 bytes (128-bit) for MD5,
     * 32 bytes (256-bit) for SHA-256, and SHA-512/256. Each byte is converted to two
     * hexadecimal characters, so the resulting string length is twice the byte array length.
     * </p>
     *
     * @param binaryData the array containing the digest bytes
     * @return encoded hexadecimal string, or {@code null} if encoding failed
     */
    static String formatHex(final byte[] binaryData) {
        final int n = binaryData.length;
        final char[] buffer = new char[n * 2];
        for (int i = 0; i < n; i++) {
            final int low = binaryData[i] & 0x0f;
            final int high = (binaryData[i] & 0xf0) >> 4;
            buffer[i * 2] = HEXADECIMAL[high];
            buffer[(i * 2) + 1] = HEXADECIMAL[low];
        }
        return new String(buffer);
    }


    /**
     * Creates a random cnonce value based on the specified algorithm's expected entropy.
     * Adjusts the length of the byte array based on the algorithm to ensure sufficient entropy.
     *
     * @param algorithm the algorithm for which the cnonce is being generated (e.g., "MD5", "SHA-256", "SHA-512-256").
     * @return The cnonce value as a byte array.
     * @since 5.5
     */
    static byte[] createCnonce(final DigestAlgorithm algorithm) {
        final SecureRandom rnd = new SecureRandom();
        final int length;
        switch (algorithm.name().toUpperCase()) {
            case "SHA-256":
            case "SHA-512/256":
                length = 32;
                break;
            case "MD5":
            default:
                length = 16;
                break;
        }
        final byte[] tmp = new byte[length];
        rnd.nextBytes(tmp);
        return tmp;
    }


    private void writeObject(final ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        out.writeUTF(defaultCharset.name());
    }

    private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        this.defaultCharset = Charset.forName(in.readUTF());
    }

    @Override
    public String toString() {
        return getName() + this.paramMap;
    }

    /**
     * Checks if a given string contains characters that are not allowed
     * in an ABNF quoted-string as per standard specifications.
     * <p>
     * The method checks for:
     * - Control characters (ASCII 0x00 to 0x1F and 0x7F).
     * - Characters outside the printable ASCII range (above 0x7E).
     * - Double quotes (&quot;) and backslashes (\), which are not allowed.
     * </p>
     *
     * @param value The string to be checked for invalid ABNF characters.
     * @return {@code true} if invalid characters are found, {@code false} otherwise.
     * @throws IllegalArgumentException if the input string is null.
     */
    private boolean containsInvalidABNFChars(final String value) {
        if (value == null) {
            throw new IllegalArgumentException("Input string should not be null.");
        }

        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);

            // Check for control characters and DEL
            if (c <= 0x1F || c == 0x7F) {
                return true;
            }

            // Check for characters outside the range 0x20 to 0x7E
            if (c > 0x7E) {
                return true;
            }

            // Exclude double quotes and backslash
            if (c == '"' || c == '\\') {
                return true;
            }
        }
        return false;
    }

    /**
     * Enum representing supported digest algorithms for HTTP Digest Authentication,
     * including session-based variants.
     */
    private enum DigestAlgorithm {

        /**
         * MD5 digest algorithm.
         */
        MD5("MD5", false),

        /**
         * MD5 digest algorithm with session-based variant.
         */
        MD5_SESS("MD5", true),

        /**
         * SHA-256 digest algorithm.
         */
        SHA_256("SHA-256", false),

        /**
         * SHA-256 digest algorithm with session-based variant.
         */
        SHA_256_SESS("SHA-256", true),

        /**
         * SHA-512/256 digest algorithm.
         */
        SHA_512_256("SHA-512/256", false),

        /**
         * SHA-512/256 digest algorithm with session-based variant.
         */
        SHA_512_256_SESS("SHA-512/256", true);

        private final String baseAlgorithm;
        private final boolean sessionBased;

        /**
         * Constructor for {@code DigestAlgorithm}.
         *
         * @param baseAlgorithm the base name of the algorithm, e.g., "MD5" or "SHA-256"
         * @param sessionBased indicates if the algorithm is session-based (i.e., includes the "-sess" suffix)
         */
        DigestAlgorithm(final String baseAlgorithm, final boolean sessionBased) {
            this.baseAlgorithm = baseAlgorithm;
            this.sessionBased = sessionBased;
        }

        /**
         * Retrieves the base algorithm name without session suffix.
         *
         * @return the base algorithm name
         */
        private String getBaseAlgorithm() {
            return baseAlgorithm;
        }

        /**
         * Checks if the algorithm is session-based.
         *
         * @return {@code true} if the algorithm includes the "-sess" suffix, otherwise {@code false}
         */
        private boolean isSessionBased() {
            return sessionBased;
        }

        /**
         * Maps a string representation of an algorithm to the corresponding enum constant.
         *
         * @param algorithm the algorithm name, e.g., "SHA-256" or "SHA-512-256-sess"
         * @return the corresponding {@code DigestAlgorithm} constant
         * @throws UnsupportedDigestAlgorithmException if the algorithm is unsupported
         */
        private static DigestAlgorithm fromString(final String algorithm) {
            switch (algorithm.toUpperCase(Locale.ROOT)) {
                case "MD5":
                    return MD5;
                case "MD5-SESS":
                    return MD5_SESS;
                case "SHA-256":
                    return SHA_256;
                case "SHA-256-SESS":
                    return SHA_256_SESS;
                case "SHA-512/256":
                case "SHA-512-256":
                    return SHA_512_256;
                case "SHA-512-256-SESS":
                    return SHA_512_256_SESS;
                default:
                    throw new UnsupportedDigestAlgorithmException("Unsupported digest algorithm: " + algorithm);
            }
        }
    }


}
