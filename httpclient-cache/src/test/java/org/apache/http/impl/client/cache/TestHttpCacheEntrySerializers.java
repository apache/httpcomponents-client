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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.Header;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.HttpCacheEntrySerializer;
import org.apache.http.client.cache.Resource;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicStatusLine;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

public class TestHttpCacheEntrySerializers {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private HttpCacheEntrySerializer impl;
    
    @Before
    public void setUp() {
        impl = new DefaultHttpCacheEntrySerializer();
    }
    
    @Test
    public void canSerializeEntriesWithVariantMaps() throws Exception {
        readWriteVerify(makeCacheEntryWithVariantMap());
    }

    public void readWriteVerify(HttpCacheEntry writeEntry) throws IOException {
        // write the entry
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        impl.writeTo(writeEntry, out);

        // read the entry
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        HttpCacheEntry readEntry = impl.readFrom(in);

        // compare
        assertTrue(areEqual(readEntry, writeEntry));
    }

    private HttpCacheEntry makeCacheEntryWithVariantMap() throws UnsupportedEncodingException {
        Header[] headers = new Header[5];
        for (int i = 0; i < headers.length; i++) {
            headers[i] = new BasicHeader("header" + i, "value" + i);
        }
        String body = "Lorem ipsum dolor sit amet";

        ProtocolVersion pvObj = new ProtocolVersion("HTTP", 1, 1);
        StatusLine slObj = new BasicStatusLine(pvObj, 200, "ok");
        Map<String,String> variantMap = new HashMap<String,String>();
        variantMap.put("test variant 1","true");
        variantMap.put("test variant 2","true");
        HttpCacheEntry cacheEntry = new HttpCacheEntry(new Date(), new Date(),
                slObj, headers, new HeapResource(Base64.decodeBase64(body
                        .getBytes(UTF8.name()))), variantMap);

        return cacheEntry;
    }
    
    private boolean areEqual(HttpCacheEntry one, HttpCacheEntry two) throws IOException {
        // dates are only stored with second precision, so scrub milliseconds
        if (!((one.getRequestDate().getTime() / 1000) == (two.getRequestDate()
                .getTime() / 1000)))
            return false;
        if (!((one.getResponseDate().getTime() / 1000) == (two
                .getResponseDate().getTime() / 1000)))
            return false;
        if (!one.getProtocolVersion().equals(two.getProtocolVersion()))
            return false;

        byte[] onesByteArray = resourceToBytes(one.getResource());
        byte[] twosByteArray = resourceToBytes(two.getResource());

        if (!Arrays.equals(onesByteArray,twosByteArray))
            return false;

        Header[] oneHeaders = one.getAllHeaders();
        Header[] twoHeaders = two.getAllHeaders();
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

    private byte[] resourceToBytes(Resource res) throws IOException {
        InputStream inputStream = res.getInputStream();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        int readBytes;
        byte[] bytes = new byte[8096];
        while ((readBytes = inputStream.read(bytes)) > 0) {
            outputStream.write(bytes, 0, readBytes);
        }

        byte[] byteData = outputStream.toByteArray();

        inputStream.close();
        outputStream.close();

        return byteData;
    }
}
