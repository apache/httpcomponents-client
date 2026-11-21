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
package org.apache.hc.client5.http.impl.async;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import org.apache.hc.client5.http.cookie.Cookie;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.entity.compress.CompressionDictionaryStore;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.util.Args;

/**
 * Binds the cookie and compression dictionary privacy partitions without
 * adding compression-specific responsibilities to {@link CookieStore}.
 *
 * @since 5.7
 */
@Contract(threading = ThreadingBehavior.SAFE_CONDITIONAL)
final class CompressionDictionaryCookieStore implements CookieStore {

    private final CookieStore delegate;
    private final CompressionDictionaryStore compressionDictionaryStore;

    CompressionDictionaryCookieStore(
            final CookieStore delegate,
            final CompressionDictionaryStore compressionDictionaryStore) {
        this.delegate = Args.notNull(delegate, "Cookie store");
        this.compressionDictionaryStore = Args.notNull(
                compressionDictionaryStore, "Compression dictionary store");
    }

    boolean isBoundTo(final CompressionDictionaryStore store) {
        return compressionDictionaryStore == store;
    }

    @Override
    public void addCookie(final Cookie cookie) {
        delegate.addCookie(cookie);
    }

    @Override
    public List<Cookie> getCookies() {
        return delegate.getCookies();
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean clearExpired(final Date date) {
        return delegate.clearExpired(date);
    }

    @Override
    public boolean clearExpired(final Instant date) {
        return delegate.clearExpired(date);
    }

    @Override
    public void clear() {
        try {
            delegate.clear();
        } finally {
            compressionDictionaryStore.clear(this);
        }
    }

}
