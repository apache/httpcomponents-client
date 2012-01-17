package org.apache.http.impl.client.cache.memcached;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.impl.client.cache.DefaultHttpCacheEntrySerializer;
import org.apache.http.impl.client.cache.HttpTestUtils;
import org.junit.Before;
import org.junit.Test;


public class TestMemcachedCacheEntryImpl {

    private MemcachedCacheEntryImpl impl;
    private HttpCacheEntry entry;

    @Before
    public void setUp() {
        entry = HttpTestUtils.makeCacheEntry();
        impl = new MemcachedCacheEntryImpl("foo", entry);
    }
    
    @Test
    public void canBeCreatedEmpty() {
        impl = new MemcachedCacheEntryImpl();
        assertNull(impl.getStorageKey());
        assertNull(impl.getHttpCacheEntry());
    }
    
    @Test
    public void canBeSerialized() {
        byte[] bytes = impl.toByteArray();
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
    }
    
    @Test
    public void knowsItsCacheKey() {
        assertEquals("foo", impl.getStorageKey());
    }
    
    @Test
    public void knowsItsCacheEntry() {
        assertEquals(entry, impl.getHttpCacheEntry());
    }
    
    @Test
    public void canBeReconstitutedFromByteArray() throws Exception {
        String key = impl.getStorageKey();
        HttpCacheEntry entry = impl.getHttpCacheEntry();
        byte[] bytes = impl.toByteArray();
        impl = new MemcachedCacheEntryImpl();
        impl.set(bytes);
        
        assertEquals(key, impl.getStorageKey());
        assertEquivalent(entry, impl.getHttpCacheEntry());
    }
    
    @Test(expected=MemcachedSerializationException.class)
    public void cannotReconstituteFromGarbage() {
        impl = new MemcachedCacheEntryImpl();
        byte[] bytes = HttpTestUtils.getRandomBytes(128);
        impl.set(bytes);
    }

    private void assertEquivalent(HttpCacheEntry entry,
            HttpCacheEntry resultEntry) throws IOException {
        /* Ugh. Implementing HttpCacheEntry#equals is problematic
         * due to the Resource response body (may cause unexpected
         * I/O to users). Checking that two entries
         * serialize to the same bytes seems simpler, on the whole,
         * (while still making for a somewhat yucky test). At
         * least we encapsulate it off here in its own method so
         * the test that uses it remains clear.
         */
        DefaultHttpCacheEntrySerializer ser = new DefaultHttpCacheEntrySerializer();
        ByteArrayOutputStream bos1 = new ByteArrayOutputStream();
        ser.writeTo(entry, bos1);
        byte[] bytes1 = bos1.toByteArray();
        ByteArrayOutputStream bos2 = new ByteArrayOutputStream();
        ser.writeTo(resultEntry, bos2);
        byte[] bytes2 = bos2.toByteArray();
        assertEquals(bytes1.length, bytes2.length);
        for(int i = 0; i < bytes1.length; i++) {
            assertEquals(bytes1[i], bytes2[i]);
        }
    }
}
