package org.apache.http.impl.client.cache.memcached;

import static org.junit.Assert.*;

import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.memcached.MemcachedCacheEntry;
import org.apache.http.impl.client.cache.HttpTestUtils;
import org.junit.Test;


public class TestMemcachedCacheEntryFactoryImpl {

    @Test
    public void createsMemcachedCacheEntryImpls() {
        String key = "key";
        HttpCacheEntry entry = HttpTestUtils.makeCacheEntry();
        MemcachedCacheEntryFactoryImpl impl = new MemcachedCacheEntryFactoryImpl();
        MemcachedCacheEntry result = impl.getMemcachedCacheEntry(key, entry);
        assertNotNull(result);
        assertSame(key, result.getStorageKey());
        assertSame(entry, result.getHttpCacheEntry());
    }
}
