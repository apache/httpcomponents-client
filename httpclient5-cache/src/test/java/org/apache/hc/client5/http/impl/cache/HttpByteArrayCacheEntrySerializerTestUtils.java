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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.Map;

import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.HttpCacheEntrySerializer;
import org.apache.hc.client5.http.cache.HttpCacheStorageEntry;
import org.apache.hc.client5.http.cache.Resource;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.BasicHeader;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

class HttpByteArrayCacheEntrySerializerTestUtils {
    private final static String TEST_RESOURCE_DIR = "src/test/resources/";
    static final String TEST_STORAGE_KEY = "xyzzy";

    /**
     * Template for incrementally building a new HttpCacheStorageEntry test object, starting from defaults.
     */
    static class HttpCacheStorageEntryTestTemplate {
        Resource resource;
        Date requestDate;
        Date responseDate;
        int responseCode;
        Header[] responseHeaders;
        Map<String, String> variantMap;
        String storageKey;

        /**
         * Return a new HttpCacheStorageEntryTestTemplate instance with all default values.
         *
         * @return new HttpCacheStorageEntryTestTemplate instance
         */
        static HttpCacheStorageEntryTestTemplate makeDefault() {
            return new HttpCacheStorageEntryTestTemplate(DEFAULT_HTTP_CACHE_STORAGE_ENTRY_TEST_TEMPLATE);
        }

        /**
         * Convert this template to a HttpCacheStorageEntry object.
         * @return HttpCacheStorageEntry object
         */
        HttpCacheStorageEntry toEntry() {
            return new HttpCacheStorageEntry(storageKey,
                    new HttpCacheEntry(
                            requestDate,
                            responseDate,
                            responseCode,
                            responseHeaders,
                            resource,
                            variantMap));
        }

        /**
         * Create a new template with all null values.
         */
        private HttpCacheStorageEntryTestTemplate() {
        }

        /**
         * Create a new template values copied from the given template
         *
         * @param src Template to copy values from
         */
        private HttpCacheStorageEntryTestTemplate(final HttpCacheStorageEntryTestTemplate src) {
            this.resource = src.resource;
            this.requestDate = src.requestDate;
            this.responseDate = src.responseDate;
            this.responseCode = src.responseCode;
            this.responseHeaders = src.responseHeaders;
            this.variantMap = src.variantMap;
            this.storageKey = src.storageKey;
        }
    }

    /**
     * Template with all default values.
     *
     * Used by HttpCacheStorageEntryTestTemplate#makeDefault()
     */
    private static final HttpCacheStorageEntryTestTemplate DEFAULT_HTTP_CACHE_STORAGE_ENTRY_TEST_TEMPLATE = new HttpCacheStorageEntryTestTemplate();
    static {
        DEFAULT_HTTP_CACHE_STORAGE_ENTRY_TEST_TEMPLATE.resource = new HeapResource("Hello World".getBytes(StandardCharsets.UTF_8));
        DEFAULT_HTTP_CACHE_STORAGE_ENTRY_TEST_TEMPLATE.requestDate = new Date(165214800000L);
        DEFAULT_HTTP_CACHE_STORAGE_ENTRY_TEST_TEMPLATE.responseDate = new Date(2611108800000L);
        DEFAULT_HTTP_CACHE_STORAGE_ENTRY_TEST_TEMPLATE.responseCode = 200;
        DEFAULT_HTTP_CACHE_STORAGE_ENTRY_TEST_TEMPLATE.responseHeaders = new Header[]{
                new BasicHeader("Content-type", "text/html"),
                new BasicHeader("Cache-control", "public, max-age=31536000"),
        };
        DEFAULT_HTTP_CACHE_STORAGE_ENTRY_TEST_TEMPLATE.variantMap = Collections.emptyMap();
        DEFAULT_HTTP_CACHE_STORAGE_ENTRY_TEST_TEMPLATE.storageKey = TEST_STORAGE_KEY;
    }

    /**
     * Test serializing and deserializing the given object with the given factory.
     * <p>
     * Compares fields to ensure the deserialized object is equivalent to the original object.
     *
     * @param serializer Factory for creating serializers
     * @param httpCacheStorageEntry    Original object to serialize and test against
     * @throws Exception if anything goes wrong
     */
    static void testWithCache(final HttpCacheEntrySerializer<byte[]> serializer, final HttpCacheStorageEntry httpCacheStorageEntry) throws Exception {
        final byte[] testBytes = serializer.serialize(httpCacheStorageEntry);
        verifyHttpCacheEntryFromBytes(serializer, httpCacheStorageEntry, testBytes);
    }

    /**
     * Verify that the given bytes deserialize to the given storage key and an equivalent cache entry.
     *
     * @param serializer Deserializer
     * @param httpCacheStorageEntry Cache entry to verify
     * @param testBytes Bytes to deserialize
     * @throws Exception if anything goes wrong
     */
    static void verifyHttpCacheEntryFromBytes(final HttpCacheEntrySerializer<byte[]> serializer, final HttpCacheStorageEntry httpCacheStorageEntry, final byte[] testBytes) throws Exception {
        final HttpCacheStorageEntry testEntry = httpCacheStorageEntryFromBytes(serializer, testBytes);

        assertCacheEntriesEqual(httpCacheStorageEntry, testEntry);
    }

