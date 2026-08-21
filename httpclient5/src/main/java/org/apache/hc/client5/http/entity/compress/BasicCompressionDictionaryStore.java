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
package org.apache.hc.client5.http.entity.compress;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.util.Args;

/**
 * Basic in-memory implementation of {@link CompressionDictionaryStore}.
 * <p>
 * Dictionaries are held in a bounded map keyed by the SHA-256 hash of their content. The store
 * retains at most {@code maxEntries} dictionaries; once that bound is exceeded the oldest entries
 * are evicted in insertion order until the store is back within capacity. Adding a dictionary whose
 * hash already exists replaces the previous entry and moves it to the most-recently-added position,
 * so it is evicted last. All mutating and reading operations are guarded by a lock, hence instances
 * are safe for concurrent use.
 *
 * @since 5.7
 */
@Contract(threading = ThreadingBehavior.SAFE)
public final class BasicCompressionDictionaryStore implements CompressionDictionaryStore {

    private static final int DEFAULT_MAX_ENTRIES = 64;

    private final int maxEntries;
    private final Map<String, CompressionDictionary> dictionaries;
    private final ReentrantLock lock;

    public BasicCompressionDictionaryStore() {
        this(DEFAULT_MAX_ENTRIES);
    }

    public BasicCompressionDictionaryStore(final int maxEntries) {
        this.maxEntries = Args.positive(maxEntries, "Maximum entries");
        this.dictionaries = new LinkedHashMap<>();
        this.lock = new ReentrantLock();
    }

    @Override
    public void add(final CompressionDictionary dictionary) {
        Args.notNull(dictionary, "Dictionary");

        lock.lock();
        try {
            final String key = key(
                    dictionary.getSource(),
                    dictionary.getSha256());

            dictionaries.remove(key);
            dictionaries.put(key, dictionary);

            while (dictionaries.size() > maxEntries) {
                final Iterator<String> iterator =
                        dictionaries.keySet().iterator();
                if (iterator.hasNext()) {
                    iterator.next();
                    iterator.remove();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public CompressionDictionary getByHash(
            final URI origin,
            final byte[] sha256) {
        Args.notNull(origin, "Origin");
        Args.notNull(sha256, "SHA-256");

        lock.lock();
        try {
            return dictionaries.get(key(origin, sha256));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<CompressionDictionary> getByOrigin(final URI origin) {
        Args.notNull(origin, "Origin");

        lock.lock();
        try {
            final List<CompressionDictionary> result =
                    new ArrayList<>();

            for (final CompressionDictionary dictionary
                    : dictionaries.values()) {
                if (sameOrigin(origin, dictionary.getSource())) {
                    result.add(dictionary);
                }
            }

            return result;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void clear() {
        lock.lock();
        try {
            dictionaries.clear();
        } finally {
            lock.unlock();
        }
    }

    private static String key(
            final URI origin,
            final byte[] sha256) {
        return originKey(origin)
                + '|'
                + Base64.getEncoder().encodeToString(sha256);
    }

    private static String originKey(final URI uri) {
        return uri.getScheme().toLowerCase(Locale.ROOT)
                + "://"
                + uri.getHost().toLowerCase(Locale.ROOT)
                + ':'
                + effectivePort(uri);
    }

    private static boolean sameOrigin(
            final URI first,
            final URI second) {
        return equalsIgnoreCase(first.getScheme(), second.getScheme())
                && equalsIgnoreCase(first.getHost(), second.getHost())
                && effectivePort(first) == effectivePort(second);
    }

    private static boolean equalsIgnoreCase(
            final String first,
            final String second) {
        return first != null
                && second != null
                && first.equalsIgnoreCase(second);
    }

    private static int effectivePort(final URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        if ("https".equalsIgnoreCase(uri.getScheme())) {
            return 443;
        }
        if ("http".equalsIgnoreCase(uri.getScheme())) {
            return 80;
        }
        return -1;
    }
}