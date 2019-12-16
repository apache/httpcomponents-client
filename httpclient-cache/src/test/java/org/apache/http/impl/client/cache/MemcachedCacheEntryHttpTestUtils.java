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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.Resource;
import org.apache.http.impl.client.cache.memcached.MemcachedCacheEntry;
import org.apache.http.impl.client.cache.memcached.MemcachedCacheEntryFactory;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicStatusLine;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

class MemcachedCacheEntryHttpTestUtils {
    private final static String TEST_RESOURCE_DIR = "src/test/resources/";

    /**
     * Create a new HttpCacheEntry object with default fields, except for those overridden by name in the template.
     *
     * @param template Map of field names to override in the test object
     * @return New object for testing
     */
    static HttpCacheEntry buildSimpleTestObjectFromTemplate(final Map<String, Object> template) {
        final Resource resource = getOrDefault(template, "resource",
                new HeapResource("Hello World".getBytes(Charset.forName("UTF-8"))));

        final Date requestDate = getOrDefault(template, "requestDate", new Date(165214800000L));
        final Date responseDate = getOrDefault(template, "responseDate", new Date(2611108800000L));
        final StatusLine statusLine = getOrDefault(template, "statusLine",
                new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1),
                        200, "OK"));
        final Header[] responseHeaders = getOrDefault(template, "headers",
                new Header[]{
                        new BasicHeader("Content-type", "text/html"),
                        new BasicHeader("Cache-control", "public, max-age=31536000"),
                });
        final Map<String, String> variantMap = getOrDefault(template, "variantMap",
                new HashMap<String, String>());
        final String requestMethod = getOrDefault(template, "requestMethod", null);
        return new HttpCacheEntry(requestDate, responseDate, statusLine, responseHeaders, resource, variantMap, requestMethod);
    }

    /**
     * Return the value from a map if it is present, and otherwise a default value.
     *
     * Implementation of map#getOrDefault for Java 6.
     *
     * @param map Map to get an entry from
     * @param key Key to look up
     * @param orDefault Value to return if the given key is not found.
     * @param <X> Type of object expected
     * @return Object from map, or default if not found
     */
    static <X> X getOrDefault(final Map<String, Object> map, final String key, final X orDefault) {
        if (map.containsKey(key)) {
            return (X) map.get(key);
        } else {
            return orDefault;
        }
    }

    /**
     * Test serializing and deserializing the given object with the given factory.
     * <p>
     * Compares fields to ensure the deserialized object is equivalent to the original object.
     *
     * @param storageKey        Storage key to serialize and deserialize
     * @param httpCacheEntry    Original object to serialize and test against
     * @param cacheEntryFactory Factory for creating serializers
     * @throws Exception if anything goes wrong
     */
    static void testWithCache(final String storageKey, final HttpCacheEntry httpCacheEntry, final MemcachedCacheEntryFactory cacheEntryFactory) throws Exception {
        final MemcachedCacheEntry memcachedCacheEntry = cacheEntryFactory.getMemcachedCacheEntry(storageKey, httpCacheEntry);

        final HttpCacheEntry testHttpCacheEntry = memcachedCacheEntry.getHttpCacheEntry();
        assertCacheEntriesEqual(httpCacheEntry, testHttpCacheEntry);

        final byte[] testBytes = memcachedCacheEntry.toByteArray();
        // For debugging
//        String testString = new String(testBytes, StandardCharsets.UTF_8);
//        System.out.println(testString);
//        System.out.printf("Cache entry is %d bytes\n", testBytes.length);

        verifyHttpCacheEntryFromBytes(storageKey, httpCacheEntry, cacheEntryFactory, testBytes);
    }

    /**
     * Verify that the given bytes deserialize to the given storage key and an equivalent cache entry.
     *
     * @param storageKey        Storage key to verify
     * @param httpCacheEntry    Cache entry to verify
     * @param cacheEntryFactory Deserializer factory
     * @param testBytes         Bytes to deserialize
     * @throws Exception if anything goes wrong
     */
    static void verifyHttpCacheEntryFromBytes(final String storageKey, final HttpCacheEntry httpCacheEntry, final MemcachedCacheEntryFactory cacheEntryFactory, final byte[] testBytes) throws Exception {
        final MemcachedCacheEntry testMemcachedCacheEntryFromBytes = memcachedCacheEntryFromBytes(cacheEntryFactory, testBytes);

        assertEquals(storageKey, testMemcachedCacheEntryFromBytes.getStorageKey());
        assertCacheEntriesEqual(httpCacheEntry, testMemcachedCacheEntryFromBytes.getHttpCacheEntry());
    }

    /**
     * Verify that the given test file deserializes to the given storage key and an equivalent cache entry.
     *
     * @param storageKey        Storage key to verify
     * @param httpCacheEntry    Cache entry to verify
     * @param cacheEntryFactory Deserializer factory
     * @param testFileName  Name of test file to deserialize
     * @throws Exception if anything goes wrong
     */
    static void verifyHttpCacheEntryFromTestFile(final String storageKey, final HttpCacheEntry httpCacheEntry,
                                                 final MemcachedCacheEntryFactory cacheEntryFactory, final String testFileName,
                                                 final boolean reserializeFiles) throws Exception {
        if (reserializeFiles) {
            final File toFile = makeTestFileObject(testFileName);
            saveEntryToFile(storageKey, httpCacheEntry, cacheEntryFactory, toFile);
        }

        final byte[] bytes = readTestFileBytes(testFileName);

        verifyHttpCacheEntryFromBytes(storageKey, httpCacheEntry, cacheEntryFactory, bytes);
    }

    /**
     * Get the bytes of the given test file.
     *
     * @param testFileName Name of test file to get bytes from
     * @return Bytes from the given test file
     * @throws Exception if anything goes wrong
     */
    static byte[] readTestFileBytes(final String testFileName) throws Exception {
        final File testFile = makeTestFileObject(testFileName);
        final byte[] bytes = new byte[(int) testFile.length()];
        FileInputStream testStream = null;
        try {
            testStream = new FileInputStream(testFile);
            readFully(testStream, bytes);
        } finally {
            if (testStream != null) {
                testStream.close();
            }
        }
        return bytes;
    }

    /**
     * Create a new memcached cache object from the given bytes.
     *
     * @param cacheEntryFactory Deserializer factory
     * @param testBytes         Bytes to deserialize
     * @return Deserialized object
     */
    static MemcachedCacheEntry memcachedCacheEntryFromBytes(final MemcachedCacheEntryFactory cacheEntryFactory, final byte[] testBytes) {
        final MemcachedCacheEntry newMemcachedCacheEntryFromBytes = cacheEntryFactory.getUnsetCacheEntry();
        newMemcachedCacheEntryFromBytes.set(testBytes);
        return newMemcachedCacheEntryFromBytes;
    }

    /**
     * Assert that the given objects are equivalent
     *
     * @param expected Expected cache entry object
     * @param actual   Actual cache entry object
     * @throws Exception if anything goes wrong
     */
    static void assertCacheEntriesEqual(final HttpCacheEntry expected, final HttpCacheEntry actual) throws Exception {
        assertEquals(expected.getRequestDate(), actual.getRequestDate());
        assertEquals(expected.getResponseDate(), actual.getResponseDate());
        assertEquals(expected.getRequestMethod(), actual.getRequestMethod());

        assertEquals(expected.getStatusLine().getProtocolVersion(), actual.getStatusLine().getProtocolVersion());
        assertEquals(expected.getStatusLine().getReasonPhrase(), actual.getStatusLine().getReasonPhrase());
        assertEquals(expected.getStatusLine().getStatusCode(), actual.getStatusLine().getStatusCode());

        assertArrayEquals(expected.getVariantMap().keySet().toArray(), actual.getVariantMap().keySet().toArray());
        for (final String key : expected.getVariantMap().keySet()) {
            assertEquals("Expected same variantMap values for key '" + key + "'",
                    expected.getVariantMap().get(key), actual.getVariantMap().get(key));
        }

        // Would love a cleaner way to do this if anybody knows of one
        assertEquals(expected.getAllHeaders().length, actual.getAllHeaders().length);
        for (int i = 0; i < expected.getAllHeaders().length; i++) {
            final Header actualHeader = actual.getAllHeaders()[i];
            final Header expectedHeader = expected.getAllHeaders()[i];

            assertEquals(expectedHeader.getName(), actualHeader.getName());
            assertEquals(expectedHeader.getValue(), actualHeader.getValue());
        }

        if (expected.getResource() == null) {
            assertNull("Expected null resource", actual.getResource());
        } else {
            final byte[] expectedBytes = readFully(
                    expected.getResource().getInputStream(),
                    (int) expected.getResource().length()
            );
            final byte[] actualBytes = readFully(
                    actual.getResource().getInputStream(),
                    (int) actual.getResource().length()
            );
            assertArrayEquals(expectedBytes, actualBytes);
        }
    }

    /**
     * Get a File object for the given test file.
     *
     * @param testFileName Name of test file
     * @return File for this test file
     * @throws Exception if anything goes wrong
     */
    static File makeTestFileObject(final String testFileName) throws Exception {
        return new File(TEST_RESOURCE_DIR + testFileName);
    }

    /**
     * Save the given storage key and cache entry serialized to the given file.
     *
     * @param storageKey        Storage key to serialize and save
     * @param httpCacheEntry    Cache entry to serialize and save
     * @param cacheEntryFactory Serializer factory
     * @param outFile           Output file to write to
     * @throws Exception if anything goes wrong
     */
    static void saveEntryToFile(final String storageKey, final HttpCacheEntry httpCacheEntry, final MemcachedCacheEntryFactory cacheEntryFactory, final File outFile) throws Exception {
        final MemcachedCacheEntry memcachedCacheEntry = cacheEntryFactory.getMemcachedCacheEntry(storageKey, httpCacheEntry);
        final byte[] bytes = memcachedCacheEntry.toByteArray();

        OutputStream out = null;
        try {
            out = new FileOutputStream(outFile);
            out.write(bytes);
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }

    /**
     * Create a Resource with an InputStream with a read(...) method that returns from the given string up to
     * the given number of bytes.
     *
     * @param readString Bytes to simulate reading
     * @param maxReadLength Largest number of bytes to return in a single call to read(...)
     * @return Mock InputSream which behaves as described above
     */
    static Resource makeMockSlowReadResource(final String readString, final int maxReadLength) {
        return makeMockSlowReadResource(readString.getBytes(Charset.forName("UTF-8")) , maxReadLength);
    }

    /**
     * Create a Resource with an InputStream with a read(...) method that returns from the given buffer up to
     * the given number of bytes.
     *
     * @param readBytes Bytes to simulate reading
     * @param maxReadLength Largest number of bytes to return in a single call to read(...)
     * @return Mock InputSream which behaves as described above
     */
    static Resource makeMockSlowReadResource(final byte[] readBytes, final int maxReadLength) {
        try {
            final InputStream mockInputStream = makeMockSlowReadInputStream(readBytes, maxReadLength);

            final Resource mockResource = Mockito.mock(Resource.class);
            Mockito.when(mockResource.length()).thenReturn(Long.valueOf(readBytes.length));
            Mockito.when(mockResource.getInputStream()).thenReturn(mockInputStream);

            return mockResource;
        } catch (final IOException ex) {
            throw new RuntimeException("Failure creating mock", ex);
        }
    }

    /**
     * Create an InputStream with a read(...) method that returns from the given buffer up to
     * the given number of bytes.
     *
     * @param readBytes Bytes to simulate reading
     * @param maxReadLength Largest number of bytes to return in a single call to read(...)
     * @return Mock InputSream which behaves as described above
     */
    static InputStream makeMockSlowReadInputStream(final byte[] readBytes, final int maxReadLength) {
        try {
            final InputStream mockInputStream = Mockito.mock(InputStream.class);
            final Answer<Integer> shortReadAnswer = makeMockSlowReadAnswer(readBytes, maxReadLength);
            Mockito.when(mockInputStream.read(Mockito.<byte[]>any(), Mockito.anyInt(), Mockito.anyInt())).
                    thenAnswer(shortReadAnswer);
            Mockito.when(mockInputStream.read()).
                    thenAnswer(shortReadAnswer);
            // No need to mock 2-argument read, base class implements it in terms of 3-argument form
            return mockInputStream;
        } catch (final IOException ex) {
            throw new RuntimeException("Failure creating mock", ex);
        }
    }

    /**
     * Create an Answer function for InputStream#read(...) which will return from the given buffer up to the
     * given number of bytes at a time.
     *
     * This allows for testing the various edge cases from how read(...) behaves.
     *
     * @param readBytes Bytes to simulate reading
     * @param maxReadLength Largest number of bytes to return in a single call to read(...)
     * @return Answer object for mock read which behaves as described above
     */
    private static Answer<Integer> makeMockSlowReadAnswer(final byte[] readBytes, final int maxReadLength) {
        return new Answer<Integer>() {
            int curPos = 0;

            @Override
            public Integer answer(final InvocationOnMock invocationOnMock) throws Throwable {
                final boolean hasArguments = invocationOnMock.getArguments().length > 0;
                final byte[] outBuf = hasArguments ? invocationOnMock.getArgumentAt(0, byte[].class) : new byte[1];
                final int outPos = hasArguments ? invocationOnMock.getArgumentAt(1, Integer.class) : 0;
                final int length = hasArguments ? invocationOnMock.getArgumentAt(2, Integer.class) : 1;

                final int bytesToCopy = Math.min(Math.min(readBytes.length - curPos, length), maxReadLength);
                if (bytesToCopy <= 0) {
                    return -1;
                }

                System.arraycopy(readBytes, curPos, outBuf, outPos, bytesToCopy);
                curPos += bytesToCopy;
                if (hasArguments) {
                    return bytesToCopy;
                } else {
                    return (int) outBuf[0];
                }
            }
        };
    };

    // Adapted from MemcachedCacheEntryHttp#copyBytes
    static void readFully(final InputStream src, final byte[] dest) throws IOException {
        final int destPos = 0;
        final int length = dest.length;
        int totalBytesRead = 0;
        int lastBytesRead;

        while (totalBytesRead < length && (lastBytesRead = src.read(dest, destPos + totalBytesRead, length - totalBytesRead)) != -1) {
            totalBytesRead += lastBytesRead;
        }
    }

    // Adapted from MemcachedCacheEntryHttp#copyBytes
    static byte[] readFully(final InputStream src, final int length) throws IOException {
        final byte[] dest = new byte[length];
        readFully(src, dest);
        return dest;
    }

    /**
     * Simulate a failure from the resource close method, and return the exception that was thrown by the serializer.
     *
     *
     * @param storageKey Storage key string to test
     * @param testException Exception to throw from the test helper
     * @return Actual exception caught
     * @throws IOException Should never happen
     */
    static Throwable resourceCloseExceptionOnSerializationTestHelper(final String storageKey, final Throwable testException) throws IOException {
        final Resource mockResource = makeMockSlowReadResource("Hello World", 4096);
        final InputStream mockInputStream = mockResource.getInputStream();

        // Throw exception from the answer so we can throw checked exceptions not allowed by the close method signature
        final Answer<Void> answer =  new Answer<Void>() {
            @Override
            public Void answer(final InvocationOnMock invocationOnMock) throws Throwable {
                throw testException;
            }
        };
        Mockito.
                doAnswer(answer).
                when(mockInputStream).
                close();

        final Map<String, Object> cacheObjectValues = new HashMap<String, Object>();
        cacheObjectValues.put("resource", mockResource);
        final HttpCacheEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);

        final MemcachedCacheEntryHttp testMemcachedEntry = new MemcachedCacheEntryHttp(storageKey, testEntry);
        try {
            testMemcachedEntry.toByteArray();
            fail("Expected MemcachedCacheEntryHttpException exception but none was thrown");
        } catch (final Throwable ex) {
            return ex;
        }

        // Should not happen
        return null;
    }
}
