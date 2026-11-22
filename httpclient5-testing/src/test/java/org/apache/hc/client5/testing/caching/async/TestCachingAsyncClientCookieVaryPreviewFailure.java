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
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.cache.HttpAsyncCacheStorage;
import org.apache.hc.client5.http.cache.HttpCacheCASOperation;
import org.apache.hc.client5.http.cache.HttpCacheContext;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.impl.Operations;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.cache.HeapResourceFactory;
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie;
import org.apache.hc.client5.testing.async.AbstractSimpleServerExchangeHandler;
import org.apache.hc.client5.testing.extension.async.CachingHttpAsyncClientResource;
import org.apache.hc.client5.testing.extension.async.ServerProtocolLevel;
import org.apache.hc.client5.testing.extension.async.TestAsyncServerResources;
import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.concurrent.FutureCallback;
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
 * A failed or cancelled previewed re-match (the second, authoritative {@code responseCache.match}
 * call {@code AsyncCachingExec.lookup()} makes once it knows what {@code Cookie} or {@code
 * Authorization} the protocol layer below the cache is about to add) must never be treated as if
 * the preview had not happened at all. Falling back to the first, un-previewed match in that case
 * can serve a credentialed request a variant the cache was never able to verify actually matches it.
 */
class TestCachingAsyncClientCookieVaryPreviewFailure {

    static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    @RegisterExtension
    private final TestAsyncServerResources serverResources;
    @RegisterExtension
    private final CachingHttpAsyncClientResource clientResource;

    private final AtomicInteger anonymousHits = new AtomicInteger();
    private final AtomicInteger aliceHits = new AtomicInteger();
    private final FaultyHttpAsyncCacheStorage cacheStorage = new FaultyHttpAsyncCacheStorage();

    /**
     * An in-memory {@link HttpAsyncCacheStorage} that can be armed to fail {@link #getEntry} for one
     * specific key, simulating a storage-layer failure (anything other than a {@link
     * ResourceIOException}, which the cache already treats as an ordinary miss) that strikes a
     * particular cache entry rather than the whole store.
     */
    private static final class FaultyHttpAsyncCacheStorage implements HttpAsyncCacheStorage {

        private final Map<String, HttpCacheEntry> map = new ConcurrentHashMap<>();
        private volatile String failingKey;

        void failGetEntryFor(final String key) {
            this.failingKey = key;
        }

        /**
         * Finds the key of the single, non-root cache entry whose stored body equals {@code body}.
         */
        String keyForBody(final String body) {
            for (final Map.Entry<String, HttpCacheEntry> e : map.entrySet()) {
                final HttpCacheEntry entry = e.getValue();
                if (!entry.hasVariants() && entry.getResource() != null) {
                    try {
                        final String content = new String(entry.getResource().get(), StandardCharsets.UTF_8);
                        if (body.equals(content)) {
                            return e.getKey();
                        }
                    } catch (final ResourceIOException ignore) {
                        // not the entry we are looking for
                    }
                }
            }
            throw new IllegalStateException("no cache entry found for body: " + body);
        }

        @Override
        public Cancellable putEntry(final String key, final HttpCacheEntry entry, final FutureCallback<Boolean> callback) {
            map.put(key, entry);
            callback.completed(Boolean.TRUE);
            return Operations.nonCancellable();
        }

        @Override
        public Cancellable getEntry(final String key, final FutureCallback<HttpCacheEntry> callback) {
            if (key.equals(failingKey)) {
                callback.failed(new IllegalStateException("simulated storage failure for key: " + key));
            } else {
                callback.completed(map.get(key));
            }
            return Operations.nonCancellable();
        }

        @Override
        public Cancellable removeEntry(final String key, final FutureCallback<Boolean> callback) {
            map.remove(key);
            callback.completed(Boolean.TRUE);
            return Operations.nonCancellable();
        }

