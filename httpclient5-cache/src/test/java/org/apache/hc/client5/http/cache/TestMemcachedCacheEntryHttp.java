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

package org.apache.hc.client5.http.cache;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.hc.client5.http.impl.cache.FileResource;
import org.apache.hc.client5.http.impl.cache.HeapResource;
import org.apache.hc.client5.http.impl.cache.MemcachedCacheEntryHttp;
import org.apache.hc.client5.http.impl.cache.MemcachedCacheEntryHttpException;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.impl.io.AbstractMessageParser;
import org.apache.hc.core5.http.io.SessionInputBuffer;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.apache.hc.client5.http.cache.MemcachedCacheEntryHttpTestUtils.buildSimpleTestObjectFromTemplate;
import static org.apache.hc.client5.http.cache.MemcachedCacheEntryHttpTestUtils.makeMockSlowReadResource;
import static org.apache.hc.client5.http.cache.MemcachedCacheEntryHttpTestUtils.makeTestFileObject;
import static org.apache.hc.client5.http.cache.MemcachedCacheEntryHttpTestUtils.memcachedCacheEntryFromBytes;
import static org.apache.hc.client5.http.cache.MemcachedCacheEntryHttpTestUtils.readFully;
import static org.apache.hc.client5.http.cache.MemcachedCacheEntryHttpTestUtils.readTestFileBytes;
import static org.apache.hc.client5.http.cache.MemcachedCacheEntryHttpTestUtils.testWithCache;
import static org.apache.hc.client5.http.cache.MemcachedCacheEntryHttpTestUtils.verifyHttpCacheEntryFromTestFile;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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

    // TODO: Rename, no longer an accurate name
    private HttpCacheEntrySerializer<byte[]> cacheEntryFactory;

    // Manually set this to true to re-generate all of the .serialized files
    private final boolean reserializeFiles = false;

    @Before
    public void before() {
        cacheEntryFactory = MemcachedCacheEntryHttp.INSTANCE;
    }

    /**
     * Serialize and deserialize a simple object with a tiny body.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void simpleObjectTest() throws Exception {
        final HttpCacheStorageEntry testEntry = buildSimpleTestObjectFromTemplate(Collections.<String, Object>emptyMap());

        testWithCache(testEntry, cacheEntryFactory);
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

        testWithCache(testEntry, cacheEntryFactory);
    }

    @Test
    public void noHeadersTest() throws Exception {
        final Map<String, Object> cacheObjectValues = new HashMap<String, Object>();
        cacheObjectValues.put("headers", new Header[0]);
        final HttpCacheStorageEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);

        testWithCache(testEntry, cacheEntryFactory);
    }

    @Test
    public void contentLengthTest() throws Exception {
        final Map<String, Object> cacheObjectValues = new HashMap<String, Object>();
        cacheObjectValues.put("headers", new Header[] {
                new BasicHeader("Content-Length", "999"),
        });
        final HttpCacheStorageEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);

        testWithCache(testEntry, cacheEntryFactory);
    }

    @Test
    public void emptyBodyTest() throws Exception {
        final Map<String, Object> cacheObjectValues = new HashMap<String, Object>();
        cacheObjectValues.put("resource", new HeapResource(new byte[0]));
        final HttpCacheStorageEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);

        testWithCache(testEntry, cacheEntryFactory);
    }

    @Test
    public void noBodyTest() throws Exception {
        final Map<String, Object> cacheObjectValues = new HashMap<String, Object>();
        cacheObjectValues.put("resource", null);
        cacheObjectValues.put("statusCode", 204);
//        cacheObjectValues.put("statusLine", new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1),
//                204, "No Content"));

        final HttpCacheStorageEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);

        testWithCache(testEntry, cacheEntryFactory);
    }

    @Test
    public void testSimpleVariantMap() throws Exception {
        final Map<String, Object> cacheObjectValues = new HashMap<String, Object>();
        final Map<String, String> variantMap = new HashMap<String, String>();
        variantMap.put("{Accept-Encoding=gzip}","{Accept-Encoding=gzip}https://example.com:1234/foo");
        variantMap.put("{Accept-Encoding=compress}","{Accept-Encoding=compress}https://example.com:1234/foo");
        cacheObjectValues.put("variantMap", variantMap);
        final HttpCacheStorageEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);

        testWithCache(testEntry, cacheEntryFactory);
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

        testWithCache(testEntry, cacheEntryFactory);
    }

    @Test(expected = IllegalStateException.class)
    public void testNullStorageKey() throws Exception {
        final Map<String, Object> cacheObjectValues = new HashMap<String, Object>();
        cacheObjectValues.put("storageKey", null);

        final HttpCacheStorageEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);
        cacheEntryFactory.serialize(testEntry);
    }

    // No test for request method, HttpCacheEntry does not seem to do anything with it
    // (parameter is unused in constructor)

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

        verifyHttpCacheEntryFromTestFile(MemcachedCacheEntryHttpTestUtils.TEST_STORAGE_KEY, testEntry, cacheEntryFactory, SIMPLE_OBJECT_SERIALIZED_NAME, reserializeFiles);
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

        verifyHttpCacheEntryFromTestFile(MemcachedCacheEntryHttpTestUtils.TEST_STORAGE_KEY, testEntry, cacheEntryFactory, FILE_TEST_SERIALIZED_NAME, reserializeFiles);
    }

    @Test
    public void variantMapTestFromPreviouslySerialized() throws Exception {
        final Map<String, Object> cacheObjectValues = new HashMap<String, Object>();
        final Map<String, String> variantMap = new HashMap<String, String>();
        variantMap.put("{Accept-Encoding=gzip}","{Accept-Encoding=gzip}https://example.com:1234/foo");
        variantMap.put("{Accept-Encoding=compress}","{Accept-Encoding=compress}https://example.com:1234/foo");
        cacheObjectValues.put("variantMap", variantMap);
        final HttpCacheStorageEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);

        verifyHttpCacheEntryFromTestFile(MemcachedCacheEntryHttpTestUtils.TEST_STORAGE_KEY, testEntry, cacheEntryFactory, VARIANTMAP_TEST_SERIALIZED_NAME, reserializeFiles);
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

        verifyHttpCacheEntryFromTestFile(MemcachedCacheEntryHttpTestUtils.TEST_STORAGE_KEY, testEntry, cacheEntryFactory, ESCAPED_HEADER_TEST_SERIALIZED_NAME, reserializeFiles);
    }

    @Test
    public void noBodyTestFromPreviouslySerialized() throws Exception {
        final Map<String, Object> cacheObjectValues = new HashMap<String, Object>();
        cacheObjectValues.put("resource", null);
        cacheObjectValues.put("statusCode", 204);
//        cacheObjectValues.put("statusLine", new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1),
//                204, "No Content"));

        final HttpCacheStorageEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);

        verifyHttpCacheEntryFromTestFile(MemcachedCacheEntryHttpTestUtils.TEST_STORAGE_KEY, testEntry, cacheEntryFactory, NO_BODY_TEST_SERIALIZED_NAME, reserializeFiles);
    }

    @Test(expected = ResourceIOException.class)
    public void testInvalidCacheEntry() throws Exception {
        // This file is a JPEG not a cache entry
        final byte[] bytes = readTestFileBytes(TEST_CONTENT_FILE_NAME);
        memcachedCacheEntryFromBytes(cacheEntryFactory, bytes);
    }

    @Test(expected = MemcachedCacheEntryHttpException.class)
    public void testMissingHeaderCacheEntry() throws Exception {
        // This file is a JPEG not a cache entry
        final byte[] bytes = readTestFileBytes(MISSING_HEADER_TEST_SERIALIZED_NAME);
        memcachedCacheEntryFromBytes(cacheEntryFactory, bytes);
    }

    @Test(expected = MemcachedCacheEntryHttpException.class)
    public void testInvalidHeaderCacheEntry() throws Exception {
        // This file is a JPEG not a cache entry
        final byte[] bytes = readTestFileBytes(INVALID_HEADER_TEST_SERIALIZED_NAME);
        memcachedCacheEntryFromBytes(cacheEntryFactory, bytes);
    }

    @Test(expected = MemcachedCacheEntryHttpException.class)
    public void testVariantMapMissingKeyCacheEntry() throws Exception {
        // This file is a JPEG not a cache entry
        final byte[] bytes = readTestFileBytes(VARIANTMAP_MISSING_KEY_TEST_SERIALIZED_NAME);
        memcachedCacheEntryFromBytes(cacheEntryFactory, bytes);
    }

    @Test(expected = MemcachedCacheEntryHttpException.class)
    public void testVariantMapMissingValueCacheEntry() throws Exception {
        // This file is a JPEG not a cache entry
        final byte[] bytes = readTestFileBytes(VARIANTMAP_MISSING_VALUE_TEST_SERIALIZED_NAME);
        memcachedCacheEntryFromBytes(cacheEntryFactory, bytes);
    }

    /**
     * Test serializing an object where the stream reader returns just one byte at a time.
     *
     * This exercises the while loop used for copying from the input stream to a memory buffer, which can in theory
     * return only some bytes, but in other tests always returns the whole buffer at once.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testSerializeReadWithShortReads() throws Exception {
        // TODO: Not sure if this really tests anything anymore!
        final String testString = "Short Hello";
        final Resource mockResource = makeMockSlowReadResource(testString, 1);

        final Map<String, Object> cacheObjectValues = new HashMap<>();
        cacheObjectValues.put("resource", mockResource);
        final HttpCacheStorageEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);

        final byte[] testBytes = cacheEntryFactory.serialize(testEntry);

        final HttpCacheStorageEntry verifyMemcachedCacheEntryFromBytes = memcachedCacheEntryFromBytes(cacheEntryFactory, testBytes);
        final byte[] verifyBytes = readFully(testEntry.getContent().getResource().getInputStream(),
                (int)verifyMemcachedCacheEntryFromBytes.getContent().getResource().length());

        assertEquals(testString, new String(verifyBytes, "UTF-8"));
    }

    // TODO: Are we sure we don't need this?
//    /**
//     * Test an error case where the resource length is greater than than the actual file size while serializing.
//     */
//    @Test(expected = MemcachedCacheEntryHttpException.class)
//    public void testSerializeStreamEndsTooEarly() throws Exception {
//        final String testString = "Short Hello";
//        final Resource mockResource = makeMockSlowReadResource(testString, 1);
//        // Return 1 byte too many
//        Mockito.when(mockResource.length()).thenReturn(Long.valueOf(testString.length() + 1));
//
//        final Map<String, Object> cacheObjectValues = new HashMap<String, Object>();
//        cacheObjectValues.put("resource", mockResource);
//        final HttpCacheStorageEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);
//
//        cacheEntryFactory.serialize(testEntry);
//    }

    // TODO: Do we still need this test?
