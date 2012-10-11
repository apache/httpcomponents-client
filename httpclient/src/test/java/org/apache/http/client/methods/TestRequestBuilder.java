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

package org.apache.http.client.methods;

import java.net.URI;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpVersion;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.junit.Assert;
import org.junit.Test;

public class TestRequestBuilder {

    @Test
    public void testBasicGet() throws Exception {
        HttpUriRequest request = RequestBuilder.create().build();
        Assert.assertNotNull(request);
        Assert.assertEquals("GET", request.getMethod());
        Assert.assertEquals(URI.create("/"), request.getURI());
        Assert.assertEquals(HttpVersion.HTTP_1_1, request.getProtocolVersion());
        Assert.assertFalse(request instanceof HttpEntityEnclosingRequest);
    }

    @Test
    public void testBasicWithEntity() throws Exception {
        HttpEntity entity = new BasicHttpEntity();
        HttpUriRequest request = RequestBuilder.create().setEntity(entity).build();
        Assert.assertNotNull(request);
        Assert.assertEquals("POST", request.getMethod());
        Assert.assertEquals(URI.create("/"), request.getURI());
        Assert.assertEquals(HttpVersion.HTTP_1_1, request.getProtocolVersion());
        Assert.assertTrue(request instanceof HttpEntityEnclosingRequest);
        Assert.assertSame(entity, ((HttpEntityEnclosingRequest) request).getEntity());
    }

    @Test
    public void testGetWithEntity() throws Exception {
        HttpEntity entity = new BasicHttpEntity();
        HttpUriRequest request = RequestBuilder.create().setMethod("get").setEntity(entity).build();
        Assert.assertNotNull(request);
        Assert.assertEquals("GET", request.getMethod());
        Assert.assertEquals(URI.create("/"), request.getURI());
        Assert.assertEquals(HttpVersion.HTTP_1_1, request.getProtocolVersion());
        Assert.assertTrue(request instanceof HttpEntityEnclosingRequest);
        Assert.assertSame(entity, ((HttpEntityEnclosingRequest) request).getEntity());
    }

    @Test
    public void testCopy() throws Exception {
        HttpEntity entity = new StringEntity("stuff");
        HttpParams params = new BasicHttpParams();
        HttpUriRequest request = RequestBuilder.create()
            .setMethod("put")
            .setUri(URI.create("/stuff"))
            .setVersion(HttpVersion.HTTP_1_0)
            .addHeader("header1", "stuff")
            .setHeader("header2", "more stuff")
            .setEntity(entity)
            .setParams(params)
            .build();
        Assert.assertNotNull(request);
        Assert.assertEquals("PUT", request.getMethod());
        Assert.assertEquals(URI.create("/stuff"), request.getURI());
        Assert.assertEquals(HttpVersion.HTTP_1_0, request.getProtocolVersion());
        Assert.assertTrue(request instanceof HttpEntityEnclosingRequest);

        HttpUriRequest copy = RequestBuilder.copy(request).setUri("/other-stuff").build();
        Assert.assertEquals("PUT", copy.getMethod());
        Assert.assertEquals(URI.create("/other-stuff"), copy.getURI());
        Assert.assertTrue(copy instanceof HttpEntityEnclosingRequest);
        Assert.assertSame(entity, ((HttpEntityEnclosingRequest) copy).getEntity());
        Assert.assertSame(params, copy.getParams());
    }

    @Test
    public void testClone() throws Exception {
        HttpEntity entity = new StringEntity("stuff");
        HttpParams params = new BasicHttpParams();
        HttpUriRequest request = RequestBuilder.create()
            .setMethod("put")
            .setUri(URI.create("/stuff"))
            .setVersion(HttpVersion.HTTP_1_0)
            .addHeader("header1", "stuff")
            .setHeader("header2", "more stuff")
            .setEntity(entity)
            .setParams(params)
            .build();
        Assert.assertNotNull(request);
        Assert.assertEquals("PUT", request.getMethod());
        Assert.assertEquals(URI.create("/stuff"), request.getURI());
        Assert.assertEquals(HttpVersion.HTTP_1_0, request.getProtocolVersion());
        Assert.assertTrue(request instanceof HttpEntityEnclosingRequest);

        HttpUriRequest clone = RequestBuilder.clone(request).setUri("/other-stuff").build();
        Assert.assertEquals("PUT", clone.getMethod());
        Assert.assertEquals(URI.create("/other-stuff"), clone.getURI());
        Assert.assertTrue(clone instanceof HttpEntityEnclosingRequest);
        Assert.assertNotNull(((HttpEntityEnclosingRequest) clone).getEntity());
        Assert.assertNotSame(entity, ((HttpEntityEnclosingRequest) clone).getEntity());
        Assert.assertNotNull(clone.getParams());
        Assert.assertNotSame(params, clone.getParams());
    }

    @Test
    public void testCopyNull() throws Exception {
        HttpUriRequest copy = RequestBuilder.copy(null).setUri("/other-stuff").build();
        Assert.assertEquals("GET", copy.getMethod());
        Assert.assertEquals(URI.create("/other-stuff"), copy.getURI());
        Assert.assertFalse(copy instanceof HttpEntityEnclosingRequest);
    }

    @Test
    public void testGettersAndMutators() throws Exception {
        HttpEntity entity = new StringEntity("stuff");
        HttpParams params = new BasicHttpParams();
        Header h1 = new BasicHeader("header1", "stuff");
        Header h2 = new BasicHeader("header1", "more-stuff");
        RequestBuilder builder = RequestBuilder.create()
            .setMethod("put")
            .setUri("/stuff")
            .setVersion(HttpVersion.HTTP_1_0)
            .addHeader(h1)
            .addHeader(h2)
            .setEntity(entity)
            .setParams(params);
        Assert.assertEquals("put", builder.getMethod());
        Assert.assertEquals(URI.create("/stuff"), builder.getUri());
        Assert.assertEquals(HttpVersion.HTTP_1_0, builder.getVersion());
        Assert.assertSame(h1, builder.getFirstHeader("header1"));
        Assert.assertSame(h2, builder.getLastHeader("header1"));
        Assert.assertEquals(2, builder.getHeaders("header1").length);
        Assert.assertSame(entity, builder.getEntity());
        Assert.assertSame(params, builder.getParams());

        builder.setMethod(null)
            .setUri((String) null)
            .setVersion(null)
            .removeHeader(h1)
            .removeHeaders("header1")
            .removeHeader(h2)
            .setEntity(null)
            .setParams(null);
        Assert.assertEquals(null, builder.getMethod());
        Assert.assertEquals(null, builder.getUri());
        Assert.assertEquals(null, builder.getVersion());
        Assert.assertSame(null, builder.getFirstHeader("header1"));
        Assert.assertSame(null, builder.getLastHeader("header1"));
        Assert.assertEquals(0, builder.getHeaders("header1").length);
        Assert.assertSame(null, builder.getEntity());
        Assert.assertSame(null, builder.getParams());
    }

}
