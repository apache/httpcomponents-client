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

package org.apache.http.impl.client.cache;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.Resource;
import org.apache.http.impl.client.cache.memcached.MemcachedCacheEntry;
import org.apache.http.impl.client.cache.memcached.MemcachedCacheEntryFactory;
import org.apache.http.impl.io.AbstractMessageParser;
import org.apache.http.impl.io.AbstractMessageWriter;
import org.apache.http.io.SessionInputBuffer;
import org.apache.http.io.SessionOutputBuffer;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicStatusLine;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.apache.http.impl.client.cache.MemcachedCacheEntryHttpTestUtils.assertCacheEntriesEqual;
import static org.apache.http.impl.client.cache.MemcachedCacheEntryHttpTestUtils.buildSimpleTestObjectFromTemplate;
import static org.apache.http.impl.client.cache.MemcachedCacheEntryHttpTestUtils.makeMockSlowReadInputStream;
import static org.apache.http.impl.client.cache.MemcachedCacheEntryHttpTestUtils.makeMockSlowReadResource;
import static org.apache.http.impl.client.cache.MemcachedCacheEntryHttpTestUtils.makeTestFileObject;
import static org.apache.http.impl.client.cache.MemcachedCacheEntryHttpTestUtils.memcachedCacheEntryFromBytes;
import static org.apache.http.impl.client.cache.MemcachedCacheEntryHttpTestUtils.readFully;
import static org.apache.http.impl.client.cache.MemcachedCacheEntryHttpTestUtils.readTestFileBytes;
import static org.apache.http.impl.client.cache.MemcachedCacheEntryHttpTestUtils.testWithCache;
import static org.apache.http.impl.client.cache.MemcachedCacheEntryHttpTestUtils.verifyHttpCacheEntryFromTestFile;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
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
    private static final String TEST_STORAGE_KEY = "xyzzy";

    private MemcachedCacheEntryFactory cacheEntryFactory;

    // Manually set this to true to re-generate all of the .serialized files
    private final boolean reserializeFiles = false;

    @Before
    public void before() {
        cacheEntryFactory = new MemcachedCacheEntryHttpFactory();
    }

    /**
     * Serialize and deserialize a simple object with a tiny body.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void simpleObjectTest() throws Exception {
        final HttpCacheEntry testEntry = buildSimpleTestObjectFromTemplate(Collections.<String, Object>emptyMap());

        testWithCache(TEST_STORAGE_KEY, testEntry, cacheEntryFactory);
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
        final HttpCacheEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);

        testWithCache(TEST_STORAGE_KEY, testEntry, cacheEntryFactory);
    }

    @Test
    public void noHeadersTest() throws Exception {
        final Map<String, Object> cacheObjectValues = new HashMap<String, Object>();
        cacheObjectValues.put("headers", new Header[0]);
        final HttpCacheEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);

        testWithCache(TEST_STORAGE_KEY, testEntry, cacheEntryFactory);
    }

    @Test
    public void contentLengthTest() throws Exception {
        final Map<String, Object> cacheObjectValues = new HashMap<String, Object>();
        cacheObjectValues.put("headers", new Header[] {
                new BasicHeader("Content-Length", "999"),
        });
        final HttpCacheEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);

        testWithCache(TEST_STORAGE_KEY, testEntry, cacheEntryFactory);
    }

    @Test
    public void emptyBodyTest() throws Exception {
        final Map<String, Object> cacheObjectValues = new HashMap<String, Object>();
        cacheObjectValues.put("resource", new HeapResource(new byte[0]));
        final HttpCacheEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);

        testWithCache(TEST_STORAGE_KEY, testEntry, cacheEntryFactory);
    }

    @Test
    public void noBodyTest() throws Exception {
        final Map<String, Object> cacheObjectValues = new HashMap<String, Object>();
        cacheObjectValues.put("resource", null);
        cacheObjectValues.put("statusLine", new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1),
                204, "No Content"));

        final HttpCacheEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);

        testWithCache(TEST_STORAGE_KEY, testEntry, cacheEntryFactory);
    }

    @Test
    public void testSimpleVariantMap() throws Exception {
        final Map<String, Object> cacheObjectValues = new HashMap<String, Object>();
        final Map<String, String> variantMap = new HashMap<String, String>();
        variantMap.put("{Accept-Encoding=gzip}","{Accept-Encoding=gzip}https://example.com:1234/foo");
        variantMap.put("{Accept-Encoding=compress}","{Accept-Encoding=compress}https://example.com:1234/foo");
        cacheObjectValues.put("variantMap", variantMap);
        final HttpCacheEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);

        testWithCache(TEST_STORAGE_KEY, testEntry, cacheEntryFactory);
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
        final HttpCacheEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);

        testWithCache(TEST_STORAGE_KEY, testEntry, cacheEntryFactory);
    }

    @Test(expected = IllegalStateException.class)
    public void testNullStorageKey() {
        final MemcachedCacheEntryHttp entry = new MemcachedCacheEntryHttp(null, buildSimpleTestObjectFromTemplate(Collections.<String, Object>emptyMap()));
        entry.toByteArray();
    }

    @Test(expected = IllegalStateException.class)
    public void testNullCacheEntry() {
        final MemcachedCacheEntryHttp entry = new MemcachedCacheEntryHttp("storageKey", null);
        entry.toByteArray();
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
        final HttpCacheEntry testEntry = buildSimpleTestObjectFromTemplate(Collections.<String, Object>emptyMap());

        verifyHttpCacheEntryFromTestFile(TEST_STORAGE_KEY, testEntry, cacheEntryFactory, SIMPLE_OBJECT_SERIALIZED_NAME, reserializeFiles);
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
        final HttpCacheEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);

        verifyHttpCacheEntryFromTestFile(TEST_STORAGE_KEY, testEntry, cacheEntryFactory, FILE_TEST_SERIALIZED_NAME, reserializeFiles);
    }

    @Test
    public void variantMapTestFromPreviouslySerialized() throws Exception {
        final Map<String, Object> cacheObjectValues = new HashMap<String, Object>();
        final Map<String, String> variantMap = new HashMap<String, String>();
        variantMap.put("{Accept-Encoding=gzip}","{Accept-Encoding=gzip}https://example.com:1234/foo");
        variantMap.put("{Accept-Encoding=compress}","{Accept-Encoding=compress}https://example.com:1234/foo");
        cacheObjectValues.put("variantMap", variantMap);
        final HttpCacheEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);

        verifyHttpCacheEntryFromTestFile(TEST_STORAGE_KEY, testEntry, cacheEntryFactory, VARIANTMAP_TEST_SERIALIZED_NAME, reserializeFiles);
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
        final HttpCacheEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);

        verifyHttpCacheEntryFromTestFile(TEST_STORAGE_KEY, testEntry, cacheEntryFactory, ESCAPED_HEADER_TEST_SERIALIZED_NAME, reserializeFiles);
    }

    @Test
    public void noBodyTestFromPreviouslySerialized() throws Exception {
        final Map<String, Object> cacheObjectValues = new HashMap<String, Object>();
        cacheObjectValues.put("resource", null);
        cacheObjectValues.put("statusLine", new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1),
                204, "No Content"));

        final HttpCacheEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);

        verifyHttpCacheEntryFromTestFile(TEST_STORAGE_KEY, testEntry, cacheEntryFactory, NO_BODY_TEST_SERIALIZED_NAME, reserializeFiles);
    }

    @Test(expected = MemcachedCacheEntryHttpException.class)
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
        final String testString = "Short Hello";
        final Resource mockResource = makeMockSlowReadResource(testString, 1);

        final Map<String, Object> cacheObjectValues = new HashMap<String, Object>();
        cacheObjectValues.put("resource", mockResource);
        final HttpCacheEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);

        final MemcachedCacheEntryHttp testMemcachedEntry = new MemcachedCacheEntryHttp(TEST_STORAGE_KEY, testEntry);
        final byte[] testBytes = testMemcachedEntry.toByteArray();

        final MemcachedCacheEntry verifyMemcachedCacheEntryFromBytes = memcachedCacheEntryFromBytes(cacheEntryFactory, testBytes);
        final byte[] verifyBytes = readFully(verifyMemcachedCacheEntryFromBytes.getHttpCacheEntry().getResource().getInputStream(),
                (int)verifyMemcachedCacheEntryFromBytes.getHttpCacheEntry().getResource().length());

        assertEquals(testString, new String(verifyBytes, "UTF-8"));
    }

    /**
     * Test an error case where the resource length is greater than than the actual file size while serializing.
     */
    @Test(expected = MemcachedCacheEntryHttpException.class)
    public void testSerializeStreamEndsTooEarly() {
        final String testString = "Short Hello";
        final Resource mockResource = makeMockSlowReadResource(testString, 1);
        // Return 1 byte too many
        Mockito.when(mockResource.length()).thenReturn(Long.valueOf(testString.length() + 1));

        final Map<String, Object> cacheObjectValues = new HashMap<String, Object>();
        cacheObjectValues.put("resource", mockResource);
        final HttpCacheEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);

        final MemcachedCacheEntryHttp testMemcachedEntry = new MemcachedCacheEntryHttp(TEST_STORAGE_KEY, testEntry);
        testMemcachedEntry.toByteArray();
    }

    /**
     * Test an error case where the length of the object to serialize is too large to fit in a 32-bit address space.
     */
    @Test(expected = MemcachedCacheEntryHttpException.class)
    public void testSerializeTooManyBytes() {
        final String testString = "Short Hello";
        final Resource mockResource = makeMockSlowReadResource(testString, 1);
        // Return way too many bytes byte too many
        Mockito.when(mockResource.length()).thenReturn(Integer.MAX_VALUE + 1L);

        final Map<String, Object> cacheObjectValues = new HashMap<String, Object>();
        cacheObjectValues.put("resource", mockResource);
        final HttpCacheEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);

        final MemcachedCacheEntryHttp testMemcachedEntry = new MemcachedCacheEntryHttp(TEST_STORAGE_KEY, testEntry);
        testMemcachedEntry.toByteArray();
    }


    /**
     * Test an HttpException being thrown while serializing.
     *
     * @throws Exception expected
     */
    @Test(expected = MemcachedCacheEntryHttpException.class)
    public void testSerializeWithHTTPException() throws Exception {
        final AbstractMessageWriter<HttpResponse> throwyHttpWriter = Mockito.mock(AbstractMessageWriter.class);
        Mockito.
                doThrow(new HttpException("Test Exception")).
                when(throwyHttpWriter).
                write(Mockito.any(HttpResponse.class));

        final Map<String, Object> cacheObjectValues = new HashMap<String, Object>();
        final HttpCacheEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);

        final MemcachedCacheEntryHttp testMemcachedEntry = new MemcachedCacheEntryHttp(TEST_STORAGE_KEY, testEntry) {
            protected AbstractMessageWriter<HttpResponse> makeHttpResponseWriter(final SessionOutputBuffer outputBuffer) {
                return throwyHttpWriter;
            }
        };
        testMemcachedEntry.toByteArray();
    }


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
        final HttpCacheEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);

        final MemcachedCacheEntryHttp testMemcachedEntry = new MemcachedCacheEntryHttp(TEST_STORAGE_KEY, testEntry);
        testMemcachedEntry.toByteArray();
    }

    /**
     * If the the close method throws an exception and the main body does not, the close method's exception should be
     * thrown.
     *
     * @throws Exception If anything goes wrong
     */
    @Test
    public void testSerializeWithCloseIOException() throws Exception {
        final Throwable testException = new IOException("Close exception");

        final Throwable actualException = MemcachedCacheEntryHttpTestUtils.resourceCloseExceptionOnSerializationTestHelper(TEST_STORAGE_KEY, testException);
        assertTrue("Expected exception " + actualException + " to be of type " + MemcachedCacheEntryHttpException.class,
                actualException instanceof MemcachedCacheEntryHttpException);
        assertSame("Expected exception cause to be the one thrown from close method", testException, actualException.getCause());
    }

    /**
     * Test special handling in case close method throws a RuntimeException.
     *
     * @throws Exception If anything goes wrong
     */
    @Test
    public void testSerializeWithCloseRuntimeException() throws Exception {
        final Throwable testException = new RuntimeException("Close exception");

        final Throwable actualException = MemcachedCacheEntryHttpTestUtils.resourceCloseExceptionOnSerializationTestHelper(TEST_STORAGE_KEY, testException);
        assertTrue("Expected exception " + actualException + " to be of type " + MemcachedCacheEntryHttpException.class,
                actualException instanceof MemcachedCacheEntryHttpException);
        assertSame("Expected exception cause to be the one thrown from close method", testException, actualException.getCause());
    }

    /**
     * Test special handling in case close method throws an Error.
     *
     * @throws Exception If anything goes wrong
     */
    @Test
    public void testSerializeWithCloseError() throws Exception {
        final Throwable testException = new Error("Close error");

        final Throwable actualException = MemcachedCacheEntryHttpTestUtils.resourceCloseExceptionOnSerializationTestHelper(TEST_STORAGE_KEY, testException);
        assertSame("Expected exception to be Error thrown from close method",
                testException, actualException);
    }

    /**
     * Test special handling in case close method throws something else unexpected.
     *
     * @throws Exception If anything goes wrong
     */
    @Test
    public void testSerializeWithCloseThrowable() throws Exception {
        final Throwable testException = new Throwable("Close error");

        final Throwable actualException = MemcachedCacheEntryHttpTestUtils.resourceCloseExceptionOnSerializationTestHelper(TEST_STORAGE_KEY, testException);
        assertTrue("Expected exception " + actualException + " to be of type " + MemcachedCacheEntryHttpException.class,
                actualException instanceof MemcachedCacheEntryHttpException);
        assertTrue("Expected exception cause " + actualException.getCause() + " to be of type " + UndeclaredThrowableException.class,
                actualException.getCause() instanceof UndeclaredThrowableException);
        assertSame("Expected exception cause cause to be Throwable thrown from close method",
                testException, actualException.getCause().getCause());
    }


    /**
     * If the main body throws an exception and the close method also throws an exception, the main body's
     * exception should be the one thrown.
     *
     * @throws Exception If anything goes wrong
     */
    @Test
    public void testSerializeWithIOExceptionAndCloseException() throws Exception {
        final Resource mockResource = makeMockSlowReadResource("Hello World", 4096);
        final InputStream mockInputStream = mockResource.getInputStream();
        Mockito.
                doThrow(new IOException("Test Exception")).
                when(mockInputStream).
                read(Mockito.<byte[]>any(), Mockito.anyInt(), Mockito.anyInt());
        Mockito.
                doThrow(new IOException("Close exception")).
                when(mockInputStream).
                close();

        final Map<String, Object> cacheObjectValues = new HashMap<String, Object>();
        cacheObjectValues.put("resource", mockResource);
        final HttpCacheEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);

        final MemcachedCacheEntryHttp testMemcachedEntry = new MemcachedCacheEntryHttp(TEST_STORAGE_KEY, testEntry);
        try {
            testMemcachedEntry.toByteArray();
            fail("Expected MemcachedCacheEntryHttpException exception but none was thrown");
        } catch (final MemcachedCacheEntryHttpException ex) {
            // Catch exception so we can look at it in more detail
            final Throwable cause = ex.getCause();
            assertTrue("Expected object of type " + cause.getClass() + " to be of type IOException",
                    cause instanceof IOException);
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
    @Test
    public void testDeserializeWithSlowReader() throws Exception {
        final byte[] bytes = readTestFileBytes(SIMPLE_OBJECT_SERIALIZED_NAME);
        final MemcachedCacheEntryHttp testMemcachedEntry = new MemcachedCacheEntryHttp() {
            @Override
            protected InputStream makeByteArrayInputStream(final byte[] bytes) {
                return makeMockSlowReadInputStream(bytes, 1);
            }
        };
        testMemcachedEntry.set(bytes);

        final HttpCacheEntry expectedEntry = buildSimpleTestObjectFromTemplate(Collections.<String, Object>emptyMap());
        assertEquals(TEST_STORAGE_KEY, testMemcachedEntry.getStorageKey());
        assertCacheEntriesEqual(expectedEntry, testMemcachedEntry.getHttpCacheEntry());
    }

    /**
     * Test an IOException being thrown while deserializing.
     *
     * @throws Exception expected
     */
    @Test(expected = MemcachedCacheEntryHttpException.class)
    public void testDeserializeIOException() throws Exception {
        final AbstractMessageParser<HttpResponse> throwyParser = Mockito.mock(AbstractMessageParser.class);
        Mockito.
                doThrow(new IOException("Test Exception")).
                when(throwyParser).
                parse();

        final MemcachedCacheEntryHttp testMemcachedEntry = new MemcachedCacheEntryHttp() {
            @Override
            protected AbstractMessageParser<HttpResponse> makeHttpResponseParser(final SessionInputBuffer outputBuffer) {
                return throwyParser;
            }
        };
        testMemcachedEntry.set(new byte[0]);
    }
}