//    /**
//     * Test an error case where the length of the object to serialize is too large to fit in a 32-bit address space.
//     */
//    @Test(expected = MemcachedCacheEntryHttpException.class)
//    public void testSerializeTooManyBytes() throws Exception {
//        final String testString = "Short Hello";
//        final Resource mockResource = makeMockSlowReadResource(testString, 1);
//        // Return way too many bytes byte too many
//        Mockito.when(mockResource.length()).thenReturn(Integer.MAX_VALUE + 1L);
//
//        final Map<String, Object> cacheObjectValues = new HashMap<String, Object>();
//        cacheObjectValues.put("resource", mockResource);
//        final HttpCacheStorageEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);
//
//        cacheEntryFactory.serialize(testEntry);
//    }

    // TODO: Do we still need this test?
//    /**
//     * Test an HttpException being thrown while serializing.
//     *
//     * @throws Exception expected
//     */
//    @Test(expected = ResourceIOException.class)
//    public void testSerializeWithHTTPException() throws Exception {
//        final AbstractMessageWriter<HttpResponse> throwyHttpWriter = Mockito.mock(AbstractMessageWriter.class);
//        Mockito.
//                doThrow(new HttpException("Test Exception")).
//                when(throwyHttpWriter).
//                write(Mockito.any(HttpResponse.class), Mockito.any(SessionOutputBuffer.class), Mockito.any(OutputStream.class));
//
//        final Map<String, Object> cacheObjectValues = new HashMap<String, Object>();
//        final HttpCacheStorageEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);
//
//        // TODO: Not sure this test is right anymore, need to see how to inject that throwy writer
////        final MemcachedCacheEntryHttp testMemcachedEntry = new MemcachedCacheEntryHttp(MemcachedCacheEntryHttpTestUtils.TEST_STORAGE_KEY, testEntry) {
////            protected AbstractMessageWriter<HttpResponse> makeHttpResponseWriter(final SessionOutputBuffer outputBuffer) {
////                return throwyHttpWriter;
////            }
////        };
//        cacheEntryFactory.serialize(testEntry);
//    }


    /**
     * Test an IOException being thrown while serializing.
     *
     * @throws Exception expected
     */
    @Test(expected = MemcachedCacheEntryHttpException.class)
    public void testSerializeIOException() throws Exception {
        final InputStream throwyInputStream = Mockito.mock(InputStream.class);
        Mockito.
                doThrow(new IOException("Test Exception")).
                when(throwyInputStream).
                read(Mockito.<byte[]>any(), Mockito.anyInt(), Mockito.anyInt());
        final Resource mockResource = Mockito.mock(Resource.class);
        Mockito.when(mockResource.length()).thenReturn(Long.valueOf(10));
        Mockito.when(mockResource.getInputStream()).thenReturn(throwyInputStream);

        final Map<String, Object> cacheObjectValues = new HashMap<String, Object>();
        cacheObjectValues.put("resource", mockResource);
        final HttpCacheStorageEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);

        cacheEntryFactory.serialize(testEntry);
    }

    // TODO: Do we still need this test?
    /**
     * If the the close method throws an exception and the main body does not, the close method's exception should be
     * thrown.
     *
     * @throws Exception If anything goes wrong
     */
