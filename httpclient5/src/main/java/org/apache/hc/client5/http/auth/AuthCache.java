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
package org.apache.hc.client5.http.auth;

import org.apache.hc.core5.http.HttpHost;

/**
 * This interface represents an cache of {@link AuthScheme} state information
 * that can be re-used for preemptive authentication by subsequent requests.
 *
 * @since 4.1
 */
public interface AuthCache {

    /**
     * Stores the authentication state with the given authentication scope in the cache.
     *
     * @param host the authentication authority.
     * @param authScheme the cacheable authentication state.
     */
    void put(HttpHost host, AuthScheme authScheme);

    /**
     * Returns the authentication state with the given authentication scope from the cache
     * if available.
     *
     * @param host the authentication authority.
     * @return the authentication state ir {@code null} if not available in the cache.
     */
    AuthScheme get(HttpHost host);

    /**
     * Removes the authentication state with the given authentication scope from the cache
     * if found.
     *
     * @param host the authentication authority.
     */
    void remove(HttpHost host);

    void clear();

    /**
     * Stores the authentication state with the given authentication scope in the cache.
     *
     * @param host the authentication authority.
     * @param pathPrefix the path prefix (the path component up to the last segment separator).
     *                   Can be {@code null}.
     * @param authScheme the cacheable authentication state.
     *
     * @since 5.2
     */
    default void put(HttpHost host, String pathPrefix, AuthScheme authScheme) {
        put(host, authScheme);
    }

    /**
     * Returns the authentication state with the given authentication scope from the cache
     * if available.
     * @param host the authentication authority.
     * @param pathPrefix the path prefix (the path component up to the last segment separator).
     *                   Can be {@code null}.
     * @return the authentication state ir {@code null} if not available in the cache.
     *
     * @since 5.2
     */
    default AuthScheme get(HttpHost host, String pathPrefix) {
        return get(host);
    }

    /**
     * Removes the authentication state with the given authentication scope from the cache
     * if found.
     *
     * @param host the authentication authority.
     * @param pathPrefix the path prefix (the path component up to the last segment separator).
     *                   Can be {@code null}.
     *
     * @since 5.2
     */
    default void remove(HttpHost host, String pathPrefix) {
        remove(host);
    }

}