    /**
     * Verify that the given test file deserializes to a cache entry equivalent to the one given.
     *
     * @param serializer Deserializer
     * @param httpCacheStorageEntry    Cache entry to verify
     * @param testFileName  Name of test file to deserialize
     * @param reserializeFiles If true, test files will be regenerated and saved to disk
     * @throws Exception if anything goes wrong
     */
    static void verifyHttpCacheEntryFromTestFile(final HttpCacheEntrySerializer<byte[]> serializer,
                                                 final HttpCacheStorageEntry httpCacheStorageEntry,
                                                 final String testFileName,
                                                 final boolean reserializeFiles) throws Exception {
        if (reserializeFiles) {
            final File toFile = makeTestFileObject(testFileName);
            saveEntryToFile(serializer, httpCacheStorageEntry, toFile);
        }

        final byte[] bytes = readTestFileBytes(testFileName);

        verifyHttpCacheEntryFromBytes(serializer, httpCacheStorageEntry, bytes);
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
        try(final FileInputStream testStream = new FileInputStream(testFile)) {
            return readFullyStrict(testStream, testFile.length());
        }
    }

    /**
     * Create a new cache object from the given bytes.
     *
     * @param serializer Deserializer
     * @param testBytes         Bytes to deserialize
     * @return Deserialized object
     */
    static HttpCacheStorageEntry httpCacheStorageEntryFromBytes(final HttpCacheEntrySerializer<byte[]> serializer, final byte[] testBytes) throws ResourceIOException {
        return serializer.deserialize(testBytes);
    }

    /**
     * Assert that the given objects are equivalent
     *
     * @param expected Expected cache entry object
     * @param actual   Actual cache entry object
     * @throws Exception if anything goes wrong
     */
    static void assertCacheEntriesEqual(final HttpCacheStorageEntry expected, final HttpCacheStorageEntry actual) throws Exception {
        assertEquals(expected.getKey(), actual.getKey());

        final HttpCacheEntry expectedContent = expected.getContent();
        final HttpCacheEntry actualContent = actual.getContent();

        assertEquals(expectedContent.getRequestDate(), actualContent.getRequestDate());
        assertEquals(expectedContent.getResponseDate(), actualContent.getResponseDate());
        assertEquals(expectedContent.getStatus(), actualContent.getStatus());

        assertArrayEquals(expectedContent.getVariantMap().keySet().toArray(), actualContent.getVariantMap().keySet().toArray());
        for (final String key : expectedContent.getVariantMap().keySet()) {
            assertEquals("Expected same variantMap values for key '" + key + "'",
                    expectedContent.getVariantMap().get(key), actualContent.getVariantMap().get(key));
        }

        // Verify that the same headers are present on the expected and actual content.
        for(final Header expectedHeader: expectedContent.getHeaders()) {
            final Header actualHeader = actualContent.getFirstHeader(expectedHeader.getName());

            if (actualHeader == null) {
                if (expectedHeader.getName().equalsIgnoreCase("content-length")) {
                    // This header is added by the cache implementation, and can be safely ignored
                } else {
                    fail("Expected header " + expectedHeader.getName() + " was not found");
                }
            } else {
                assertEquals(expectedHeader.getName(), actualHeader.getName());
                assertEquals(expectedHeader.getValue(), actualHeader.getValue());
            }
        }

        if (expectedContent.getResource() == null) {
            assertNull("Expected null resource", actualContent.getResource());
        } else {
            final byte[] expectedBytes = readFullyStrict(
                    expectedContent.getResource().getInputStream(),
                    (int) expectedContent.getResource().length()
            );
            final byte[] actualBytes = readFullyStrict(
                    actualContent.getResource().getInputStream(),
                    (int) actualContent.getResource().length()
            );
            assertArrayEquals(expectedBytes, actualBytes);
        }
    }

    /**
     * Get a File object for the given test file.
     *
     * @param testFileName Name of test file
     * @return File for this test file
     */
    static File makeTestFileObject(final String testFileName) {
        return new File(TEST_RESOURCE_DIR + testFileName);
    }

    /**
     * Save the given cache entry serialized to the given file.
     *
     * @param serializer Serializer
     * @param httpCacheStorageEntry Cache entry to serialize and save
     * @param outFile Output file to write to
     * @throws Exception if anything goes wrong
     */
    static void saveEntryToFile(final HttpCacheEntrySerializer<byte[]> serializer, final HttpCacheStorageEntry httpCacheStorageEntry, final File outFile) throws Exception {
        final byte[] bytes = serializer.serialize(httpCacheStorageEntry);

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
     * Copy bytes from the given input stream to the given destination buffer until the buffer is full,
     * or end-of-file is reached, and return the number of bytes read.
     *
     * @param src Input stream to read from
     * @param dest Output buffer to write to
     * @return Number of bytes read
     * @throws IOException if an I/O error occurs
     */
    private static int readFully(final InputStream src, final byte[] dest) throws IOException {
        final int destPos = 0;
        final int length = dest.length;
        int totalBytesRead = 0;
        int lastBytesRead;

        while (totalBytesRead < length && (lastBytesRead = src.read(dest, destPos + totalBytesRead, length - totalBytesRead)) != -1) {
            totalBytesRead += lastBytesRead;
        }
        return totalBytesRead;
    }

    /**
     * Copy bytes from the given input stream to a new buffer until the given length is reached,
     * and returns the new buffer.  If end-of-file is reached first, an IOException is thrown
     *
     * @param src Input stream to read from
     * @param length Maximum bytes to read
     * @return All bytes from file
     * @throws IOException if an I/O error occurs or end-of-file is reached before the requested
     *                     number of bytes have been read
     */
    static byte[] readFullyStrict(final InputStream src, final long length) throws IOException {
        if (length > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(String.format("Length %d is too large to fit in an array", length));
        }
        final int intLength = (int) length;
        final byte[] dest = new byte[intLength];
        final int bytesRead = readFully(src, dest);

        if (bytesRead == intLength) {
            return dest;
        } else {
            throw new IOException(String.format("Expected to read %d bytes but only got %d", intLength, bytesRead));
        }
    }
}
