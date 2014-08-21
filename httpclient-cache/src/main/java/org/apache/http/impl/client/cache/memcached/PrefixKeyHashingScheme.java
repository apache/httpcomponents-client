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
package org.apache.http.impl.client.cache.memcached;


/**
 * This is a {@link KeyHashingScheme} decorator that simply adds
 * a known prefix to the results of another {@code KeyHashingScheme}.
 * Primarily useful for namespacing a shared memcached cluster, for
 * example.
 */
public class PrefixKeyHashingScheme implements KeyHashingScheme {

    private final String prefix;
    private final KeyHashingScheme backingScheme;

    /**
     * Creates a new {@link KeyHashingScheme} that prepends the given
     * prefix to the results of hashes from the given backing scheme.
     * Users should be aware that memcached has a fixed maximum key
     * length, so the combination of this prefix plus the results of
     * the backing hashing scheme must still fit within these limits.
     * @param prefix
     * @param backingScheme
     */
    public PrefixKeyHashingScheme(final String prefix, final KeyHashingScheme backingScheme) {
        this.prefix = prefix;
        this.backingScheme = backingScheme;
    }

    @Override
    public String hash(final String storageKey) {
        return prefix + backingScheme.hash(storageKey);
    }

}
