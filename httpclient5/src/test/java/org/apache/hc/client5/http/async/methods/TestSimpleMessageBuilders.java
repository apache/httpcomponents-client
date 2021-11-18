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

package org.apache.hc.client5.http.async.methods;

import static org.hamcrest.MatcherAssert.assertThat;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.hc.client5.http.ContentTypeMatcher;
import org.apache.hc.client5.http.HeaderMatcher;
import org.apache.hc.client5.http.HeadersMatcher;
import org.apache.hc.client5.http.NameValuePairsMatcher;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.net.URIAuthority;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Simple tests for {@link SimpleResponseBuilder} and {@link SimpleRequestBuilder}.
 */
public class TestSimpleMessageBuilders {

    @Test
    public void testResponseBasics() throws Exception {
        final SimpleResponseBuilder builder = SimpleResponseBuilder.create(200);
        Assertions.assertEquals(200, builder.getStatus());
        Assertions.assertNull(builder.getHeaders());
        Assertions.assertNull(builder.getVersion());

        final SimpleHttpResponse r1 = builder.build();
        Assertions.assertNotNull(r1);
        Assertions.assertEquals(200, r1.getCode());
        Assertions.assertNull(r1.getVersion());

        builder.setStatus(500);
        builder.setVersion(HttpVersion.HTTP_1_0);
        Assertions.assertEquals(500, builder.getStatus());
        Assertions.assertEquals(HttpVersion.HTTP_1_0, builder.getVersion());

        final SimpleHttpResponse r2 = builder.build();
        Assertions.assertEquals(500, r2.getCode());
        Assertions.assertEquals(HttpVersion.HTTP_1_0, r2.getVersion());

        builder.addHeader("h1", "v1");
        builder.addHeader("h1", "v2");
        builder.addHeader("h2", "v2");
        assertThat(builder.getHeaders(), HeadersMatcher.same(
                new BasicHeader("h1", "v1"), new BasicHeader("h1", "v2"), new BasicHeader("h2", "v2")));
        assertThat(builder.getHeaders("h1"), HeadersMatcher.same(
                new BasicHeader("h1", "v1"), new BasicHeader("h1", "v2")));
        assertThat(builder.getFirstHeader("h1"), HeaderMatcher.same("h1", "v1"));
        assertThat(builder.getLastHeader("h1"), HeaderMatcher.same("h1", "v2"));

        final SimpleHttpResponse r3 = builder.build();
        assertThat(r3.getHeaders(), HeadersMatcher.same(
                new BasicHeader("h1", "v1"), new BasicHeader("h1", "v2"), new BasicHeader("h2", "v2")));
        assertThat(r3.getHeaders("h1"), HeadersMatcher.same(
                new BasicHeader("h1", "v1"), new BasicHeader("h1", "v2")));
        assertThat(r3.getFirstHeader("h1"), HeaderMatcher.same("h1", "v1"));
        assertThat(r3.getLastHeader("h1"), HeaderMatcher.same("h1", "v2"));

        builder.removeHeader(new BasicHeader("h1", "v2"));
        assertThat(builder.getHeaders("h1"), HeadersMatcher.same(new BasicHeader("h1", "v1")));
        assertThat(builder.getHeaders(), HeadersMatcher.same(
                new BasicHeader("h1", "v1"), new BasicHeader("h2", "v2")));

        final SimpleHttpResponse r4 = builder.build();
        assertThat(r4.getHeaders("h1"), HeadersMatcher.same(new BasicHeader("h1", "v1")));
        assertThat(r4.getHeaders(), HeadersMatcher.same(
                new BasicHeader("h1", "v1"), new BasicHeader("h2", "v2")));

        builder.removeHeaders("h1");
        assertThat(builder.getHeaders("h1"), HeadersMatcher.same());
        assertThat(builder.getHeaders(), HeadersMatcher.same(new BasicHeader("h2", "v2")));

        final SimpleHttpResponse r5 = builder.build();
        assertThat(r5.getHeaders("h1"), HeadersMatcher.same());
        assertThat(r5.getHeaders(), HeadersMatcher.same(new BasicHeader("h2", "v2")));
    }

