package org.apache.http.impl.client.cache.memcached;

import static org.junit.Assert.*;

import org.apache.http.client.cache.memcached.KeyHashingScheme;
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
            public String hash(String storageKey) {
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
