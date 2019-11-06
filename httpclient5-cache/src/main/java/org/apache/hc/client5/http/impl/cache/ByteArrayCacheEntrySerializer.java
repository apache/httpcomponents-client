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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.hc.client5.http.cache.HttpCacheEntrySerializer;
import org.apache.hc.client5.http.cache.HttpCacheStorageEntry;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;

/**
 * {@link HttpCacheEntrySerializer} implementation that uses the default (native)
 * serialization.
 *
 * @see java.io.Serializable
 *
 * @since 4.1
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public final class ByteArrayCacheEntrySerializer implements HttpCacheEntrySerializer<byte[]> {

    public static final ByteArrayCacheEntrySerializer INSTANCE = new ByteArrayCacheEntrySerializer();

    @Override
    public byte[] serialize(final HttpCacheStorageEntry cacheEntry) throws ResourceIOException {
        if (cacheEntry == null) {
            return null;
        }
        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (final ObjectOutputStream oos = new ObjectOutputStream(buf)) {
            oos.writeObject(cacheEntry);
        } catch (final IOException ex) {
            throw new ResourceIOException(ex.getMessage(), ex);
        }
        return buf.toByteArray();
    }

    @Override
    public HttpCacheStorageEntry deserialize(final byte[] serializedObject) throws ResourceIOException {
        if (serializedObject == null) {
            return null;
        }
        try (final ObjectInputStream ois = new RestrictedObjectInputStream(new ByteArrayInputStream(serializedObject))) {
            return (HttpCacheStorageEntry) ois.readObject();
        } catch (final IOException | ClassNotFoundException ex) {
            throw new ResourceIOException(ex.getMessage(), ex);
        }
    }

    // visible for testing
    static class RestrictedObjectInputStream extends ObjectInputStream {

        private static final List<Pattern> ALLOWED_CLASS_PATTERNS = Collections.unmodifiableList(Arrays.asList(
                Pattern.compile("^(\\[L)?org\\.apache\\.hc\\.(.*)"),
                Pattern.compile("^(?:\\[+L)?java\\.util\\..*$"),
                Pattern.compile("^(?:\\[+L)?java\\.lang\\..*$"),
                Pattern.compile("^\\[+Z$"), // boolean
                Pattern.compile("^\\[+B$"), // byte
                Pattern.compile("^\\[+C$"), // char
                Pattern.compile("^\\[+D$"), // double
                Pattern.compile("^\\[+F$"), // float
                Pattern.compile("^\\[+I$"), // int
                Pattern.compile("^\\[+J$"), // long
                Pattern.compile("^\\[+S$") // short
        ));

        private RestrictedObjectInputStream(final InputStream in) throws IOException {
            super(in);
        }

        @Override
        protected Class<?> resolveClass(final ObjectStreamClass objectStreamClass) throws IOException, ClassNotFoundException {
            final String className = objectStreamClass.getName();
            if (!isAllowedClassName(className)) {
                throw new ResourceIOException(String.format(
                        "Class %s is not allowed for deserialization", objectStreamClass.getName()));
            }
            return super.resolveClass(objectStreamClass);
        }

        // visible for testing
        static boolean isAllowedClassName(final String className) {
            for (final Pattern allowedClassPattern : ALLOWED_CLASS_PATTERNS) {
                if (allowedClassPattern.matcher(className).matches()) {
                    return true;
                }
            }
            return false;
        }

    }

}
