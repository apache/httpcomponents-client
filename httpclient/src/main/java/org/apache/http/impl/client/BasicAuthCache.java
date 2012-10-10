/*
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.impl.client;

import java.util.HashMap;

import org.apache.http.HttpHost;
import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.auth.AuthScheme;
import org.apache.http.client.AuthCache;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;

/**
 * Default implementation of {@link AuthCache}.
 *
 * @since 4.0
 */
@NotThreadSafe
public class BasicAuthCache implements AuthCache {

    private final SchemeRegistry schemeRegistry;
    private final HashMap<HttpHost, AuthScheme> map;

    /**
     * Default constructor.
     *
     * @param schemeRegistry the scheme registry to resolve default port by scheme name.
     *
     * @since 4.3
     */
    public BasicAuthCache(final SchemeRegistry schemeRegistry) {
        super();
        this.schemeRegistry = schemeRegistry;
        this.map = new HashMap<HttpHost, AuthScheme>();
    }

    public BasicAuthCache() {
        this(null);
    }

    protected HttpHost getKey(final HttpHost host) {
        if (host.getPort() <= 0) {
            int port;
            if (this.schemeRegistry != null) {
                Scheme scheme = this.schemeRegistry.getScheme(host);
                port = scheme.resolvePort(host.getPort());
            } else {
                port = host.getSchemeName().equalsIgnoreCase("https") ? 443 : 80;
            }
            return new HttpHost(host.getHostName(), port, host.getSchemeName());
        } else {
            return host;
        }
    }

    public void put(final HttpHost host, final AuthScheme authScheme) {
        if (host == null) {
            throw new IllegalArgumentException("HTTP host may not be null");
        }
        this.map.put(getKey(host), authScheme);
    }

    public AuthScheme get(final HttpHost host) {
        if (host == null) {
            throw new IllegalArgumentException("HTTP host may not be null");
        }
        return this.map.get(getKey(host));
    }

    public void remove(final HttpHost host) {
        if (host == null) {
            throw new IllegalArgumentException("HTTP host may not be null");
        }
        this.map.remove(getKey(host));
    }

    public void clear() {
        this.map.clear();
    }

    @Override
    public String toString() {
        return this.map.toString();
    }

}
