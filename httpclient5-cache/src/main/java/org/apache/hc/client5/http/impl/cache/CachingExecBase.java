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

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.auth.AuthExchange;
import org.apache.hc.client5.http.auth.ChallengeType;
import org.apache.hc.client5.http.cache.HttpCacheContext;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.impl.RequestSupport;
import org.apache.hc.client5.http.impl.auth.AuthCacheKeeper;
import org.apache.hc.client5.http.impl.auth.AuthSupport;
import org.apache.hc.client5.http.impl.auth.AuthenticationHandler;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.protocol.RequestAddCookies;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.MessageHeaders;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.net.URIAuthority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CachingExecBase {

    /**
     * Maximum size of a request content body that can be buffered in memory
     * in order to compute a cache key. Requests with a larger content body
     * are considered non-cacheable.
     */
    static final int MAX_BUFFERED_CONTENT_LENGTH = 25 * 1024;

    private static final Logger LOG = LoggerFactory.getLogger(CachingExecBase.class);

    final AtomicLong cacheHits = new AtomicLong();
    final AtomicLong cacheMisses = new AtomicLong();
    final AtomicLong cacheUpdates = new AtomicLong();

    final ResponseCachingPolicy responseCachingPolicy;
    final CacheValidityPolicy validityPolicy;
    final CachedHttpResponseGenerator responseGenerator;
    final CacheableRequestPolicy cacheableRequestPolicy;
    final CachedResponseSuitabilityChecker suitabilityChecker;
    final CacheConfig cacheConfig;
    final SchemePortResolver schemePortResolver;
    final AuthCacheKeeper authCacheKeeper;
    final boolean cookieManagementEnabled;
    final AuthenticationHandler authenticationHandler;

    CachingExecBase(
            final CacheValidityPolicy validityPolicy,
            final ResponseCachingPolicy responseCachingPolicy,
            final CachedHttpResponseGenerator responseGenerator,
            final CacheableRequestPolicy cacheableRequestPolicy,
            final CachedResponseSuitabilityChecker suitabilityChecker,
            final CacheConfig config) {
        this.responseCachingPolicy = responseCachingPolicy;
        this.validityPolicy = validityPolicy;
        this.responseGenerator = responseGenerator;
        this.cacheableRequestPolicy = cacheableRequestPolicy;
        this.suitabilityChecker = suitabilityChecker;
        this.cacheConfig = config != null ? config : CacheConfig.DEFAULT;
        this.schemePortResolver = DefaultSchemePortResolver.INSTANCE;
        this.authCacheKeeper = new AuthCacheKeeper(this.schemePortResolver);
        this.cookieManagementEnabled = true;
        this.authenticationHandler = new AuthenticationHandler();
    }

    CachingExecBase(final CacheConfig config) {
        this(config, DefaultSchemePortResolver.INSTANCE, new AuthCacheKeeper(DefaultSchemePortResolver.INSTANCE), true);
    }

    /**
     * @param schemePortResolver resolves the target host exactly the way {@code ProtocolExec} /
     *                            {@code AsyncProtocolExec} do, so that previewing the auth exchange below
     *                            looks up the same {@link AuthExchange}; {@code null} selects the default.
     * @param authCacheKeeper    the auth cache keeper the real exec chain was built with, or {@code null}
     *                           if auth caching is disabled on the client, in which case the {@code Authorization}
     *                           Vary preview is limited to an auth exchange already established earlier in
     *                           the same context.
     * @param cookieManagementEnabled whether {@code RequestAddCookies} runs as part of the real exchange.
     *
     * @since 5.7
     */
    // The query-string caching options are deprecated but still honoured while they remain on the API.
    @SuppressWarnings("deprecation")
    CachingExecBase(
            final CacheConfig config,
            final SchemePortResolver schemePortResolver,
            final AuthCacheKeeper authCacheKeeper,
            final boolean cookieManagementEnabled) {
        super();
        this.cacheConfig = config != null ? config : CacheConfig.DEFAULT;
        this.validityPolicy = new CacheValidityPolicy(config);
        this.responseGenerator = new CachedHttpResponseGenerator(this.validityPolicy);
        this.cacheableRequestPolicy = new CacheableRequestPolicy();
        this.suitabilityChecker = new CachedResponseSuitabilityChecker(this.validityPolicy, this.cacheConfig);
        this.responseCachingPolicy = new ResponseCachingPolicy(
                this.cacheConfig.isSharedCache(),
                this.cacheConfig.isNeverCacheHTTP10ResponsesWithQuery(),
                this.cacheConfig.isNeverCacheHTTP11ResponsesWithQuery());
        this.schemePortResolver = schemePortResolver != null ? schemePortResolver : DefaultSchemePortResolver.INSTANCE;
        this.authCacheKeeper = authCacheKeeper;
        this.cookieManagementEnabled = cookieManagementEnabled;
        this.authenticationHandler = new AuthenticationHandler();
    }

    /**
     * Reports the number of times that the cache successfully responded
     * to an {@link HttpRequest} without contacting the origin server.
     * @return the number of cache hits
     */
    public long getCacheHits() {
        return cacheHits.get();
    }

    /**
     * Reports the number of times that the cache contacted the origin
     * server because it had no appropriate response cached.
     * @return the number of cache misses
     */
    public long getCacheMisses() {
        return cacheMisses.get();
    }

    /**
     * Reports the number of times that the cache was able to satisfy
     * a response by revalidating an existing but stale cache entry.
     * @return the number of cache revalidations
     */
    public long getCacheUpdates() {
        return cacheUpdates.get();
    }

    /**
     * Determines whether the protocol layer below the cache authenticated the request
     * to the route target. The {@code Authorization} header generated from the
     * credentials provider or the auth cache is added to the request instance
     * executed by the protocol layer and is not visible to the cache, so check the
     * request last sent to the backend. A connection-based scheme does not resend
     * the header once the exchange has succeeded, so check the exchange state as well.
     */
    static boolean isTargetAuthenticated(final HttpClientContext context, final HttpRoute route) {
        final HttpRequest sentRequest = context.getRequest();
        if (sentRequest != null && sentRequest.containsHeader(HttpHeaders.AUTHORIZATION)) {
            return true;
        }
        final AuthExchange authExchange = context.getAuthExchanges().get(route.getTargetHost());
        return authExchange != null && authExchange.getState() == AuthExchange.State.SUCCESS;
    }

    SimpleHttpResponse generateCachedResponse(
            final HttpRequest request,
            final HttpCacheEntry entry,
            final Instant now) throws ResourceIOException {
        if (shouldSendNotModifiedResponse(request, entry, now)) {
            return responseGenerator.generateNotModifiedResponse(entry);
        }
        return responseGenerator.generateResponse(request, entry);
    }

    SimpleHttpResponse generateGatewayTimeout() {
        return SimpleHttpResponse.create(HttpStatus.SC_GATEWAY_TIMEOUT, "Gateway Timeout");
    }

    /**
     * Returns the {@link CacheStatus} for the current exchange, creating and attaching a fresh one
     * when absent.
     */
    CacheStatus cacheStatus(final HttpCacheContext context) {
        CacheStatus cacheStatus = context.getCacheStatus();
        if (cacheStatus == null) {
            cacheStatus = new CacheStatus();
            context.setCacheStatus(cacheStatus);
        }
        return cacheStatus;
    }

    /**
     * Adds the RFC 9211 {@code Cache-Status} header to the response when enabled, describing how the
     * cache handled the exchange as recorded on the context.
     */
    void applyCacheStatus(final HttpResponse response, final HttpCacheContext context) {
        if (response != null && cacheConfig.isCacheStatusEnabled()) {
            final CacheStatus cacheStatus = context.getCacheStatus();
            if (cacheStatus != null) {
                final Header header = CacheStatusHeaderGenerator.INSTANCE.generate(cacheStatus);
                if (header != null) {
                    response.addHeader(header);
                }
            }
        }
    }

    Instant getCurrentDate() {
        return Instant.now();
    }

    boolean clientRequestsOurOptions(final HttpRequest request) {
        if (!Method.OPTIONS.isSame(request.getMethod())) {
            return false;
        }

        if (!"*".equals(request.getRequestUri())) {
            return false;
        }

        final Header h = request.getFirstHeader(HttpHeaders.MAX_FORWARDS);
        return "0".equals(h != null ? h.getValue() : null);
    }

    boolean shouldSendNotModifiedResponse(final HttpRequest request, final HttpCacheEntry responseEntry, final Instant now) {
        return suitabilityChecker.isConditional(request)
                && suitabilityChecker.allConditionalsMatch(request, responseEntry, now);
    }

    boolean staleIfErrorAppliesTo(final int statusCode) {
        return statusCode == HttpStatus.SC_INTERNAL_SERVER_ERROR
                || statusCode == HttpStatus.SC_BAD_GATEWAY
                || statusCode == HttpStatus.SC_SERVICE_UNAVAILABLE
                || statusCode == HttpStatus.SC_GATEWAY_TIMEOUT;
    }

    /**
     * For 304 Not modified responses, adds a "Last-Modified" header with the
     * value of the "If-Modified-Since" header passed in the request. This
     * header is required to be able to reuse match the cache entry for
     * subsequent requests but as defined in http specifications it is not
     * included in 304 responses by backend servers. This header will not be
     * included in the resulting response.
     */
    void storeRequestIfModifiedSinceFor304Response(final HttpRequest request, final HttpResponse backendResponse) {
        if (backendResponse.getCode() == HttpStatus.SC_NOT_MODIFIED) {
            final Header h = request.getFirstHeader(HttpHeaders.IF_MODIFIED_SINCE);
            if (h != null) {
                backendResponse.addHeader(HttpHeaders.LAST_MODIFIED, h.getValue());
            }
        }
    }

    boolean isResponseTooBig(final EntityDetails entityDetails) {
        if (entityDetails == null) {
            return false;
        }
        final long contentLength = entityDetails.getContentLength();
        if (contentLength == -1) {
            return false;
        }
        final long maxObjectSize = cacheConfig.getMaxObjectSize();
        return contentLength > maxObjectSize;
    }

    /**
     * Result of a cache lookup that may have used {@link #previewVaryRequest} to find a match: carries,
     * alongside the {@link CacheMatch} itself, the request the match was actually found with, so that
     * later, request-based checks against the hit (notably {@code CachedResponseSuitabilityChecker}'s own
     * {@code Vary} comparison) agree with the match and do not re-declare it a mismatch.
     */
    static final class VaryLookupResult {

        final CacheMatch match;
        final SimpleHttpRequest effectiveRequest;

        VaryLookupResult(final CacheMatch match, final SimpleHttpRequest effectiveRequest) {
            this.match = match;
            this.effectiveRequest = effectiveRequest;
        }
    }

    private static boolean containsIgnoreCase(final List<String> names, final String name) {
        for (final String candidate : names) {
            if (candidate.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves the target host for a request the same way {@code ProtocolExec} / {@code AsyncProtocolExec}
     * do, so that the {@code Authorization} Vary preview below looks up the same {@link AuthExchange} those
     * classes will use for the real request.
     */
    HttpHost resolveAuthTarget(final SimpleHttpRequest request, final HttpRoute route) {
        final URIAuthority authority = request.getAuthority();
        if (authority == null) {
            return route.getTargetHost();
        }
        final String scheme = request.getScheme() != null ? request.getScheme() : route.getTargetHost().getSchemeName();
        return new HttpHost(scheme, authority.getHostName(), schemePortResolver.resolve(scheme, authority));
    }

    /**
     * Outcome of {@link #previewVaryRequest}: either there was nothing to preview, or nothing to add (use
     * {@link #NONE}, which carries no request), the preview found something to add ({@link #of} a request,
     * never sent anywhere), or an {@code Authorization} preview had to be declined because it could not be
     * attempted safely ({@link #DECLINED}). {@code NONE} and a successful preview are both safe to use, or
     * to fall back from, exactly as the original match found things; {@code DECLINED} is not: the caller
     * must not trust the original, unpreviewed match (hit or miss) and has to force a miss instead, since
     * there is no way to tell whether the credentials this request is about to send match the variant that
     * match would have selected.
     */
    static final class VaryPreview {

        static final VaryPreview NONE = new VaryPreview(null, false);
        static final VaryPreview DECLINED = new VaryPreview(null, true);

        final SimpleHttpRequest request;
        final boolean declined;

        private VaryPreview(final SimpleHttpRequest request, final boolean declined) {
            this.request = request;
            this.declined = declined;
        }

        static VaryPreview of(final SimpleHttpRequest request) {
            return new VaryPreview(request, false);
        }

    }

    /**
     * Outcome of attempting to preview the {@code Authorization} header alone: {@code UNCHANGED} when
     * there is nothing for the real request to add either (no established auth state for the target, or
     * the preview produced no header), {@code ADDED} when a header was found and copied into the preview
     * request, and {@code DECLINED} when auth state exists but cannot be previewed safely, so the caller
     * must not fall back to treating this as "nothing to add".
     */
    private enum AuthPreviewOutcome {
        UNCHANGED, ADDED, DECLINED
    }

    /**
     * Previews, without sending a request, what the protocol layer below the cache is about to add to the
     * {@code Cookie} and/or {@code Authorization} headers of the given request, for whichever of those two
     * headers the given variant names list (the {@code Vary} header of the matched root cache entry) names
     * and the request does not already carry. The preview is used only to compute a variant key for a cache
     * lookup; it is never sent anywhere.
     * <p>
     * This deliberately covers only {@code Cookie} and {@code Authorization}: every other header named in
     * a {@code Vary} response, including ones generated below the cache by the client itself (such as a
     * default {@code User-Agent}) or by a user-supplied interceptor, is matched exactly as before, against
     * whatever value the request already carries above the cache.
     * <p>
     * Returns {@link VaryPreview#NONE} when no preview applies (the request already carries the header,
     * the relevant feature is disabled, or there is no established auth state to preview), in which case
     * the caller should fall back on the original, unmodified request: a lookup that could have been a hit
     * may then be a miss instead, which is always safe, only suboptimal. Returns {@link VaryPreview#DECLINED}
     * when an {@code Authorization} preview was needed but the auth state cannot be previewed safely (for
     * example Digest, or a pending challenge queue); unlike {@code NONE}, this must never be treated as
     * "fall back to the original match" (see {@link VaryPreview}). A {@code Cookie} preview that already
     * succeeded is discarded in that case too: forcing a miss is always safe, only suboptimal.
     */
    VaryPreview previewVaryRequest(
            final HttpHost authTarget,
            final SimpleHttpRequest request,
            final List<String> variantNames,
            final HttpRoute route,
            final HttpClientContext context) {
        final boolean previewCookie = cookieManagementEnabled
                && containsIgnoreCase(variantNames, HttpHeaders.COOKIE)
                && !request.containsHeader(HttpHeaders.COOKIE);
        final boolean previewAuthorization = containsIgnoreCase(variantNames, HttpHeaders.AUTHORIZATION)
                && !request.containsHeader(HttpHeaders.AUTHORIZATION);
        if (!previewCookie && !previewAuthorization) {
            return VaryPreview.NONE;
        }
        final SimpleRequestBuilder builder = SimpleRequestBuilder.copy(request);
        boolean changed = false;
        if (previewCookie) {
            changed |= previewCookieHeader(builder, route, context);
        }
        if (previewAuthorization) {
            final AuthPreviewOutcome outcome = previewAuthorizationHeader(authTarget, builder, context);
            if (outcome == AuthPreviewOutcome.DECLINED) {
                return VaryPreview.DECLINED;
            }
            changed |= outcome == AuthPreviewOutcome.ADDED;
        }
        return changed ? VaryPreview.of(builder.build()) : VaryPreview.NONE;
    }

    /**
     * Applies {@link RequestAddCookies} to a throwaway copy of the request using a scratch context, so the
     * cookie origin/spec of the real exchange is never touched by this preview. The scratch context shares
     * the real cookie store and cookie spec registry (so the preview sees the same cookies and eviction of
     * expired ones, which is harmless to run early), but not the real {@code CookieOrigin}, which
     * {@link RequestAddCookies} records on the context it is given and {@code ResponseProcessCookies} later
     * reads back; using the real context here would corrupt that for the exchange that is about to happen.
     */
    private boolean previewCookieHeader(final SimpleRequestBuilder builder, final HttpRoute route, final HttpClientContext context) {
        if (context.getCookieStore() == null || context.getCookieSpecRegistry() == null) {
            return false;
        }
        final HttpClientContext scratchContext = HttpClientContext.create();
        scratchContext.setCookieStore(context.getCookieStore());
        scratchContext.setCookieSpecRegistry(context.getCookieSpecRegistry());
        scratchContext.setRequestConfig(context.getRequestConfigOrDefault());
        scratchContext.setRoute(route);
        final SimpleHttpRequest probe = builder.build();
        try {
            RequestAddCookies.INSTANCE.process(probe, null, scratchContext);
        } catch (final HttpException | IOException ex) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Unable to preview Cookie header for cache lookup", ex);
            }
            return false;
        }
        final Header[] cookieHeaders = probe.getHeaders(HttpHeaders.COOKIE);
        if (cookieHeaders.length == 0) {
            return false;
        }
        builder.removeHeaders(HttpHeaders.COOKIE);
        for (final Header header : cookieHeaders) {
            builder.addHeader(header);
        }
        return true;
    }

    /**
     * Replicates the pre-challenge-loop steps {@code ProtocolExec} / {@code AsyncProtocolExec} run before
     * entering their 401 retry loop (path-prefix reset, loading preemptive auth from the auth cache,
     * {@code addAuthResponse}) via the shared {@link AuthSupport} helper, operating on the real,
     * live {@link AuthExchange} held by the context. Doing so is safe: those same steps are idempotent, and
     * {@code ProtocolExec} / {@code AsyncProtocolExec} will perform them again, with the same outcome, for
     * the real request moments later in the same exchange.
     * <p>
     * Generating the actual auth response is different: it can have a persistent, security relevant side
     * effect (Digest's nonce count), so {@link AuthSupport#isPreviewSafe(AuthExchange)} gates it to schemes
     * that are known, by construction, to have none. This covers the preemptive/cached-auth case (Basic,
     * Bearer); a scheme already mid-handshake (Digest, or a connection-based scheme such as NTLM/SPNEGO) is
     * declined instead, since the real request would actually carry a header computed from state this
     * method cannot safely reproduce - the caller must not mistake that for "nothing to add".
     */
    private AuthPreviewOutcome previewAuthorizationHeader(final HttpHost authTarget, final SimpleRequestBuilder builder, final HttpClientContext context) {
        final Supplier<String> pathPrefixSupplier = RequestSupport.pathPrefixSupplier(builder.getPath());
        final AuthExchange targetAuthExchange = context.getAuthExchange(authTarget);
        AuthSupport.primeAuthExchange(authTarget, pathPrefixSupplier, targetAuthExchange, authCacheKeeper, context);
        if (targetAuthExchange.getAuthScheme() == null && targetAuthExchange.getAuthOptions() == null) {
            // No auth state at all for this target (never challenged, or reset after a failure):
            // addAuthResponse would add nothing to the real request either, so falling back to the
            // original, unmodified request is correct, not just safe.
            return AuthPreviewOutcome.UNCHANGED;
        }
        if (!AuthSupport.isPreviewSafe(targetAuthExchange)) {
            // Auth state exists (a pending challenge queue, or an already-selected scheme not known safe
            // to preview) and addAuthResponse would act on it for the real request; what it would produce
            // cannot be determined without side effects, so the caller must force a miss instead of
            // falling back to the first, unpreviewed match.
            return AuthPreviewOutcome.DECLINED;
        }
        final SimpleHttpRequest probe = builder.build();
        authenticationHandler.addAuthResponse(authTarget, ChallengeType.TARGET, probe, targetAuthExchange, context);
        final Header[] authHeaders = probe.getHeaders(HttpHeaders.AUTHORIZATION);
        if (authHeaders.length == 0) {
            return AuthPreviewOutcome.UNCHANGED;
        }
        builder.removeHeaders(HttpHeaders.AUTHORIZATION);
        for (final Header header : authHeaders) {
            builder.addHeader(header);
        }
        return AuthPreviewOutcome.ADDED;
    }

    /**
     * Storage-side counterpart of {@link #previewVaryRequest}: returns a copy of {@code cacheRequest} with
     * its {@code Cookie} and/or {@code Authorization} headers replaced by the ones the backend actually
     * received (as recorded on {@code sentRequest}, the request {@code MainClientExec} /
     * {@code HttpAsyncMainClientExec} / {@code H2AsyncMainClientExec} set on the context), for whichever of
     * those two headers {@code varySource} names in its {@code Vary} header. Every other header, and the
     * request identity when neither applies, is left untouched.
     * <p>
     * {@code varySource} is the origin response being stored for a fresh entry, or the existing cache entry
     * when updating one from a 304, which in practice often omits its own {@code Vary} header even though
     * RFC 9110 15.4.5 requires one to be repeated.
     */
    static SimpleHttpRequest effectiveRequestForStorage(
            final SimpleHttpRequest cacheRequest,
            final MessageHeaders varySource,
            final HttpRequest sentRequest) {
        if (sentRequest == null) {
            return cacheRequest;
        }
        final List<String> variantNames = CacheKeyGenerator.variantNames(varySource);
        SimpleRequestBuilder builder = null;
        if (containsIgnoreCase(variantNames, HttpHeaders.COOKIE)) {
            builder = withRealHeader(builder, cacheRequest, sentRequest, HttpHeaders.COOKIE);
        }
        if (containsIgnoreCase(variantNames, HttpHeaders.AUTHORIZATION)) {
            builder = withRealHeader(builder, cacheRequest, sentRequest, HttpHeaders.AUTHORIZATION);
        }
        return builder != null ? builder.build() : cacheRequest;
    }

    private static SimpleRequestBuilder withRealHeader(
            final SimpleRequestBuilder builderIn,
            final SimpleHttpRequest cacheRequest,
            final HttpRequest sentRequest,
            final String headerName) {
        final SimpleRequestBuilder builder = builderIn != null ? builderIn : SimpleRequestBuilder.copy(cacheRequest);
        builder.removeHeaders(headerName);
        final Header[] headers = sentRequest.getHeaders(headerName);
        for (final Header header : headers) {
            builder.addHeader(header);
        }
        return builder;
    }

}
