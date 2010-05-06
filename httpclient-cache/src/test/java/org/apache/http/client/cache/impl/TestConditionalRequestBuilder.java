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
package org.apache.http.client.cache.impl;

import java.util.Date;

import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.ProtocolException;
import org.apache.http.ProtocolVersion;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpRequest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestConditionalRequestBuilder {

    private ConditionalRequestBuilder impl;

    @Before
    public void setUp() throws Exception {
        impl = new ConditionalRequestBuilder();
    }

    @Test
    public void testBuildConditionalRequestWithLastModified() throws ProtocolException {
        String theMethod = "GET";
        String theUri = "/theuri";
        String lastModified = "this is my last modified date";

        HttpRequest request = new BasicHttpRequest(theMethod, theUri);
        request.addHeader("Accept-Encoding", "gzip");

        Header[] headers = new Header[] {
                new BasicHeader("Date", DateUtils.formatDate(new Date())),
                new BasicHeader("Last-Modified", lastModified) };

        CacheEntry cacheEntry = new CacheEntry(new Date(),new Date(),new ProtocolVersion("HTTP",1,1),headers, new byte[]{},200,"OK");
        HttpRequest newRequest = impl.buildConditionalRequest(request, cacheEntry);

        Assert.assertNotSame(request, newRequest);

        Assert.assertEquals(theMethod, newRequest.getRequestLine().getMethod());
        Assert.assertEquals(theUri, newRequest.getRequestLine().getUri());
        Assert.assertEquals(request.getRequestLine().getProtocolVersion(), newRequest
                .getRequestLine().getProtocolVersion());
        Assert.assertEquals(2, newRequest.getAllHeaders().length);

        Assert.assertEquals("Accept-Encoding", newRequest.getAllHeaders()[0].getName());
        Assert.assertEquals("gzip", newRequest.getAllHeaders()[0].getValue());

        Assert.assertEquals("If-Modified-Since", newRequest.getAllHeaders()[1].getName());
        Assert.assertEquals(lastModified, newRequest.getAllHeaders()[1].getValue());
    }

    @Test
    public void testBuildConditionalRequestWithETag() throws ProtocolException {
        String theMethod = "GET";
        String theUri = "/theuri";
        String theETag = "this is my eTag";

        HttpRequest request = new BasicHttpRequest(theMethod, theUri);
        request.addHeader("Accept-Encoding", "gzip");

        Header[] headers = new Header[] {
                new BasicHeader("Date", DateUtils.formatDate(new Date())),
                new BasicHeader("Last-Modified", DateUtils.formatDate(new Date())),
                new BasicHeader("ETag", theETag) };

        CacheEntry cacheEntry = new CacheEntry(new Date(),new Date(),new ProtocolVersion("HTTP",1,1),headers, new byte[]{},200,"OK");


        HttpRequest newRequest = impl.buildConditionalRequest(request, cacheEntry);

        Assert.assertNotSame(request, newRequest);

        Assert.assertEquals(theMethod, newRequest.getRequestLine().getMethod());
        Assert.assertEquals(theUri, newRequest.getRequestLine().getUri());
        Assert.assertEquals(request.getRequestLine().getProtocolVersion(), newRequest
                .getRequestLine().getProtocolVersion());

        Assert.assertEquals(2, newRequest.getAllHeaders().length);

        Assert.assertEquals("Accept-Encoding", newRequest.getAllHeaders()[0].getName());
        Assert.assertEquals("gzip", newRequest.getAllHeaders()[0].getValue());

        Assert.assertEquals("If-None-Match", newRequest.getAllHeaders()[1].getName());
        Assert.assertEquals(theETag, newRequest.getAllHeaders()[1].getValue());
    }

}
