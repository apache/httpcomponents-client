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
package org.apache.hc.client5.http.impl.cache.memcached;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;


public class TestPrefixKeyHashingScheme {

    private static final String KEY = "key";
    private static final String PREFIX = "prefix";
    private PrefixKeyHashingScheme impl;
    private KeyHashingScheme scheme;

    @Before
    public void setUp() {
        scheme = new KeyHashingScheme() {
            @Override
            public String hash(final String storageKey) {
                assertEquals(KEY, storageKey);
                return "hash";
            }
        };
        impl = new PrefixKeyHashingScheme(PREFIX, scheme);
    }

    @Test
    public void addsPrefixToBackingScheme() {
        assertEquals("prefixhash", impl.hash(KEY));
    }
}
