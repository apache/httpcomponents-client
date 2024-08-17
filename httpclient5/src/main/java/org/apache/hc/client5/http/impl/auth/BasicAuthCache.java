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

import java.io.Serializable;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.auth.AuthCache;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.impl.StateHolder;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.LangUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of {@link AuthCache}. This implements
 * expects {@link AuthScheme} to be {@link Serializable}
 * in order to be cacheable.
 * <p>
 * Instances of this class are thread safe as of version 4.4.
 * </p>
 *
 * @since 4.1
 */
@Contract(threading = ThreadingBehavior.SAFE_CONDITIONAL)
public class BasicAuthCache implements AuthCache {

    private static final Logger LOG = LoggerFactory.getLogger(BasicAuthCache.class);

    static class Key {

        final String scheme;
        final String host;
        final int port;
        final String pathPrefix;

        Key(final String scheme, final String host, final int port, final String pathPrefix) {
            Args.notBlank(scheme, "Scheme");
            Args.notBlank(host, "Scheme");
            this.scheme = scheme.toLowerCase(Locale.ROOT);
            this.host = host.toLowerCase(Locale.ROOT);
            this.port = port;
            this.pathPrefix = pathPrefix;
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof Key) {
                final Key that = (Key) obj;
                return this.scheme.equals(that.scheme) &&
                        this.host.equals(that.host) &&
                        this.port == that.port &&
                        Objects.equals(this.pathPrefix, that.pathPrefix);
            }
            return false;
        }

        @Override
        public int hashCode() {
            int hash = LangUtils.HASH_SEED;
            hash = LangUtils.hashCode(hash, this.scheme);
            hash = LangUtils.hashCode(hash, this.host);
            hash = LangUtils.hashCode(hash, this.port);
            hash = LangUtils.hashCode(hash, this.pathPrefix);
            return hash;
        }

        @Override
        public String toString() {
            final StringBuilder buf = new StringBuilder();
            buf.append(scheme).append("://").append(host);
            if (port >= 0) {
                buf.append(":").append(port);
            }
            if (pathPrefix != null) {
                if (!pathPrefix.startsWith("/")) {
                    buf.append("/");
                }
                buf.append(pathPrefix);
            }
            return buf.toString();
        }
    }

    static class AuthData {

        final Class<? extends AuthScheme> clazz;
        final Object state;

        public AuthData(final Class<? extends AuthScheme> clazz, final Object state) {
            this.clazz = clazz;
            this.state = state;
        }

    }

    private final Map<Key, AuthData> map;
    private final SchemePortResolver schemePortResolver;

    /**
     * Default constructor.
     *
     * @since 4.3
     */
    public BasicAuthCache(final SchemePortResolver schemePortResolver) {
        super();
        this.map = new ConcurrentHashMap<>();
        this.schemePortResolver = schemePortResolver != null ? schemePortResolver : DefaultSchemePortResolver.INSTANCE;
    }

    public BasicAuthCache() {
        this(null);
    }

    private Key key(final String scheme, final NamedEndpoint authority, final String pathPrefix) {
        return new Key(scheme, authority.getHostName(), schemePortResolver.resolve(scheme, authority), pathPrefix);
    }

    private AuthData data(final AuthScheme authScheme) {
        return new AuthData(authScheme.getClass(), ((StateHolder) authScheme).store());
    }

    @Override
    public void put(final HttpHost host, final AuthScheme authScheme) {
        put(host, null, authScheme);
    }

    @Override
    public AuthScheme get(final HttpHost host) {
        return get(host, null);
    }

    @Override
    public void remove(final HttpHost host) {
        remove(host, null);
    }

    @Override
    public void put(final HttpHost host, final String pathPrefix, final AuthScheme authScheme) {
        Args.notNull(host, "HTTP host");
        if (authScheme == null) {
            return;
        }
        if (authScheme instanceof StateHolder) {
            this.map.put(key(host.getSchemeName(), host, pathPrefix), data(authScheme));
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Auth scheme {} cannot be cached", authScheme.getClass());
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public AuthScheme get(final HttpHost host, final String pathPrefix) {
        Args.notNull(host, "HTTP host");
        final AuthData authData = this.map.get(key(host.getSchemeName(), host, pathPrefix));
        if (authData != null) {
            try {
                final AuthScheme authScheme = authData.clazz.newInstance();
                ((StateHolder<Object>) authScheme).restore(authData.state);
                return authScheme;
            } catch (final IllegalAccessException | InstantiationException ex) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("Unexpected error while reading auth scheme state", ex);
                }
            }
        }
        return null;
    }

    @Override
    public void remove(final HttpHost host, final String pathPrefix) {
        Args.notNull(host, "HTTP host");
        this.map.remove(key(host.getSchemeName(), host, pathPrefix));
    }

    @Override
    public void clear() {
        this.map.clear();
    }

    @Override
    public String toString() {
        return this.map.toString();
    }

}
