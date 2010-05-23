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
package org.apache.http.impl.client.cache;

import org.apache.http.Header;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.cache.HttpCacheEntrySerializer;
import org.apache.http.message.BasicHeader;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Date;

public class TestDefaultCacheEntrySerializer {

    @Test
    public void testSerialization() throws Exception {

        HttpCacheEntrySerializer<CacheEntry> serializer = new DefaultCacheEntrySerializer();

        // write the entry
        CacheEntry writeEntry = newCacheEntry();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        serializer.writeTo(writeEntry, out);

        // read the entry
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        CacheEntry readEntry = serializer.readFrom(in);

        // compare
        Assert.assertTrue(areEqual(readEntry, writeEntry));

    }

    private CacheEntry newCacheEntry() throws UnsupportedEncodingException {


        Header[] headers = new Header[5];
        for (int i = 0; i < headers.length; i++) {
            headers[i] = new BasicHeader("header" + i, "value" + i);
        }
        ProtocolVersion version = new HttpVersion(1, 1);
        String body = "Lorem ipsum dolor sit amet";

        CacheEntry cacheEntry = new CacheEntry(new Date(), new Date(), version, headers,
                new CacheEntity(body.getBytes("US-ASCII"), null, null), 200, "OK");

        return cacheEntry;

    }

    private boolean areEqual(CacheEntry one, CacheEntry two) throws IOException {

        if (!one.getRequestDate().equals(two.getRequestDate()))
            return false;
        if (!one.getResponseDate().equals(two.getResponseDate()))
            return false;
        if (!one.getProtocolVersion().equals(two.getProtocolVersion()))
            return false;

        byte[] bytesOne, bytesTwo;

        ByteArrayOutputStream streamOne = new ByteArrayOutputStream();
        one.getBody().writeTo(streamOne);
        bytesOne = streamOne.toByteArray();

        ByteArrayOutputStream streamTwo = new ByteArrayOutputStream();

        two.getBody().writeTo(streamTwo);
        bytesTwo = streamTwo.toByteArray();


        if (!Arrays.equals(bytesOne, bytesTwo))
            return false;

        Header[] oneHeaders = one.getAllHeaders();
        Header[] twoHeaders = one.getAllHeaders();
        if (!(oneHeaders.length == twoHeaders.length))
            return false;
        for (int i = 0; i < oneHeaders.length; i++) {
            if (!oneHeaders[i].getName().equals(twoHeaders[i].getName()))
                return false;
            if (!oneHeaders[i].getValue().equals(twoHeaders[i].getValue()))
                return false;
        }

        return true;

    }

}
