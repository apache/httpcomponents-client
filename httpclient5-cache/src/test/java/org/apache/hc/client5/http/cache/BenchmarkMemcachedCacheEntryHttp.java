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

import java.io.File;
import java.io.FileInputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.hc.client5.http.impl.cache.ByteArrayCacheEntrySerializer;
import org.apache.hc.client5.http.impl.cache.FileResource;
import org.apache.hc.client5.http.impl.cache.HeapResource;
import org.apache.hc.client5.http.impl.cache.MemcachedCacheEntryHttp;
//import org.apache.http.client.cache.HttpCacheEntry;
//import org.apache.http.impl.client.cache.memcached.MemcachedCacheEntry;
//import org.apache.http.impl.client.cache.memcached.MemcachedCacheEntryFactory;
//import org.apache.http.impl.client.cache.memcached.MemcachedCacheEntryFactoryImpl;
import org.junit.Before;
import org.junit.Test;

import static org.apache.hc.client5.http.cache.MemcachedCacheEntryHttpTestUtils.buildSimpleTestObjectFromTemplate;
import static org.apache.hc.client5.http.cache.MemcachedCacheEntryHttpTestUtils.makeTestFileObject;
import static org.apache.hc.client5.http.cache.MemcachedCacheEntryHttpTestUtils.readFully;
import static org.apache.hc.client5.http.cache.MemcachedCacheEntryHttpTestUtils.verifyHttpCacheEntryFromBytes;

public class BenchmarkMemcachedCacheEntryHttp {
    private static final String TEST_CONTENT_FILE_NAME = "ApacheLogo.png";

    // TODO: Name is no longer accurate
    private HttpCacheEntrySerializer<byte[]> cacheEntryFactory;
    private String newCacheName;

    @Before
    public void before() {
        cacheEntryFactory = MemcachedCacheEntryHttp.INSTANCE;
        newCacheName = "HTTP";
    }

    @Test
    public void simpleTestBenchmark() throws Exception {
        final HttpCacheStorageEntry testEntry = buildSimpleTestObjectFromTemplate(Collections.<String, Object>emptyMap());

        benchmarkSerializeDeserialize(newCacheName + " simple object", testEntry, cacheEntryFactory);
    }

    @Test
    public void fileTestBenchmark() throws Exception {
        final Map<String, Object> cacheObjectValues = new HashMap<String, Object>();
        cacheObjectValues.put("resource", new FileResource(makeTestFileObject(TEST_CONTENT_FILE_NAME)));
        final HttpCacheStorageEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);

        benchmarkSerializeDeserialize(newCacheName + " file object", testEntry, cacheEntryFactory);
    }

    @Test
    public void oldSimpleTestBenchmark() throws Exception {
        final HttpCacheStorageEntry testEntry = buildSimpleTestObjectFromTemplate(Collections.<String, Object>emptyMap());
        final HttpCacheEntrySerializer<byte[]> oldSerializer = new ByteArrayCacheEntrySerializer();

        benchmarkSerializeDeserialize("Java simple object", testEntry, oldSerializer);
    }

    @Test
    public void oldFileTestBenchmark() throws Exception {
        final Map<String, Object> cacheObjectValues = new HashMap<String, Object>();
        final File testFile = makeTestFileObject(TEST_CONTENT_FILE_NAME);
        // Turn this into a heap resource, otherwise the Java serializer doesn't serialize the whole body.
        final byte[] testBytes = readFully(new FileInputStream(testFile), (int) testFile.length());
        cacheObjectValues.put("resource", new HeapResource(testBytes));
        final HttpCacheStorageEntry testEntry = buildSimpleTestObjectFromTemplate(cacheObjectValues);
        final HttpCacheEntrySerializer<byte[]> oldSerializer = new ByteArrayCacheEntrySerializer();

        benchmarkSerializeDeserialize("Java file object", testEntry, oldSerializer);
    }

    // Helper methods

    /**
     * Benchmark the given factory's serialization size and time, and deserialization time, printing a summary to
     * System.out.
     *
     * @param testName Name of test for printing
     * @param testEntry Test object to serialize/deserialize
     * @param serializer Factory for serialization/deserialization
     * @throws Exception if anything goes wrong
     */
    private static void benchmarkSerializeDeserialize(final String testName, final HttpCacheStorageEntry testEntry, final HttpCacheEntrySerializer<byte[]> serializer) throws Exception {
        final byte[] testBytes = serializer.serialize(testEntry);

        // Verify once to make sure everything is right, and maybe warm things up a little
        verifyHttpCacheEntryFromBytes(testEntry, serializer, testBytes);

        System.out.printf("%40s: %6d bytes\n", testName + " serialized size", testBytes.length);

        simpleBenchmark(testName + " serialize", new Runnable() {
            public void run () {
                try {
                    serializer.serialize(testEntry);
                } catch (final ResourceIOException e) {
                    throw new RuntimeException("Serialization failed", e);
                }
            }
        });

        simpleBenchmark(testName + " deserialize", new Runnable() {
            public void run () {
                try {
                    serializer.deserialize(testBytes);
                } catch (final ResourceIOException e) {
                    throw new RuntimeException("Deserialization failed", e);
                }
            }
        });
    }

    /**
     * Benchmark the given function object, and print timing information to System.out.
     *
     * @param testName Name of benchmark for printing
     * @param func Function to benchmark
     */
    static void simpleBenchmark(final String testName, final Runnable func) {
        // Warm Up
        final int warmupRuns = 5000;
        for(int i=0;i<warmupRuns;i++) {
            func.run();
        }

        final long start = System.nanoTime();
        final int runs = 5000;
        for(int i=0;i<runs;i++) {
            func.run();
        }
        final long end = System.nanoTime();
        final long elapsed = end - start;
        final long each = elapsed / runs;
        System.out.printf("%40s: %6d runs in %7.3f ms, %7.3f ms/run\n", testName, runs, elapsed/1000000.0, each/1000000.0);
    }
}