//    @Test
//    public void testSerializeWithCloseIOException() throws Exception {
//        final Throwable testException = new IOException("Close exception");
//
//        final Throwable actualException = MemcachedCacheEntryHttpTestUtils.resourceCloseExceptionOnSerializationTestHelper(cacheEntryFactory, testException);
//        if (!(actualException instanceof MemcachedCacheEntryHttpException)) {
//            throw new AssertionError("Expected exception of type " + MemcachedCacheEntryHttpException.class + " but was this instead", actualException);
//        }
//        if (actualException.getCause() != testException) {
//            throw new AssertionError("Expected exception cause to be " + testException + " but was this instead", actualException.getCause());
//        }
//    }

    // TODO: Are we sure we don't need this?
//    /**
//     * Test special handling in case close method throws a RuntimeException.
//     *
//     * @throws Exception If anything goes wrong
//     */
//    @Test
//    public void testSerializeWithCloseRuntimeException() throws Exception {
//        final Throwable testException = new RuntimeException("Close exception");
//
//        final Throwable actualException = MemcachedCacheEntryHttpTestUtils.resourceCloseExceptionOnSerializationTestHelper(cacheEntryFactory, testException);
//        assertTrue("Expected exception " + actualException + " to be of type " + MemcachedCacheEntryHttpException.class,
//                actualException instanceof MemcachedCacheEntryHttpException);
//        assertSame("Expected exception cause to be the one thrown from close method", testException, actualException.getCause());
//    }

    // TODO: Do we still need this test?
