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

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.Configuration;
import net.sf.ehcache.store.MemoryStoreEvictionPolicy;

import org.apache.http.client.HttpClient;
import org.apache.http.client.cache.HttpCacheStorage;
import org.apache.http.impl.client.cache.CacheConfig;
import org.apache.http.impl.client.cache.CachingHttpClient;
import org.apache.http.impl.client.cache.HeapResourceFactory;
import org.apache.http.impl.client.cache.TestProtocolRequirements;
import org.easymock.classextension.EasyMock;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

public class TestEhcacheProtocolRequirements extends TestProtocolRequirements{

    protected final String TEST_EHCACHE_NAME = "TestEhcacheProtocolRequirements-cache";

    protected static CacheManager CACHE_MANAGER;

    @BeforeClass
    public static void setUpGlobal() {
        Configuration config = new Configuration();
        config.addDefaultCache(
                new CacheConfiguration("default", Integer.MAX_VALUE)
                    .memoryStoreEvictionPolicy(MemoryStoreEvictionPolicy.LFU)
                    .overflowToDisk(false));
        CACHE_MANAGER = CacheManager.create(config);
    }

    @Override
    @Before
    public void setUp() {
        super.setUp();
        params = new CacheConfig();
        params.setMaxObjectSizeBytes(MAX_BYTES);

        if (CACHE_MANAGER.cacheExists(TEST_EHCACHE_NAME)){
            CACHE_MANAGER.removeCache(TEST_EHCACHE_NAME);
        }
        CACHE_MANAGER.addCache(TEST_EHCACHE_NAME);
        HttpCacheStorage storage = new EhcacheHttpCacheStorage(CACHE_MANAGER.getCache(TEST_EHCACHE_NAME));
        mockBackend = EasyMock.createMock(HttpClient.class);

        impl = new CachingHttpClient(mockBackend, new HeapResourceFactory(), storage, params);
    }

    @After
    public void tearDown(){
        CACHE_MANAGER.removeCache(TEST_EHCACHE_NAME);
    }

    @AfterClass
    public static void tearDownGlobal(){
        CACHE_MANAGER.shutdown();
    }

}
