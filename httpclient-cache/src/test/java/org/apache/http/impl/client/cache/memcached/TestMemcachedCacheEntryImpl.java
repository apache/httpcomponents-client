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
package org.apache.http.impl.client.cache.memcached;

import static org.apache.http.impl.client.cache.memcached.MemcachedSerializationTestUtils.assertEntryEquals;
import static org.apache.http.impl.client.cache.memcached.MemcachedSerializationTestUtils.assertNoCallbacks;
import static org.apache.http.impl.client.cache.memcached.MemcachedSerializationTestUtils.entry;
import static org.apache.http.impl.client.cache.memcached.MemcachedSerializationTestUtils.serialize;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.Header;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.Resource;
import org.apache.http.impl.client.cache.FileResource;
import org.apache.http.impl.client.cache.HeapResource;
import org.apache.http.impl.client.cache.memcached.MemcachedSerializationTestUtils.ExternalResource;
import org.apache.http.impl.client.cache.memcached.MemcachedSerializationTestUtils.ReadObjectProbe;
import org.apache.http.impl.client.cache.memcached.MemcachedSerializationTestUtils.ReadObjectResource;
import org.apache.http.impl.client.cache.memcached.MemcachedSerializationTestUtils.ReadResolveResource;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.message.BufferedHeader;
import org.apache.http.util.CharArrayBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Tests serialization and deserialization of {@link MemcachedCacheEntryImpl}.
 * <p>
 * Covers standard cache entry graphs, legacy serialized forms, class and proxy
 * filtering, malformed streams, and preservation of entry state after failed reads.
 * Harmless callback probes verify that the restricted reader rejects disallowed
 * classes before executing their deserialization callbacks.
 * </p>
 */
public class TestMemcachedCacheEntryImpl {

    /**
     * Date subclass used to verify exact-class filtering.
     */
    private static class CustomDate extends Date {
        private static final long serialVersionUID = 1L;
    }

    /**
     * Cache entry subclass used to verify rejection of custom entry types.
     */
    private static class CustomEntry extends HttpCacheEntry {
        private static final long serialVersionUID = 1L;

        CustomEntry() {
            super(new Date(1L), new Date(2L), new BasicStatusLine(HttpVersion.HTTP_1_1, 200, "OK"),
                    new Header[0], null);
        }
    }

    /**
     * Header subclass used to test rejection in entries and legacy header arrays.
     */
    private static class CustomHeader extends BasicHeader {
        private static final long serialVersionUID = 1L;

        CustomHeader() {
            super("Custom", "value");
        }
    }

    /**
     * Protocol version subclass used to verify rejection within a status line.
     */
    private static class CustomProtocolVersion extends ProtocolVersion {
        private static final long serialVersionUID = 1L;

        CustomProtocolVersion() {
            super("HTTP", 1, 1);
        }
    }

    /**
     * Status line subclass used to verify rejection within a cache entry.
     */
    private static class CustomStatusLine extends BasicStatusLine {
        private static final long serialVersionUID = 1L;

        CustomStatusLine() {
            super(HttpVersion.HTTP_1_1, 200, "OK");
        }
    }

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private MemcachedCacheEntryImpl impl;

    private HttpCacheEntry originalEntry;

    @Test
    public void acceptsClassAnnotationsContainingAllowedData() throws Exception {
        impl.set(MemcachedSerializationTestUtils.serializeWithAnnotation(new Date(1L), "new-key", originalEntry));
        assertEquals("new-key", impl.getStorageKey());
        assertEntryEquals(originalEntry, impl.getHttpCacheEntry());
    }

    @Test
    public void acceptsTrailingNonObjectDataWithoutReadingIt() throws Exception {
        final byte[] bytes = serialize("new-key", originalEntry);
        final byte[] withTrailingData = Arrays.copyOf(bytes, bytes.length + 1);
        withTrailingData[bytes.length] = (byte) 0xff;
        impl.set(withTrailingData);
        assertEquals("new-key", impl.getStorageKey());
        assertEntryEquals(originalEntry, impl.getHttpCacheEntry());
    }

