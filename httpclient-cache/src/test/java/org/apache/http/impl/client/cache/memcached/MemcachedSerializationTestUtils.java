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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.ByteArrayOutputStream;
import java.io.Externalizable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Date;

import org.apache.http.Header;
import org.apache.http.HttpVersion;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.Resource;
import org.apache.http.impl.client.cache.FileResource;
import org.apache.http.impl.client.cache.HeapResource;
import org.apache.http.message.BasicStatusLine;

/**
 * Provides serialized fixtures, cache entry comparisons, and harmless callback
 * probes for the Memcached cache tests.
 * <p>
 * Builders for malformed streams exercise deserialization failure paths. Callback
 * counters let tests verify that rejected objects do not execute deserialization
 * callbacks and that unfiltered control reads exercise the intended callbacks.
 * </p>
 */
final class MemcachedSerializationTestUtils {

    static int readObjectCalls;
    static int readResolveCalls;
    static int readExternalCalls;

    private MemcachedSerializationTestUtils() {
    }

    static void resetCallbacks() {
        readObjectCalls = 0;
        readResolveCalls = 0;
        readExternalCalls = 0;
    }

    static void assertNoCallbacks() {
        assertEquals(0, readObjectCalls);
        assertEquals(0, readResolveCalls);
        assertEquals(0, readExternalCalls);
    }

