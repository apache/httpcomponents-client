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
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.cache.HttpCacheEntrySerializer;
import org.apache.hc.client5.http.cache.HttpCacheStorageEntry;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.impl.io.AbstractMessageParser;
import org.apache.hc.core5.http.impl.io.AbstractMessageWriter;
import org.apache.hc.core5.http.io.SessionInputBuffer;
import org.apache.hc.core5.http.io.SessionOutputBuffer;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.apache.hc.client5.http.impl.cache.MemcachedCacheEntryHttpTestUtils.buildSimpleTestObjectFromTemplate;
import static org.apache.hc.client5.http.impl.cache.MemcachedCacheEntryHttpTestUtils.makeTestFileObject;
import static org.apache.hc.client5.http.impl.cache.MemcachedCacheEntryHttpTestUtils.memcachedCacheEntryFromBytes;
import static org.apache.hc.client5.http.impl.cache.MemcachedCacheEntryHttpTestUtils.readTestFileBytes;
import static org.apache.hc.client5.http.impl.cache.MemcachedCacheEntryHttpTestUtils.testWithCache;
import static org.apache.hc.client5.http.impl.cache.MemcachedCacheEntryHttpTestUtils.verifyHttpCacheEntryFromTestFile;

public class TestMemcachedCacheEntryHttp {
    private static final String FILE_TEST_SERIALIZED_NAME = "ApacheLogo.serialized";
    private static final String SIMPLE_OBJECT_SERIALIZED_NAME = "simpleObject.serialized";
    private static final String VARIANTMAP_TEST_SERIALIZED_NAME = "variantMap.serialized";
    private static final String ESCAPED_HEADER_TEST_SERIALIZED_NAME = "escapedHeader.serialized";
    private static final String NO_BODY_TEST_SERIALIZED_NAME = "noBody.serialized";
    private static final String MISSING_HEADER_TEST_SERIALIZED_NAME = "missingHeader.serialized";
    private static final String INVALID_HEADER_TEST_SERIALIZED_NAME = "invalidHeader.serialized";
    private static final String VARIANTMAP_MISSING_KEY_TEST_SERIALIZED_NAME = "variantMapMissingKey.serialized";
    private static final String VARIANTMAP_MISSING_VALUE_TEST_SERIALIZED_NAME = "variantMapMissingValue.serialized";

    private static final String TEST_CONTENT_FILE_NAME = "ApacheLogo.png";

    private HttpCacheEntrySerializer<byte[]> serializer;

    // Manually set this to true to re-generate all of the .serialized files
    private final boolean reserializeFiles = false;

    @Before
    public void before() {
        serializer = MemcachedCacheEntryHttp.INSTANCE;
    }