    @Test
    public void testRequestBasics() throws Exception {
        final SimpleRequestBuilder builder = SimpleRequestBuilder.get();
        Assertions.assertEquals(URI.create("/"), builder.getUri());
        Assertions.assertEquals("GET", builder.getMethod());
        Assertions.assertNull(builder.getScheme());
        Assertions.assertNull(builder.getAuthority());
        Assertions.assertNull(builder.getPath());
        Assertions.assertNull(builder.getHeaders());
        Assertions.assertNull(builder.getVersion());
        Assertions.assertNull(builder.getCharset());
        Assertions.assertNull(builder.getParameters());

        final SimpleHttpRequest r1 = builder.build();
        Assertions.assertNotNull(r1);
        Assertions.assertEquals("GET", r1.getMethod());
        Assertions.assertNull(r1.getScheme());
        Assertions.assertNull(r1.getAuthority());
        Assertions.assertNull(r1.getPath());
        Assertions.assertEquals(URI.create("/"), r1.getUri());
        Assertions.assertNull(r1.getVersion());

        builder.setUri(URI.create("http://host:1234/blah?param=value"));
        builder.setVersion(HttpVersion.HTTP_1_1);
        Assertions.assertEquals("http", builder.getScheme());
        Assertions.assertEquals(new URIAuthority("host", 1234), builder.getAuthority());
        Assertions.assertEquals("/blah?param=value", builder.getPath());
        Assertions.assertEquals(URI.create("http://host:1234/blah?param=value"), builder.getUri());
        Assertions.assertEquals(HttpVersion.HTTP_1_1, builder.getVersion());

        final SimpleHttpRequest r2 = builder.build();
        Assertions.assertEquals("GET", r2.getMethod());
        Assertions.assertEquals("http", r2.getScheme());
        Assertions.assertEquals(new URIAuthority("host", 1234), r2.getAuthority());
        Assertions.assertEquals("/blah?param=value", r2.getPath());
        Assertions.assertEquals(URI.create("http://host:1234/blah?param=value"), r2.getUri());
        Assertions.assertEquals(HttpVersion.HTTP_1_1, builder.getVersion());

        builder.setCharset(StandardCharsets.US_ASCII);
        builder.addParameter("param1", "value1");
        builder.addParameter("param2", null);
        builder.addParameters(new BasicNameValuePair("param3", "value3"), new BasicNameValuePair("param4", null));

        Assertions.assertEquals(builder.getParameters(), Arrays.asList(
                new BasicNameValuePair("param1", "value1"), new BasicNameValuePair("param2", null),
                new BasicNameValuePair("param3", "value3"), new BasicNameValuePair("param4", null)
        ));
        Assertions.assertEquals(URI.create("http://host:1234/blah?param=value"), builder.getUri());

        final SimpleHttpRequest r3 = builder.build();
        Assertions.assertEquals("GET", r3.getMethod());
        Assertions.assertEquals("http", r3.getScheme());
        Assertions.assertEquals(new URIAuthority("host", 1234), r3.getAuthority());
        Assertions.assertEquals("/blah?param=value&param1=value1&param2&param3=value3&param4", r3.getPath());
        Assertions.assertEquals(URI.create("http://host:1234/blah?param=value&param1=value1&param2&param3=value3&param4"),
                r3.getUri());

        builder.addHeader("h1", "v1");
        builder.addHeader("h1", "v2");
        builder.addHeader("h2", "v2");
        assertThat(builder.getHeaders(), HeadersMatcher.same(
                new BasicHeader("h1", "v1"), new BasicHeader("h1", "v2"), new BasicHeader("h2", "v2")));
        assertThat(builder.getHeaders("h1"), HeadersMatcher.same(
                new BasicHeader("h1", "v1"), new BasicHeader("h1", "v2")));
        assertThat(builder.getFirstHeader("h1"), HeaderMatcher.same("h1", "v1"));
        assertThat(builder.getLastHeader("h1"), HeaderMatcher.same("h1", "v2"));

        final SimpleHttpRequest r4 = builder.build();
        assertThat(r4.getHeaders(), HeadersMatcher.same(
                new BasicHeader("h1", "v1"), new BasicHeader("h1", "v2"), new BasicHeader("h2", "v2")));
        assertThat(r4.getHeaders("h1"), HeadersMatcher.same(
                new BasicHeader("h1", "v1"), new BasicHeader("h1", "v2")));
        assertThat(r4.getFirstHeader("h1"), HeaderMatcher.same("h1", "v1"));
        assertThat(r4.getLastHeader("h1"), HeaderMatcher.same("h1", "v2"));

        builder.removeHeader(new BasicHeader("h1", "v2"));
        assertThat(builder.getHeaders("h1"), HeadersMatcher.same(new BasicHeader("h1", "v1")));
        assertThat(builder.getHeaders(), HeadersMatcher.same(
                new BasicHeader("h1", "v1"), new BasicHeader("h2", "v2")));

        final SimpleHttpRequest r5 = builder.build();
        assertThat(r5.getHeaders("h1"), HeadersMatcher.same(new BasicHeader("h1", "v1")));
        assertThat(r5.getHeaders(), HeadersMatcher.same(
                new BasicHeader("h1", "v1"), new BasicHeader("h2", "v2")));

        builder.removeHeaders("h1");
        assertThat(builder.getHeaders("h1"), HeadersMatcher.same());
        assertThat(builder.getHeaders(), HeadersMatcher.same(new BasicHeader("h2", "v2")));

        final SimpleHttpRequest r6 = builder.build();
        assertThat(r6.getHeaders("h1"), HeadersMatcher.same());
        assertThat(r6.getHeaders(), HeadersMatcher.same(new BasicHeader("h2", "v2")));
    }