    static byte[] serialize(final Object... objects) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final ObjectOutputStream out = new ObjectOutputStream(bytes);
        try {
            for (final Object object : objects) {
                out.writeObject(object);
            }
        } finally {
            out.close();
        }
        return bytes.toByteArray();
    }

    static byte[] serializeReplacing(final Object target, final Object replacement, final Object... objects)
            throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final ObjectOutputStream out = new ReplacingObjectOutputStream(bytes, target, replacement);
        try {
            for (final Object object : objects) {
                out.writeObject(object);
            }
        } finally {
            out.close();
        }
        return bytes.toByteArray();
    }

    static byte[] serializeWithAnnotation(final Object annotation, final String key, final HttpCacheEntry entry)
            throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final ObjectOutputStream out = new ObjectOutputStream(bytes) {
            @Override
            protected void annotateClass(final Class<?> type) throws IOException {
                if (type == HttpCacheEntry.class) {
                    writeInt(42);
                    writeObject(annotation);
                }
            }
        };
        try {
            out.writeObject(key);
            out.writeObject(entry);
        } finally {
            out.close();
        }
        return bytes.toByteArray();
    }

    static byte[] negativeArrayLength(final String key, final Object emptyArray) throws IOException {
        final byte[] bytes = serialize(key, emptyArray);
        // The final four bytes encode the length of the empty array.
        assertArrayEquals(new byte[4], Arrays.copyOfRange(bytes, bytes.length - 4, bytes.length));
        Arrays.fill(bytes, bytes.length - 4, bytes.length, (byte) -1);
        return bytes;
    }

    static byte[] negativeProxyInterfaceCount(final String key) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(serialize(key));
        // TC_OBJECT, TC_PROXYCLASSDESC, and a negative interface count.
        bytes.write(new byte[] {0x73, 0x7d, -1, -1, -1, -1});
        return bytes.toByteArray();
    }

    static byte[] nullFilePath(final String key) throws IOException {
        final File file = new File("unused-cache-resource");
        return serializeReplacing(file.getPath(), null, key, entry(new FileResource(file)));
    }

    static HttpCacheEntry entry(final Resource resource) {
        return new HttpCacheEntry(new Date(123456789L), new Date(123456999L),
                new BasicStatusLine(HttpVersion.HTTP_1_1, 200, "OK"), new Header[0], resource);
    }

    static byte[] readBytes(final InputStream in) throws IOException {
        try {
            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            final byte[] buffer = new byte[256];
            int count;
            while ((count = in.read(buffer)) != -1) {
                bytes.write(buffer, 0, count);
            }
            return bytes.toByteArray();
        } finally {
            in.close();
        }
    }

    static void assertEntryEquals(final HttpCacheEntry expected, final HttpCacheEntry actual) throws IOException {
        if (expected == null) {
            assertNull(actual);
            return;
        }
        assertEquals(expected.getRequestDate(), actual.getRequestDate());
        assertEquals(expected.getResponseDate(), actual.getResponseDate());
        assertEquals(expected.getDate(), actual.getDate());
        assertEquals(expected.getProtocolVersion(), actual.getProtocolVersion());
        assertEquals(expected.getStatusCode(), actual.getStatusCode());
        assertEquals(expected.getReasonPhrase(), actual.getReasonPhrase());
        assertEquals(expected.getVariantMap(), actual.getVariantMap());
        final Header[] expectedHeaders = expected.getAllHeaders();
        final Header[] actualHeaders = actual.getAllHeaders();
        assertEquals(expectedHeaders.length, actualHeaders.length);
        for (int i = 0; i < expectedHeaders.length; i++) {
            assertEquals(expectedHeaders[i].getClass(), actualHeaders[i].getClass());
            assertEquals(expectedHeaders[i].getName(), actualHeaders[i].getName());
            assertEquals(expectedHeaders[i].getValue(), actualHeaders[i].getValue());
        }
        if (expected.getResource() == null) {
            assertNull(actual.getResource());
        } else {
            assertEquals(expected.getResource().getClass(), actual.getResource().getClass());
            assertEquals(expected.getResource().length(), actual.getResource().length());
            assertArrayEquals(readBytes(expected.getResource().getInputStream()),
                    readBytes(actual.getResource().getInputStream()));
        }
    }

    /**
     * Counts {@code readObject} callbacks and serves as a proxy invocation handler.
     */
    public static class ReadObjectProbe implements Serializable, InvocationHandler {

        private static final long serialVersionUID = 1L;

        private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException {
            readObjectCalls++;
            in.defaultReadObject();
        }

        @Override
        public Object invoke(final Object proxy, final Method method, final Object[] args) {
            return null;
        }
    }

    /**
     * Resource subclass that counts {@code readObject} callbacks.
     */
    public static class ReadObjectResource extends HeapResource {

        private static final long serialVersionUID = 1L;

        public ReadObjectResource() {
            super(new byte[] {1, 2, 3});
        }

        private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException {
            readObjectCalls++;
            in.defaultReadObject();
        }
    }

    /**
     * Counts {@code readResolve} callbacks and substitutes a standard {@link HeapResource}.
     */
    public static class ReadResolveResource extends HeapResource {

        private static final long serialVersionUID = 1L;

        public ReadResolveResource() {
            super(new byte[] {1, 2, 3});
        }

        private Object readResolve() {
            readResolveCalls++;
            return new HeapResource(new byte[] {1, 2, 3});
        }
    }

    /**
     * Resource that counts {@link Externalizable#readExternal(ObjectInput)} callbacks.
     */
    public static class ExternalResource implements Resource, Externalizable {

        private static final long serialVersionUID = 1L;

        public ExternalResource() {
        }

        @Override
        public void writeExternal(final ObjectOutput out) throws IOException {
            out.writeInt(1);
        }

        @Override
        public void readExternal(final ObjectInput in) throws IOException {
            readExternalCalls++;
            in.readInt();
        }

        @Override
        public InputStream getInputStream() {
            return new HeapResource(new byte[0]).getInputStream();
        }

        @Override
        public long length() {
            return 0;
        }

        @Override
        public void dispose() {
        }
    }

    /**
     * Creates malformed fixtures by replacing a selected object during serialization.
     */
    private static class ReplacingObjectOutputStream extends ObjectOutputStream {

        private final Object target;
        private final Object replacement;

        ReplacingObjectOutputStream(final ByteArrayOutputStream out, final Object target, final Object replacement)
                throws IOException {
            super(out);
            this.target = target;
            this.replacement = replacement;
            enableReplaceObject(true);
        }

        @Override
        protected Object replaceObject(final Object object) {
            return object == target ? replacement : object;
        }
    }
}
