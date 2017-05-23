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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.HttpCacheEntrySerializationException;
import org.apache.hc.client5.http.cache.HttpCacheEntrySerializer;
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
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class DefaultHttpCacheEntrySerializer implements HttpCacheEntrySerializer {

    @Override
    public void writeTo(final HttpCacheEntry cacheEntry, final OutputStream os) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(os)) {
            oos.writeObject(cacheEntry);
        }
    }

    @Override
    public HttpCacheEntry readFrom(final InputStream is) throws IOException {
        try (ObjectInputStream ois = new ObjectInputStream(is)) {
            return (HttpCacheEntry) ois.readObject();
        } catch (final ClassNotFoundException ex) {
            throw new HttpCacheEntrySerializationException("Class not found: " + ex.getMessage(), ex);
        }
    }

}
