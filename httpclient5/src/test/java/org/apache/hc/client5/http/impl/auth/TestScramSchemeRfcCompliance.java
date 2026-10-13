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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.hc.client5.http.auth.AuthChallenge;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.AuthenticationException;
import org.apache.hc.client5.http.auth.ChallengeType;
import org.apache.hc.client5.http.auth.MalformedChallengeException;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.junit.jupiter.api.Test;

final class TestScramSchemeRfcCompliance {

    private static final HttpHost HOST = new HttpHost("https", "example.test");
    private static final String REALM = "s1";
    private static final String SID = "sid-1";

    private static String b64(final byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }

    private static String b64(final String value) {
        return b64(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String deb64(final String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static Map<String, String> attrs(final String value) {
        final Map<String, String> result = new LinkedHashMap<>();
        for (final String part : value.split(",")) {
            final int i = part.indexOf('=');
            result.put(part.substring(0, i), part.substring(i + 1));
        }
        return result;
    }

    private static String param(final String header, final String name) {
        final String marker = name + "=";
        int p = header.indexOf(marker);
        if (p < 0) {
            return null;
        }
        p += marker.length();
        if (p < header.length() && header.charAt(p) == '"') {
            final int end = header.indexOf('"', p + 1);
            return header.substring(p + 1, end);
        }
        int end = header.indexOf(',', p);
        if (end < 0) {
            end = header.length();
        }
        return header.substring(p, end).trim();
    }

    private static final class Started {
        final ScramScheme scheme;
        final HttpClientContext context;
        final String clientFirstBare;
        final String clientNonce;

        Started(final ScramScheme scheme, final HttpClientContext context,
                final String clientFirstBare, final String clientNonce) {
            this.scheme = scheme;
            this.context = context;
            this.clientFirstBare = clientFirstBare;
            this.clientNonce = clientNonce;
        }
    }

    private static Started start() throws Exception {
        return start("u", "p".toCharArray());
    }

    private static Started start(final String user, final char[] password) throws Exception {
        final ScramScheme scheme = new ScramScheme();
        final BasicCredentialsProvider creds = new BasicCredentialsProvider();
        creds.setCredentials(new AuthScope(HOST, REALM, scheme.getName()),
                new UsernamePasswordCredentials(user, password));
        final HttpClientContext context = HttpClientContext.create();
        scheme.processChallenge(HOST, true,
                new AuthChallenge(ChallengeType.TARGET, scheme.getName(),
                        new BasicNameValuePair("realm", REALM)), context);
        assertTrue(scheme.isResponseReady(HOST, creds, context));
        final String authorization = scheme.generateAuthResponse(HOST, null, context);
        final String first = deb64(param(authorization, "data"));
        final String bare = first.substring("n,,".length());
        return new Started(scheme, context, bare, attrs(bare).get("r"));
    }

    private static void serverFirst(final Started started, final String message) throws Exception {
        started.scheme.processChallenge(HOST, true,
                new AuthChallenge(ChallengeType.TARGET, started.scheme.getName(),
                        new BasicNameValuePair("sid", SID),
                        new BasicNameValuePair("data", b64(message))),
                started.context);
    }

    private static String normalServerFirst(final Started started) {
        return "r=" + started.clientNonce + "Z,s=" + b64("salt") + ",i=4096";
    }

    @Test
    void canonicalBase64IsGenerated() throws Exception {
        final Started started = start();
        serverFirst(started, normalServerFirst(started));
        final String authorization = started.scheme.generateAuthResponse(HOST, null, started.context);
        final String data = param(authorization, "data");
        assertEquals(data, b64(Base64.getDecoder().decode(data)));
        final String proof = attrs(deb64(data)).get("p");
        assertEquals(proof, b64(Base64.getDecoder().decode(proof)));
        assertTrue(proof.endsWith("="));
        assertTrue(authorization.contains("sid=" + SID + ","));
        assertFalse(authorization.contains("sid=\""));
    }

    @Test
    void unpaddedOuterDataIsRejected() throws Exception {
        final Started started = start();
        final String message = normalServerFirst(started);
        String data = b64(message);
        while (data.endsWith("=")) {
            data = data.substring(0, data.length() - 1);
        }
        final String unpadded = data;
        assertThrows(MalformedChallengeException.class, () ->
                started.scheme.processChallenge(HOST, true,
                        new AuthChallenge(ChallengeType.TARGET, started.scheme.getName(),
                                new BasicNameValuePair("sid", SID),
                                new BasicNameValuePair("data", unpadded)), started.context));
    }

    @Test
    void unpaddedSaltIsRejected() throws Exception {
        final Started started = start();
        final String canonical = b64("salt");
        final String unpadded = canonical.substring(0, canonical.length() - 1);
        assertThrows(MalformedChallengeException.class, () ->
                serverFirst(started, "r=" + started.clientNonce + "Z,s=" + unpadded + ",i=4096"));
    }

    @Test
    void serverNonceMustActuallyExtendClientNonce() throws Exception {
        final Started started = start();
        assertThrows(AuthenticationException.class, () ->
                serverFirst(started, "r=" + started.clientNonce + ",s=" + b64("salt") + ",i=4096"));
    }

    @Test
    void serverNonceMustUsePrintableAsciiExceptComma() throws Exception {
        final Started started = start();
        assertThrows(MalformedChallengeException.class, () ->
                serverFirst(started, "r=" + started.clientNonce + " Z,s=" + b64("salt") + ",i=4096"));
    }

    @Test
    void leadingZeroIterationCountIsRejected() throws Exception {
        final Started started = start();
        assertThrows(MalformedChallengeException.class, () ->
                serverFirst(started, "r=" + started.clientNonce + "Z,s=" + b64("salt") + ",i=04096"));
    }

    @Test
    void mandatoryExtensionIsRejected() throws Exception {
        final Started started = start();
        assertThrows(MalformedChallengeException.class, () ->
                serverFirst(started, "m=required,r=" + started.clientNonce + "Z,s=" + b64("salt") + ",i=4096"));
    }

    @Test
    void serverFirstMissingRequiredAttributesIsRejectedAsMissing() throws Exception {
        final Started started = start();
        final MalformedChallengeException ex = assertThrows(MalformedChallengeException.class, () ->
                serverFirst(started, "r=" + started.clientNonce + "Z"));
        assertTrue(ex.getMessage().contains("missing"));
    }

    @Test
    void serverFirstAttributeOrderIsFixed() throws Exception {
        final Started started = start();
        assertThrows(MalformedChallengeException.class, () ->
                serverFirst(started, "s=" + b64("salt") + ",r=" + started.clientNonce + "Z,i=4096"));
    }

    @Test
    void duplicateScramAttributeIsRejected() throws Exception {
        final Started started = start();
        assertThrows(MalformedChallengeException.class, () ->
                serverFirst(started, "r=" + started.clientNonce + "Z,s=" + b64("salt")
                        + ",i=4096,i=4097"));
    }

    @Test
    void emptyOptionalExtensionIsRejected() throws Exception {
        final Started started = start();
        assertThrows(MalformedChallengeException.class, () ->
                serverFirst(started, normalServerFirst(started) + ",x="));
    }

    @Test
    void unknownNonEmptyOptionalExtensionIsIgnored() throws Exception {
        final Started started = start();
        serverFirst(started, normalServerFirst(started) + ",x=hello");
        final String authorization = started.scheme.generateAuthResponse(HOST, null, started.context);
        assertTrue(authorization.contains("sid=" + SID));
    }

    @Test
    void reservedAttributeCannotAppearAsExtension() throws Exception {
        final Started started = start();
        assertThrows(MalformedChallengeException.class, () ->
                serverFirst(started, normalServerFirst(started) + ",v=AAAA"));
    }

    @Test
    void invalidUtf8InDataIsRejected() throws Exception {
        final Started started = start();
        final byte[] invalid = new byte[] {(byte) 0xc3, 0x28};
        assertThrows(MalformedChallengeException.class, () ->
                started.scheme.processChallenge(HOST, true,
                        new AuthChallenge(ChallengeType.TARGET, started.scheme.getName(),
                                new BasicNameValuePair("sid", SID),
                                new BasicNameValuePair("data", b64(invalid))), started.context));
    }

    @Test
    void duplicateHttpAuthParamIsRejected() throws Exception {
        final Started started = start();
        final String data = b64(normalServerFirst(started));
        assertThrows(MalformedChallengeException.class, () ->
                started.scheme.processChallenge(HOST, true,
                        new AuthChallenge(ChallengeType.TARGET, started.scheme.getName(),
                                new BasicNameValuePair("sid", SID),
                                new BasicNameValuePair("data", data),
                                new BasicNameValuePair("DATA", data)), started.context));
    }

    @Test
    void invalidSidTokenIsRejected() throws Exception {
        final Started started = start();
        assertThrows(MalformedChallengeException.class, () ->
                started.scheme.processChallenge(HOST, true,
                        new AuthChallenge(ChallengeType.TARGET, started.scheme.getName(),
                                new BasicNameValuePair("sid", "bad sid"),
                                new BasicNameValuePair("data", b64(normalServerFirst(started)))), started.context));
    }

    private static AuthenticationException assertPasswordRejected(final char[] password) throws Exception {
        final ScramScheme scheme = new ScramScheme();
        final BasicCredentialsProvider creds = new BasicCredentialsProvider();
        creds.setCredentials(new AuthScope(HOST, REALM, scheme.getName()),
                new UsernamePasswordCredentials("u", password));
        final HttpClientContext context = HttpClientContext.create();
        scheme.processChallenge(HOST, true,
                new AuthChallenge(ChallengeType.TARGET, scheme.getName(),
                        new BasicNameValuePair("realm", REALM)), context);
        return assertThrows(AuthenticationException.class,
                () -> scheme.isResponseReady(HOST, creds, context));
    }

    @Test
    void nonAsciiPasswordIsRejectedWithoutOpaqueStringImplementation() throws Exception {
        final AuthenticationException ex = assertPasswordRejected("páss".toCharArray());
        assertTrue(ex.getMessage().contains("OpaqueString"));
    }

    @Test
    void asciiControlPasswordIsRejectedWithoutOpaqueStringImplementation() throws Exception {
        final AuthenticationException ex = assertPasswordRejected(new char[] {'p', '\u001f', 'x'});
        assertTrue(ex.getMessage().contains("printable US-ASCII"));
    }

    @Test
    void asciiDeletePasswordIsRejectedWithoutOpaqueStringImplementation() throws Exception {
        final AuthenticationException ex = assertPasswordRejected(new char[] {'p', '\u007f', 'x'});
        assertTrue(ex.getMessage().contains("printable US-ASCII"));
    }

    @Test
    void serverFinalMustStartWithErrorOrVerifier() throws Exception {
        final Started started = start();
        serverFirst(started, normalServerFirst(started));
        started.scheme.generateAuthResponse(HOST, null, started.context);
        assertThrows(MalformedChallengeException.class, () ->
                started.scheme.processChallenge(HOST, false,
                        new AuthChallenge(ChallengeType.TARGET, started.scheme.getName(),
                                new BasicNameValuePair("sid", SID),
                                new BasicNameValuePair("data", b64("x=hello,v=AAAA"))), started.context));
    }

    @Test
    void serverFinalCannotContainBothVerifierAndError() throws Exception {
        final Started started = start();
        serverFirst(started, normalServerFirst(started));
        started.scheme.generateAuthResponse(HOST, null, started.context);
        assertThrows(MalformedChallengeException.class, () ->
                started.scheme.processChallenge(HOST, false,
                        new AuthChallenge(ChallengeType.TARGET, started.scheme.getName(),
                                new BasicNameValuePair("sid", SID),
                                new BasicNameValuePair("data", b64("v=AAAA,e=other-error"))), started.context));
    }

    @Test
    void unpaddedVerifierIsRejectedAsSignatureMismatch() throws Exception {
        final Started started = start();
        final String serverFirst = normalServerFirst(started);
        serverFirst(started, serverFirst);
        final String clientFinal = deb64(param(started.scheme.generateAuthResponse(HOST, null, started.context), "data"));
        final Map<String, String> cf = attrs(clientFinal);
        final String noProof = "c=" + cf.get("c") + ",r=" + cf.get("r");
        final String authMessage = started.clientFirstBare + "," + serverFirst + "," + noProof;
        final byte[] salted = pbkdf2("p".toCharArray(), Base64.getDecoder().decode(b64("salt")), 4096, 32);
        final byte[] serverKey = hmac(salted, "Server Key");
        final String canonical = b64(hmac(serverKey, authMessage));
        final String unpadded = canonical.substring(0, canonical.length() - 1);
        final MalformedChallengeException ex = assertThrows(MalformedChallengeException.class, () ->
                started.scheme.processChallenge(HOST, false,
                        new AuthChallenge(ChallengeType.TARGET, started.scheme.getName(),
                                new BasicNameValuePair("sid", SID),
                                new BasicNameValuePair("data", b64("v=" + unpadded))), started.context));
        assertTrue(ex.getMessage().contains("signature"));
    }

    private static byte[] pbkdf2(final char[] password, final byte[] salt, final int iter, final int len)
            throws GeneralSecurityException {
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(new PBEKeySpec(password, salt, iter, len * 8)).getEncoded();
    }

    private static byte[] hmac(final byte[] key, final String message) throws GeneralSecurityException {
        final Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
    }
}
