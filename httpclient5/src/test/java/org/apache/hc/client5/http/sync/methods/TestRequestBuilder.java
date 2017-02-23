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

package org.apache.hc.client5.http.sync.methods;

import java.net.URI;
import java.util.List;

import org.apache.hc.client5.http.config.Configurable;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.entity.BasicHttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.junit.Assert;
import org.junit.Test;

public class TestRequestBuilder {

    @Test
    public void testBasicGet() throws Exception {
        final HttpUriRequest request = RequestBuilder.get().build();
        Assert.assertNotNull(request);
        Assert.assertEquals("GET", request.getMethod());
        Assert.assertEquals(URI.create("/"), request.getUri());
    }

    @Test
    public void testArbitraryMethod() throws Exception {
        final HttpUriRequest request = RequestBuilder.create("Whatever").build();
        Assert.assertNotNull(request);
        Assert.assertEquals("Whatever", request.getMethod());
        Assert.assertEquals(URI.create("/"), request.getUri());
    }

    @Test
    public void testBasicWithEntity() throws Exception {
        final HttpEntity entity = new BasicHttpEntity();
        final HttpUriRequest request = RequestBuilder.post().setEntity(entity).build();
        Assert.assertNotNull(request);
        Assert.assertEquals("POST", request.getMethod());
        Assert.assertEquals(URI.create("/"), request.getUri());
        Assert.assertSame(entity, request.getEntity());
    }

    @Test
    public void testGetWithEntity() throws Exception {
        final HttpEntity entity = new BasicHttpEntity();
        final HttpUriRequest request = RequestBuilder.get().setEntity(entity).build();
        Assert.assertNotNull(request);
        Assert.assertEquals("GET", request.getMethod());
        Assert.assertEquals(URI.create("/"), request.getUri());
        Assert.assertSame(entity, request.getEntity());
    }

    @Test
    public void testAddParameters1() throws Exception {
        final HttpUriRequest request = RequestBuilder.get()
                .addParameter("p1", "this")
                .addParameter("p2", "that")
                .build();
        Assert.assertEquals(new URI("/?p1=this&p2=that"), request.getUri());
    }

    @Test
    public void testAddParameters2() throws Exception {
        final HttpUriRequest request = RequestBuilder.get()
                .addParameter("p1", "this")
                .addParameters(new BasicNameValuePair("p2", "that"))
                .build();
        Assert.assertEquals(new URI("/?p1=this&p2=that"), request.getUri());
    }

    @Test
    public void testAddParameters3() throws Exception {
        final HttpUriRequest request = RequestBuilder.post()
                .addParameter("p1", "this")
                .addParameter("p2", "that")
                .build();
        final HttpEntity entity = request.getEntity();
        Assert.assertNotNull(entity);
        Assert.assertEquals(new URI("/"), request.getUri());
        Assert.assertEquals("p1=this&p2=that", EntityUtils.toString(entity));
    }

    @Test
    public void testAddParameters4() throws Exception {
        final HttpUriRequest request = RequestBuilder.post()
                .setUri("http://targethost/?blah")
                .addParameter("p1", "this")
                .addParameter("p2", "that")
                .setEntity(new StringEntity("blah"))
                .build();
        Assert.assertEquals(new URI("http://targethost/?blah&p1=this&p2=that"), request.getUri());
    }

    @Test
    public void testCopy() throws Exception {
        final HttpEntity entity = new StringEntity("stuff");
        final RequestConfig config = RequestConfig.custom().build();
        final HttpUriRequest request = RequestBuilder.put()
            .setUri(URI.create("/stuff"))
            .setVersion(HttpVersion.HTTP_1_0)
            .addHeader("header1", "stuff")
            .setHeader("header2", "more stuff")
            .setEntity(entity)
            .setConfig(config)
            .build();
        Assert.assertNotNull(request);
        Assert.assertEquals("PUT", request.getMethod());
        Assert.assertEquals(URI.create("/stuff"), request.getUri());
        Assert.assertEquals(HttpVersion.HTTP_1_0, request.getVersion());

        final HttpUriRequest copy = RequestBuilder.copy(request).setUri("/other-stuff").build();
        Assert.assertEquals("PUT", copy.getMethod());
        Assert.assertEquals(URI.create("/other-stuff"), copy.getUri());
        Assert.assertSame(entity, copy.getEntity());
        Assert.assertTrue(copy instanceof Configurable);
        Assert.assertSame(config, ((Configurable) copy).getConfig());
    }

