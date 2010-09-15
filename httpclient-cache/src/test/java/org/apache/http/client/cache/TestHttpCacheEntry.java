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
package org.apache.http.client.cache;

import static org.easymock.classextension.EasyMock.*;
import java.util.Date;

import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicStatusLine;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestHttpCacheEntry {

    private Date now;
    private Date elevenSecondsAgo;
    private Date nineSecondsAgo;
    private Resource mockResource;
    private StatusLine statusLine;

    @Before
    public void setUp() {
        now = new Date();
        elevenSecondsAgo = new Date(now.getTime() - 11 * 1000L);
        nineSecondsAgo = new Date(now.getTime() - 9 * 1000L);
        statusLine = new BasicStatusLine(HttpVersion.HTTP_1_1,
                HttpStatus.SC_OK, "OK");
        mockResource = createMock(Resource.class);
    }

    private HttpCacheEntry makeEntry(Header[] headers) {
        return new HttpCacheEntry(elevenSecondsAgo, nineSecondsAgo,
                statusLine, headers, mockResource, null);
    }

    @Test
    public void testGetHeadersReturnsCorrectHeaders() {
        Header[] headers = { new BasicHeader("foo", "fooValue"),
                new BasicHeader("bar", "barValue1"),
                new BasicHeader("bar", "barValue2")
        };
        HttpCacheEntry entry = makeEntry(headers);
        Assert.assertEquals(2, entry.getHeaders("bar").length);
    }

    @Test
    public void testGetFirstHeaderReturnsCorrectHeader() {
        Header[] headers = { new BasicHeader("foo", "fooValue"),
                new BasicHeader("bar", "barValue1"),
                new BasicHeader("bar", "barValue2")
        };
        HttpCacheEntry entry = makeEntry(headers);
        Assert.assertEquals("barValue1", entry.getFirstHeader("bar").getValue());
    }

    @Test
    public void testGetHeadersReturnsEmptyArrayIfNoneMatch() {
        Header[] headers = { new BasicHeader("foo", "fooValue"),
                new BasicHeader("bar", "barValue1"),
                new BasicHeader("bar", "barValue2")
        };
        HttpCacheEntry entry = makeEntry(headers);

        Assert.assertEquals(0, entry.getHeaders("baz").length);
    }

    @Test
    public void testGetFirstHeaderReturnsNullIfNoneMatch() {
        Header[] headers = { new BasicHeader("foo", "fooValue"),
                new BasicHeader("bar", "barValue1"),
                new BasicHeader("bar", "barValue2")
        };
        HttpCacheEntry entry = makeEntry(headers);

        Assert.assertEquals(null, entry.getFirstHeader("quux"));
    }

    @Test
    public void testCacheEntryWithNoVaryHeaderDoesNotHaveVariants() {
        Header[] headers = new Header[0];
        HttpCacheEntry entry = makeEntry(headers);
        Assert.assertFalse(entry.hasVariants());
    }

    @Test
    public void testCacheEntryWithOneVaryHeaderHasVariants() {
        Header[] headers = { new BasicHeader("Vary", "User-Agent") };
        HttpCacheEntry entry = makeEntry(headers);
        Assert.assertTrue(entry.hasVariants());
    }

    @Test
    public void testCacheEntryWithMultipleVaryHeadersHasVariants() {
        Header[] headers = { new BasicHeader("Vary", "User-Agent"),
                new BasicHeader("Vary", "Accept-Encoding")
        };
        HttpCacheEntry entry = makeEntry(headers);
        Assert.assertTrue(entry.hasVariants());
    }

    @Test
    public void testCacheEntryWithVaryStarHasVariants(){
        Header[] headers = { new BasicHeader("Vary", "*") };
        HttpCacheEntry entry = makeEntry(headers);
        Assert.assertTrue(entry.hasVariants());
    }

}
