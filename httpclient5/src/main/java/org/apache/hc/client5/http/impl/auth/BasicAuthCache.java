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
package org.apache.hc.client5.http.impl.auth;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.UnsupportedSchemeException;
import org.apache.hc.client5.http.auth.AuthCache;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.config.AuthSchemes;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.util.Args;

/**
 * Default implementation of {@link AuthCache}. This implements
 * expects {@link org.apache.hc.client5.http.auth.AuthScheme} to be {@link java.io.Serializable}
 * in order to be cacheable.
 * <p>
 * Instances of this class are thread safe as of version 4.4.
 * </p>
 *
 * @since 4.1
 */
@Contract(threading = ThreadingBehavior.SAFE_CONDITIONAL)
public class BasicAuthCache implements AuthCache {

    private static final int DEFAULT_INITIAL_CAPACITY = 10;
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;
    private static final int DEFAULT_MAX_ENTRIES = 1000;

    private final Map<HttpHost, AuthScheme> map;
    private final SchemePortResolver schemePortResolver;

    /**
     * Default constructor.
     *
     * @since 4.3
     */
    public BasicAuthCache(final SchemePortResolver schemePortResolver, final int initialCapacity,
            final float loadFactor, final int maxEntries) {
        super();
        this.map = new LinkedHashMap<HttpHost, AuthScheme>(initialCapacity, loadFactor, true) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean removeEldestEntry(final Map.Entry<HttpHost, AuthScheme> eldest) {
                return size() > maxEntries;
            }
        };
        this.schemePortResolver = schemePortResolver != null ? schemePortResolver :
            DefaultSchemePortResolver.INSTANCE;
    }

    public BasicAuthCache(final SchemePortResolver schemePortResolver) {
        this(schemePortResolver, DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR, DEFAULT_MAX_ENTRIES);
    }

    public BasicAuthCache() {
        this(null, DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR, DEFAULT_MAX_ENTRIES);
    }

    protected HttpHost getKey(final HttpHost host) {
        if (host.getPort() <= 0) {
            final int port;
            try {
                port = schemePortResolver.resolve(host);
            } catch (final UnsupportedSchemeException ignore) {
                return host;
            }
            return new HttpHost(host.getHostName(), port, host.getSchemeName());
        }
        return host;
    }

    @Override
    public void put(final HttpHost host, final AuthScheme authScheme) {
        Args.notNull(host, "HTTP host");
        if (authScheme == null) {
            return;
        }

        synchronized (this.map) {
            this.map.put(getKey(host), authScheme);
        }
    }

    @Override
    public AuthScheme get(final HttpHost host) {
        Args.notNull(host, "HTTP host");
        final HttpHost hostKey = getKey(host);
        synchronized (this.map) {
            final AuthScheme authScheme = this.map.get(hostKey);
            if (authScheme != null && AuthSchemes.DIGEST.equals(authScheme.getName())) {
                this.map.remove(hostKey);
            }
            return authScheme;
        }
    }

    @Override
    public void remove(final HttpHost host) {
        Args.notNull(host, "HTTP host");
        synchronized (this.map) {
            this.map.remove(getKey(host));
        }
    }

    @Override
    public void clear() {
        synchronized (this.map) {
            this.map.clear();
        }
    }

    @Override
    public String toString() {
        synchronized (this.map) {
            return this.map.toString();
        }
    }

    @Override
    public void putAfterReusing(final HttpHost host, final AuthScheme authScheme) {
        Args.notNull(host, "HTTP host");
        if (authScheme != null && AuthSchemes.DIGEST.equals(authScheme.getName())) {
            put(host, authScheme);
        }
    }

}
