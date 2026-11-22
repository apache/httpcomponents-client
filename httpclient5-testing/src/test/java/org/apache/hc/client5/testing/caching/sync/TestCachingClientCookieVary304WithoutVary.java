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
package org.apache.hc.client5.testing.caching.sync;

import java.net.InetSocketAddress;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.cache.HttpCacheCASOperation;
import org.apache.hc.client5.http.cache.HttpCacheContext;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.HttpCacheStorage;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.impl.cache.CacheKeyGenerator;
import org.apache.hc.client5.http.impl.cache.HeapResourceFactory;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie;
import org.apache.hc.client5.testing.extension.sync.CachingHttpClientResource;
import org.apache.hc.client5.testing.extension.sync.TestServerResources;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * RFC 9110 15.4.5 requires a 304 to repeat {@code Vary}, but many real servers violate that MUST and
 * omit it anyway. The effective-request computation used to match a 304 against the cache must come
 * from the matched ROOT cache entry's own {@code Vary}, never from the 304 itself: a cookie-bearing
 * requester doing a conditional GET must still be matched/stored against her own {@code Cookie}
 * variant, and a Vary-less 304 must never let that matching collapse onto - and corrupt - the
 * unrelated anonymous variant already cached for the same URI.
 */
class TestCachingClientCookieVary304WithoutVary {

    static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    @RegisterExtension
    private final TestServerResources serverResources;
    @RegisterExtension
    private final CachingHttpClientResource clientResource;

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

    TestCachingClientCookieVary304WithoutVary() throws Exception {
        this.serverResources = new TestServerResources(URIScheme.HTTP, TIMEOUT);
        this.serverResources.configureServer(bootstrap -> bootstrap
                .register("/resource", (request, response, context) -> {
                    final Header cookie = request.getFirstHeader(HttpHeaders.COOKIE);
                    response.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=60, public");
                    if (cookie == null) {
                        anonymousHits.incrementAndGet();
                        response.setCode(HttpStatus.SC_OK);
                        response.setHeader(HttpHeaders.VARY, HttpHeaders.COOKIE);
                        response.setHeader(HttpHeaders.LAST_MODIFIED, oneHourAgo());
                        response.setEntity(new StringEntity("anonymous content", ContentType.TEXT_PLAIN));
                    } else {
                        // A non-compliant revalidation response: no Vary at all, simulating a real
                        // origin/intermediary that does not repeat it on a 304, for a cookie-bearing,
                        // caller-issued conditional GET that the cache has never seen a variant for yet.
                        alice304Hits.incrementAndGet();
                        response.setCode(HttpStatus.SC_NOT_MODIFIED);
                    }
                }));
        this.clientResource = new CachingHttpClientResource();
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

    private String fetch(final CloseableHttpClient client, final HttpHost target, final HttpCacheContext context) throws Exception {
        return client.execute(target, new HttpGet("/resource"), context, response -> {
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            return EntityUtils.toString(response.getEntity());
        });
    }

    @Test
    void testCookieBearingConditionalRequestIsNotCollapsedOntoTheAnonymousVariant() throws Exception {
        final HttpHost target = startServer();
        final CloseableHttpClient client = clientResource.client();

        // caches the anonymous Vary: Cookie variant
        Assertions.assertEquals("anonymous content", fetch(client, target, HttpCacheContext.create()));
        Assertions.assertEquals(1, anonymousHits.get());

        // alice has never been cached yet for this URI, but issues her own, directly conditional GET
        // (If-Modified-Since), carrying her cookie; the origin answers with a Vary-less 304. The match
        // used to look up (and, had it matched, to store/update) this response must never collapse
        // onto the anonymous variant just because the 304 itself does not repeat Vary.
        final HttpCacheContext aliceContext = sessionContext(target, "alice-session");
        final HttpGet conditional = new HttpGet("/resource");
        conditional.setHeader(HttpHeaders.IF_MODIFIED_SINCE, oneHourAgo());
        client.execute(target, conditional, aliceContext, response -> {
            EntityUtils.consume(response.getEntity());
            return null;
        });
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
        final String aliceRefetchBody = client.execute(target, new HttpGet("/resource"), aliceContext, response ->
                response.getEntity() != null ? EntityUtils.toString(response.getEntity()) : null);
        Assertions.assertNotEquals("anonymous content", aliceRefetchBody,
                "alice must never be served the anonymous variant's content");
    }

}
