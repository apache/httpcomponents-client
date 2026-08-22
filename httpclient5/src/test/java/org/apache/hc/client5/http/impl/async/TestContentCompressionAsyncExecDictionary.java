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
package org.apache.hc.client5.http.impl.async;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecRuntime;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.entity.compress.BasicCompressionDictionaryStore;
import org.apache.hc.client5.http.entity.compress.CompressionDictionary;
import org.apache.hc.client5.http.entity.compress.CompressionDictionaryStore;
import org.apache.hc.client5.http.impl.Brotli4jRuntime;
import org.apache.hc.client5.http.impl.ZstdRuntime;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.CancellableDependency;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class TestContentCompressionAsyncExecDictionary {

    private static final String ORIGIN = "https://example.com";
    private static final String AVAILABLE_DICTIONARY = "Available-Dictionary";
    private static final String DICTIONARY_ID = "Dictionary-ID";
    private static final String USE_AS_DICTIONARY = "Use-As-Dictionary";

    @Mock
    private AsyncExecChain execChain;
    @Mock
    private AsyncEntityProducer entityProducer;
    @Mock
    private AsyncExecCallback originalCb;
    @Mock
    private AsyncExecRuntime execRuntime;
    @Mock
    private CancellableDependency dependency;

    private HttpClientContext context;
    private AsyncExecChain.Scope scope;
    private BasicCompressionDictionaryStore store;
    private CookieStore cookieStore;
    private ContentCompressionAsyncExec impl;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);

        final HttpHost target = new HttpHost("https", "example.com", 443);
        final HttpRequest originalRequest = new BasicHttpRequest(Method.GET, "/");
        context = HttpClientContext.create();
        scope = new AsyncExecChain.Scope(
                "test",
                new HttpRoute(target),
                originalRequest,
                dependency,
                context,
                execRuntime,
                null,
                new AtomicInteger());

        store = new BasicCompressionDictionaryStore();
        cookieStore = new CompressionDictionaryCookieStore(new BasicCookieStore(), store);
        context.setCookieStore(cookieStore);
        impl = new ContentCompressionAsyncExec((CompressionDictionaryStore) store);
    }

    private AsyncExecCallback executeAndCapture(final HttpRequest request) throws Exception {
        final ArgumentCaptor<AsyncExecCallback> cap = ArgumentCaptor.forClass(AsyncExecCallback.class);
        doNothing().when(execChain).proceed(eq(request), eq(entityProducer), eq(scope), cap.capture());
        impl.execute(request, entityProducer, scope, execChain, originalCb);
        return cap.getValue();
    }

    private static CompressionDictionary freshDictionary(final String match, final String id) {
        final Instant now = Instant.now();
        return new CompressionDictionary(
                "dictionary-content".getBytes(StandardCharsets.UTF_8),
                URI.create(ORIGIN),
                match,
                id,
                now.minusSeconds(60),
                now.plusSeconds(3600));
    }

    private static String expectedAvailableDictionary(final CompressionDictionary dictionary) {
        return ":" + Base64.getEncoder().encodeToString(dictionary.getSha256()) + ":";
    }

    @Test
    void testFreshDictionaryAddsAvailableDictionaryHeader() throws Exception {
        final CompressionDictionary dictionary = freshDictionary("/*", "dict-1");
        store.add(cookieStore, dictionary);

        final HttpRequest request = new BasicHttpRequest(Method.GET, URI.create(ORIGIN + "/page.html"));
        executeAndCapture(request);

        assertTrue(request.containsHeader(AVAILABLE_DICTIONARY));
        assertEquals(expectedAvailableDictionary(dictionary),
                request.getFirstHeader(AVAILABLE_DICTIONARY).getValue());
    }

    @Test
    void testFreshDictionaryAddsDictionaryIdHeaderWhenIdPresent() throws Exception {
        store.add(cookieStore, freshDictionary("/*", "dict-1"));

        final HttpRequest request = new BasicHttpRequest(Method.GET, URI.create(ORIGIN + "/page.html"));
        executeAndCapture(request);

        assertTrue(request.containsHeader(DICTIONARY_ID));
        assertEquals("\"dict-1\"", request.getFirstHeader(DICTIONARY_ID).getValue());
    }

    @Test
    void testDictionaryIdHeaderOmittedWhenIdEmpty() throws Exception {
        store.add(cookieStore, freshDictionary("/*", ""));

        final HttpRequest request = new BasicHttpRequest(Method.GET, URI.create(ORIGIN + "/page.html"));
        executeAndCapture(request);

        assertTrue(request.containsHeader(AVAILABLE_DICTIONARY));
        assertFalse(request.containsHeader(DICTIONARY_ID));
    }

    @Test
    void testAcceptEncodingIncludesDictionaryTokensWhenAvailable() throws Exception {
        store.add(cookieStore, freshDictionary("/*", "dict-1"));

        final HttpRequest request = new BasicHttpRequest(Method.GET, URI.create(ORIGIN + "/page.html"));
        executeAndCapture(request);

        assertTrue(request.containsHeader(HttpHeaders.ACCEPT_ENCODING));
        final String acceptEncoding = request.getFirstHeader(HttpHeaders.ACCEPT_ENCODING).getValue();

        // dcb / dcz tokens are only offered when the matching native runtime is present
        assertEquals(Brotli4jRuntime.available(), tokenPresent(acceptEncoding, "dcb"));
        assertEquals(ZstdRuntime.available(), tokenPresent(acceptEncoding, "dcz"));
    }

    private static boolean tokenPresent(final String headerValue, final String token) {
        for (final String part : headerValue.split(",")) {
            if (token.equalsIgnoreCase(part.trim())) {
                return true;
            }
        }
        return false;
    }

    @Test
    void testNoDictionaryWhenRequestAlreadyHasAvailableDictionary() throws Exception {
        store.add(cookieStore, freshDictionary("/*", "dict-1"));

        final HttpRequest request = new BasicHttpRequest(Method.GET, URI.create(ORIGIN + "/page.html"));
        request.addHeader(AVAILABLE_DICTIONARY, ":preset:");
        executeAndCapture(request);

        // the exec must not add a second Available-Dictionary header nor a Dictionary-ID header
        assertEquals(1, request.getHeaders(AVAILABLE_DICTIONARY).length);
        assertEquals(":preset:", request.getFirstHeader(AVAILABLE_DICTIONARY).getValue());
        assertFalse(request.containsHeader(DICTIONARY_ID));

        final String acceptEncoding = request.getFirstHeader(HttpHeaders.ACCEPT_ENCODING).getValue();
        assertFalse(tokenPresent(acceptEncoding, "dcb"));
        assertFalse(tokenPresent(acceptEncoding, "dcz"));
    }

    @Test
    void testNoDictionaryWhenRequestUriNotHttps() throws Exception {
        store.add(cookieStore, freshDictionary("/*", "dict-1"));

        final HttpRequest request = new BasicHttpRequest(Method.GET, URI.create("http://example.com/page.html"));
        executeAndCapture(request);

        assertFalse(request.containsHeader(AVAILABLE_DICTIONARY));
        assertFalse(request.containsHeader(DICTIONARY_ID));

        final String acceptEncoding = request.getFirstHeader(HttpHeaders.ACCEPT_ENCODING).getValue();
        assertFalse(tokenPresent(acceptEncoding, "dcb"));
        assertFalse(tokenPresent(acceptEncoding, "dcz"));
    }

    @Test
    void testDcbContentEncodingRejectedWithoutNegotiatedDictionary() throws Exception {
        // empty store -> no dictionary negotiated for the request
        final HttpRequest request = new BasicHttpRequest(Method.GET, URI.create(ORIGIN + "/page.html"));
        final AsyncExecCallback cb = executeAndCapture(request);

        final HttpResponse rsp = new BasicHttpResponse(200, "OK");
        final EntityDetails details = mock(EntityDetails.class);
        when(details.getContentEncoding()).thenReturn("dcb");
        when(originalCb.handleResponse(same(rsp), any(EntityDetails.class)))
                .thenReturn(mock(AsyncDataConsumer.class));

        assertThrows(HttpException.class, () -> cb.handleResponse(rsp, details));
    }

    @Test
    void testDczContentEncodingRejectedWithoutNegotiatedDictionary() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, URI.create(ORIGIN + "/page.html"));
        final AsyncExecCallback cb = executeAndCapture(request);

        final HttpResponse rsp = new BasicHttpResponse(200, "OK");
        final EntityDetails details = mock(EntityDetails.class);
        when(details.getContentEncoding()).thenReturn("dcz");
        when(originalCb.handleResponse(same(rsp), any(EntityDetails.class)))
                .thenReturn(mock(AsyncDataConsumer.class));

        assertThrows(HttpException.class, () -> cb.handleResponse(rsp, details));
    }

    @Test
    void testUseAsDictionaryWrapsDownstreamAndCapturesBody() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, URI.create(ORIGIN + "/page.html"));
        final AsyncExecCallback cb = executeAndCapture(request);

        final HttpResponse rsp = new BasicHttpResponse(200, "OK");
        rsp.addHeader(USE_AS_DICTIONARY, "match=\"/page.html\"");
        rsp.addHeader(HttpHeaders.CACHE_CONTROL, "max-age=3600");

        final EntityDetails details = mock(EntityDetails.class);
        when(details.getContentEncoding()).thenReturn(null);

        final DrainingConsumer downstream = new DrainingConsumer();
        when(originalCb.handleResponse(same(rsp), any(EntityDetails.class))).thenReturn(downstream);

        final AsyncDataConsumer wrapped = cb.handleResponse(rsp, details);

        assertNotNull(wrapped);
        assertTrue(wrapped instanceof DictionaryCapturingAsyncDataConsumer);

        final byte[] body = "hello dictionary".getBytes(StandardCharsets.UTF_8);
        wrapped.consume(ByteBuffer.wrap(body));
        wrapped.streamEnd(null);

        assertTrue(downstream.ended);

        final List<CompressionDictionary> stored =
                store.getByOrigin(cookieStore, URI.create(ORIGIN + "/page.html"));
        assertEquals(1, stored.size());
        assertEquals("/page.html", stored.get(0).getMatch());
        assertArrayEquals(body, stored.get(0).getContent());
    }

    @Test
    void testUseAsDictionaryCombinesMultipleFieldLines() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, URI.create(ORIGIN + "/page.html"));
        final AsyncExecCallback cb = executeAndCapture(request);
        final HttpResponse rsp = new BasicHttpResponse(200, "OK");
        rsp.addHeader(USE_AS_DICTIONARY, "match=\"/page.html\"");
        rsp.addHeader(USE_AS_DICTIONARY, "id=\"split-field\"");
        rsp.addHeader(HttpHeaders.CACHE_CONTROL, "max-age=3600");
        final EntityDetails details = mock(EntityDetails.class);
        when(details.getContentEncoding()).thenReturn(null);
        final DrainingConsumer downstream = new DrainingConsumer();
        when(originalCb.handleResponse(same(rsp), any(EntityDetails.class))).thenReturn(downstream);

        final AsyncDataConsumer wrapped = cb.handleResponse(rsp, details);
        wrapped.consume(ByteBuffer.wrap("dictionary".getBytes(StandardCharsets.UTF_8)));
        wrapped.streamEnd(null);

        final List<CompressionDictionary> stored =
                store.getByOrigin(cookieStore, URI.create(ORIGIN + "/page.html"));
        assertEquals(1, stored.size());
        assertEquals("split-field", stored.get(0).getId());
    }

    @Test
    void testUseAsDictionaryNotWrappedWithoutFreshnessDirective() throws Exception {
        final HttpRequest request = new BasicHttpRequest(Method.GET, URI.create(ORIGIN + "/page.html"));
        final AsyncExecCallback cb = executeAndCapture(request);

        final HttpResponse rsp = new BasicHttpResponse(200, "OK");
        // Use-As-Dictionary present but no max-age -> validUntil is null -> no capture wrapping
        rsp.addHeader(USE_AS_DICTIONARY, "match=\"/page.html\"");

        final EntityDetails details = mock(EntityDetails.class);
        when(details.getContentEncoding()).thenReturn(null);

        final DrainingConsumer downstream = new DrainingConsumer();
        when(originalCb.handleResponse(same(rsp), any(EntityDetails.class))).thenReturn(downstream);

        final AsyncDataConsumer wrapped = cb.handleResponse(rsp, details);

        assertSame(downstream, wrapped);
    }

    @Test
    void testDictionaryIsNotVisibleFromAnotherCookiePartition() throws Exception {
        final CompressionDictionary dictionary = freshDictionary("/*", "dict-1");
        store.add(cookieStore, dictionary);
        context.setCookieStore(new CompressionDictionaryCookieStore(new BasicCookieStore(), store));

        final HttpRequest request = new BasicHttpRequest(Method.GET, URI.create(ORIGIN + "/page.html"));
        executeAndCapture(request);

        assertFalse(request.containsHeader(AVAILABLE_DICTIONARY));
        assertFalse(request.containsHeader(DICTIONARY_ID));
    }

    @Test
    void testClearingCookiesClearsDictionaryPartition() throws Exception {
        final CompressionDictionary dictionary = freshDictionary("/*", "dict-1");
        store.add(cookieStore, dictionary);
        final HttpRequest request = new BasicHttpRequest(Method.GET, URI.create(ORIGIN + "/page.html"));
        executeAndCapture(request);

        cookieStore.clear();

        assertTrue(store.getByOrigin(cookieStore, URI.create(ORIGIN)).isEmpty());
    }

    @Test
    void testUnmanagedCookieStoreDisablesDictionaryTransport() throws Exception {
        final CookieStore unmanagedPartition = new BasicCookieStore();
        context.setCookieStore(unmanagedPartition);
        store.add(unmanagedPartition, freshDictionary("/*", "dict-1"));

        final HttpRequest request = new BasicHttpRequest(Method.GET, URI.create(ORIGIN + "/page.html"));
        executeAndCapture(request);

        assertFalse(request.containsHeader(AVAILABLE_DICTIONARY));
        assertFalse(request.containsHeader(DICTIONARY_ID));
    }

    @Test
    void testCustomDcbDecoderIsUsed() throws Exception {
        final LinkedHashMap<String, UnaryOperator<AsyncDataConsumer>> decoders = new LinkedHashMap<>();
        final AsyncDataConsumer custom = mock(AsyncDataConsumer.class);
        decoders.put("DCB", downstream -> custom);
        impl = new ContentCompressionAsyncExec(decoders, store);
        store.add(cookieStore, freshDictionary("/*", "dict-1"));

        final HttpRequest request = new BasicHttpRequest(Method.GET, URI.create(ORIGIN + "/page.html"));
        final AsyncExecCallback cb = executeAndCapture(request);
        final HttpResponse rsp = new BasicHttpResponse(200, "OK");
        final EntityDetails details = mock(EntityDetails.class);
        when(details.getContentEncoding()).thenReturn("dcb");
        when(originalCb.handleResponse(same(rsp), any(EntityDetails.class)))
                .thenReturn(mock(AsyncDataConsumer.class));

        assertSame(custom, cb.handleResponse(rsp, details));
    }

    private static final class DrainingConsumer implements AsyncDataConsumer {

        private boolean ended;

        @Override
        public void updateCapacity(final CapacityChannel capacityChannel) {
        }

        @Override
        public void consume(final ByteBuffer src) {
            src.position(src.limit());
        }

        @Override
        public void streamEnd(final List<? extends Header> trailers) {
            ended = true;
        }

        @Override
        public void releaseResources() {
        }
    }
}
