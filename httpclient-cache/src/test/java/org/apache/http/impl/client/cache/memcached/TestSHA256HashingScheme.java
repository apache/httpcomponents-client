package org.apache.http.impl.client.cache.memcached;

import static org.junit.Assert.*;

import org.junit.Test;


public class TestSHA256HashingScheme {

    @Test
    public void canHash() {
        SHA256KeyHashingScheme impl = new SHA256KeyHashingScheme();
        String result = impl.hash("hello, hashing world");
        assertTrue(result != null && result.length() > 0);
    }
    
}
