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

import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.util.Args;

/**
 * Basic in-memory implementation of {@link CompressionDictionaryStore}.
 * <p>
 * Dictionaries are held in a bounded map keyed by cookie partition, request origin and the SHA-256
 * hash of the dictionary content, so identical content stored in different privacy partitions or
 * against different origins yields distinct entries. The origin is normalised to its scheme, host
 * and effective port, {@code 443} being assumed for {@code https} when no explicit port is present. The
 * store retains at most {@code maxEntries} dictionaries; once that bound is exceeded the eldest
 * entries are evicted in insertion order until the store is back within capacity. Adding a
 * dictionary whose key already exists replaces the previous entry and moves it to the
 * most-recently-added position, so it is evicted last. All access is guarded by a
 * {@link ReentrantLock}, hence instances are safe for concurrent use.
 *
 * @since 5.7
 */
@Contract(threading = ThreadingBehavior.SAFE)
public final class BasicCompressionDictionaryStore implements CompressionDictionaryStore {

    private static final int DEFAULT_MAX_ENTRIES = 64;

    private final int maxEntries;
    private final Map<Key, CompressionDictionary> dictionaries;
    private final ReentrantLock lock;

    /**
     * Creates a store bounded to the default of {@value #DEFAULT_MAX_ENTRIES} dictionaries.
     *
     * @since 5.7
     */
    public BasicCompressionDictionaryStore() {
        this(DEFAULT_MAX_ENTRIES);
    }

    /**
     * Creates a store bounded to the given number of dictionaries.
     *
     * @param maxEntries the maximum number of dictionaries to retain; must be positive.
     * @throws IllegalArgumentException if {@code maxEntries} is not positive.
     * @since 5.7
     */
    public BasicCompressionDictionaryStore(final int maxEntries) {
        this.maxEntries = Args.positive(maxEntries, "Maximum entries");
        this.dictionaries = new LinkedHashMap<>();
        this.lock = new ReentrantLock();
    }

    /**
     * Stores the dictionary under the compound key formed from the privacy partition, its
     * {@link CompressionDictionary#getSource() source} origin and SHA-256 hash. A pre-existing entry
     * with the same key is replaced and promoted to the most-recently-added position; the store then
     * evicts its eldest entries until it no longer exceeds {@code maxEntries}.
     *
     * @param partition the cookie storage partition; must not be {@code null}.
     * @param dictionary the dictionary to store; must not be {@code null}.
     * @throws NullPointerException if {@code partition} or {@code dictionary} is {@code null}.
     * @since 5.7
     */
    @Override
    public void add(
            final CookieStore partition,
            final CompressionDictionary dictionary) {
        Args.notNull(partition, "Cookie partition");
        Args.notNull(dictionary, "Dictionary");

        lock.lock();
        try {
            final Key key = key(
                    partition,
                    dictionary.getSource(),
                    dictionary.getSha256());

            dictionaries.remove(key);
            dictionaries.put(key, dictionary);

            while (dictionaries.size() > maxEntries) {
                final Iterator<Key> iterator =
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

    /**
     * Returns the dictionary previously stored for the given origin and SHA-256 hash. This resolves a
     * single candidate by the exact hash a response advertises, for example the hash carried in the
     * {@code Available-Dictionary} negotiation.
     *
     * @param partition the cookie storage partition; must not be {@code null}.
     * @param origin the request origin the dictionary was stored against; must not be {@code null}.
     * @param sha256 the SHA-256 hash of the dictionary content; must not be {@code null}.
     * @return the matching dictionary, or {@code null} if none is stored under that key.
     * @throws NullPointerException if {@code partition}, {@code origin} or {@code sha256} is {@code null}.
     * @since 5.7
     */
    @Override
    public CompressionDictionary getByHash(
            final CookieStore partition,
            final URI origin,
            final byte[] sha256) {
        Args.notNull(partition, "Cookie partition");
        Args.notNull(origin, "Origin");
        Args.notNull(sha256, "SHA-256");

        lock.lock();
        try {
            return dictionaries.get(key(partition, origin, sha256));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns all dictionaries whose source shares the scheme, host and effective port of the given
     * origin, in insertion order. This is the set of candidates a client may advertise for an outbound
     * request to that origin, before further filtering by match pattern or freshness.
     *
     * @param partition the cookie storage partition; must not be {@code null}.
     * @param origin the request origin to match; must not be {@code null}.
     * @return a newly allocated, modifiable list of matching dictionaries, empty if none match.
     * @throws NullPointerException if {@code partition} or {@code origin} is {@code null}.
     * @since 5.7
     */
    @Override
    public List<CompressionDictionary> getByOrigin(
            final CookieStore partition,
            final URI origin) {
        Args.notNull(partition, "Cookie partition");
        Args.notNull(origin, "Origin");

        lock.lock();
        try {
            final List<CompressionDictionary> result =
                    new ArrayList<>();

            for (final Map.Entry<Key, CompressionDictionary> entry
                    : dictionaries.entrySet()) {
                if (entry.getKey().partition == partition
                        && sameOrigin(origin, entry.getValue().getSource())) {
                    result.add(entry.getValue());
                }
            }

            return result;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes all stored dictionaries, returning the store to an empty state.
     *
     * @since 5.7
     */
    @Override
    public void clear() {
        lock.lock();
        try {
            dictionaries.clear();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void clear(final CookieStore partition) {
        Args.notNull(partition, "Cookie partition");
        lock.lock();
        try {
            final Iterator<Key> iterator = dictionaries.keySet().iterator();
            while (iterator.hasNext()) {
                if (iterator.next().partition == partition) {
                    iterator.remove();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private static Key key(
            final CookieStore partition,
            final URI origin,
            final byte[] sha256) {
        return new Key(partition, originKey(origin), Base64.getEncoder().encodeToString(sha256));
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
        return -1;
    }

    private static final class Key {
        private final CookieStore partition;
        private final String origin;
        private final String hash;

        Key(final CookieStore partition, final String origin, final String hash) {
            this.partition = partition;
            this.origin = origin;
            this.hash = hash;
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(partition);
            result = 31 * result + origin.hashCode();
            result = 31 * result + hash.hashCode();
            return result;
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Key)) {
                return false;
            }
            final Key that = (Key) obj;
            return partition == that.partition
                    && origin.equals(that.origin)
                    && hash.equals(that.hash);
        }
    }
}