    @Test
    public void testCopyWithQueryParams() throws Exception {
        final HttpGet get = new HttpGet("/stuff?p1=this&p2=that");
        final RequestBuilder builder = RequestBuilder.copy(get);
        final List<NameValuePair> parameters = builder.getParameters();
        Assert.assertNotNull(parameters);
        Assert.assertEquals(0, parameters.size());
        Assert.assertEquals(new URI("/stuff?p1=this&p2=that"), builder.getUri());
    }

    @Test
    public void testCopyWithFormParams() throws Exception {
        final HttpPost post = new HttpPost("/stuff?p1=wtf");
        post.setEntity(new StringEntity("p1=this&p2=that", ContentType.APPLICATION_FORM_URLENCODED));
        final RequestBuilder builder = RequestBuilder.copy(post);
        final List<NameValuePair> parameters = builder.getParameters();
        Assert.assertNotNull(parameters);
        Assert.assertEquals(2, parameters.size());
        assertNameValuePair(new BasicNameValuePair("p1", "this"), parameters.get(0));
        assertNameValuePair(new BasicNameValuePair("p2", "that"), parameters.get(1));
        Assert.assertEquals(new URI("/stuff?p1=wtf"), builder.getUri());
        Assert.assertNull(builder.getEntity());
    }

    private static void assertNameValuePair (
            final NameValuePair expected,
            final NameValuePair result) {
        Assert.assertNotNull(result);
        Assert.assertEquals(expected.getName(), result.getName());
        Assert.assertEquals(expected.getValue(), result.getValue());
    }

    @Test
    public void testCopyWithStringEntity() throws Exception {
        final HttpPost post = new HttpPost("/stuff?p1=wtf");
        final HttpEntity entity = new StringEntity("p1=this&p2=that", ContentType.TEXT_PLAIN);
        post.setEntity(entity);
        final RequestBuilder builder = RequestBuilder.copy(post);
        final List<NameValuePair> parameters = builder.getParameters();
        Assert.assertNotNull(parameters);
        Assert.assertEquals(0, parameters.size());
        Assert.assertEquals(new URI("/stuff?p1=wtf"), builder.getUri());
        Assert.assertSame(entity, builder.getEntity());
    }

    @Test
    public void testCopyAndSetUri() throws Exception {
        final URI uri1 = URI.create("http://host1.com/path?param=something");
        final URI uri2 = URI.create("http://host2.com/path?param=somethingdifferent");
        final HttpGet request1 = new HttpGet(uri1);
        final HttpUriRequest request2 = RequestBuilder.copy(request1).setUri(uri2).build();
        Assert.assertEquals(request2.getUri(), uri2);
    }

    @Test
    public void testGettersAndMutators() throws Exception {
        final HttpEntity entity = new StringEntity("stuff");
        final RequestConfig config = RequestConfig.custom().build();
        final Header h1 = new BasicHeader("header1", "stuff");
        final Header h2 = new BasicHeader("header1", "more-stuff");
        final RequestBuilder builder = RequestBuilder.put()
            .setUri("/stuff")
            .setVersion(HttpVersion.HTTP_1_0)
            .addHeader(h1)
            .addHeader(h2)
            .setEntity(entity)
            .setConfig(config);
        Assert.assertEquals("PUT", builder.getMethod());
        Assert.assertEquals(URI.create("/stuff"), builder.getUri());
        Assert.assertEquals(HttpVersion.HTTP_1_0, builder.getVersion());
        Assert.assertSame(h1, builder.getFirstHeader("header1"));
        Assert.assertSame(h2, builder.getLastHeader("header1"));
        Assert.assertEquals(2, builder.getHeaders("header1").length);
        Assert.assertSame(entity, builder.getEntity());
        Assert.assertSame(config, builder.getConfig());

        builder.setUri((String) null)
            .setVersion(null)
            .removeHeader(h1)
            .removeHeaders("header1")
            .removeHeader(h2)
            .setEntity(null)
            .setConfig(null);
        Assert.assertEquals(null, builder.getUri());
        Assert.assertEquals(null, builder.getVersion());
        Assert.assertSame(null, builder.getFirstHeader("header1"));
        Assert.assertSame(null, builder.getLastHeader("header1"));
        Assert.assertEquals(0, builder.getHeaders("header1").length);
        Assert.assertSame(null, builder.getEntity());
        Assert.assertSame(null, builder.getConfig());

        builder.setHeader(h2)
            .setHeader("header1", "a-lot-more-stuff");
        Assert.assertSame("a-lot-more-stuff", builder.getLastHeader("header1").getValue());
        Assert.assertEquals(1, builder.getHeaders("header1").length);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testCopyNull() throws Exception {
        RequestBuilder.copy(null);
    }

}
