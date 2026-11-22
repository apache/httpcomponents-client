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
package org.apache.hc.client5.testing.caching.async;

import java.net.InetSocketAddress;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.cache.HttpCacheCASOperation;
import org.apache.hc.client5.http.cache.HttpCacheContext;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.HttpCacheStorage;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.cache.CacheKeyGenerator;
import org.apache.hc.client5.http.impl.cache.HeapResourceFactory;
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie;
import org.apache.hc.client5.testing.async.AbstractSimpleServerExchangeHandler;
import org.apache.hc.client5.testing.extension.async.CachingHttpAsyncClientResource;
import org.apache.hc.client5.testing.extension.async.ServerProtocolLevel;
import org.apache.hc.client5.testing.extension.async.TestAsyncServerResources;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Async equivalent of {@code TestCachingClientCookieVary304WithoutVary}: RFC 9110 15.4.5 requires a
 * 304 to repeat {@code Vary}, but many real servers violate that MUST and omit it anyway. The
 * effective-request computation used to match a 304 against the cache must come from the matched
 * ROOT cache entry's own {@code Vary}, never from the 304 itself: a cookie-bearing requester doing a
 * conditional GET must still be matched/stored against her own {@code Cookie} variant, and a
 * Vary-less 304 must never let that matching collapse onto - and corrupt - the unrelated anonymous
 * variant already cached for the same URI.
 */
class TestCachingAsyncClientCookieVary304WithoutVary {

    static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    @RegisterExtension
    private final TestAsyncServerResources serverResources;
    @RegisterExtension
    private final CachingHttpAsyncClientResource clientResource;

    private final AtomicInteger anonymousHits = new AtomicInteger();
    private final AtomicInteger alice304Hits = new AtomicInteger();
    private final RecordingCacheStorage cacheStorage = new RecordingCacheStorage();

    /**
     * A trivial in-memory {@link HttpCacheStorage} that lets the test inspect the stored root entry's
     * own variant set directly, rather than relying on backend hit counts, which cannot distinguish a
     * corrupted root (a 304 that overwrote the anonymous variant in place, leaving a single, stale
     * variant behind) from a correctly-added second variant.
     */
    private static final class RecordingCacheStorage implements HttpCacheStorage {

        private final Map<String, HttpCacheEntry> map = new ConcurrentHashMap<>();

        @Override
        public void putEntry(final String key, final HttpCacheEntry entry) {
            map.put(key, entry);
        }

        @Override
        public HttpCacheEntry getEntry(final String key) {
            return map.get(key);
        }

        @Override
        public void removeEntry(final String key) {
            map.remove(key);
        }

        @Override
        public void updateEntry(final String key, final HttpCacheCASOperation casOperation) throws ResourceIOException {
            map.put(key, casOperation.execute(map.get(key)));
        }

        @Override
        public Map<String, HttpCacheEntry> getEntries(final Collection<String> keys) {
            final Map<String, HttpCacheEntry> result = new ConcurrentHashMap<>();
            for (final String key : keys) {
                final HttpCacheEntry entry = map.get(key);
                if (entry != null) {
                    result.put(key, entry);
                }
            }
            return result;
        }

    }

    TestCachingAsyncClientCookieVary304WithoutVary() throws Exception {
        this.serverResources = new TestAsyncServerResources(URIScheme.HTTP, TIMEOUT, ServerProtocolLevel.STANDARD);
        this.serverResources.configureServer(bootstrap -> bootstrap
                .register("/resource", () -> new AbstractSimpleServerExchangeHandler() {

                    @Override
                    protected SimpleHttpResponse handle(
                            final SimpleHttpRequest request,
                            final HttpCoreContext context) {
                        final Header cookie = request.getFirstHeader(HttpHeaders.COOKIE);
                        if (cookie == null) {
                            anonymousHits.incrementAndGet();
                            final SimpleHttpResponse response = new SimpleHttpResponse(HttpStatus.SC_OK);
                            response.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=60, public");
                            response.setHeader(HttpHeaders.VARY, HttpHeaders.COOKIE);
                            response.setHeader(HttpHeaders.LAST_MODIFIED, oneHourAgo());
                            response.setBody("anonymous content", ContentType.TEXT_PLAIN);
                            return response;
                        }
                        // A non-compliant revalidation response: no Vary at all, simulating a real
                        // origin/intermediary that does not repeat it on a 304, for a cookie-bearing,
                        // caller-issued conditional GET that the cache has never seen a variant for yet.
                        alice304Hits.incrementAndGet();
                        final SimpleHttpResponse response = new SimpleHttpResponse(HttpStatus.SC_NOT_MODIFIED);
                        response.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=60, public");
                        return response;
                    }

                }));
        this.clientResource = new CachingHttpAsyncClientResource(HttpVersionPolicy.FORCE_HTTP_1);
        this.clientResource.configure(builder -> builder
                .setResourceFactory(HeapResourceFactory.INSTANCE)
                .setHttpCacheStorage(cacheStorage));
    }

