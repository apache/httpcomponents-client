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
package org.apache.hc.client5.http.impl.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.auth.AuthExchange;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.client5.http.auth.ChallengeType;
import org.apache.hc.client5.http.auth.NTCredentials;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.cache.HttpCacheContext;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.CookieSpecFactory;
import org.apache.hc.client5.http.cookie.StandardCookieSpec;
import org.apache.hc.client5.http.impl.DefaultAuthenticationStrategy;
import org.apache.hc.client5.http.impl.auth.AuthenticationHandler;
import org.apache.hc.client5.http.impl.auth.BasicAuthCache;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.auth.CredentialsProviderBuilder;
import org.apache.hc.client5.http.impl.auth.DigestScheme;
import org.apache.hc.client5.http.impl.auth.DigestSchemeFactory;
import org.apache.hc.client5.http.impl.auth.NTLMSchemeFactory;
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie;
import org.apache.hc.client5.http.impl.cookie.IgnoreCookieSpecFactory;
import org.apache.hc.client5.http.impl.cookie.RFC6265CookieSpecFactory;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the Cookie/Authorization Vary handling in {@link CachingExecBase}: using the request
 * actually sent to the backend as the key material at storage time ({@link #effectiveRequestForStorage}),
 * and previewing what the protocol layer is about to add at lookup time ({@link #previewVaryRequest}),
 * without ever sending a request or mutating persistent auth state.
 */
class TestCachingExecBaseVarySupport {

    private HttpHost target;
    private HttpRoute route;
    private HttpCacheContext context;
    private CachingExec impl;

    @BeforeEach
    void setUp() {
        target = new HttpHost("localhost.local", 80);
        route = new HttpRoute(target);
        context = HttpCacheContext.create();
        context.setRoute(route);
        impl = new CachingExec(new BasicHttpCache(), null, CacheConfig.DEFAULT);
    }

    private static Lookup<CookieSpecFactory> cookieSpecRegistry() {
        return RegistryBuilder.<CookieSpecFactory>create()
                .register(StandardCookieSpec.RELAXED, new RFC6265CookieSpecFactory(
                        RFC6265CookieSpecFactory.CompatibilityLevel.RELAXED, null))
                .register(StandardCookieSpec.STRICT, new RFC6265CookieSpecFactory(
                        RFC6265CookieSpecFactory.CompatibilityLevel.STRICT, null))
                .register(StandardCookieSpec.IGNORE, new IgnoreCookieSpecFactory())
                .build();
    }

    // -------------------------------------------------------------------------------------------
    // Storage side: effectiveRequestForStorage
    // -------------------------------------------------------------------------------------------

    @Test
    void effectiveRequestUsesRealCookieWhenResponseVariesOnCookie() {
        final SimpleHttpRequest cacheRequest = SimpleRequestBuilder.get("http://localhost.local/").build();
        final BasicHttpRequest sentRequest = new BasicHttpRequest("GET", "/");
        sentRequest.addHeader(HttpHeaders.COOKIE, "session=alice");
        final SimpleHttpResponse response = SimpleHttpResponse.create(200);
        response.addHeader(HttpHeaders.VARY, "Cookie");

        final SimpleHttpRequest effective = CachingExecBase.effectiveRequestForStorage(cacheRequest, response, sentRequest);

        assertNotSame(cacheRequest, effective);
        assertEquals("session=alice", effective.getFirstHeader(HttpHeaders.COOKIE).getValue());
    }

    @Test
    void effectiveRequestUsesRealAuthorizationWhenResponseVariesOnAuthorization() {
        final SimpleHttpRequest cacheRequest = SimpleRequestBuilder.get("http://localhost.local/").build();
        final BasicHttpRequest sentRequest = new BasicHttpRequest("GET", "/");
        sentRequest.addHeader(HttpHeaders.AUTHORIZATION, "Basic YWxpY2U6c2VjcmV0");
        final SimpleHttpResponse response = SimpleHttpResponse.create(200);
        response.addHeader(HttpHeaders.VARY, "Authorization");

        final SimpleHttpRequest effective = CachingExecBase.effectiveRequestForStorage(cacheRequest, response, sentRequest);

        assertNotSame(cacheRequest, effective);
        assertEquals("Basic YWxpY2U6c2VjcmV0", effective.getFirstHeader(HttpHeaders.AUTHORIZATION).getValue());
    }

    @Test
    void effectiveRequestLeavesUnrelatedVaryHeaderAlone() {
        // Vary: User-Agent is deliberately out of scope: the cache's own request (as it exists above
        // the chain) keeps being used for it, exactly as before this fix, even though the real sent
        // request differs and also happens to carry Cookie headers that are irrelevant to this response.
        final SimpleHttpRequest cacheRequest = SimpleRequestBuilder.get("http://localhost.local/")
                .addHeader(HttpHeaders.USER_AGENT, "test-agent/1.0")
                .build();
        final BasicHttpRequest sentRequest = new BasicHttpRequest("GET", "/");
        sentRequest.addHeader(HttpHeaders.USER_AGENT, "real-agent/9.9");
        sentRequest.addHeader(HttpHeaders.COOKIE, "session=alice");
        final SimpleHttpResponse response = SimpleHttpResponse.create(200);
        response.addHeader(HttpHeaders.VARY, "User-Agent");

        final SimpleHttpRequest effective = CachingExecBase.effectiveRequestForStorage(cacheRequest, response, sentRequest);

        assertSame(cacheRequest, effective);
        assertEquals("test-agent/1.0", effective.getFirstHeader(HttpHeaders.USER_AGENT).getValue());
        assertNull(effective.getFirstHeader(HttpHeaders.COOKIE));
    }

    @Test
    void effectiveRequestIsUnchangedWhenResponseHasNoVary() {
        final SimpleHttpRequest cacheRequest = SimpleRequestBuilder.get("http://localhost.local/").build();
        final BasicHttpRequest sentRequest = new BasicHttpRequest("GET", "/");
        sentRequest.addHeader(HttpHeaders.COOKIE, "session=alice");
        final SimpleHttpResponse response = SimpleHttpResponse.create(200);

        final SimpleHttpRequest effective = CachingExecBase.effectiveRequestForStorage(cacheRequest, response, sentRequest);

        assertSame(cacheRequest, effective);
    }

    @Test
    void effectiveRequestIsUnchangedWhenSentRequestUnavailable() {
        final SimpleHttpRequest cacheRequest = SimpleRequestBuilder.get("http://localhost.local/").build();
        final SimpleHttpResponse response = SimpleHttpResponse.create(200);
        response.addHeader(HttpHeaders.VARY, "Cookie");

        final SimpleHttpRequest effective = CachingExecBase.effectiveRequestForStorage(cacheRequest, response, null);

        assertSame(cacheRequest, effective);
    }

    // -------------------------------------------------------------------------------------------
    // Lookup side: previewVaryRequest - Cookie
    // -------------------------------------------------------------------------------------------

    @Test
    void previewAddsCookieHeaderWhenStoreHasAMatchingCookie() {
        final BasicCookieStore cookieStore = new BasicCookieStore();
        final BasicClientCookie cookie = new BasicClientCookie("session", "alice");
        cookie.setDomain("localhost.local");
        cookie.setPath("/");
        cookieStore.addCookie(cookie);
        context.setCookieStore(cookieStore);
        context.setCookieSpecRegistry(cookieSpecRegistry());

        final SimpleHttpRequest request = SimpleRequestBuilder.get("http://localhost.local/").build();
        final CachingExecBase.VaryPreview preview = impl.previewVaryRequest(
                target, request, Collections.singletonList("Cookie"), route, context);

        assertFalse(preview.declined);
        assertNotNull(preview.request);
        assertEquals("session=alice", preview.request.getFirstHeader(HttpHeaders.COOKIE).getValue());
        // the preview must not leak into the real exchange's own cookie origin/spec
        assertNull(context.getCookieOrigin());
        assertNull(context.getCookieSpec());
    }

    @Test
    void previewIsNullWhenNoCookieMatches() {
        context.setCookieStore(new BasicCookieStore());
        context.setCookieSpecRegistry(cookieSpecRegistry());

        final SimpleHttpRequest request = SimpleRequestBuilder.get("http://localhost.local/").build();
        final CachingExecBase.VaryPreview preview = impl.previewVaryRequest(
                target, request, Collections.singletonList("Cookie"), route, context);
        assertFalse(preview.declined);
        assertNull(preview.request);
    }

    @Test
    void previewIsNullWhenRequestAlreadyCarriesACookieHeader() {
        final BasicCookieStore cookieStore = new BasicCookieStore();
        final BasicClientCookie cookie = new BasicClientCookie("session", "alice");
        cookie.setDomain("localhost.local");
        cookie.setPath("/");
        cookieStore.addCookie(cookie);
        context.setCookieStore(cookieStore);
        context.setCookieSpecRegistry(cookieSpecRegistry());

        final SimpleHttpRequest request = SimpleRequestBuilder.get("http://localhost.local/")
                .addHeader(HttpHeaders.COOKIE, "explicit=1")
                .build();
        final CachingExecBase.VaryPreview preview = impl.previewVaryRequest(
                target, request, Collections.singletonList("Cookie"), route, context);
        assertFalse(preview.declined);
        assertNull(preview.request);
    }

    // -------------------------------------------------------------------------------------------
    // Lookup side: previewVaryRequest - Authorization (preemptive/cached auth)
    // -------------------------------------------------------------------------------------------

    @Test
    void previewAddsAuthorizationFromPreemptiveBasicAuthCache() {
        final BasicScheme basicScheme = new BasicScheme();
        basicScheme.initPreemptive(new UsernamePasswordCredentials("alice", "secret".toCharArray()));
        final BasicAuthCache authCache = new BasicAuthCache();
        authCache.put(target, basicScheme);
        context.setAuthCache(authCache);

        final SimpleHttpRequest request = SimpleRequestBuilder.get("http://localhost.local/").build();
        final CachingExecBase.VaryPreview preview = impl.previewVaryRequest(
                target, request, Collections.singletonList("Authorization"), route, context);

        assertFalse(preview.declined);
        assertNotNull(preview.request);
        assertTrue(preview.request.getFirstHeader(HttpHeaders.AUTHORIZATION).getValue().startsWith("Basic "));
    }

    @Test
    void previewIsNullWhenAuthCacheHasNoEntry() {
        context.setAuthCache(new BasicAuthCache());

        final SimpleHttpRequest request = SimpleRequestBuilder.get("http://localhost.local/").build();
        final CachingExecBase.VaryPreview preview = impl.previewVaryRequest(
                target, request, Collections.singletonList("Authorization"), route, context);
        // no auth state at all was established for this target: safe to fall back, not a decline
        assertFalse(preview.declined);
        assertNull(preview.request);
    }

    @Test
    void previewIsNullWhenRequestAlreadyCarriesAnAuthorizationHeader() {
        final BasicScheme basicScheme = new BasicScheme();
        basicScheme.initPreemptive(new UsernamePasswordCredentials("alice", "secret".toCharArray()));
        final BasicAuthCache authCache = new BasicAuthCache();
        authCache.put(target, basicScheme);
        context.setAuthCache(authCache);

        final SimpleHttpRequest request = SimpleRequestBuilder.get("http://localhost.local/")
                .addHeader(HttpHeaders.AUTHORIZATION, "Bearer explicit-token")
                .build();
        final CachingExecBase.VaryPreview preview = impl.previewVaryRequest(
                target, request, Collections.singletonList("Authorization"), route, context);
        assertFalse(preview.declined);
        assertNull(preview.request);
    }

    /**
     * The safety requirement from the design: a scheme whose response generation has a persistent side
     * effect (Digest's nonce count) must never be exercised by a preview, since a cache hit means no
     * request is ever actually sent, which would desynchronize the client's nonce count from the server's.
     * Unlike "nothing to preview", this is a case where the real request WOULD carry a header the preview
     * cannot reproduce, so the preview must be declined, forcing the lookup to a miss rather than falling
     * back to the first, unpreviewed match.
     */
    @Test
    void previewDoesNotInvokeOrMutateAnAlreadyEstablishedDigestScheme() {
        final DigestScheme digestScheme = new DigestScheme();
        final AuthExchange authExchange = context.getAuthExchange(target);
        authExchange.select(digestScheme);
        authExchange.setState(AuthExchange.State.SUCCESS);
        final long nonceCountBefore = digestScheme.getNounceCount();

        final SimpleHttpRequest request = SimpleRequestBuilder.get("http://localhost.local/").build();
        final CachingExecBase.VaryPreview preview = impl.previewVaryRequest(
                target, request, Collections.singletonList("Authorization"), route, context);

        assertTrue(preview.declined);
        assertNull(preview.request);
        assertEquals(nonceCountBefore, digestScheme.getNounceCount());
    }

    /**
     * State left behind by {@link AuthenticationHandler#handleResponse} for a 401 carrying a Digest
     * challenge: {@code CHALLENGED}, a queue of pending auth options, no scheme selected yet. A
     * retry-through-the-cache path (an I/O error draining the 401 body, an exchange failure in the
     * async client) can reach the lookup preview in exactly this state. {@code isPreviewSafe} must not
     * default to "safe" just because no scheme happens to be selected: the preview must not consume the
     * pending option, or it would advance Digest's nonce count as a side effect of a mere cache lookup.
     */
    @Test
    void previewDoesNotConsumeAPendingDigestChallenge() throws Exception {
        context.setAuthSchemeRegistry(RegistryBuilder.<AuthSchemeFactory>create()
                .register(StandardAuthScheme.DIGEST, DigestSchemeFactory.INSTANCE)
                .build());
        context.setCredentialsProvider(CredentialsProviderBuilder.create()
                .add(target, "alice", "secret".toCharArray())
                .build());

        final BasicHttpResponse challenge = new BasicHttpResponse(401);
        challenge.addHeader(HttpHeaders.WWW_AUTHENTICATE,
                "Digest realm=\"test\", nonce=\"0123456789abcdef\", qop=\"auth\", algorithm=MD5");
        final AuthExchange authExchange = context.getAuthExchange(target);
        final AuthenticationHandler authenticationHandler = new AuthenticationHandler();
        assertTrue(authenticationHandler.handleResponse(
                target, ChallengeType.TARGET, challenge, DefaultAuthenticationStrategy.INSTANCE, authExchange, context));

        assertEquals(AuthExchange.State.CHALLENGED, authExchange.getState());
        assertNull(authExchange.getAuthScheme());
        assertNotNull(authExchange.getAuthOptions());
        final DigestScheme pending = (DigestScheme) authExchange.getAuthOptions().peek();
        assertEquals(0, pending.getNounceCount());

        final SimpleHttpRequest request = SimpleRequestBuilder.get("http://localhost.local/").build();
        final CachingExecBase.VaryPreview preview = impl.previewVaryRequest(
                target, request, Collections.singletonList("Authorization"), route, context);

        assertTrue(preview.declined);
        assertNull(preview.request);
        assertEquals(0, pending.getNounceCount());
        assertNull(authExchange.getAuthScheme());
        assertSame(pending, authExchange.getAuthOptions().peek());

        // the real request, the one that actually goes out, must still see the first nonce count
        final BasicHttpRequest real = new BasicHttpRequest("GET", "/");
        authenticationHandler.addAuthResponse(target, ChallengeType.TARGET, real, authExchange, context);
        assertTrue(real.getFirstHeader(HttpHeaders.AUTHORIZATION).getValue().contains("nc=00000001"),
                "first Digest response on the wire must carry nc=00000001");
    }

    /**
     * Same hole as {@link #previewDoesNotConsumeAPendingDigestChallenge}, for a connection-based scheme:
     * a pending NTLM option must not be advanced from {@code CHALLENGE_RECEIVED} to
     * {@code MSG_TYPE1_GENERATED} by the mere preview.
     */
    @Test
    @SuppressWarnings("deprecation")
    void previewDoesNotAdvanceAPendingNtlmChallenge() throws Exception {
        context.setAuthSchemeRegistry(RegistryBuilder.<AuthSchemeFactory>create()
                .register(StandardAuthScheme.NTLM, NTLMSchemeFactory.INSTANCE)
                .build());
        // NTLM is not in DefaultAuthenticationStrategy's default scheme priority; name it explicitly
        context.setRequestConfig(RequestConfig.custom()
                .setTargetPreferredAuthSchemes(Collections.singletonList(StandardAuthScheme.NTLM))
                .build());
        context.setCredentialsProvider(CredentialsProviderBuilder.create()
                .add(target, new NTCredentials("alice", "secret".toCharArray(), "WORKSTATION", "DOMAIN"))
                .build());

        final BasicHttpResponse challenge = new BasicHttpResponse(401);
        challenge.addHeader(HttpHeaders.WWW_AUTHENTICATE, "NTLM");
        final AuthExchange authExchange = context.getAuthExchange(target);
        final AuthenticationHandler authenticationHandler = new AuthenticationHandler();
        assertTrue(authenticationHandler.handleResponse(
                target, ChallengeType.TARGET, challenge, DefaultAuthenticationStrategy.INSTANCE, authExchange, context));

        assertEquals(AuthExchange.State.CHALLENGED, authExchange.getState());
        assertNull(authExchange.getAuthScheme());
        assertNotNull(authExchange.getAuthOptions());
        final AuthScheme pending = authExchange.getAuthOptions().peek();
        assertTrue(pending.toString().contains("CHALLENGE_RECEIVED"));

        final SimpleHttpRequest request = SimpleRequestBuilder.get("http://localhost.local/").build();
        final CachingExecBase.VaryPreview preview = impl.previewVaryRequest(
                target, request, Collections.singletonList("Authorization"), route, context);

        assertTrue(preview.declined);
        assertNull(preview.request);
        assertNull(authExchange.getAuthScheme());
        assertSame(pending, authExchange.getAuthOptions().peek());
        assertTrue(pending.toString().contains("CHALLENGE_RECEIVED"));
        assertFalse(pending.toString().contains("MSG_TYPE1_GENERATED"));
    }

    // -------------------------------------------------------------------------------------------
    // Scope boundary: only Cookie and Authorization are ever previewed
    // -------------------------------------------------------------------------------------------

    @Test
    void previewIsNullForAnyOtherVaryHeader() {
        // User-Agent (or any header this fix does not cover, including one added by a user's own
        // interceptor) must never trigger a preview or a second cache lookup.
        final SimpleHttpRequest request = SimpleRequestBuilder.get("http://localhost.local/").build();
        final CachingExecBase.VaryPreview preview = impl.previewVaryRequest(
                target, request, Collections.singletonList("User-Agent"), route, context);
        assertFalse(preview.declined);
        assertNull(preview.request);
    }

}
