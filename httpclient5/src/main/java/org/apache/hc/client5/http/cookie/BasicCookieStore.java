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
package org.apache.hc.client5.http.cookie;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;

/**
 * Default implementation of {@link CookieStore}
 *
 * @since 4.0
 */
@Contract(threading = ThreadingBehavior.SAFE)
public class BasicCookieStore implements CookieStore, Serializable {

    private static final long serialVersionUID = -7581093305228232025L;

    private final TreeSet<Cookie> cookies;
    private transient ReadWriteLock lock;

    public BasicCookieStore() {
        super();
        this.cookies = new TreeSet<>(CookieIdentityComparator.INSTANCE);
        this.lock = new ReentrantReadWriteLock();
    }

    private void readObject(final ObjectInputStream stream) throws IOException, ClassNotFoundException {
        stream.defaultReadObject();

        /* Reinstantiate transient fields. */
        this.lock = new ReentrantReadWriteLock();
    }

    /**
     * Adds an {@link Cookie HTTP cookie}, replacing any existing equivalent cookies.
     * If the given cookie has already expired it will not be added, but existing
     * values will still be removed.
     *
     * @param cookie the {@link Cookie cookie} to be added
     *
     * @see #addCookies(Cookie[])
     *
     */
    @Override
    public void addCookie(final Cookie cookie) {
        addCookie(cookie, true);
    }

    /**
     * Adds an {@link Cookie HTTP cookie} received over a connection whose security is described by
     * {@code secureConnection}, replacing any existing equivalent cookie. A cookie received over a
     * non-secure connection does not replace an existing secure cookie of the same identity. If the
     * given cookie has already expired it will not be added, but an existing equivalent cookie will
     * still be removed.
     *
     * @param cookie the {@link Cookie cookie} to be added
     * @param secureConnection whether the cookie was received over a secure connection
     *
     * @since 5.7
     */
    @Override
    public void addCookie(final Cookie cookie, final boolean secureConnection) {
        if (cookie != null) {
            lock.writeLock().lock();
            try {
                if (!secureConnection && overlaysSecureCookie(cookie)) {
                    return;
                }
                final Cookie oldCookie = cookies.ceiling(cookie);
                if (oldCookie != null && CookieIdentityComparator.INSTANCE.compare(oldCookie, cookie) == 0) {
                    if (cookie instanceof SetCookie) {
                        final Instant creationInstant = oldCookie.getCreationInstant();
                        if (creationInstant != null) {
                            ((SetCookie) cookie).setCreationInstant(creationInstant);
                        }
                    }
                    cookies.remove(oldCookie);
                } else {
                    cookies.remove(cookie);
                }
                if (!cookie.isExpired(Instant.now())) {
                    cookies.add(cookie);
                }
            } finally {
                lock.writeLock().unlock();
            }
        }
    }

    private boolean overlaysSecureCookie(final Cookie cookie) {
        for (final Cookie existing : cookies) {
            if (existing.isSecure()
                    && namesMatch(existing.getName(), cookie.getName())
                    && (domainMatch(cookie.getDomain(), existing.getDomain())
                        || domainMatch(existing.getDomain(), cookie.getDomain()))
                    && pathMatch(cookie.getPath(), existing.getPath())) {
                return true;
            }
        }
        return false;
    }

    private static boolean namesMatch(final String a, final String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static boolean domainMatch(final String host, final String domain) {
        if (host == null || domain == null) {
            return false;
        }
        final String h = host.toLowerCase(Locale.ROOT);
        String d = domain.toLowerCase(Locale.ROOT);
        if (d.startsWith(".")) {
            d = d.substring(1);
        }
        return h.equals(d)
                || h.length() > d.length() && h.endsWith(d) && h.charAt(h.length() - d.length() - 1) == '.';
    }

    private static boolean pathMatch(final String path, final String cookiePath) {
        final String p = path == null ? "/" : path;
        final String cp = cookiePath == null ? "/" : cookiePath;
        if (p.equals(cp)) {
            return true;
        }
        if (p.startsWith(cp)) {
            return cp.endsWith("/") || p.charAt(cp.length()) == '/';
        }
        return false;
    }

    /**
     * Adds an array of {@link Cookie HTTP cookies}. Cookies are added individually and
     * in the given array order. If any of the given cookies has already expired it will
     * not be added, but existing values will still be removed.
     *
     * @param cookies the {@link Cookie cookies} to be added
     *
     * @see #addCookie(Cookie)
     *
     */
    public void addCookies(final Cookie[] cookies) {
        if (cookies != null) {
            for (final Cookie cookie : cookies) {
                this.addCookie(cookie);
            }
        }
    }

    /**
     * Returns an immutable array of {@link Cookie cookies} that this HTTP
     * state currently contains.
     *
     * @return an array of {@link Cookie cookies}.
     */
    @Override
    public List<Cookie> getCookies() {
        lock.readLock().lock();
        try {
            //create defensive copy so it won't be concurrently modified
            return new ArrayList<>(cookies);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Removes all of {@link Cookie cookies} in this HTTP state
     * that have expired by the specified {@link Date date}.
     *
     * @return true if any cookies were purged.
     *
     * @see Cookie#isExpired(Date)
     */
    @Override
    @SuppressWarnings("deprecation")
    public boolean clearExpired(final Date date) {
        if (date == null) {
            return false;
        }
        lock.writeLock().lock();
        try {
            boolean removed = false;
            for (final Iterator<Cookie> it = cookies.iterator(); it.hasNext(); ) {
                if (it.next().isExpired(date)) {
                    it.remove();
                    removed = true;
                }
            }
            return removed;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Removes all of {@link Cookie cookies} in this HTTP state that have expired by the specified
     * {@link Instant date}.
     *
     * @return true if any cookies were purged.
     * @see Cookie#isExpired(Instant)
     * @since 5.2
     */
    @Override
    public boolean clearExpired(final Instant instant) {
        if (instant == null) {
            return false;
        }
        lock.writeLock().lock();
        try {
            boolean removed = false;
            for (final Iterator<Cookie> it = cookies.iterator(); it.hasNext(); ) {
                if (it.next().isExpired(instant)) {
                    it.remove();
                    removed = true;
                }
            }
            return removed;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Clears all cookies.
     */
    @Override
    public void clear() {
        lock.writeLock().lock();
        try {
            cookies.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public String toString() {
        lock.readLock().lock();
        try {
            return cookies.toString();
        } finally {
            lock.readLock().unlock();
        }
    }

}
