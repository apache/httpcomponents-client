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
package org.apache.http.impl.client.cache.ehcache;

import java.io.IOException;

import junit.framework.TestCase;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;

import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.impl.client.cache.CacheEntry;
import org.easymock.EasyMock;
import org.junit.Test;

public class TestEhcacheHttpCache extends TestCase {

    private Ehcache mockCache;
    private EhcacheHttpCache impl;

    public void setUp() {
        mockCache = EasyMock.createMock(Ehcache.class);
        impl = new EhcacheHttpCache(mockCache);
    }

    @Test
    public void testCachePut() throws IOException {
        final String key = "foo";
        final HttpCacheEntry value = new CacheEntry();

        Element e = new Element(key, value);

        mockCache.put(e);

        EasyMock.replay(mockCache);
        impl.putEntry(key, value);
        EasyMock.verify(mockCache);
    }

    @Test
    public void testCacheGet() {
        final String key = "foo";
        final HttpCacheEntry cachedValue = new CacheEntry();
        Element element = new Element(key, cachedValue);

        EasyMock.expect(mockCache.get(key))
                .andReturn(element);

        EasyMock.replay(mockCache);
        HttpCacheEntry resultingEntry = impl.getEntry(key);
        EasyMock.verify(mockCache);

        assertSame(cachedValue, resultingEntry);
    }

    @Test
    public void testCacheRemove() {
        final String key = "foo";

        EasyMock.expect(mockCache.remove(key)).andReturn(true);

        EasyMock.replay(mockCache);
        impl.removeEntry(key);
        EasyMock.verify(mockCache);
    }

}