    @Test
    public void testResponseCopy() throws Exception {
        final SimpleHttpResponse response = SimpleHttpResponse.create(400);
        response.addHeader("h1", "v1");
        response.addHeader("h1", "v2");
        response.addHeader("h2", "v2");
        response.setVersion(HttpVersion.HTTP_2);

        final SimpleResponseBuilder builder = SimpleResponseBuilder.copy(response);
        Assertions.assertEquals(400, builder.getStatus());
        Assertions.assertEquals(HttpVersion.HTTP_2, builder.getVersion());
        assertThat(builder.getHeaders(), HeadersMatcher.same(
                new BasicHeader("h1", "v1"), new BasicHeader("h1", "v2"), new BasicHeader("h2", "v2")));
    }

    @Test
    public void testRequestCopy() throws Exception {
        final SimpleHttpRequest request = SimpleHttpRequest.create(Method.GET, URI.create("https://host:3456/stuff?blah")) ;
        request.addHeader("h1", "v1");
        request.addHeader("h1", "v2");
        request.addHeader("h2", "v2");
        request.setVersion(HttpVersion.HTTP_2);

        final SimpleRequestBuilder builder = SimpleRequestBuilder.copy(request);
        Assertions.assertEquals("GET", builder.getMethod());
        Assertions.assertEquals("https", builder.getScheme());
        Assertions.assertEquals(new URIAuthority("host", 3456), builder.getAuthority());
        Assertions.assertEquals("/stuff?blah", builder.getPath());
        Assertions.assertEquals(HttpVersion.HTTP_2, builder.getVersion());
        assertThat(builder.getHeaders(), HeadersMatcher.same(
                new BasicHeader("h1", "v1"), new BasicHeader("h1", "v2"), new BasicHeader("h2", "v2")));
    }

    @Test
    public void testGetParameters() throws Exception {
        final SimpleRequestBuilder builder = SimpleRequestBuilder.get(URI.create("https://host:3456/stuff?p0=p0"));
        builder.addParameter("p1", "v1");
        builder.addParameters(new BasicNameValuePair("p2", "v2"), new BasicNameValuePair("p3", "v3"));
        builder.addParameter(new BasicNameValuePair("p3", "v3.1"));
        Assertions.assertEquals("GET", builder.getMethod());
        Assertions.assertEquals("https", builder.getScheme());
        Assertions.assertEquals(new URIAuthority("host", 3456), builder.getAuthority());
        Assertions.assertEquals("/stuff?p0=p0", builder.getPath());
        assertThat(builder.getParameters(), NameValuePairsMatcher.same(
                new BasicNameValuePair("p1", "v1"), new BasicNameValuePair("p2", "v2"),
                new BasicNameValuePair("p3", "v3"), new BasicNameValuePair("p3", "v3.1")));
        final SimpleHttpRequest request = builder.build();
        assertThat(request.getPath(), CoreMatchers.equalTo("/stuff?p0=p0&p1=v1&p2=v2&p3=v3&p3=v3.1"));
        Assertions.assertNull(request.getBody());
    }

    @Test
    public void testPostParameters() throws Exception {
        final SimpleRequestBuilder builder = SimpleRequestBuilder.post(URI.create("https://host:3456/stuff?p0=p0"));
        builder.addParameter("p1", "v1");
        builder.addParameters(new BasicNameValuePair("p2", "v2"), new BasicNameValuePair("p3", "v3"));
        builder.addParameter(new BasicNameValuePair("p3", "v3.1"));
        Assertions.assertEquals("POST", builder.getMethod());
        Assertions.assertEquals("https", builder.getScheme());
        Assertions.assertEquals(new URIAuthority("host", 3456), builder.getAuthority());
        Assertions.assertEquals("/stuff?p0=p0", builder.getPath());
        assertThat(builder.getParameters(), NameValuePairsMatcher.same(
                new BasicNameValuePair("p1", "v1"), new BasicNameValuePair("p2", "v2"),
                new BasicNameValuePair("p3", "v3"), new BasicNameValuePair("p3", "v3.1")));
        final SimpleHttpRequest request = builder.build();
        assertThat(request.getPath(), CoreMatchers.equalTo("/stuff?p0=p0"));
        Assertions.assertNotNull(request.getBody());
        assertThat(request.getBody().getContentType(),
                ContentTypeMatcher.sameMimeType(ContentType.APPLICATION_FORM_URLENCODED));
        assertThat(request.getBody().getBodyText(),
                CoreMatchers.equalTo("p1=v1&p2=v2&p3=v3&p3=v3.1"));
    }

}