    /**
     * Serialize and deserialize a simple object with a tiny body.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void simpleObjectTest() throws Exception {
        final HttpCacheStorageEntry testEntry = buildSimpleTestObjectFromTemplate(Collections.<String, Object>emptyMap());

        testWithCache(testEntry, serializer);
    }

    /**
     * Serialize and deserialize a larger object with a binary file for a body.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void fileObjectTest() throws Exception {
        final Map<String, Object> cacheObjectValues = new HashMap<String, Object>();
        cacheObjectValues.put("resource", new FileResource(makeTestFileObject(TEST_CONTENT_FILE_NAME)));
        final HttpCacheStorageEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);

        testWithCache(testEntry, serializer);
    }

    @Test
    public void noHeadersTest() throws Exception {
        final Map<String, Object> cacheObjectValues = new HashMap<String, Object>();
        cacheObjectValues.put("headers", new Header[0]);
        final HttpCacheStorageEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);

        testWithCache(testEntry, serializer);
    }

    @Test
    public void contentLengthTest() throws Exception {
        final Map<String, Object> cacheObjectValues = new HashMap<String, Object>();
        cacheObjectValues.put("headers", new Header[] {
                new BasicHeader("Content-Length", "999"),
        });
        final HttpCacheStorageEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);

        testWithCache(testEntry, serializer);
    }

    @Test
    public void emptyBodyTest() throws Exception {
        final Map<String, Object> cacheObjectValues = new HashMap<String, Object>();
        cacheObjectValues.put("resource", new HeapResource(new byte[0]));
        final HttpCacheStorageEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);

        testWithCache(testEntry, serializer);
    }

    @Test
    public void noBodyTest() throws Exception {
        final Map<String, Object> cacheObjectValues = new HashMap<String, Object>();
        cacheObjectValues.put("resource", null);
        cacheObjectValues.put("responseCode", 204);
//        cacheObjectValues.put("statusLine", new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1),
//                204, "No Content"));

        final HttpCacheStorageEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);

        testWithCache(testEntry, serializer);
    }

    @Test
    public void testSimpleVariantMap() throws Exception {
        final Map<String, Object> cacheObjectValues = new HashMap<String, Object>();
        final Map<String, String> variantMap = new HashMap<String, String>();
        variantMap.put("{Accept-Encoding=gzip}","{Accept-Encoding=gzip}https://example.com:1234/foo");
        variantMap.put("{Accept-Encoding=compress}","{Accept-Encoding=compress}https://example.com:1234/foo");
        cacheObjectValues.put("variantMap", variantMap);
        final HttpCacheStorageEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);

        testWithCache(testEntry, serializer);
    }

    /**
     * Ensures that if the server uses our reserved header names we don't mix them up with our own pseudo-headers.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testEscapedHeaders() throws Exception {
        final Map<String, Object> cacheObjectValues = new HashMap<String, Object>();
        cacheObjectValues.put("headers", new Header[] {
                new BasicHeader("hc-test-1", "hc-test-1-value"),
                new BasicHeader("hc-sk", "hc-sk-value"),
                new BasicHeader("hc-resp-date", "hc-resp-date-value"),
                new BasicHeader("hc-req-date-date", "hc-req-date-value"),
                new BasicHeader("hc-varmap-key", "hc-varmap-key-value"),
                new BasicHeader("hc-varmap-val", "hc-varmap-val-value"),
        });
        final HttpCacheStorageEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);

        testWithCache(testEntry, serializer);
    }

    @Test(expected = IllegalStateException.class)
    public void testNullStorageKey() throws Exception {
        final Map<String, Object> cacheObjectValues = new HashMap<String, Object>();
        cacheObjectValues.put("storageKey", null);

        final HttpCacheStorageEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);
        serializer.serialize(testEntry);
    }

    /**
     * Deserialize a simple object from a previously saved file.
     *
     * Ensures that if the serialization format changes in an incompatible way, we'll find out in a test.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void simpleTestFromPreviouslySerialized() throws Exception {
        final HttpCacheStorageEntry testEntry = buildSimpleTestObjectFromTemplate(Collections.<String, Object>emptyMap());

        verifyHttpCacheEntryFromTestFile(serializer, testEntry, SIMPLE_OBJECT_SERIALIZED_NAME, reserializeFiles);
    }

    /**
     * Deserialize a larger object with a binary body from a previously saved file.
     *
     * Ensures that if the serialization format changes in an incompatible way, we'll find out in a test.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void fileTestFromPreviouslySerialized() throws Exception {
        final Map<String, Object> cacheObjectValues = new HashMap<String, Object>();
        cacheObjectValues.put("resource", new FileResource(makeTestFileObject(TEST_CONTENT_FILE_NAME)));
        final HttpCacheStorageEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);

        verifyHttpCacheEntryFromTestFile(serializer, testEntry, FILE_TEST_SERIALIZED_NAME, reserializeFiles);
    }

    // TODO: Add explanations for these tests
    @Test
    public void variantMapTestFromPreviouslySerialized() throws Exception {
        final Map<String, Object> cacheObjectValues = new HashMap<String, Object>();
        final Map<String, String> variantMap = new HashMap<String, String>();
        variantMap.put("{Accept-Encoding=gzip}","{Accept-Encoding=gzip}https://example.com:1234/foo");
        variantMap.put("{Accept-Encoding=compress}","{Accept-Encoding=compress}https://example.com:1234/foo");
        cacheObjectValues.put("variantMap", variantMap);
        final HttpCacheStorageEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);

        verifyHttpCacheEntryFromTestFile(serializer, testEntry, VARIANTMAP_TEST_SERIALIZED_NAME, reserializeFiles);
    }

    @Test
    public void escapedHeaderTestFromPreviouslySerialized() throws Exception {
        final Map<String, Object> cacheObjectValues = new HashMap<String, Object>();
        cacheObjectValues.put("headers", new Header[] {
                new BasicHeader("hc-test-1", "hc-test-1-value"),
                new BasicHeader("hc-sk", "hc-sk-value"),
                new BasicHeader("hc-resp-date", "hc-resp-date-value"),
                new BasicHeader("hc-req-date-date", "hc-req-date-value"),
                new BasicHeader("hc-varmap-key", "hc-varmap-key-value"),
                new BasicHeader("hc-varmap-val", "hc-varmap-val-value"),
        });
        final HttpCacheStorageEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);

        verifyHttpCacheEntryFromTestFile(serializer, testEntry, ESCAPED_HEADER_TEST_SERIALIZED_NAME, reserializeFiles);
    }

    @Test
    public void noBodyTestFromPreviouslySerialized() throws Exception {
        final Map<String, Object> cacheObjectValues = new HashMap<String, Object>();
        cacheObjectValues.put("resource", null);
        cacheObjectValues.put("responseCode", 204);
//        cacheObjectValues.put("statusLine", new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1),
//                204, "No Content"));

        final HttpCacheStorageEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);

        verifyHttpCacheEntryFromTestFile(serializer, testEntry, NO_BODY_TEST_SERIALIZED_NAME, reserializeFiles);
    }

    @Test(expected = ResourceIOException.class)
    public void testInvalidCacheEntry() throws Exception {
        // This file is a JPEG not a cache entry, so should fail to deserialize
        final byte[] bytes = readTestFileBytes(TEST_CONTENT_FILE_NAME);
        memcachedCacheEntryFromBytes(serializer, bytes);
    }

    @Test(expected = ResourceIOException.class)
    public void testMissingHeaderCacheEntry() throws Exception {
        // This file hand-edited to be missing a necessary header
        final byte[] bytes = readTestFileBytes(MISSING_HEADER_TEST_SERIALIZED_NAME);
        memcachedCacheEntryFromBytes(serializer, bytes);
    }

    @Test(expected = ResourceIOException.class)
    public void testInvalidHeaderCacheEntry() throws Exception {
        // This file hand-edited to have an invalid header
        final byte[] bytes = readTestFileBytes(INVALID_HEADER_TEST_SERIALIZED_NAME);
        memcachedCacheEntryFromBytes(serializer, bytes);
    }

    @Test(expected = ResourceIOException.class)
    public void testVariantMapMissingKeyCacheEntry() throws Exception {
        // This file hand-edited to be missing a VariantCache key
        final byte[] bytes = readTestFileBytes(VARIANTMAP_MISSING_KEY_TEST_SERIALIZED_NAME);
        memcachedCacheEntryFromBytes(serializer, bytes);
    }

    @Test(expected = ResourceIOException.class)
    public void testVariantMapMissingValueCacheEntry() throws Exception {
        // This file hand-edited to be missing a VariantCache value
        final byte[] bytes = readTestFileBytes(VARIANTMAP_MISSING_VALUE_TEST_SERIALIZED_NAME);
        memcachedCacheEntryFromBytes(serializer, bytes);
    }

    /**
     * Test an HttpException being thrown while serializing.
     *
     * @throws Exception expected
     */
    @Test(expected = ResourceIOException.class)
    public void testSerializeWithHTTPException() throws Exception {
        final AbstractMessageWriter<SimpleHttpResponse> throwyHttpWriter = Mockito.mock(AbstractMessageWriter.class);
        Mockito.
                doThrow(new HttpException("Test Exception")).
                when(throwyHttpWriter).
                write(Mockito.any(SimpleHttpResponse.class), Mockito.any(SessionOutputBuffer.class), Mockito.any(OutputStream.class));

        final Map<String, Object> cacheObjectValues = new HashMap<String, Object>();
        final HttpCacheStorageEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);