//    /**
//     * Test special handling in case close method throws an Error.
//     *
//     * @throws Exception If anything goes wrong
//     */
//    @Test
//    public void testSerializeWithCloseError() throws Exception {
//        final Throwable testException = new Error("Close error");
//
//        final Throwable actualException = MemcachedCacheEntryHttpTestUtils.resourceCloseExceptionOnSerializationTestHelper(cacheEntryFactory, testException);
//        assertSame("Expected exception to be Error thrown from close method",
//                testException, actualException);
//    }

    // TODO: Do we still need this test?
//    /**
//     * Test special handling in case close method throws something else unexpected.
//     *
//     * @throws Exception If anything goes wrong
//     */
//    @Test
//    public void testSerializeWithCloseThrowable() throws Exception {
//        final Throwable testException = new Throwable("Close error");
//
//        final Throwable actualException = MemcachedCacheEntryHttpTestUtils.resourceCloseExceptionOnSerializationTestHelper(cacheEntryFactory, testException);
//        assertTrue("Expected exception " + actualException + " to be of type " + MemcachedCacheEntryHttpException.class,
//                actualException instanceof MemcachedCacheEntryHttpException);
//        assertTrue("Expected exception cause " + actualException.getCause() + " to be of type " + UndeclaredThrowableException.class,
//                actualException.getCause() instanceof UndeclaredThrowableException);
//        assertSame("Expected exception cause cause to be Throwable thrown from close method",
//                testException, actualException.getCause().getCause());
//    }


    /**
     * If the main body throws an exception and the close method also throws an exception, the main body's
     * exception should be the one thrown.
     *
     * @throws Exception If anything goes wrong
     */
    @Test
    public void testSerializeWithIOExceptionAndCloseException() throws Exception {
        final Resource mockResource = makeMockSlowReadResource("Hello World", 4096);
        Mockito.
                doThrow(new ResourceIOException("Test Exception")).
                when(mockResource).
                get();
//        final InputStream mockInputStream = mockResource.getInputStream();
//        Mockito.
//                doThrow(new IOException("Test Exception")).
//                when(mockInputStream).
//                read(Mockito.<byte[]>any(), Mockito.anyInt(), Mockito.anyInt());
//        Mockito.
//                doThrow(new IOException("Close exception")).
//                when(mockInputStream).
//                close();

        final Map<String, Object> cacheObjectValues = new HashMap<String, Object>();
        cacheObjectValues.put("resource", mockResource);
        final HttpCacheStorageEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);

        try {
            cacheEntryFactory.serialize(testEntry);
            fail("Expected MemcachedCacheEntryHttpException exception but none was thrown");
        } catch (final MemcachedCacheEntryHttpException ex) {
            // Catch exception so we can look at it in more detail
            final Throwable cause = ex.getCause();
            if (!(cause instanceof ResourceIOException)) {
                throw new AssertionError("Expected exception cause to be of type ResourceIOException but instead it was this", cause);
            }
            assertTrue("Expected exception message '" + cause.getMessage() + "' to contain string 'test'",
                    cause.getMessage().toLowerCase().contains("test"));
        }
    }

    /**
     * Test deserialization with a mock reader that returns 1 byte at a time.
     *
     * Other tests tend to read everything in a single read, but there is no guarantee that will happen in real life,
     * so this test simulates a slower reader that will exercise the rest of the read loop.
     *
     * @throws Exception if anything goes wrong
     */
    // TODO: Figure out how to test this (and if it's still necessary)
//    @Test
//    public void testDeserializeWithSlowReader() throws Exception {
//        final byte[] bytes = readTestFileBytes(SIMPLE_OBJECT_SERIALIZED_NAME);
//        final MemcachedCacheEntryHttp testMemcachedEntry = new MemcachedCacheEntryHttp() {
//            @Override
//            protected InputStream makeByteArrayInputStream(final byte[] bytes) {
//                return makeMockSlowReadInputStream(bytes, 1);
//            }
//        };
//        testMemcachedEntry.set(bytes);
//
//        final HttpCacheStorageEntry expectedEntry = buildSimpleTestObjectFromTemplate(Collections.<String, Object>emptyMap());
//        assertEquals(MemcachedCacheEntryHttpTestUtils.TEST_STORAGE_KEY, testMemcachedEntry.getStorageKey());
//        assertCacheEntriesEqual(expectedEntry, testMemcachedEntry.getHttpCacheEntry());
//    }

    /**
     * Test an IOException being thrown while deserializing.
     *
     * @throws Exception expected
     */
    @Test(expected = ResourceIOException.class)
    public void testDeserializeIOException() throws Exception {
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
}