    private static String oneHourAgo() {
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC).minusHours(1));
    }

    HttpHost startServer() throws Exception {
        final InetSocketAddress address = serverResources.server().start();
        return new HttpHost(serverResources.scheme().id, "localhost", address.getPort());
    }

    private HttpCacheContext sessionContext(final HttpHost target, final String sessionCookieValue) {
        final BasicCookieStore cookieStore = new BasicCookieStore();
        final BasicClientCookie cookie = new BasicClientCookie("session", sessionCookieValue);
        cookie.setDomain(target.getHostName());
        cookie.setPath("/");
        cookieStore.addCookie(cookie);
        final HttpCacheContext context = HttpCacheContext.create();
        context.setCookieStore(cookieStore);
        return context;
    }

    private String fetch(final CloseableHttpAsyncClient client, final HttpHost target, final HttpCacheContext context) throws Exception {
        final SimpleHttpResponse response = client.execute(SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath("/resource")
                        .build(), context, null)
                .get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
        return response.getBodyText();
    }

    @Test
    void testCookieBearingConditionalRequestIsNotCollapsedOntoTheAnonymousVariant() throws Exception {
        final HttpHost target = startServer();
        final CloseableHttpAsyncClient client = clientResource.client();

        // caches the anonymous Vary: Cookie variant
        Assertions.assertEquals("anonymous content", fetch(client, target, HttpCacheContext.create()));
        Assertions.assertEquals(1, anonymousHits.get());

        // alice has never been cached yet for this URI, but issues her own, directly conditional GET
        // (If-Modified-Since), carrying her cookie; the origin answers with a Vary-less 304. The match
        // used to look up (and, had it matched, to store/update) this response must never collapse
        // onto the anonymous variant just because the 304 itself does not repeat Vary.
        final HttpCacheContext aliceContext = sessionContext(target, "alice-session");
        final SimpleHttpRequest conditional = SimpleRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/resource")
                .addHeader(HttpHeaders.IF_MODIFIED_SINCE, oneHourAgo())
                .build();
        client.execute(conditional, aliceContext, null).get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        // the ETag-less negotiation retries once against the plain backend before giving up and
        // treating this as an ordinary vary-miss; either way, the origin always saw alice's cookie
        Assertions.assertTrue(alice304Hits.get() >= 1);

        // the anonymous variant must not have been corrupted or evicted by alice's 304
        Assertions.assertEquals("anonymous content", fetch(client, target, HttpCacheContext.create()));
        Assertions.assertEquals(1, anonymousHits.get(), "the anonymous variant must not have been re-fetched from the backend");

        // decisive, white-box check: alice's response must have been recorded as its OWN variant
        // under the root, alongside the anonymous one untouched, never as an in-place overwrite of
        // the anonymous variant's own stored slot (which a buggy match would have produced).
        final SimpleHttpRequest rootKeyRequest = SimpleRequestBuilder.get("http://" + target.getHostName() + ":" + target.getPort() + "/resource").build();
        final String rootKey = CacheKeyGenerator.INSTANCE.generateKey(target, rootKeyRequest, r -> null);
        final HttpCacheEntry root = cacheStorage.getEntry(rootKey);
        Assertions.assertNotNull(root);
        Assertions.assertTrue(root.hasVariants(), "the resource is known to vary on Cookie");
        Assertions.assertEquals(2, root.getVariants().size(),
                "alice's 304 must have been recorded as a distinct variant, not collapsed onto the anonymous one: " + root.getVariants());

        // decisive, black-box check: alice's own variant must never be a copy of the anonymous
        // content attributed to her under her own key - fetching again with her cookie must not
        // silently return the anonymous body.
        final SimpleHttpResponse aliceRefetch = client.execute(SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath("/resource")
                        .build(), aliceContext, null)
                .get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertNotEquals("anonymous content", aliceRefetch.getBodyText(),
                "alice must never be served the anonymous variant's content");
    }

}
