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

import org.apache.hc.client5.http.cache.HttpCacheEntrySerializer;
import org.apache.hc.client5.http.cache.HttpCacheStorageEntry;
import org.apache.hc.client5.http.cache.ResourceIOException;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;

/**
 * {@link HttpCacheEntrySerializer} that uses {@link HttpCacheStorageEntry}
 * as its cache content representation.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public class NoopCacheEntrySerializer implements HttpCacheEntrySerializer<HttpCacheStorageEntry> {

    public static final NoopCacheEntrySerializer INSTANCE = new NoopCacheEntrySerializer();

    @Override
    public HttpCacheStorageEntry serialize(final HttpCacheStorageEntry cacheEntry) throws ResourceIOException {
        return cacheEntry;
    }

    @Override
    public HttpCacheStorageEntry deserialize(final HttpCacheStorageEntry cacheEntry) throws ResourceIOException {
        return cacheEntry;
    }

}
