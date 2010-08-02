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

import java.util.Date;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.message.BasicHttpResponse;
import org.junit.Assert;
import org.junit.Test;

public class TestCacheEntryGenerator {

    @Test
    public void testEntryMatchesInputs() {

        MemCacheEntryFactory gen = new MemCacheEntryFactory();

        HttpResponse response = new BasicHttpResponse(new ProtocolVersion("HTTP", 1, 1),
                HttpStatus.SC_OK, "Success");
        HttpEntity entity = new ByteArrayEntity(new byte[] {});
        response.setEntity(entity);

        response.setHeader("fooHeader", "fooHeaderValue");

        HttpCacheEntry entry = gen.generateEntry(new Date(), new Date(), response, new byte[] {});

        Assert.assertEquals("HTTP", entry.getProtocolVersion().getProtocol());
        Assert.assertEquals(1, entry.getProtocolVersion().getMajor());
        Assert.assertEquals(1, entry.getProtocolVersion().getMinor());
        Assert.assertEquals("fooHeaderValue", entry.getFirstHeader("fooHeader").getValue());
    }
}
