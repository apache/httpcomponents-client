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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.auth.AuthCache;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.routing.RoutingSupport;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.util.Args;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final Map<HttpHost, byte[]> map;
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

    @Override
    public void put(final HttpHost host, final AuthScheme authScheme) {
        Args.notNull(host, "HTTP host");
        if (authScheme == null) {
            return;
        }
        if (authScheme instanceof Serializable) {
            try {
                final ByteArrayOutputStream buf = new ByteArrayOutputStream();
                try (final ObjectOutputStream out = new ObjectOutputStream(buf)) {
                    out.writeObject(authScheme);
                }
                final HttpHost key = RoutingSupport.normalize(host, schemePortResolver);
                this.map.put(key, buf.toByteArray());
            } catch (final IOException ex) {
                if (log.isWarnEnabled()) {
                    log.warn("Unexpected I/O error while serializing auth scheme", ex);
                }
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Auth scheme " + authScheme.getClass() + " is not serializable");
            }
        }
    }

    @Override
    public AuthScheme get(final HttpHost host) {
        Args.notNull(host, "HTTP host");
        final HttpHost key = RoutingSupport.normalize(host, schemePortResolver);
        final byte[] bytes = this.map.get(key);
        if (bytes != null) {
            try {
                final ByteArrayInputStream buf = new ByteArrayInputStream(bytes);
                try (final ObjectInputStream in = new ObjectInputStream(buf)) {
                    return (AuthScheme) in.readObject();
                }
            } catch (final IOException ex) {
                if (log.isWarnEnabled()) {
                    log.warn("Unexpected I/O error while de-serializing auth scheme", ex);
                }
                return null;
            } catch (final ClassNotFoundException ex) {
                if (log.isWarnEnabled()) {
                    log.warn("Unexpected error while de-serializing auth scheme", ex);
                }
                return null;
            }
        }
        return null;
    }

    @Override
    public void remove(final HttpHost host) {
        Args.notNull(host, "HTTP host");
        final HttpHost key = RoutingSupport.normalize(host, schemePortResolver);
        this.map.remove(key);
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