        @Override
        public Cancellable updateEntry(final String key, final HttpCacheCASOperation casOperation, final FutureCallback<Boolean> callback) {
            try {
                map.put(key, casOperation.execute(map.get(key)));
                callback.completed(Boolean.TRUE);
            } catch (final ResourceIOException ex) {
                callback.failed(ex);
            }
            return Operations.nonCancellable();
        }

        @Override
        public Cancellable getEntries(final Collection<String> keys, final FutureCallback<Map<String, HttpCacheEntry>> callback) {
            final Map<String, HttpCacheEntry> result = new ConcurrentHashMap<>();
            for (final String key : keys) {
                final HttpCacheEntry entry = map.get(key);
                if (entry != null) {
                    result.put(key, entry);
                }
            }
            callback.completed(result);
            return Operations.nonCancellable();
        }

    }

    TestCachingAsyncClientCookieVaryPreviewFailure() throws Exception {
        this.serverResources = new TestAsyncServerResources(URIScheme.HTTP, TIMEOUT, ServerProtocolLevel.STANDARD);
        this.serverResources.configureServer(bootstrap -> bootstrap
                .register("*", () -> new AbstractSimpleServerExchangeHandler() {

                    @Override
                    protected SimpleHttpResponse handle(
                            final SimpleHttpRequest request,
                            final HttpCoreContext context) {
                        final Header cookie = request.getFirstHeader(HttpHeaders.COOKIE);
                        final String body;
                        if (cookie == null) {
                            anonymousHits.incrementAndGet();
                            body = "anonymous content";
                        } else {
                            aliceHits.incrementAndGet();
                            body = "alice's private content";
                        }
                        final SimpleHttpResponse response = new SimpleHttpResponse(HttpStatus.SC_OK);
                        response.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=60");
                        response.setHeader(HttpHeaders.VARY, HttpHeaders.COOKIE);
                        response.setBody(body, ContentType.TEXT_PLAIN);
                        return response;
                    }

                }));
        this.clientResource = new CachingHttpAsyncClientResource(HttpVersionPolicy.FORCE_HTTP_1);
        this.clientResource.configure(builder -> builder
                .setResourceFactory(HeapResourceFactory.INSTANCE)
                .setHttpCacheStorage(cacheStorage));
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
    void testFailedPreviewedMatchNeverFallsBackToTheUnPreviewedResult() throws Exception {
        final HttpHost target = startServer();
        final CloseableHttpAsyncClient client = clientResource.client();

        // caches the anonymous Vary: Cookie variant
        Assertions.assertEquals("anonymous content", fetch(client, target, HttpCacheContext.create()));
        Assertions.assertEquals(1, anonymousHits.get());

        // caches alice's own Vary: Cookie variant
        final HttpCacheContext aliceContext = sessionContext(target, "alice-session");
        Assertions.assertEquals("alice's private content", fetch(client, target, aliceContext));
        Assertions.assertEquals(1, aliceHits.get());

        // From now on, reading alice's own variant entry back from storage fails. Her next request's
        // FIRST, un-previewed match looks up the anonymous variant (she carries no Cookie header yet
        // above the cache) and finds it; only the SECOND, previewed match - made once the preview adds
        // her actual Cookie header - ever looks up her own variant entry, so this is the one storage
        // read that fails.
        cacheStorage.failGetEntryFor(cacheStorage.keyForBody("alice's private content"));

        // The previewed match is authoritative and just failed; the lookup must surface that failure
        // rather than silently falling back to the first match's anonymous hit and serving alice
        // someone else's cached content without ever asking the origin.
        final Throwable cause = Assertions.assertThrows(ExecutionException.class, () ->
                client.execute(SimpleRequestBuilder.get()
                                .setHttpHost(target)
                                .setPath("/resource")
                                .build(), aliceContext, null)
                        .get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit())).getCause();
        Assertions.assertInstanceOf(IllegalStateException.class, cause);

        // neither variant was disturbed by the failed lookup
        Assertions.assertEquals(1, anonymousHits.get());
        Assertions.assertEquals(1, aliceHits.get());
    }

}