    private void assertLegacyFixture(final String name, final Date expectedDate) throws Exception {
        impl.set(legacyFixture(name));
        final HttpCacheEntry restored = impl.getHttpCacheEntry();
        assertEquals("legacy-key", impl.getStorageKey());
        assertEquals(new Date(1470000000123L), restored.getRequestDate());
        assertEquals(new Date(1470000000456L), restored.getResponseDate());
        assertEquals(HttpVersion.HTTP_1_1, restored.getProtocolVersion());
        assertEquals(200, restored.getStatusCode());
        assertEquals("OK", restored.getReasonPhrase());
        assertEquals(expectedDate, restored.getDate());
        assertEquals(4, restored.getAllHeaders().length);
        assertEquals(BufferedHeader.class, restored.getFirstHeader("Buffered").getClass());
        assertEquals("value", restored.getFirstHeader("Buffered").getValue());
        assertEquals("one", restored.getHeaders("Duplicate")[0].getValue());
        assertEquals("two", restored.getHeaders("Duplicate")[1].getValue());
        assertEquals("variant-key", restored.getVariantMap().get("variant"));
        assertArrayEquals(new byte[] {0, 1, -1},
                MemcachedSerializationTestUtils.readBytes(restored.getResource().getInputStream()));
    }

    private MemcachedSerializationException assertRejected(final byte[] bytes,
            final Class<? extends Throwable> causeType) {
        final String previousKey = impl.getStorageKey();
        final HttpCacheEntry previousEntry = impl.getHttpCacheEntry();
        try {
            impl.set(bytes);
            fail("Expected deserialization to fail");
            return null;
        } catch (final MemcachedSerializationException expected) {
            assertNotNull(expected.getCause());
            assertTrue("Unexpected cause: " + expected.getCause(), causeType.isInstance(expected.getCause()));
            return expected;
        } finally {
            assertSame(previousKey, impl.getStorageKey());
            assertSame(previousEntry, impl.getHttpCacheEntry());
        }
    }

    private void assertRoundTrip(final String key, final HttpCacheEntry entry) throws IOException {
        impl.set(new MemcachedCacheEntryImpl(key, entry).toByteArray());
        assertEquals(key, impl.getStorageKey());
        assertEntryEquals(entry, impl.getHttpCacheEntry());
    }

    @Test
    public void canBeCreatedEmpty() {
        impl = new MemcachedCacheEntryImpl();
        assertNull(impl.getStorageKey());
        assertNull(impl.getHttpCacheEntry());
    }

    @Test
    public void canBeReconstitutedFromByteArray() throws Exception {
        final byte[] bytes = impl.toByteArray();
        impl = new MemcachedCacheEntryImpl();
        impl.set(bytes);
        assertEquals("original-key", impl.getStorageKey());
        assertEntryEquals(originalEntry, impl.getHttpCacheEntry());
    }

    @Test
    public void canBeSerialized() {
        assertTrue(impl.toByteArray().length > 0);
    }

    @Test
    public void canRecoverAfterFailedReplacement() throws Exception {
        assertRejected(serialize("new-key", new ReadObjectProbe()), InvalidClassException.class);
        assertRoundTrip("replacement-key", entry(null));
        assertRejected(serialize("another-key"), IOException.class);
        assertRoundTrip("last-key", originalEntry);
    }

    @Test
    public void doesNotDeserializeTrailingObjects() throws Exception {
        impl.set(serialize("new-key", originalEntry, new ReadObjectProbe()));
        assertEquals("new-key", impl.getStorageKey());
        assertEntryEquals(originalEntry, impl.getHttpCacheEntry());
        assertNoCallbacks();
    }

    @Test
    public void knowsItsCacheEntry() {
        assertSame(originalEntry, impl.getHttpCacheEntry());
    }

    @Test
    public void knowsItsCacheKey() {
        assertEquals("original-key", impl.getStorageKey());
    }

    private byte[] legacyFixture(final String name) throws IOException {
        final Properties fixture = new Properties();
        final InputStream in = getClass().getResourceAsStream("legacy-entry.properties");
        assertNotNull(in);
        try {
            fixture.load(in);
        } finally {
            in.close();
        }
        final String encoded = fixture.getProperty(name);
        assertNotNull(name, encoded);
        return Base64.decodeBase64(encoded);
    }

