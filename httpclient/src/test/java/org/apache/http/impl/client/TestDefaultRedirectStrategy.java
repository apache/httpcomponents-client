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
package org.apache.http.impl.client;

import java.net.URI;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpTrace;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.junit.Assert;
import org.junit.Test;

public class TestDefaultRedirectStrategy {

    @Test
    public void testIsRedirectable() {
        final DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
        Assert.assertTrue(redirectStrategy.isRedirectable(HttpGet.METHOD_NAME));
        Assert.assertTrue(redirectStrategy.isRedirectable(HttpHead.METHOD_NAME));
        Assert.assertFalse(redirectStrategy.isRedirectable(HttpPut.METHOD_NAME));
        Assert.assertFalse(redirectStrategy.isRedirectable(HttpPost.METHOD_NAME));
        Assert.assertFalse(redirectStrategy.isRedirectable(HttpDelete.METHOD_NAME));
    }

    @Test
    public void testIsRedirectedMovedTemporary() throws Exception {
        final DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
        final HttpClientContext context = HttpClientContext.create();
        final HttpGet httpget = new HttpGet("http://localhost/");
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_MOVED_TEMPORARILY, "Redirect");
        response.addHeader("Location", "http://localhost/stuff");
        Assert.assertTrue(redirectStrategy.isRedirected(httpget, response, context));
        final HttpPost httppost = new HttpPost("http://localhost/");
        Assert.assertFalse(redirectStrategy.isRedirected(httppost, response, context));
    }

    @Test
    public void testIsRedirectedMovedTemporaryNoLocation() throws Exception {
        final DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
        final HttpClientContext context = HttpClientContext.create();
        final HttpGet httpget = new HttpGet("http://localhost/");
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_MOVED_TEMPORARILY, "Redirect");
        Assert.assertFalse(redirectStrategy.isRedirected(httpget, response, context));
    }

    @Test
    public void testIsRedirectedMovedPermanently() throws Exception {
        final DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
        final HttpClientContext context = HttpClientContext.create();
        final HttpGet httpget = new HttpGet("http://localhost/");
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_MOVED_PERMANENTLY, "Redirect");
        Assert.assertTrue(redirectStrategy.isRedirected(httpget, response, context));
        final HttpPost httppost = new HttpPost("http://localhost/");
        Assert.assertFalse(redirectStrategy.isRedirected(httppost, response, context));
    }

    @Test
    public void testIsRedirectedTemporaryRedirect() throws Exception {
        final DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
        final HttpClientContext context = HttpClientContext.create();
        final HttpGet httpget = new HttpGet("http://localhost/");
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_TEMPORARY_REDIRECT, "Redirect");
        Assert.assertTrue(redirectStrategy.isRedirected(httpget, response, context));
        final HttpPost httppost = new HttpPost("http://localhost/");
        Assert.assertFalse(redirectStrategy.isRedirected(httppost, response, context));
    }

    @Test
    public void testIsRedirectedSeeOther() throws Exception {
        final DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
        final HttpClientContext context = HttpClientContext.create();
        final HttpGet httpget = new HttpGet("http://localhost/");
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_SEE_OTHER, "Redirect");
        Assert.assertTrue(redirectStrategy.isRedirected(httpget, response, context));
        final HttpPost httppost = new HttpPost("http://localhost/");
        Assert.assertTrue(redirectStrategy.isRedirected(httppost, response, context));
    }

    @Test
    public void testIsRedirectedUnknownStatus() throws Exception {
        final DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
        final HttpClientContext context = HttpClientContext.create();
        final HttpGet httpget = new HttpGet("http://localhost/");
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 333, "Redirect");
        Assert.assertFalse(redirectStrategy.isRedirected(httpget, response, context));
        final HttpPost httppost = new HttpPost("http://localhost/");
        Assert.assertFalse(redirectStrategy.isRedirected(httppost, response, context));
    }

    @Test
    public void testIsRedirectedInvalidInput() throws Exception {
        final DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
        final HttpClientContext context = HttpClientContext.create();
        final HttpGet httpget = new HttpGet("http://localhost/");
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_SEE_OTHER, "Redirect");
        try {
            redirectStrategy.isRedirected(null, response, context);
            Assert.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }
        try {
            redirectStrategy.isRedirected(httpget, null, context);
            Assert.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }
    }

    @Test
    public void testGetLocationUri() throws Exception {
        final DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
        final HttpClientContext context = HttpClientContext.create();
        final HttpGet httpget = new HttpGet("http://localhost/");
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_MOVED_TEMPORARILY, "Redirect");
        response.addHeader("Location", "http://localhost/stuff");
        final URI uri = redirectStrategy.getLocationURI(httpget, response, context);
        Assert.assertEquals(URI.create("http://localhost/stuff"), uri);
    }

    @Test(expected=ProtocolException.class)
    public void testGetLocationUriMissingHeader() throws Exception {
        final DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
        final HttpClientContext context = HttpClientContext.create();
        final HttpGet httpget = new HttpGet("http://localhost/");
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_MOVED_TEMPORARILY, "Redirect");
        redirectStrategy.getLocationURI(httpget, response, context);
    }

    @Test(expected=ProtocolException.class)
    public void testGetLocationUriInvalidLocation() throws Exception {
        final DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
        final HttpClientContext context = HttpClientContext.create();
        final HttpGet httpget = new HttpGet("http://localhost/");
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_MOVED_TEMPORARILY, "Redirect");
        response.addHeader("Location", "http://localhost/not valid");
        redirectStrategy.getLocationURI(httpget, response, context);
    }

    @Test
    public void testGetLocationUriRelative() throws Exception {
        final DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
        final HttpClientContext context = HttpClientContext.create();
        context.setAttribute(HttpCoreContext.HTTP_TARGET_HOST, new HttpHost("localhost"));
        final HttpGet httpget = new HttpGet("http://localhost/");
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_MOVED_TEMPORARILY, "Redirect");
        response.addHeader("Location", "/stuff");
        final URI uri = redirectStrategy.getLocationURI(httpget, response, context);
        Assert.assertEquals(URI.create("http://localhost/stuff"), uri);
    }

    @Test(expected=IllegalStateException.class)
    public void testGetLocationUriRelativeMissingTargetHost() throws Exception {
        final DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
        final HttpClientContext context = HttpClientContext.create();
        final HttpGet httpget = new HttpGet("http://localhost/");
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_MOVED_TEMPORARILY, "Redirect");
        response.addHeader("Location", "/stuff");
        final URI uri = redirectStrategy.getLocationURI(httpget, response, context);
        Assert.assertEquals(URI.create("http://localhost/stuff"), uri);
    }

    @Test
    public void testGetLocationUriRelativeWithFragment() throws Exception {
        final DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
        final HttpClientContext context = HttpClientContext.create();
        context.setAttribute(HttpCoreContext.HTTP_TARGET_HOST, new HttpHost("localhost"));
        final HttpGet httpget = new HttpGet("http://localhost/");
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_MOVED_TEMPORARILY, "Redirect");
        response.addHeader("Location", "/stuff#fragment");
        final URI uri = redirectStrategy.getLocationURI(httpget, response, context);
        Assert.assertEquals(URI.create("http://localhost/stuff"), uri);
    }

    @Test
    public void testGetLocationUriAbsoluteWithFragment() throws Exception {
        final DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
        final HttpClientContext context = HttpClientContext.create();
        context.setAttribute(HttpCoreContext.HTTP_TARGET_HOST, new HttpHost("localhost"));
        final HttpGet httpget = new HttpGet("http://localhost/");
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_MOVED_TEMPORARILY, "Redirect");
        response.addHeader("Location", "http://localhost/stuff#fragment");
        final URI uri = redirectStrategy.getLocationURI(httpget, response, context);
        Assert.assertEquals(URI.create("http://localhost/stuff"), uri);
    }

    @Test
    public void testGetLocationUriNormalized() throws Exception {
        final DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
        final HttpClientContext context = HttpClientContext.create();
        context.setAttribute(HttpCoreContext.HTTP_TARGET_HOST, new HttpHost("localhost"));
        final HttpGet httpget = new HttpGet("http://localhost/");
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_MOVED_TEMPORARILY, "Redirect");
        response.addHeader("Location", "http://localhost/././stuff/../morestuff");
        final URI uri = redirectStrategy.getLocationURI(httpget, response, context);
        Assert.assertEquals(URI.create("http://localhost/morestuff"), uri);
    }

    @Test(expected=ProtocolException.class)
    public void testGetLocationUriRelativeLocationNotAllowed() throws Exception {
        final DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
        final HttpClientContext context = HttpClientContext.create();
        context.setAttribute(HttpCoreContext.HTTP_TARGET_HOST, new HttpHost("localhost"));
        final RequestConfig config = RequestConfig.custom().setRelativeRedirectsAllowed(false).build();
        context.setRequestConfig(config);

        final HttpGet httpget = new HttpGet("http://localhost/");
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_MOVED_TEMPORARILY, "Redirect");
        response.addHeader("Location", "/stuff");
        redirectStrategy.getLocationURI(httpget, response, context);
    }

    @Test
    public void testGetLocationUriAllowCircularRedirects() throws Exception {
        final DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
        final HttpClientContext context = HttpClientContext.create();
        context.setAttribute(HttpCoreContext.HTTP_TARGET_HOST, new HttpHost("localhost"));
        final HttpGet httpget = new HttpGet("http://localhost/");
        final RequestConfig config = RequestConfig.custom().setCircularRedirectsAllowed(true).build();
        context.setRequestConfig(config);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_MOVED_TEMPORARILY, "Redirect");
        response.addHeader("Location", "http://localhost/stuff");
        final URI uri = URI.create("http://localhost/stuff");
        Assert.assertEquals(uri, redirectStrategy.getLocationURI(httpget, response, context));
        Assert.assertEquals(uri, redirectStrategy.getLocationURI(httpget, response, context));
        Assert.assertEquals(uri, redirectStrategy.getLocationURI(httpget, response, context));

        final RedirectLocations redirectLocations = (RedirectLocations) context.getAttribute(
                DefaultRedirectStrategy.REDIRECT_LOCATIONS);
        Assert.assertNotNull(redirectLocations);
        Assert.assertTrue(redirectLocations.contains(uri));
        final List<URI> uris = redirectLocations.getAll();
        Assert.assertNotNull(uris);
        Assert.assertEquals(3, uris.size());
        Assert.assertEquals(uri, uris.get(0));
        Assert.assertEquals(uri, uris.get(1));
        Assert.assertEquals(uri, uris.get(2));
    }

    @Test(expected=ProtocolException.class)
    public void testGetLocationUriDisallowCircularRedirects() throws Exception {
        final DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
        final HttpClientContext context = HttpClientContext.create();
        context.setAttribute(HttpCoreContext.HTTP_TARGET_HOST, new HttpHost("localhost"));
        final HttpGet httpget = new HttpGet("http://localhost/");
        final RequestConfig config = RequestConfig.custom().setCircularRedirectsAllowed(false).build();
        context.setRequestConfig(config);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_MOVED_TEMPORARILY, "Redirect");
        response.addHeader("Location", "http://localhost/stuff");
        final URI uri = URI.create("http://localhost/stuff");
        Assert.assertEquals(uri, redirectStrategy.getLocationURI(httpget, response, context));
        redirectStrategy.getLocationURI(httpget, response, context);
    }

    @Test
    public void testGetLocationUriInvalidInput() throws Exception {
        final DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
        final HttpClientContext context = HttpClientContext.create();
        final HttpGet httpget = new HttpGet("http://localhost/");
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_MOVED_TEMPORARILY, "Redirect");
        response.addHeader("Location", "http://localhost/stuff");
        try {
            redirectStrategy.getLocationURI(null, response, context);
            Assert.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }
        try {
            redirectStrategy.getLocationURI(httpget, null, context);
            Assert.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }
        try {
            redirectStrategy.getLocationURI(httpget, response, null);
            Assert.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }
    }

    @Test
    public void testGetRedirectRequest() throws Exception {
        final DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_SEE_OTHER, "Redirect");
        response.addHeader("Location", "http://localhost/stuff");
        final HttpContext context1 = new BasicHttpContext();
        final HttpUriRequest redirect1 = redirectStrategy.getRedirect(
                new HttpGet("http://localhost/"), response, context1);
        Assert.assertEquals("GET", redirect1.getMethod());
        final HttpContext context2 = new BasicHttpContext();
        final HttpUriRequest redirect2 = redirectStrategy.getRedirect(
                new HttpPost("http://localhost/"), response, context2);
        Assert.assertEquals("GET", redirect2.getMethod());
        final HttpContext context3 = new BasicHttpContext();
        final HttpUriRequest redirect3 = redirectStrategy.getRedirect(
                new HttpHead("http://localhost/"), response, context3);
        Assert.assertEquals("HEAD", redirect3.getMethod());
    }

    @Test
    public void testGetRedirectRequestForTemporaryRedirect() throws Exception {
        final DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_TEMPORARY_REDIRECT, "Temporary Redirect");
        response.addHeader("Location", "http://localhost/stuff");
        final HttpContext context1 = new BasicHttpContext();
        final HttpUriRequest redirect1 = redirectStrategy.getRedirect(
                new HttpTrace("http://localhost/"), response, context1);
        Assert.assertEquals("TRACE", redirect1.getMethod());
        final HttpContext context2 = new BasicHttpContext();
        final HttpPost httppost = new HttpPost("http://localhost/");
        final HttpEntity entity = new BasicHttpEntity();
        httppost.setEntity(entity);
        final HttpUriRequest redirect2 = redirectStrategy.getRedirect(
                httppost, response, context2);
        Assert.assertEquals("POST", redirect2.getMethod());
        Assert.assertTrue(redirect2 instanceof HttpEntityEnclosingRequest);
        Assert.assertSame(entity, ((HttpEntityEnclosingRequest) redirect2).getEntity());
    }

}
