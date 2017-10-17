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
package org.apache.hc.client5.http.impl.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import java.nio.charset.Charset;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.HttpCacheStorageEntry;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.Before;
import org.junit.Test;

public class TestByteArrayCacheEntrySerializer {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private ByteArrayCacheEntrySerializer impl;

    @Before
    public void setUp() {
        impl = new ByteArrayCacheEntrySerializer();
    }

    @Test
    public void canSerializeEntriesWithVariantMaps() throws Exception {
        readWriteVerify(makeCacheEntryWithVariantMap("key"));
    }

    public void readWriteVerify(final HttpCacheStorageEntry writeEntry) throws Exception {
        // write the entry
        final byte[] bytes = impl.serialize(writeEntry);
        // read the entry
        final HttpCacheStorageEntry readEntry = impl.deserialize(bytes);
        // compare
        assertEquals(readEntry.getKey(), writeEntry.getKey());
        assertThat(readEntry.getContent(), HttpCacheEntryMatcher.equivalent(writeEntry.getContent()));
    }

    private HttpCacheStorageEntry makeCacheEntryWithVariantMap(final String key) {
        final Header[] headers = new Header[5];
        for (int i = 0; i < headers.length; i++) {
            headers[i] = new BasicHeader("header" + i, "value" + i);
        }
        final String body = "Lorem ipsum dolor sit amet";

        final Map<String,String> variantMap = new HashMap<>();
        variantMap.put("test variant 1","true");
        variantMap.put("test variant 2","true");
        final HttpCacheEntry cacheEntry = new HttpCacheEntry(new Date(), new Date(),
                HttpStatus.SC_OK, headers,
                new HeapResource(Base64.decodeBase64(body.getBytes(UTF8))), variantMap);

        return new HttpCacheStorageEntry(key, cacheEntry);
    }

}
