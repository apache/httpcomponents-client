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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
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

final class TestScramScheme {

    private static final HttpHost HOST = new HttpHost("https", "example.test");
    private static final String REALM = "s1";
    private static final String USER = "u";
    private static final String PASS = "p";
    private static final String SID = "sid-1";

    static String b64(final byte[] b) {
        return Base64.getEncoder().withoutPadding().encodeToString(b);
    }

    static byte[] b64d(final String s) {
        return Base64.getDecoder().decode(s);
    }

    static String deb64s(final String s) {
        return new String(b64d(s), StandardCharsets.UTF_8);
    }

    static Map<String, String> parseCsvAttrs(final String s) {
        final Map<String, String> m = new LinkedHashMap<>();
        int i = 0;
        while (i < s.length()) {
            final int eq = s.indexOf('=', i);
            if (eq < 0) {
                break;
            }
            final String k = s.substring(i, eq);
            i = eq + 1;
            final StringBuilder v = new StringBuilder();
            while (i < s.length()) {
                final char c = s.charAt(i);
                if (c == ',') {
                    i++;
                    break;
                }
                v.append(c);
                i++;
            }
            m.put(k, v.toString());
        }
        return m;
    }

    static Map<String, String> splitHeader(final String header) {
        final int sp = header.indexOf(' ');
        final String params = header.substring(sp + 1);
        final Map<String, String> map = new HashMap<>();
        for (String part : params.split(",")) {
            part = part.trim();
            final int eq = part.indexOf('=');
            if (eq > 0) {
                final String k = part.substring(0, eq).toLowerCase(Locale.ROOT);
                String v = part.substring(eq + 1);
                if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
                    v = v.substring(1, v.length() - 1);
                }
                map.put(k, v);
            }
        }
        return map;
    }

    static byte[] pbkdf2(final char[] password, final byte[] salt, final int iter, final int dkLen)
            throws GeneralSecurityException {
        final PBEKeySpec spec = new PBEKeySpec(password, salt, iter, dkLen * 8);
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    }

    static byte[] hmac(final byte[] key, final String msg) throws GeneralSecurityException {
        final Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(msg.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void strictScram_fullRoundtrip_completes() throws Exception {
        final ScramScheme scheme = new ScramScheme();
        final BasicCredentialsProvider creds = new BasicCredentialsProvider();
        creds.setCredentials(new AuthScope(HOST, REALM, scheme.getName()),
                new UsernamePasswordCredentials(USER, PASS.toCharArray()));
        final HttpClientContext ctx = HttpClientContext.create();

        scheme.processChallenge(HOST, true,
                new AuthChallenge(ChallengeType.TARGET, scheme.getName(),
                        new BasicNameValuePair("realm", REALM)),
                ctx);
        assertTrue(scheme.isResponseReady(HOST, creds, ctx));

        final String authz1 = scheme.generateAuthResponse(HOST, null, ctx);
        final Map<String, String> h1 = splitHeader(authz1);
        assertEquals(REALM, h1.get("realm"));
        final String clientFirst = deb64s(h1.get("data"));
        assertTrue(clientFirst.startsWith("n,,"));
        final String clientFirstBare = clientFirst.substring("n,,".length());
        final String clientNonce = parseCsvAttrs(clientFirstBare).get("r");
        assertNotNull(clientNonce);

        final String saltB64 = b64("salt-256".getBytes(StandardCharsets.UTF_8));
        final int iters = 4096;
        final String serverFirst = "r=" + clientNonce + "XYZ,s=" + saltB64 + ",i=" + iters;
        scheme.processChallenge(HOST, true,
                new AuthChallenge(ChallengeType.TARGET, scheme.getName(),
                        new BasicNameValuePair("sid", SID),
                        new BasicNameValuePair("data", b64(serverFirst.getBytes(StandardCharsets.UTF_8)))),
                ctx);

        final String authz2 = scheme.generateAuthResponse(HOST, null, ctx);
        final Map<String, String> h2 = splitHeader(authz2);
        assertEquals(SID, h2.get("sid"));
        final String clientFinal = deb64s(h2.get("data"));
        final Map<String, String> cf2 = parseCsvAttrs(clientFinal);
        assertEquals("biws", cf2.get("c"));

        final String clientFinalNoProof = "c=" + cf2.get("c") + ",r=" + cf2.get("r");
        final String authMessage = clientFirstBare + "," + serverFirst + "," + clientFinalNoProof;
        final byte[] salted = pbkdf2(PASS.toCharArray(), b64d(saltB64), iters, 32);
        final byte[] serverKey = hmac(salted, "Server Key");
        final String vB64 = b64(hmac(serverKey, authMessage));

        scheme.processChallenge(HOST, false,
                new AuthChallenge(ChallengeType.TARGET, scheme.getName(),
                        new BasicNameValuePair("sid", SID),
                        new BasicNameValuePair("data", b64(("v=" + vB64).getBytes(StandardCharsets.UTF_8)))),
                ctx);

        assertFalse(scheme.isChallengeComplete());
    }

    @Test
    void strictScram_lowIterations_warnsButSucceeds() throws Exception {
        final ScramScheme scheme = new ScramScheme(4096, 0, 100000, null);
        final BasicCredentialsProvider creds = new BasicCredentialsProvider();
        creds.setCredentials(new AuthScope(HOST, REALM, scheme.getName()),
                new UsernamePasswordCredentials(USER, PASS.toCharArray()));
        final HttpClientContext ctx = HttpClientContext.create();

        scheme.processChallenge(HOST, true,
                new AuthChallenge(ChallengeType.TARGET, scheme.getName(),
                        new BasicNameValuePair("realm", REALM)),
                ctx);
        assertTrue(scheme.isResponseReady(HOST, creds, ctx));

        final String authz1 = scheme.generateAuthResponse(HOST, null, ctx);
        final String clientFirstBare = deb64s(splitHeader(authz1).get("data")).substring("n,,".length());
        final String clientNonce = parseCsvAttrs(clientFirstBare).get("r");

        final String saltB64 = b64("salt-low".getBytes(StandardCharsets.UTF_8));
        final String serverFirst = "r=" + clientNonce + "Z,s=" + saltB64 + ",i=1024";
        scheme.processChallenge(HOST, true,
                new AuthChallenge(ChallengeType.TARGET, scheme.getName(),
                        new BasicNameValuePair("sid", SID),
                        new BasicNameValuePair("data", b64(serverFirst.getBytes(StandardCharsets.UTF_8)))),
                ctx);

        final String authz2 = scheme.generateAuthResponse(HOST, null, ctx);
        final String clientFinal = deb64s(splitHeader(authz2).get("data"));
        final Map<String, String> cf = parseCsvAttrs(clientFinal);
        final String clientFinalNoProof = "c=" + cf.get("c") + ",r=" + cf.get("r");
        final String authMessage = clientFirstBare + "," + serverFirst + "," + clientFinalNoProof;

        final byte[] salted = pbkdf2(PASS.toCharArray(), b64d(saltB64), 1024, 32);
        final byte[] serverKey = hmac(salted, "Server Key");
        final String vB64 = b64(hmac(serverKey, authMessage));

        scheme.processChallenge(HOST, false,
                new AuthChallenge(ChallengeType.TARGET, scheme.getName(),
                        new BasicNameValuePair("sid", SID),
                        new BasicNameValuePair("data", b64(("v=" + vB64).getBytes(StandardCharsets.UTF_8)))),
                ctx);

        assertFalse(scheme.isChallengeComplete());
    }

    @Test
    void strictScram_invalidServerNonce_rejectedAt401() throws Exception {
        final ScramScheme scheme = new ScramScheme();
        final BasicCredentialsProvider creds = new BasicCredentialsProvider();
        creds.setCredentials(new AuthScope(HOST, REALM, scheme.getName()),
                new UsernamePasswordCredentials(USER, PASS.toCharArray()));
        final HttpClientContext ctx = HttpClientContext.create();

        scheme.processChallenge(HOST, true,
                new AuthChallenge(ChallengeType.TARGET, scheme.getName(),
                        new BasicNameValuePair("realm", REALM)),
                ctx);
        assertTrue(scheme.isResponseReady(HOST, creds, ctx));
        scheme.generateAuthResponse(HOST, null, ctx);

        final String badServerFirst = "r=NOTPREFIXED,s=" + b64("salt".getBytes(StandardCharsets.UTF_8)) + ",i=4096";
        final AuthenticationException ex = assertThrows(AuthenticationException.class, () ->
                scheme.processChallenge(HOST, true,
                        new AuthChallenge(ChallengeType.TARGET, scheme.getName(),
                                new BasicNameValuePair("sid", SID),
                                new BasicNameValuePair("data", b64(badServerFirst.getBytes(StandardCharsets.UTF_8)))),
                        ctx));
        assertTrue(ex.getMessage().toLowerCase(Locale.ROOT).contains("nonce"));
        assertTrue(scheme.isChallengeComplete());
    }

    @Test
    void strictScram_minIterations_enforced() throws Exception {
        final ScramScheme scheme = new ScramScheme(4096, 4096, 100000, null);
        final BasicCredentialsProvider creds = new BasicCredentialsProvider();
        creds.setCredentials(new AuthScope(HOST, REALM, scheme.getName()),
                new UsernamePasswordCredentials(USER, PASS.toCharArray()));
        final HttpClientContext ctx = HttpClientContext.create();

        scheme.processChallenge(HOST, true,
                new AuthChallenge(ChallengeType.TARGET, scheme.getName(),
                        new BasicNameValuePair("realm", REALM)),
                ctx);
        assertTrue(scheme.isResponseReady(HOST, creds, ctx));

        final String authz1 = scheme.generateAuthResponse(HOST, null, ctx);
        final String clientFirstBare = deb64s(splitHeader(authz1).get("data")).substring("n,,".length());
        final String clientNonce = parseCsvAttrs(clientFirstBare).get("r");

        final String serverFirst = "r=" + clientNonce + "Z,s=" + b64("salt".getBytes(StandardCharsets.UTF_8)) + ",i=1024";
        final AuthenticationException ex = assertThrows(AuthenticationException.class, () ->
                scheme.processChallenge(HOST, true,
                        new AuthChallenge(ChallengeType.TARGET, scheme.getName(),
                                new BasicNameValuePair("sid", SID),
                                new BasicNameValuePair("data", b64(serverFirst.getBytes(StandardCharsets.UTF_8)))),
                        ctx));
        assertTrue(ex.getMessage().toLowerCase(Locale.ROOT).contains("iteration"));
        assertTrue(scheme.isChallengeComplete());
    }

    @Test
    void strictScram_maxIterations_enforced() throws Exception {
        final ScramScheme scheme = new ScramScheme(4096, 0, 8192, null);
        final BasicCredentialsProvider creds = new BasicCredentialsProvider();
        creds.setCredentials(new AuthScope(HOST, REALM, scheme.getName()),
                new UsernamePasswordCredentials(USER, PASS.toCharArray()));
        final HttpClientContext ctx = HttpClientContext.create();

        scheme.processChallenge(HOST, true,
                new AuthChallenge(ChallengeType.TARGET, scheme.getName(),
                        new BasicNameValuePair("realm", REALM)),
                ctx);
        assertTrue(scheme.isResponseReady(HOST, creds, ctx));

        final String authz1 = scheme.generateAuthResponse(HOST, null, ctx);
        final String clientFirstBare = deb64s(splitHeader(authz1).get("data")).substring("n,,".length());
        final String clientNonce = parseCsvAttrs(clientFirstBare).get("r");

        final String serverFirst = "r=" + clientNonce + "Z,s=" + b64("salt".getBytes(StandardCharsets.UTF_8)) + ",i=20000";
        final AuthenticationException ex = assertThrows(AuthenticationException.class, () ->
                scheme.processChallenge(HOST, true,
                        new AuthChallenge(ChallengeType.TARGET, scheme.getName(),
                                new BasicNameValuePair("sid", SID),
                                new BasicNameValuePair("data", b64(serverFirst.getBytes(StandardCharsets.UTF_8)))),
                        ctx));
        assertTrue(ex.getMessage().toLowerCase(Locale.ROOT).contains("maximum"));
        assertTrue(scheme.isChallengeComplete());
    }

    @Test
    void strictScram_missingSidInServerFirst_isMalformed() throws Exception {
        final ScramScheme scheme = new ScramScheme();
        final BasicCredentialsProvider creds = new BasicCredentialsProvider();
        creds.setCredentials(new AuthScope(HOST, REALM, scheme.getName()),
                new UsernamePasswordCredentials(USER, PASS.toCharArray()));
        final HttpClientContext ctx = HttpClientContext.create();

        scheme.processChallenge(HOST, true,
                new AuthChallenge(ChallengeType.TARGET, scheme.getName(),
                        new BasicNameValuePair("realm", REALM)),
                ctx);
        assertTrue(scheme.isResponseReady(HOST, creds, ctx));
        final String authz1 = scheme.generateAuthResponse(HOST, null, ctx);
        final String clientFirstBare = deb64s(splitHeader(authz1).get("data")).substring("n,,".length());
        final String clientNonce = parseCsvAttrs(clientFirstBare).get("r");

        final String serverFirst = "r=" + clientNonce + "Z,s=" + b64("salt".getBytes(StandardCharsets.UTF_8)) + ",i=4096";
        final MalformedChallengeException ex = assertThrows(MalformedChallengeException.class, () ->
                scheme.processChallenge(HOST, true,
                        new AuthChallenge(ChallengeType.TARGET, scheme.getName(),
                                new BasicNameValuePair("data", b64(serverFirst.getBytes(StandardCharsets.UTF_8)))),
                        ctx));
        assertTrue(ex.getMessage().toLowerCase(Locale.ROOT).contains("sid"));
        assertTrue(scheme.isChallengeComplete());
    }

    @Test
    void strictScram_authInfo_missingSid_fails() throws Exception {
        final ScramScheme scheme = new ScramScheme();
        final BasicCredentialsProvider creds = new BasicCredentialsProvider();
        creds.setCredentials(new AuthScope(HOST, REALM, scheme.getName()),
                new UsernamePasswordCredentials(USER, PASS.toCharArray()));
        final HttpClientContext ctx = HttpClientContext.create();

        scheme.processChallenge(HOST, true,
                new AuthChallenge(ChallengeType.TARGET, scheme.getName(),
                        new BasicNameValuePair("realm", REALM)),
                ctx);
        assertTrue(scheme.isResponseReady(HOST, creds, ctx));

        final String authz1 = scheme.generateAuthResponse(HOST, null, ctx);
        final String clientFirstBare = deb64s(splitHeader(authz1).get("data")).substring("n,,".length());
        final String clientNonce = parseCsvAttrs(clientFirstBare).get("r");

        final String saltB64 = b64("salt".getBytes(StandardCharsets.UTF_8));
        final String serverFirst = "r=" + clientNonce + "Z,s=" + saltB64 + ",i=4096";
        scheme.processChallenge(HOST, true,
                new AuthChallenge(ChallengeType.TARGET, scheme.getName(),
                        new BasicNameValuePair("sid", SID),
                        new BasicNameValuePair("data", b64(serverFirst.getBytes(StandardCharsets.UTF_8)))),
                ctx);

        final String authz2 = scheme.generateAuthResponse(HOST, null, ctx);
        final String clientFinal = deb64s(splitHeader(authz2).get("data"));
        final Map<String, String> cf = parseCsvAttrs(clientFinal);
        final String clientFinalNoProof = "c=" + cf.get("c") + ",r=" + cf.get("r");
        final String authMessage = clientFirstBare + "," + serverFirst + "," + clientFinalNoProof;
        final byte[] salted = pbkdf2(PASS.toCharArray(), b64d(saltB64), 4096, 32);
        final byte[] serverKey = hmac(salted, "Server Key");
        final String vB64 = b64(hmac(serverKey, authMessage));

        final MalformedChallengeException ex = assertThrows(MalformedChallengeException.class, () ->
                scheme.processChallenge(HOST, false,
                        new AuthChallenge(ChallengeType.TARGET, scheme.getName(),
                                new BasicNameValuePair("data", b64(("v=" + vB64).getBytes(StandardCharsets.UTF_8)))),
                        ctx));
        assertTrue(ex.getMessage().toLowerCase(Locale.ROOT).contains("sid"));
        assertTrue(scheme.isChallengeComplete());
    }

    @Test
    void strictScram_authInfo_sidMismatch_fails() throws Exception {
        final ScramScheme scheme = new ScramScheme();
        final BasicCredentialsProvider creds = new BasicCredentialsProvider();
        creds.setCredentials(new AuthScope(HOST, REALM, scheme.getName()),
                new UsernamePasswordCredentials(USER, PASS.toCharArray()));
        final HttpClientContext ctx = HttpClientContext.create();

        scheme.processChallenge(HOST, true,
                new AuthChallenge(ChallengeType.TARGET, scheme.getName(),
                        new BasicNameValuePair("realm", REALM)),
                ctx);
        assertTrue(scheme.isResponseReady(HOST, creds, ctx));

        final String authz1 = scheme.generateAuthResponse(HOST, null, ctx);
        final String clientFirstBare = deb64s(splitHeader(authz1).get("data")).substring("n,,".length());
        final String clientNonce = parseCsvAttrs(clientFirstBare).get("r");

        final String saltB64 = b64("salt".getBytes(StandardCharsets.UTF_8));
        final String serverFirst = "r=" + clientNonce + "Z,s=" + saltB64 + ",i=4096";
        scheme.processChallenge(HOST, true,
                new AuthChallenge(ChallengeType.TARGET, scheme.getName(),
                        new BasicNameValuePair("sid", SID),
                        new BasicNameValuePair("data", b64(serverFirst.getBytes(StandardCharsets.UTF_8)))),
                ctx);

        final String authz2 = scheme.generateAuthResponse(HOST, null, ctx);
        final String clientFinal = deb64s(splitHeader(authz2).get("data"));
        final Map<String, String> cf = parseCsvAttrs(clientFinal);
        final String clientFinalNoProof = "c=" + cf.get("c") + ",r=" + cf.get("r");
        final String authMessage = clientFirstBare + "," + serverFirst + "," + clientFinalNoProof;
        final byte[] salted = pbkdf2(PASS.toCharArray(), b64d(saltB64), 4096, 32);
        final byte[] serverKey = hmac(salted, "Server Key");
        final String vB64 = b64(hmac(serverKey, authMessage));

        final AuthenticationException ex = assertThrows(AuthenticationException.class, () ->
                scheme.processChallenge(HOST, false,
                        new AuthChallenge(ChallengeType.TARGET, scheme.getName(),
                                new BasicNameValuePair("sid", "sid-other"),
                                new BasicNameValuePair("data", b64(("v=" + vB64).getBytes(StandardCharsets.UTF_8)))),
                        ctx));
        assertTrue(ex.getMessage().toLowerCase(Locale.ROOT).contains("sid"));
        assertTrue(scheme.isChallengeComplete());
    }

    @Test
    void strictScram_authInfo_mismatchV_fails() throws Exception {
        final ScramScheme scheme = new ScramScheme();
        final BasicCredentialsProvider creds = new BasicCredentialsProvider();
        creds.setCredentials(new AuthScope(HOST, REALM, scheme.getName()),
                new UsernamePasswordCredentials(USER, PASS.toCharArray()));
        final HttpClientContext ctx = HttpClientContext.create();

        scheme.processChallenge(HOST, true,
                new AuthChallenge(ChallengeType.TARGET, scheme.getName(),
                        new BasicNameValuePair("realm", REALM)),
                ctx);
        assertTrue(scheme.isResponseReady(HOST, creds, ctx));

        final String authz1 = scheme.generateAuthResponse(HOST, null, ctx);
        final String clientFirstBare = deb64s(splitHeader(authz1).get("data")).substring("n,,".length());
        final String clientNonce = parseCsvAttrs(clientFirstBare).get("r");

        final String serverFirst = "r=" + clientNonce + "Z,s=" + b64("salt".getBytes(StandardCharsets.UTF_8)) + ",i=4096";
        scheme.processChallenge(HOST, true,
                new AuthChallenge(ChallengeType.TARGET, scheme.getName(),
                        new BasicNameValuePair("sid", SID),
                        new BasicNameValuePair("data", b64(serverFirst.getBytes(StandardCharsets.UTF_8)))),
                ctx);

        scheme.generateAuthResponse(HOST, null, ctx);

        final MalformedChallengeException ex = assertThrows(MalformedChallengeException.class, () ->
                scheme.processChallenge(HOST, false,
                        new AuthChallenge(ChallengeType.TARGET, scheme.getName(),
                                new BasicNameValuePair("sid", SID),
                                new BasicNameValuePair("data", b64("v=WRONG".getBytes(StandardCharsets.UTF_8)))),
                        ctx));
        assertTrue(ex.getMessage().toLowerCase(Locale.ROOT).contains("signature"));
        assertTrue(scheme.isChallengeComplete());
    }

    @Test
    void strictScram_authInfo_errorE_fails() throws Exception {
        final ScramScheme scheme = new ScramScheme();
        final BasicCredentialsProvider creds = new BasicCredentialsProvider();
        creds.setCredentials(new AuthScope(HOST, REALM, scheme.getName()),
                new UsernamePasswordCredentials(USER, PASS.toCharArray()));
        final HttpClientContext ctx = HttpClientContext.create();

        scheme.processChallenge(HOST, true,
                new AuthChallenge(ChallengeType.TARGET, scheme.getName(),
                        new BasicNameValuePair("realm", REALM)),
                ctx);
        assertTrue(scheme.isResponseReady(HOST, creds, ctx));

        final String authz1 = scheme.generateAuthResponse(HOST, null, ctx);
        final String clientFirstBare = deb64s(splitHeader(authz1).get("data")).substring("n,,".length());
        final String clientNonce = parseCsvAttrs(clientFirstBare).get("r");

        final String serverFirst = "r=" + clientNonce + "Z,s=" + b64("salt".getBytes(StandardCharsets.UTF_8)) + ",i=4096";
        scheme.processChallenge(HOST, true,
                new AuthChallenge(ChallengeType.TARGET, scheme.getName(),
                        new BasicNameValuePair("sid", SID),
                        new BasicNameValuePair("data", b64(serverFirst.getBytes(StandardCharsets.UTF_8)))),
                ctx);

        scheme.generateAuthResponse(HOST, null, ctx);

        final AuthenticationException ex = assertThrows(AuthenticationException.class, () ->
                scheme.processChallenge(HOST, false,
                        new AuthChallenge(ChallengeType.TARGET, scheme.getName(),
                                new BasicNameValuePair("sid", SID),
                                new BasicNameValuePair("data", b64("e=server-fail".getBytes(StandardCharsets.UTF_8)))),
                        ctx));
        assertTrue(ex.getMessage().contains("server error"));
        assertTrue(scheme.isChallengeComplete());
    }

    @Test
    void strictScram_badBase64In401Data_isMalformed() {
        final ScramScheme scheme = new ScramScheme();
        final HttpClientContext ctx = HttpClientContext.create();

        final MalformedChallengeException ex = assertThrows(MalformedChallengeException.class, () ->
                scheme.processChallenge(HOST, true,
                        new AuthChallenge(ChallengeType.TARGET, scheme.getName(),
                                new BasicNameValuePair("sid", SID),
                                new BasicNameValuePair("data", "%%%not-base64%%%")),
                        ctx));
        assertTrue(ex.getMessage().toLowerCase(Locale.ROOT).contains("base64"));
        assertTrue(scheme.isChallengeComplete());
    }

    @Test
    void strictScram_missingAttrsInServerFirst_isMalformed() {
        final ScramScheme scheme = new ScramScheme();
        final HttpClientContext ctx = HttpClientContext.create();

        final MalformedChallengeException ex = assertThrows(MalformedChallengeException.class, () ->
                scheme.processChallenge(HOST, true,
                        new AuthChallenge(ChallengeType.TARGET, scheme.getName(),
                                new BasicNameValuePair("sid", SID),
                                new BasicNameValuePair("data", b64("r=only".getBytes(StandardCharsets.UTF_8)))),
                        ctx));
        assertTrue(ex.getMessage().toLowerCase(Locale.ROOT).contains("missing"));
        assertTrue(scheme.isChallengeComplete());
    }

    @Test
    void testPreemptiveAuthentication() throws Exception {
        final ScramScheme scheme = new ScramScheme();
        final BasicCredentialsProvider creds = new BasicCredentialsProvider();
        creds.setCredentials(new AuthScope(HOST, REALM, scheme.getName()),
                new UsernamePasswordCredentials(USER, PASS.toCharArray()));
        final HttpClientContext ctx = HttpClientContext.create();

        assertTrue(scheme.isResponseReady(HOST, creds, ctx));
        final String response = scheme.generateAuthResponse(HOST, null, ctx);
        assertNotNull(response);
        assertTrue(response.contains("data="));
    }

    @Test
    void testNullCredentialsProvider() {
        final ScramScheme scheme = new ScramScheme();
        final HttpClientContext ctx = HttpClientContext.create();

        assertThrows(NullPointerException.class, () -> scheme.isResponseReady(HOST, null, ctx));
    }

    @Test
    void testInvalidBase64InAuthInfo() throws Exception {
        final ScramScheme scheme = new ScramScheme();
        final BasicCredentialsProvider creds = new BasicCredentialsProvider();
        creds.setCredentials(new AuthScope(HOST, REALM, scheme.getName()),
                new UsernamePasswordCredentials(USER, PASS.toCharArray()));
        final HttpClientContext ctx = HttpClientContext.create();

        scheme.processChallenge(HOST, true,
                new AuthChallenge(ChallengeType.TARGET, scheme.getName(),
                        new BasicNameValuePair("realm", REALM)),
                ctx);
        assertTrue(scheme.isResponseReady(HOST, creds, ctx));
        final String authz1 = scheme.generateAuthResponse(HOST, null, ctx);
        final String clientFirstBare = deb64s(splitHeader(authz1).get("data")).substring("n,,".length());
        final String clientNonce = parseCsvAttrs(clientFirstBare).get("r");

        final String serverFirst = "r=" + clientNonce + "Z,s=" + b64("salt".getBytes(StandardCharsets.UTF_8)) + ",i=4096";
        scheme.processChallenge(HOST, true,
                new AuthChallenge(ChallengeType.TARGET, scheme.getName(),
                        new BasicNameValuePair("sid", SID),
                        new BasicNameValuePair("data", b64(serverFirst.getBytes(StandardCharsets.UTF_8)))),
                ctx);
        scheme.generateAuthResponse(HOST, null, ctx);

        final MalformedChallengeException ex = assertThrows(MalformedChallengeException.class, () ->
                scheme.processChallenge(HOST, false,
                        new AuthChallenge(ChallengeType.TARGET, scheme.getName(),
                                new BasicNameValuePair("sid", SID),
                                new BasicNameValuePair("data", "invalid-base64")),
                        ctx));
        assertTrue(ex.getMessage().toLowerCase(Locale.ROOT).contains("base64"));
        assertTrue(scheme.isChallengeComplete());
    }

    @Test
    void testInvalidStateTransition() throws Exception {
        final ScramScheme scheme = new ScramScheme();
        final HttpClientContext ctx = HttpClientContext.create();

        final AuthenticationException ex = assertThrows(AuthenticationException.class, () ->
                scheme.generateAuthResponse(HOST, null, ctx));
        assertTrue(ex.getMessage().toLowerCase(Locale.ROOT).contains("sequence"));
    }

    @Test
    void testEmptyPassword() throws Exception {
        final ScramScheme scheme = new ScramScheme();
        final BasicCredentialsProvider creds = new BasicCredentialsProvider();
        creds.setCredentials(new AuthScope(HOST, REALM, scheme.getName()),
                new UsernamePasswordCredentials(USER, "".toCharArray()));
        final HttpClientContext ctx = HttpClientContext.create();

        scheme.processChallenge(HOST, true,
                new AuthChallenge(ChallengeType.TARGET, scheme.getName(),
                        new BasicNameValuePair("realm", REALM)),
                ctx);
        assertTrue(scheme.isResponseReady(HOST, creds, ctx));
    }

    @Test
    void testSpecialCharacters() throws Exception {
        final ScramScheme scheme = new ScramScheme();
        final BasicCredentialsProvider creds = new BasicCredentialsProvider();
        final String specialUser = "user@domain.com";
        final String specialPass = "p@ssw0rd!";
        creds.setCredentials(new AuthScope(HOST, REALM, scheme.getName()),
                new UsernamePasswordCredentials(specialUser, specialPass.toCharArray()));
        final HttpClientContext ctx = HttpClientContext.create();

        scheme.processChallenge(HOST, true,
                new AuthChallenge(ChallengeType.TARGET, scheme.getName(),
                        new BasicNameValuePair("realm", REALM)),
                ctx);
        assertTrue(scheme.isResponseReady(HOST, creds, ctx));

        final String response = scheme.generateAuthResponse(HOST, null, ctx);
        assertNotNull(response);
        assertTrue(response.contains("data="));
    }

    @Test
    void testIsConnectionBased() {
        final ScramScheme scheme = new ScramScheme();
        assertFalse(scheme.isConnectionBased());
    }

    @Test
    void testIsChallengeExpected() {
        final ScramScheme scheme = new ScramScheme();
        assertTrue(scheme.isChallengeExpected());
    }
}
