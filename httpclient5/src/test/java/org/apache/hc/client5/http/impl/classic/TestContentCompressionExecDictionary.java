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
package org.apache.hc.client5.http.impl.classic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecRuntime;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.entity.EntityBuilder;
import org.apache.hc.client5.http.entity.compress.BasicCompressionDictionaryStore;
import org.apache.hc.client5.http.entity.compress.CompressionDictionary;
import org.apache.hc.client5.http.impl.Brotli4jRuntime;
import org.apache.hc.client5.http.impl.CompressionDictionaryCookieStore;
import org.apache.hc.client5.http.impl.ZstdRuntime;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

class TestContentCompressionExecDictionary {

    private static final String ORIGIN = "https://example.com";

    @Mock
    private ExecRuntime execRuntime;
    @Mock
    private ExecChain execChain;
    @Mock
    private ClassicHttpRequest originalRequest;

    private HttpClientContext context;
    private ExecChain.Scope scope;
    private BasicCompressionDictionaryStore store;
    private CookieStore cookieStore;
    private ContentCompressionExec impl;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        final HttpHost target = new HttpHost("https", "example.com", 443);
        context = HttpClientContext.create();
        scope = new ExecChain.Scope(
                "test", new HttpRoute(target), originalRequest, execRuntime, context);
        store = new BasicCompressionDictionaryStore();
        cookieStore = new CompressionDictionaryCookieStore(new BasicCookieStore(), store);
        context.setCookieStore(cookieStore);
        impl = new ContentCompressionExec(store);
    }

    private static CompressionDictionary freshDictionary(final String match, final String id) {
        final Instant now = Instant.now();
        return new CompressionDictionary(
                "dictionary-content".getBytes(StandardCharsets.UTF_8),
                URI.create(ORIGIN + "/dictionary"),
                match,
                id,
                now.minusSeconds(60),
                now.plusSeconds(3600));
    }

    private static ClassicHttpRequest request(final String path) {
        return new BasicClassicHttpRequest(
                Method.GET, new HttpHost("https", "example.com", 443), path);
    }

    @Test
    void testFreshDictionaryAddsNegotiationHeaders() throws Exception {
        final CompressionDictionary dictionary = freshDictionary("/*", "dict-1");
        store.add(cookieStore, dictionary);
        final ClassicHttpRequest request = request("/page.html");
        Mockito.when(execChain.proceed(request, scope))
                .thenReturn(new BasicClassicHttpResponse(204, "No Content"));

        impl.execute(request, scope, execChain);

        assertTrue(request.containsHeader("Available-Dictionary"));
        assertEquals("\"dict-1\"", request.getFirstHeader("Dictionary-ID").getValue());
        final String acceptEncoding = request.getFirstHeader(HttpHeaders.ACCEPT_ENCODING).getValue();
        assertEquals(Brotli4jRuntime.available(), tokenPresent(acceptEncoding, "dcb"));
        assertEquals(ZstdRuntime.available(), tokenPresent(acceptEncoding, "dcz"));
    }

    @Test
    void testExplicitAcceptEncodingOptsOutOfDictionaryNegotiation() throws Exception {
        store.add(cookieStore, freshDictionary("/*", "dict-1"));
        final ClassicHttpRequest request = request("/page.html");
        request.addHeader(HttpHeaders.ACCEPT_ENCODING, "gzip, dcb");
        Mockito.when(execChain.proceed(request, scope))
                .thenReturn(new BasicClassicHttpResponse(204, "No Content"));

        impl.execute(request, scope, execChain);

        assertEquals("gzip, dcb", request.getFirstHeader(HttpHeaders.ACCEPT_ENCODING).getValue());
        assertFalse(request.containsHeader("Available-Dictionary"));
        assertFalse(request.containsHeader("Dictionary-ID"));
    }

    @Test
    void testDictionaryContentEncodingRejectedWithoutNegotiation() throws Exception {
        final ClassicHttpRequest request = request("/page.html");
        final ClassicHttpResponse response = Mockito.spy(new BasicClassicHttpResponse(200, "OK"));
        response.setEntity(EntityBuilder.create().setText("encoded").setContentEncoding("dcb").build());
        Mockito.when(execChain.proceed(request, scope)).thenReturn(response);

        assertThrows(HttpException.class, () -> impl.execute(request, scope, execChain));
        Mockito.verify(response).close();
    }

    @Test
    void testUseAsDictionaryCapturesDecodedEntity() throws Exception {
        final ClassicHttpRequest request = request("/dictionary");
        final BasicClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        response.addHeader("Use-As-Dictionary", "match=\"/*\", id=\"captured\"");
        response.addHeader(HttpHeaders.CACHE_CONTROL, "max-age=3600");
        final byte[] body = "hello dictionary".getBytes(StandardCharsets.UTF_8);
        response.setEntity(new StringEntity("hello dictionary"));
        Mockito.when(execChain.proceed(request, scope)).thenReturn(response);

        final ClassicHttpResponse result = impl.execute(request, scope, execChain);
        assertTrue(result.getEntity() instanceof DictionaryCapturingEntity);
        assertArrayEquals(body, EntityUtils.toByteArray(result.getEntity()));

        final List<CompressionDictionary> dictionaries =
                store.getByOrigin(cookieStore, URI.create(ORIGIN + "/dictionary"));
        assertEquals(1, dictionaries.size());
        assertEquals("captured", dictionaries.get(0).getId());
        assertArrayEquals(body, dictionaries.get(0).getContent());
    }

    @Test
    void testUnmanagedCookieStoreIsRejected() {
        context.setCookieStore(new BasicCookieStore());
        final ClassicHttpRequest request = request("/page.html");
        assertThrows(HttpException.class, () -> impl.execute(request, scope, execChain));
    }

    @Test
    void testBuilderRejectsCustomCookieStoreWithDictionaryTransport() {
        assertThrows(IllegalStateException.class, () -> HttpClientBuilder.create()
                .setDefaultCookieStore(new BasicCookieStore())
                .setCompressionDictionaryStore(store)
                .build());
    }

    @Test
    void testBuilderRejectsDisabledCookieManagementWithDictionaryTransport() {
        assertThrows(IllegalStateException.class, () -> HttpClientBuilder.create()
                .disableCookieManagement()
                .setCompressionDictionaryStore(store)
                .build());
    }

    @Test
    void testCustomDictionaryDecoderIsRejected() {
        final UnaryOperator<HttpEntity> identity = entity -> entity;
        assertThrows(IllegalArgumentException.class, () -> new ContentCompressionExec(
                Arrays.asList("gzip", "DCB"),
                RegistryBuilder.<UnaryOperator<HttpEntity>>create()
                        .register("gzip", identity)
                        .register("DCB", identity)
                        .build(),
                store));
    }

    private static boolean tokenPresent(final String headerValue, final String token) {
        for (final String part : headerValue.split(",")) {
            if (token.equalsIgnoreCase(part.trim())) {
                return true;
            }
        }
        return false;
    }
}