    @Test
    public void preservesEveryNullStateAfterFailure() throws Exception {
        for (final String key : new String[] {null, "previous-key"}) {
            for (final HttpCacheEntry entry : new HttpCacheEntry[] {null, originalEntry}) {
                impl = new MemcachedCacheEntryImpl(key, entry);
                assertRejected(serialize("new-key", new ReadObjectProbe()), InvalidClassException.class);
                assertRejected(serialize("new-key"), IOException.class);
                assertRejected(null, IOException.class);
            }
        }
        assertNoCallbacks();
    }

    private Object readSecondObjectWithLegacyReader(final byte[] bytes) throws IOException, ClassNotFoundException {
        final ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes));
        try {
            in.readObject();
            return in.readObject();
        } finally {
            in.close();
        }
    }

    @Test
    public void readsLegacyEntryWithoutCachedDate() throws Exception {
        // 4.2 did not serialize a date field. Preserve the existing reader's default for that field.
        assertLegacyFixture("entry4.2", null);
    }

    @Test
    public void readsLegacyFixture() throws Exception {
        assertLegacyFixture("entry", new Date(784111777000L));
    }

    @Test
    public void readsLegacyHeaderArrayFixture() throws Exception {
        assertLegacyFixture("entry4.5.5", new Date(784111777000L));
    }

    @Test
    public void rejectsAllowedClassesInEntryPosition() throws Exception {
        for (final Object object : new Object[] {"not-an-entry", new Date(), new ArrayList<String>(),
                new HashMap<String, String>(), new byte[0], new char[0], new Header[0]}) {
            assertRejected(serialize("new-key", object), InvalidObjectException.class);
        }
    }

    @Test
    public void rejectsAllowedClassesInKeyPosition() throws Exception {
        for (final Object object : new Object[] {new Date(), new ArrayList<String>(),
                new HashMap<String, String>(), originalEntry, new byte[0], new char[0], new Header[0]}) {
            assertRejected(serialize(object, originalEntry), InvalidObjectException.class);
        }
    }

    @Test
    public void rejectsArraysOfUnapprovedClassesIncludingEmptyArrays() throws Exception {
        for (final Object array : new Object[] {new ReadObjectProbe[0], new ReadObjectProbe[] {new ReadObjectProbe()},
                new ReadObjectProbe[][] {{new ReadObjectProbe()}}, new String[0], new String[][] {{"value"}},
                new Object[] {new ReadObjectProbe()}}) {
            assertRejected(serialize("new-key", array), InvalidClassException.class);
            assertNoCallbacks();
        }
    }

    @Test
    public void rejectsCallbacksInClassAnnotations() throws Exception {
        for (final Object probe : new Object[] {new ReadObjectProbe(), new ReadResolveResource(),
                new ExternalResource()}) {
            final byte[] bytes = MemcachedSerializationTestUtils.serializeWithAnnotation(probe, "new-key", originalEntry);
            // Confirm these otherwise discarded annotations really invoke a callback without filtering.
            MemcachedSerializationTestUtils.resetCallbacks();
            readSecondObjectWithLegacyReader(bytes);
            assertEquals(1, MemcachedSerializationTestUtils.readObjectCalls
                    + MemcachedSerializationTestUtils.readResolveCalls + MemcachedSerializationTestUtils.readExternalCalls);
            MemcachedSerializationTestUtils.resetCallbacks();
            assertRejected(bytes, InvalidClassException.class);
            assertNoCallbacks();
        }
    }

    @Test
    public void rejectsCallbacksInsideAllowedCollections() throws Exception {
        final ArrayList<Object> list = new ArrayList<Object>();
        list.add(new ReadObjectProbe());
        final HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("nested", list);
        assertRejected(serialize("new-key", map), InvalidClassException.class);
        assertNoCallbacks();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    public void rejectsCallbacksInsideVariantMap() throws Exception {
        final Map<String, String> variants = new HashMap<String, String>();
        // Deliberately violate the generic type to exercise the serialized object graph.
        final Map untyped = variants;
        untyped.put("nested", new ReadObjectProbe());
        final HttpCacheEntry entry = new HttpCacheEntry(new Date(1L), new Date(2L),
                new BasicStatusLine(HttpVersion.HTTP_1_1, 200, "OK"), new Header[0], null, variants);
        assertRejected(serialize("new-key", entry), InvalidClassException.class);
        assertNoCallbacks();
    }

    @Test
    public void rejectsCustomDateSubclass() throws Exception {
        final HttpCacheEntry entry = new HttpCacheEntry(new CustomDate(), new Date(2L),
                new BasicStatusLine(HttpVersion.HTTP_1_1, 200, "OK"), new Header[0], null);
        assertRejected(serialize("new-key", entry), InvalidClassException.class);
    }

    @Test
    public void rejectsCustomEntrySubclass() throws Exception {
        assertRejected(serialize("new-key", new CustomEntry()), InvalidClassException.class);
    }

    @Test
    public void rejectsCustomHeadersInsideLegacyHeaderArrays() throws Exception {
        assertRejected(serialize("new-key", new Header[] {new CustomHeader()}), InvalidClassException.class);
    }

    @Test
    public void rejectsCustomHeaderSubclass() throws Exception {
        final HttpCacheEntry entry = new HttpCacheEntry(new Date(1L), new Date(2L),
                new BasicStatusLine(HttpVersion.HTTP_1_1, 200, "OK"),
                new Header[] {new CustomHeader()}, null);
        assertRejected(serialize("new-key", entry), InvalidClassException.class);
    }

    @Test
    public void rejectsCustomProtocolVersionSubclass() throws Exception {
        final HttpCacheEntry entry = new HttpCacheEntry(new Date(1L), new Date(2L),
                new BasicStatusLine(new CustomProtocolVersion(), 200, "OK"), new Header[0], null);
        assertRejected(serialize("new-key", entry), InvalidClassException.class);
    }

    @Test
    public void rejectsCustomStatusLineSubclass() throws Exception {
        final HttpCacheEntry entry = new HttpCacheEntry(new Date(1L), new Date(2L),
                new CustomStatusLine(), new Header[0], null);
        assertRejected(serialize("new-key", entry), InvalidClassException.class);
    }

    @Test
    public void rejectsEmptyAndInvalidStreamHeaders() {
        assertRejected(new byte[0], IOException.class);
        assertRejected(new byte[] {1, 2, 3, 4}, IOException.class);
        assertRejected(new byte[] {(byte) 0xac, (byte) 0xed, 0, 6}, IOException.class);
    }

    @Test
    public void rejectsEveryTruncatedPrefix() throws Exception {
        final byte[] complete = serialize("new-key", originalEntry);
        for (int length = 0; length < complete.length; length++) {
            assertRejected(Arrays.copyOf(complete, length), IOException.class);
        }
    }

    @Test
    public void rejectsExternalizableCallbacksInEitherRoot() throws Exception {
        assertRejected(serialize(new ExternalResource(), originalEntry), InvalidClassException.class);
        assertRejected(serialize("new-key", new ExternalResource()), InvalidClassException.class);
        assertNoCallbacks();
    }

    @Test
    public void rejectsIncompatibleSerialVersionUid() throws Exception {
        final byte[] bytes = serialize("new-key", originalEntry);
        final byte[] name = HttpCacheEntry.class.getName().getBytes("UTF-8");
        int offset = -1;
        for (int i = 0; i <= bytes.length - name.length; i++) {
            if (Arrays.equals(name, Arrays.copyOfRange(bytes, i, i + name.length))) {
                offset = i + name.length;
                break;
            }
        }
        assertTrue(offset >= 0);
        bytes[offset] ^= 1;
        assertRejected(bytes, InvalidClassException.class);
    }

    @Test
    public void rejectsMissingSecondObject() throws Exception {
        assertRejected(serialize("new-key"), IOException.class);
    }

    @Test
    public void rejectsNegativeArrayLengths() throws Exception {
        assertRejected(MemcachedSerializationTestUtils.negativeArrayLength("new-key", new byte[0]), Exception.class);
        assertRejected(MemcachedSerializationTestUtils.negativeArrayLength("new-key", new char[0]), Exception.class);
    }

    @Test
    public void rejectsNegativeProxyInterfaceCount() throws Exception {
        assertRejected(MemcachedSerializationTestUtils.negativeProxyInterfaceCount("new-key"), Exception.class);
    }

    @Test
    public void rejectsNestedResourceCallbacks() throws Exception {
        for (final Resource resource : new Resource[] {new ReadObjectResource(),
                new ReadResolveResource(), new ExternalResource()}) {
            assertRejected(serialize("new-key", entry(resource)), InvalidClassException.class);
            assertNoCallbacks();
        }
    }

    @Test
    public void rejectsNullBytes() {
        assertRejected(null, IOException.class);
    }

    @Test
    public void rejectsNullFilePath() throws Exception {
        assertRejected(MemcachedSerializationTestUtils.nullFilePath("new-key"), Exception.class);
    }

    @Test
    public void rejectsPrimitiveDataInEitherRootPosition() throws Exception {
        for (final boolean keyFirst : new boolean[] {false, true}) {
            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            final ObjectOutputStream out = new ObjectOutputStream(bytes);
            try {
                if (keyFirst) {
                    out.writeObject("new-key");
                }
                out.writeInt(42);
                out.writeObject(originalEntry);
            } finally {
                out.close();
            }
            assertRejected(bytes.toByteArray(), IOException.class);
        }
    }

    @Test
    public void rejectsProxiesInRemovedLegacyFields() throws Exception {
        final byte[] bytes = legacyFixture("unknownFieldProxy");
        readSecondObjectWithLegacyReader(bytes);
        assertEquals(1, MemcachedSerializationTestUtils.readObjectCalls);
        MemcachedSerializationTestUtils.resetCallbacks();
        final Throwable cause = assertRejected(bytes, InvalidClassException.class).getCause();
        assertTrue(cause.getMessage().contains("proxy"));
        assertNoCallbacks();
    }

    @Test
    public void rejectsProxiesInsideLegacyHeaderArrays() throws Exception {
        final Header proxy = (Header) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {Header.class}, new ReadObjectProbe());
        final Throwable cause = assertRejected(serialize("new-key", new Header[] {proxy}),
                InvalidClassException.class).getCause();
        assertTrue(cause.getMessage().contains("proxy"));
        assertNoCallbacks();
    }

    @Test
    public void rejectsProxyDescriptorsBeforeReadingHandlers() throws Exception {
        final Object proxy = Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {Serializable.class}, new ReadObjectProbe());
        for (final byte[] bytes : new byte[][] {serialize(proxy, originalEntry), serialize("new-key", proxy)}) {
            final Throwable cause = assertRejected(bytes, InvalidClassException.class).getCause();
            assertTrue(cause.getMessage().contains("proxy"));
            assertNoCallbacks();
        }
    }

    @Test
    public void rejectsReadObjectCallbacksInEitherRoot() throws Exception {
        assertRejected(serialize(new ReadObjectProbe(), originalEntry), InvalidClassException.class);
        assertRejected(serialize("new-key", new ReadObjectProbe()), InvalidClassException.class);
        assertNoCallbacks();
    }

    @Test
    public void rejectsReadResolveCallbacksInEitherRoot() throws Exception {
        assertRejected(serialize(new ReadResolveResource(), originalEntry), InvalidClassException.class);
        assertRejected(serialize("new-key", new ReadResolveResource()), InvalidClassException.class);
        assertNoCallbacks();
    }

    @Test
    public void rejectsUnneededJdkClasses() throws Exception {
        for (final Object object : new Object[] {Integer.valueOf(1), new java.util.HashSet<String>(),
                new java.util.LinkedHashMap<String, String>(), new StringBuilder("value")}) {
            assertRejected(serialize("new-key", object), InvalidClassException.class);
        }
    }

    @Test
    public void rejectsUnneededPrimitiveAndMultidimensionalArrays() throws Exception {
        for (final Object array : new Object[] {new boolean[0], new short[0], new int[0], new long[0],
                new float[0], new double[0], new byte[0][0], new char[0][0]}) {
            assertRejected(serialize("new-key", array), InvalidClassException.class);
        }
    }

    @Test
    public void rejectsWrongKeyBeforeReadingSecondObject() throws Exception {
        assertRejected(serialize(new Date(), new ReadObjectProbe()), InvalidObjectException.class);
        assertNoCallbacks();
    }

    @Test
    public void roundTripsAllNullRootCombinations() throws Exception {
        assertRoundTrip(null, null);
        assertRoundTrip("key", null);
        assertRoundTrip(null, originalEntry);
    }

    @Test
    public void roundTripsEmptyUnicodeAndLongKeys() throws Exception {
        final char[] longKey = new char[70000];
        Arrays.fill(longKey, 'x');
        for (final String key : new String[] {"", "caf\u00e9-\u2603", new String(longKey)}) {
            assertRoundTrip(key, originalEntry);
        }
    }

    @Test
    public void roundTripsFileResourceWithoutDisposingIt() throws Exception {
        final File file = temporaryFolder.newFile();
        final FileOutputStream out = new FileOutputStream(file);
        try {
            out.write(new byte[] {0, 1, -1});
        } finally {
            out.close();
        }
        assertRoundTrip("key", entry(new FileResource(file)));
        assertTrue(file.exists());
    }

    @Test
    public void roundTripsNullEmptyAndPopulatedResources() throws Exception {
        for (final Resource resource : new Resource[] {null, new HeapResource(new byte[0]),
                new HeapResource(new byte[] {0, 1, -1})}) {
            assertRoundTrip("key", entry(resource));
        }
    }

    @Test
    public void roundTripsNullVariantMap() throws Exception {
        final HttpCacheEntry entry = new HttpCacheEntry(new Date(123L), new Date(456L),
                new BasicStatusLine(HttpVersion.HTTP_1_1, 200, "OK"), new Header[0], null,
                (Map<String, String>) null);
        final byte[] bytes = serialize("key", entry);
        impl.set(bytes);
        // getVariantMap() does not support null; verify the stored representation instead.
        assertArrayEquals(bytes, impl.toByteArray());
    }

    @Test
    public void roundTripsSharedReferencesAcrossRoots() throws Exception {
        final String key = "shared-value";
        final Header header = new BasicHeader("Shared", key);
        final HttpCacheEntry entry = new HttpCacheEntry(new Date(1L), new Date(2L),
                new BasicStatusLine(HttpVersion.HTTP_1_1, 200, "OK"), new Header[] {header, header}, null);
        assertRoundTrip(key, entry);
        final Header[] headers = impl.getHttpCacheEntry().getAllHeaders();
        assertSame(headers[0], headers[1]);
        assertSame(impl.getStorageKey(), headers[0].getValue());
    }

    @Test
    public void roundTripsStandardHeadersVersionsDatesAndVariants() throws Exception {
        final CharArrayBuffer buffer = new CharArrayBuffer(32);
        buffer.append("Buffered: value");
        final Header[] headers = {new BasicHeader("Date", "Sun, 06 Nov 1994 08:49:37 GMT"),
                new BufferedHeader(buffer), new BasicHeader("Duplicate", "one"),
                new BasicHeader("Duplicate", "two"), new BasicHeader("Empty", ""), new BasicHeader("Null", null)};
        final Map<String, String> variants = new HashMap<String, String>();
        variants.put("variant", "cache-key");
        for (final ProtocolVersion version : new ProtocolVersion[] {HttpVersion.HTTP_1_0,
                HttpVersion.HTTP_1_1, new ProtocolVersion("HTTP", 1, 1)}) {
            final StatusLine status = new BasicStatusLine(version, 200, "OK");
            assertRoundTrip("key", new HttpCacheEntry(new Date(123L), new Date(456L),
                    status, headers, null, variants));
            assertRoundTrip("key", new HttpCacheEntry(new Date(123L), new Date(456L),
                    status, new Header[0], null, new HashMap<String, String>()));
        }
    }

    @Before
    public void setUp() {
        originalEntry = entry(new HeapResource(new byte[] {0, 1, -1}));
        impl = new MemcachedCacheEntryImpl("original-key", originalEntry);
        MemcachedSerializationTestUtils.resetCallbacks();
    }

    @Test
    public void wrapsIncompatibleFieldTypesInOtherwiseAllowedGraphs() throws Exception {
        assertRejected(MemcachedSerializationTestUtils.serializeReplacing(
                originalEntry.getStatusLine(), new Date(1L), "new-key", originalEntry), ClassCastException.class);
    }

    @Test
    public void writesTheLegacyWireFormat() throws Exception {
        assertArrayEquals(serialize("original-key", originalEntry), impl.toByteArray());
        final ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(impl.toByteArray()));
        try {
            assertEquals("original-key", in.readObject());
            assertEntryEquals(originalEntry, (HttpCacheEntry) in.readObject());
            assertEquals(-1, in.read());
        } finally {
            in.close();
        }
    }

}