        final MemcachedCacheEntryHttp testMemcachedEntry = new MemcachedCacheEntryHttp() {
            protected AbstractMessageWriter<SimpleHttpResponse> makeHttpResponseWriter(final SessionOutputBuffer outputBuffer) {
                return throwyHttpWriter;
            }
        };
        testMemcachedEntry.serialize(testEntry);
    }

    /**
     * Test an IOException being thrown while deserializing.
     *
     * @throws Exception expected
     */
    @Test(expected = ResourceIOException.class)
    public void testDeserializeWithIOException() throws Exception {
        final AbstractMessageParser<ClassicHttpResponse> throwyParser = Mockito.mock(AbstractMessageParser.class);
        Mockito.
                doThrow(new IOException("Test Exception")).
                when(throwyParser).
                parse(Mockito.any(SessionInputBuffer.class), Mockito.any(InputStream.class));

        final MemcachedCacheEntryHttp testMemcachedEntry = new MemcachedCacheEntryHttp() {
            @Override
            protected AbstractMessageParser<ClassicHttpResponse> makeHttpResponseParser() {
                return throwyParser;
            }
        };
        testMemcachedEntry.deserialize(new byte[0]);
    }

    // TODO: This test currently breaks the HttpMessageParser.
    // It seems to make bad assumptions about read(), so fails when it returns
    // fewer bytes than it assumes.
    /**
     * Test edge cases in read lengths.
     *
     * This basically tests the copyBytes() helper method used by deserialize.
     * The SessionInputBuffer read method can return any number of bytes, and copyBytes
     * has a while loop to handle all of these cases.  The other tests will only return
     * the entire buffer at once, so don't test the while loop at all.  This test mocks
     * the underlying InputStream read method to return 1 byte at a time, which should
     * exercise all of the edge cases in that method.
     *
     * @throws Exception if anything goes wrong
     */
//    @Test
//    public void testDeserializeWithSlowReader() throws Exception {
//        final byte[] bytes = readTestFileBytes(TEST_CONTENT_FILE_NAME);
//
//        final MemcachedCacheEntryHttp testMemcachedEntry = new MemcachedCacheEntryHttp() {
//            @Override
//            protected InputStream makeByteArrayInputStream(byte[] bytes) {
//                return makeMockSlowReadInputStream(bytes, 1);
//            }
//        };
//
//        final Map<String, Object> cacheObjectValues = new HashMap<String, Object>();
//        cacheObjectValues.put("resource", new FileResource(makeTestFileObject(TEST_CONTENT_FILE_NAME)));
//        final HttpCacheStorageEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);
//
//        verifyHttpCacheEntryFromBytes(testEntry, testMemcachedEntry, bytes);
//    }
}
