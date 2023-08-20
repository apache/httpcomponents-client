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
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.HttpCacheEntrySerializer;
import org.apache.hc.client5.http.cache.HttpCacheStorageEntry;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.util.ByteArrayBuffer;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TestHttpByteArrayCacheEntrySerializer {

    private HttpCacheEntrySerializer<byte[]> httpCacheEntrySerializer;

    @BeforeEach
    public void before() {
        httpCacheEntrySerializer = HttpByteArrayCacheEntrySerializer.INSTANCE;
    }

    @Test
    public void testSimpleSerializeAndDeserialize() throws Exception {
        final String content = "Hello World";
        final ContentType contentType = ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8);
        final HttpCacheEntry cacheEntry = new HttpCacheEntry(Instant.now(), Instant.now(),
                "GET", "/stuff", HttpTestUtils.headers(),
                HttpStatus.SC_OK, HttpTestUtils.headers(new BasicHeader(HttpHeaders.CONTENT_TYPE, contentType.toString())),
                new HeapResource(content.getBytes(contentType.getCharset())),
                null);
        final HttpCacheStorageEntry storageEntry = new HttpCacheStorageEntry("unique-cache-key", cacheEntry);
        final byte[] serialized = httpCacheEntrySerializer.serialize(storageEntry);

        final HttpCacheStorageEntry deserialized = httpCacheEntrySerializer.deserialize(serialized);
        MatcherAssert.assertThat(deserialized.getKey(), Matchers.equalTo(storageEntry.getKey()));
        MatcherAssert.assertThat(deserialized.getContent(), HttpCacheEntryMatcher.equivalent(storageEntry.getContent()));
    }

    @Test
    public void testSerializeAndDeserializeLargeContent() throws Exception {
        final ContentType contentType = ContentType.IMAGE_JPEG;
        final HeapResource resource = load(getClass().getResource("/ApacheLogo.png"));
        final HttpCacheEntry cacheEntry = new HttpCacheEntry(Instant.now(), Instant.now(),
                "GET", "/stuff", HttpTestUtils.headers(),
                HttpStatus.SC_OK, HttpTestUtils.headers(new BasicHeader(HttpHeaders.CONTENT_TYPE, contentType.toString())),
                resource,
                null);
        final HttpCacheStorageEntry storageEntry = new HttpCacheStorageEntry("unique-cache-key", cacheEntry);
        final byte[] serialized = httpCacheEntrySerializer.serialize(storageEntry);

        final HttpCacheStorageEntry deserialized = httpCacheEntrySerializer.deserialize(serialized);
        MatcherAssert.assertThat(deserialized.getKey(), Matchers.equalTo(storageEntry.getKey()));
        MatcherAssert.assertThat(deserialized.getContent(), HttpCacheEntryMatcher.equivalent(storageEntry.getContent()));
    }

    /**
     * Deserialize a cache entry in a bad format, expecting an exception.
     */
    @Test
    public void testInvalidCacheEntry() throws Exception {
        // This file is a JPEG not a cache entry, so should fail to deserialize
        final HeapResource resource = load(getClass().getResource("/ApacheLogo.png"));
        Assertions.assertThrows(ResourceIOException.class, () ->
                httpCacheEntrySerializer.deserialize(resource.get()));
    }

    /**
     * Deserialize truncated cache entries.
     */
    @Test
    public void testTruncatedCacheEntry() throws Exception {
        final String content1 = HttpByteArrayCacheEntrySerializer.HC_CACHE_VERSION_LINE + "\n" +
                "HC-Key: unique-cache-key\n" +
                "HC-Resource-Length: 11\n" +
                "HC-Request-Instant: 1686210849596\n" +
                "HC-Response-Instant: 1686210849596\n" +
                "\n" +
                "GET /stuff HTTP/1.1\n" +
                "\n" +
                "HTTP/1.1 200 \n" +
                "Content-Type: text/plain; charset=UTF-8\n" +
                "Cache-control: public, max-age=31536000\n" +
                "\n" +
                "Huh?";
        final byte[] bytes1 = content1.getBytes(StandardCharsets.UTF_8);
        final ResourceIOException exception1 = Assertions.assertThrows(ResourceIOException.class, () ->
                httpCacheEntrySerializer.deserialize(bytes1));
        Assertions.assertEquals("Unexpected end of cache content", exception1.getMessage());

        final String content2 = HttpByteArrayCacheEntrySerializer.HC_CACHE_VERSION_LINE + "\n" +
                "HC-Key: unique-cache-key\n" +
                "HC-Resource-Length: 11\n" +
                "HC-Request-Instant: 1686210849596\n" +
                "HC-Response-Instant: 1686210849596\n" +
                "\n" +
                "GET /stuff HTTP/1.1\n" +
                "\n" +
                "HTTP/1.1 200 \n" +
                "Content-Type: text/plain; charset=UTF-8\n" +
                "Cache-control: public, max-age=31536000\n";
        final byte[] bytes2 = content2.getBytes(StandardCharsets.UTF_8);
        final ResourceIOException exception2 = Assertions.assertThrows(ResourceIOException.class, () ->
                httpCacheEntrySerializer.deserialize(bytes2));
        Assertions.assertEquals("Unexpected end of stream", exception2.getMessage());

        final String content3 = HttpByteArrayCacheEntrySerializer.HC_CACHE_VERSION_LINE + "\n" +
                "HC-Key: unique-cache-key\n" +
                "HC-Resource-Length: 11\n" +
                "HC-Request-Instant: 1686210849596\n" +
                "HC-Response-Instant: 1686210849596\n" +
                "\n" +
                "GET /stuff HTTP/1.1\n" +
                "\n";
        final byte[] bytes3 = content3.getBytes(StandardCharsets.UTF_8);
        final ResourceIOException exception3 = Assertions.assertThrows(ResourceIOException.class, () ->
                httpCacheEntrySerializer.deserialize(bytes3));
        Assertions.assertEquals("Unexpected end of stream", exception3.getMessage());

        final String content4 = HttpByteArrayCacheEntrySerializer.HC_CACHE_VERSION_LINE + "\n" +
                "HC-Key: unique-cache-key\n" +
                "HC-Resource-Length: 11\n" +
                "HC-Request-Instant: 1686210849596\n" +
                "HC-Response-Instant: 1686210849596\n";
        final byte[] bytes4 = content4.getBytes(StandardCharsets.UTF_8);
        final ResourceIOException exception4 = Assertions.assertThrows(ResourceIOException.class, () ->
                httpCacheEntrySerializer.deserialize(bytes4));
        Assertions.assertEquals("Unexpected end of stream", exception4.getMessage());

        final String content5 = HttpByteArrayCacheEntrySerializer.HC_CACHE_VERSION_LINE + "\n" +
                "HC-Key: unique-cache-key\n";
        final byte[] bytes5 = content5.getBytes(StandardCharsets.UTF_8);
        final ResourceIOException exception5 = Assertions.assertThrows(ResourceIOException.class, () ->
                httpCacheEntrySerializer.deserialize(bytes5));
        Assertions.assertEquals("Unexpected end of stream", exception5.getMessage());

        final String content6 = HttpByteArrayCacheEntrySerializer.HC_CACHE_VERSION_LINE + "\n";
        final byte[] bytes6 = content6.getBytes(StandardCharsets.UTF_8);
        final ResourceIOException exception6 = Assertions.assertThrows(ResourceIOException.class, () ->
                httpCacheEntrySerializer.deserialize(bytes6));
        Assertions.assertEquals("Unexpected end of stream", exception6.getMessage());

        final String content7 = "HttpClient CacheEntry 1\n";
        final byte[] bytes7 = content7.getBytes(StandardCharsets.UTF_8);
        final ResourceIOException exception7 = Assertions.assertThrows(ResourceIOException.class, () ->
                httpCacheEntrySerializer.deserialize(bytes7));
        Assertions.assertEquals("Unexpected cache entry version line", exception7.getMessage());
    }

    /**
     * Deserialize cache entries with a missing mandatory header.
     */
    @Test
    public void testMissingHeaderCacheEntry() throws Exception {
        final String content1 = HttpByteArrayCacheEntrySerializer.HC_CACHE_VERSION_LINE + "\n" +
                "HC-Key: unique-cache-key\n" +
                "HC-Resource-Length: 11\n" +
                "HC-Response-Instant: 1686210849596\n" +
                "\n" +
                "GET /stuff HTTP/1.1\n" +
                "\n" +
                "HTTP/1.1 200 \n" +
                "Content-Type: text/plain; charset=UTF-8\n" +
                "Cache-control: public, max-age=31536000\n" +
                "\n" +
                "Hello World";
        final byte[] bytes1 = content1.getBytes(StandardCharsets.UTF_8);
        final ResourceIOException exception1 = Assertions.assertThrows(ResourceIOException.class, () ->
                httpCacheEntrySerializer.deserialize(bytes1));
        Assertions.assertEquals("Invalid cache header format", exception1.getMessage());

        final String content2 = HttpByteArrayCacheEntrySerializer.HC_CACHE_VERSION_LINE + "\n" +
                "HC-Key: unique-cache-key\n" +
                "HC-Resource-Length: 11\n" +
                "HC-Request-Instant: 1686210849596\n" +
                "\n" +
                "GET /stuff HTTP/1.1\n" +
                "\n" +
                "HTTP/1.1 200 \n" +
                "Content-Type: text/plain; charset=UTF-8\n" +
                "Cache-control: public, max-age=31536000\n" +
                "\n" +
                "Hello World";
        final byte[] bytes2 = content2.getBytes(StandardCharsets.UTF_8);
        final ResourceIOException exception2 = Assertions.assertThrows(ResourceIOException.class, () ->
                httpCacheEntrySerializer.deserialize(bytes2));
        Assertions.assertEquals("Invalid cache header format", exception2.getMessage());
    }

    /**
     * Deserialize cache entries with an invalid header value.
     */
    @Test
    public void testInvalidHeaderCacheEntry() throws Exception {
        final String content1 = HttpByteArrayCacheEntrySerializer.HC_CACHE_VERSION_LINE + "\n" +
                "HC-Key: unique-cache-key\n" +
                "HC-Resource-Length: 11\n" +
                "HC-Request-Instant: boom\n" +
                "HC-Response-Instant: 1686210849596\n" +
                "\n" +
                "GET /stuff HTTP/1.1\n" +
                "\n" +
                "HTTP/1.1 200 \n" +
                "Content-Type: text/plain; charset=UTF-8\n" +
                "Cache-control: public, max-age=31536000\n" +
                "\n" +
                "Hello World";
        final byte[] bytes1 = content1.getBytes(StandardCharsets.UTF_8);
        final ResourceIOException exception1 = Assertions.assertThrows(ResourceIOException.class, () ->
                httpCacheEntrySerializer.deserialize(bytes1));
        Assertions.assertEquals("Invalid cache header format", exception1.getMessage());
        final String content2 = HttpByteArrayCacheEntrySerializer.HC_CACHE_VERSION_LINE + "\n" +
                "HC-Key: unique-cache-key\n" +
                "HC-Resource-Length: 11\n" +
                "HC-Request-Instant: 1686210849596\n" +
                "HC-Response-Instant: boom\n" +
                "\n" +
                "GET /stuff HTTP/1.1\n" +
                "\n" +
                "HTTP/1.1 200 \n" +
                "Content-Type: text/plain; charset=UTF-8\n" +
                "Cache-control: public, max-age=31536000\n" +
                "\n" +
                "Hello World";
        final byte[] bytes2 = content1.getBytes(StandardCharsets.UTF_8);
        final ResourceIOException exception2 = Assertions.assertThrows(ResourceIOException.class, () ->
                httpCacheEntrySerializer.deserialize(bytes2));
        Assertions.assertEquals("Invalid cache header format", exception2.getMessage());
    }

    /**
     * Deserialize cache entries with an invalid request line.
     */
    @Test
    public void testInvalidRequestLineCacheEntry() throws Exception {
        final String content1 = HttpByteArrayCacheEntrySerializer.HC_CACHE_VERSION_LINE + "\n" +
                "HC-Key: unique-cache-key\n" +
                "HC-Resource-Length: 11\n" +
                "HC-Request-Instant: 1686210849596\n" +
                "HC-Response-Instant: 1686210849596\n" +
                "\n" +
                "GET boom\n" +
                "\n" +
                "HTTP/1.1 200 \n" +
                "Content-Type: text/plain; charset=UTF-8\n" +
                "Cache-control: public, max-age=31536000\n" +
                "\n" +
                "Hello World";
        final byte[] bytes1 = content1.getBytes(StandardCharsets.UTF_8);
        final ResourceIOException exception1 = Assertions.assertThrows(ResourceIOException.class, () ->
                httpCacheEntrySerializer.deserialize(bytes1));
        Assertions.assertEquals("Invalid cache header format", exception1.getMessage());
    }

    /**
     * Deserialize cache entries with an invalid request line.
     */
    @Test
    public void testInvalidStatusLineCacheEntry() throws Exception {
        final String content1 = HttpByteArrayCacheEntrySerializer.HC_CACHE_VERSION_LINE + "\n" +
                "HC-Key: unique-cache-key\n" +
                "HC-Resource-Length: 11\n" +
                "HC-Request-Instant: 1686210849596\n" +
                "HC-Response-Instant: 1686210849596\n" +
                "\n" +
                "GET /stuff HTTP/1.1\n" +
                "\n" +
                "HTTP/1.1 boom \n" +
                "Content-Type: text/plain; charset=UTF-8\n" +
                "Cache-control: public, max-age=31536000\n" +
                "\n" +
                "Hello World";
        final byte[] bytes1 = content1.getBytes(StandardCharsets.UTF_8);
        final ResourceIOException exception1 = Assertions.assertThrows(ResourceIOException.class, () ->
                httpCacheEntrySerializer.deserialize(bytes1));
        Assertions.assertEquals("Invalid cache header format", exception1.getMessage());
    }

    /**
     * Serialize and deserialize a cache entry with no headers.
     */
    @Test
    public void noHeadersTest() throws Exception {
        final String content = "Hello World";
        final ContentType contentType = ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8);
        final HttpCacheEntry cacheEntry = new HttpCacheEntry(Instant.now(), Instant.now(),
                "GET", "/stuff", HttpTestUtils.headers(),
                HttpStatus.SC_OK, HttpTestUtils.headers(),
                new HeapResource(content.getBytes(contentType.getCharset())),
                null);
        final HttpCacheStorageEntry storageEntry = new HttpCacheStorageEntry("unique-cache-key", cacheEntry);
        final byte[] serialized = httpCacheEntrySerializer.serialize(storageEntry);

        final HttpCacheStorageEntry deserialized = httpCacheEntrySerializer.deserialize(serialized);
        MatcherAssert.assertThat(deserialized.getKey(), Matchers.equalTo(storageEntry.getKey()));
        MatcherAssert.assertThat(deserialized.getContent(), HttpCacheEntryMatcher.equivalent(storageEntry.getContent()));
    }

    /**
     * Serialize and deserialize a cache entry with an empty body.
     */
    @Test
    public void emptyBodyTest() throws Exception {
        final HttpCacheEntry cacheEntry = new HttpCacheEntry(Instant.now(), Instant.now(),
                "GET", "/stuff", HttpTestUtils.headers(),
                HttpStatus.SC_OK, HttpTestUtils.headers(),
                new HeapResource(new byte[] {}),
                null);
        final HttpCacheStorageEntry storageEntry = new HttpCacheStorageEntry("unique-cache-key", cacheEntry);
        final byte[] serialized = httpCacheEntrySerializer.serialize(storageEntry);

        final HttpCacheStorageEntry deserialized = httpCacheEntrySerializer.deserialize(serialized);
        MatcherAssert.assertThat(deserialized.getKey(), Matchers.equalTo(storageEntry.getKey()));
        MatcherAssert.assertThat(deserialized.getContent(), HttpCacheEntryMatcher.equivalent(storageEntry.getContent()));
    }

    /**
     * Serialize and deserialize a cache entry with no body.
     */
    @Test
    public void noBodyTest() throws Exception {
        final HttpCacheEntry cacheEntry = new HttpCacheEntry(Instant.now(), Instant.now(),
                "GET", "/stuff", HttpTestUtils.headers(),
                HttpStatus.SC_OK, HttpTestUtils.headers(),
                null,
                null);
        final HttpCacheStorageEntry storageEntry = new HttpCacheStorageEntry("unique-cache-key", cacheEntry);
        final byte[] serialized = httpCacheEntrySerializer.serialize(storageEntry);

        final HttpCacheStorageEntry deserialized = httpCacheEntrySerializer.deserialize(serialized);
        MatcherAssert.assertThat(deserialized.getKey(), Matchers.equalTo(storageEntry.getKey()));
        MatcherAssert.assertThat(deserialized.getContent(), HttpCacheEntryMatcher.equivalent(storageEntry.getContent()));
    }

    /**
     * Serialize and deserialize a cache entry with a variant map.
     */
    @Test
    public void testSimpleVariantMap() throws Exception {
        final String content = "Hello World";
        final ContentType contentType = ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8);
        final Set<String> variants = new HashSet<>();
        variants.add("{Accept-Encoding=gzip}");
        variants.add("{Accept-Encoding=compress}");
        final HttpCacheEntry cacheEntry = new HttpCacheEntry(Instant.now(), Instant.now(),
                "GET", "/stuff", HttpTestUtils.headers(),
                HttpStatus.SC_OK, HttpTestUtils.headers(new BasicHeader(HttpHeaders.CONTENT_TYPE, contentType.toString())),
                new HeapResource(content.getBytes(contentType.getCharset())),
                variants);
        final HttpCacheStorageEntry storageEntry = new HttpCacheStorageEntry("unique-cache-key", cacheEntry);
        final byte[] serialized = httpCacheEntrySerializer.serialize(storageEntry);

        final HttpCacheStorageEntry deserialized = httpCacheEntrySerializer.deserialize(serialized);
        MatcherAssert.assertThat(deserialized.getKey(), Matchers.equalTo(storageEntry.getKey()));
        MatcherAssert.assertThat(deserialized.getContent(), HttpCacheEntryMatcher.equivalent(storageEntry.getContent()));
    }

    /**
     * Deserialize cache entries with trailing garbage.
     */
    @Test
    public void testDeserializeCacheEntryWithTrailingGarbage() throws Exception {
        final String content1 =HttpByteArrayCacheEntrySerializer.HC_CACHE_VERSION_LINE + "\n" +
                "HC-Key: unique-cache-key\n" +
                        "HC-Resource-Length: 11\n" +
                        "HC-Request-Instant: 1686210849596\n" +
                        "HC-Response-Instant: 1686210849596\n" +
                        "\n" +
                        "GET /stuff HTTP/1.1\n" +
                        "\n" +
                        "HTTP/1.1 200 \n" +
                        "Content-Type: text/plain; charset=UTF-8\n" +
                        "Cache-control: public, max-age=31536000\n" +
                        "\n" +
                        "Hello World..... Rubbish";
        final byte[] bytes1 = content1.getBytes(StandardCharsets.UTF_8);
        final ResourceIOException exception1 = Assertions.assertThrows(ResourceIOException.class, () ->
                httpCacheEntrySerializer.deserialize(bytes1));
        Assertions.assertEquals("Unexpected content at the end of cache content", exception1.getMessage());

        final String content2 =HttpByteArrayCacheEntrySerializer.HC_CACHE_VERSION_LINE + "\n" +
                "HC-Key: unique-cache-key\n" +
                        "HC-Request-Instant: 1686210849596\n" +
                        "HC-Response-Instant: 1686210849596\n" +
                        "\n" +
                        "GET /stuff HTTP/1.1\n" +
                        "\n" +
                        "HTTP/1.1 200 \n" +
                        "Content-Type: text/plain; charset=UTF-8\n" +
                        "Cache-control: public, max-age=31536000\n" +
                        "\n" +
                        "Rubbish";
        final byte[] bytes2 = content2.getBytes(StandardCharsets.UTF_8);
        final ResourceIOException exception2 = Assertions.assertThrows(ResourceIOException.class, () ->
                httpCacheEntrySerializer.deserialize(bytes2));
        Assertions.assertEquals("Unexpected content at the end of cache content", exception1.getMessage());
    }

    static HeapResource load(final URL resource) throws IOException {
        try (final InputStream in = resource.openStream()) {
            final ByteArrayBuffer buf = new ByteArrayBuffer(1024);
            final byte[] tmp = new byte[2048];
            int len;
            while ((len = in.read(tmp)) != -1) {
                buf.append(tmp, 0, len);
            }
            return new HeapResource(buf.toByteArray());
        }
    }

}
