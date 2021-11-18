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
package org.apache.hc.client5.http.impl;

import java.net.URI;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestDefaultRedirectStrategy {

    @Test
    public void testIsRedirectedMovedTemporary() throws Exception {
        final DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
        final HttpClientContext context = HttpClientContext.create();
        final HttpGet httpget = new HttpGet("http://localhost/");
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_MOVED_TEMPORARILY, "Redirect");
        Assertions.assertFalse(redirectStrategy.isRedirected(httpget, response, context));
        response.setHeader(HttpHeaders.LOCATION, "http://localhost/blah");
        Assertions.assertTrue(redirectStrategy.isRedirected(httpget, response, context));
    }

    @Test
    public void testIsRedirectedMovedTemporaryNoLocation() throws Exception {
        final DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
        final HttpClientContext context = HttpClientContext.create();
        final HttpGet httpget = new HttpGet("http://localhost/");
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_MOVED_TEMPORARILY, "Redirect");
        Assertions.assertFalse(redirectStrategy.isRedirected(httpget, response, context));
    }

    @Test
    public void testIsRedirectedMovedPermanently() throws Exception {
        final DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
        final HttpClientContext context = HttpClientContext.create();
        final HttpGet httpget = new HttpGet("http://localhost/");
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_MOVED_PERMANENTLY, "Redirect");
        Assertions.assertFalse(redirectStrategy.isRedirected(httpget, response, context));
        response.setHeader(HttpHeaders.LOCATION, "http://localhost/blah");
        Assertions.assertTrue(redirectStrategy.isRedirected(httpget, response, context));
    }

    @Test
    public void testIsRedirectedTemporaryRedirect() throws Exception {
        final DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
        final HttpClientContext context = HttpClientContext.create();
        final HttpGet httpget = new HttpGet("http://localhost/");
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_TEMPORARY_REDIRECT, "Redirect");
        Assertions.assertFalse(redirectStrategy.isRedirected(httpget, response, context));
        response.setHeader(HttpHeaders.LOCATION, "http://localhost/blah");
        Assertions.assertTrue(redirectStrategy.isRedirected(httpget, response, context));
    }

    @Test
    public void testIsRedirectedSeeOther() throws Exception {
        final DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
        final HttpClientContext context = HttpClientContext.create();
        final HttpGet httpget = new HttpGet("http://localhost/");
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_SEE_OTHER, "Redirect");
        Assertions.assertFalse(redirectStrategy.isRedirected(httpget, response, context));
        response.setHeader(HttpHeaders.LOCATION, "http://localhost/blah");
        Assertions.assertTrue(redirectStrategy.isRedirected(httpget, response, context));
    }

    @Test
    public void testIsRedirectedUnknownStatus() throws Exception {
        final DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
        final HttpClientContext context = HttpClientContext.create();
        final HttpGet httpget = new HttpGet("http://localhost/");
        final HttpResponse response = new BasicHttpResponse(333, "Redirect");
        Assertions.assertFalse(redirectStrategy.isRedirected(httpget, response, context));
        final HttpPost httppost = new HttpPost("http://localhost/");
        Assertions.assertFalse(redirectStrategy.isRedirected(httppost, response, context));
    }

    @Test
    public void testIsRedirectedInvalidInput() throws Exception {
        final DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
        final HttpClientContext context = HttpClientContext.create();
        final HttpGet httpget = new HttpGet("http://localhost/");
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_SEE_OTHER, "Redirect");
        Assertions.assertThrows(NullPointerException.class, () ->
                redirectStrategy.isRedirected(null, response, context));
        Assertions.assertThrows(NullPointerException.class, () ->
                redirectStrategy.isRedirected(httpget, null, context));
    }

    @Test
    public void testGetLocationUri() throws Exception {
        final DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
        final HttpClientContext context = HttpClientContext.create();
        final HttpGet httpget = new HttpGet("http://localhost/");
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_MOVED_TEMPORARILY, "Redirect");
        response.addHeader("Location", "http://localhost/stuff");
        final URI uri = redirectStrategy.getLocationURI(httpget, response, context);
        Assertions.assertEquals(URI.create("http://localhost/stuff"), uri);
    }

    @Test
    public void testGetLocationUriMissingHeader() throws Exception {
        final DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
        final HttpClientContext context = HttpClientContext.create();
        final HttpGet httpget = new HttpGet("http://localhost/");
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_MOVED_TEMPORARILY, "Redirect");
        Assertions.assertThrows(HttpException.class, () ->
                redirectStrategy.getLocationURI(httpget, response, context));
    }

    @Test
    public void testGetLocationUriInvalidLocation() throws Exception {
        final DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
        final HttpClientContext context = HttpClientContext.create();
        final HttpGet httpget = new HttpGet("http://localhost/");
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_MOVED_TEMPORARILY, "Redirect");
        response.addHeader("Location", "http://localhost/not valid");
        Assertions.assertThrows(ProtocolException.class, () ->
                redirectStrategy.getLocationURI(httpget, response, context));
    }

    @Test
    public void testGetLocationUriRelative() throws Exception {
        final DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
        final HttpClientContext context = HttpClientContext.create();
        final HttpGet httpget = new HttpGet("http://localhost/");
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_MOVED_TEMPORARILY, "Redirect");
        response.addHeader("Location", "/stuff");
        final URI uri = redirectStrategy.getLocationURI(httpget, response, context);
        Assertions.assertEquals(URI.create("http://localhost/stuff"), uri);
    }

    @Test
    public void testGetLocationUriRelativeWithFragment() throws Exception {
        final DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
        final HttpClientContext context = HttpClientContext.create();
        final HttpGet httpget = new HttpGet("http://localhost/");
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_MOVED_TEMPORARILY, "Redirect");
        response.addHeader("Location", "/stuff#fragment");
        final URI uri = redirectStrategy.getLocationURI(httpget, response, context);
        Assertions.assertEquals(URI.create("http://localhost/stuff#fragment"), uri);
    }

    @Test
    public void testGetLocationUriAbsoluteWithFragment() throws Exception {
        final DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
        final HttpClientContext context = HttpClientContext.create();
        final HttpGet httpget = new HttpGet("http://localhost/");
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_MOVED_TEMPORARILY, "Redirect");
        response.addHeader("Location", "http://localhost/stuff#fragment");
        final URI uri = redirectStrategy.getLocationURI(httpget, response, context);
        Assertions.assertEquals(URI.create("http://localhost/stuff#fragment"), uri);
    }

    @Test
    public void testGetLocationUriNormalized() throws Exception {
        final DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
        final HttpClientContext context = HttpClientContext.create();
        final HttpGet httpget = new HttpGet("http://localhost/");
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_MOVED_TEMPORARILY, "Redirect");
        response.addHeader("Location", "http://localhost/././stuff/../morestuff");
        final URI uri = redirectStrategy.getLocationURI(httpget, response, context);
        Assertions.assertEquals(URI.create("http://localhost/morestuff"), uri);
    }

    @Test
    public void testGetLocationUriInvalidInput() throws Exception {
        final DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
        final HttpClientContext context = HttpClientContext.create();
        final HttpGet httpget = new HttpGet("http://localhost/");
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_MOVED_TEMPORARILY, "Redirect");
        response.addHeader("Location", "http://localhost/stuff");
        Assertions.assertThrows(NullPointerException.class, () ->
                redirectStrategy.getLocationURI(null, response, context));
        Assertions.assertThrows(NullPointerException.class, () ->
                redirectStrategy.getLocationURI(httpget, null, context));
        Assertions.assertThrows(NullPointerException.class, () ->
                redirectStrategy.getLocationURI(httpget, response, null));
    }

    @Test
    public void testCreateLocationURI() throws Exception {
        final DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
        Assertions.assertEquals("http://blahblah/",
                redirectStrategy.createLocationURI("http://BlahBlah").toASCIIString());
    }

    @Test
    public void testCreateLocationURIInvalid() throws Exception {
        final DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
        Assertions.assertThrows(ProtocolException.class, () ->
                redirectStrategy.createLocationURI(":::::::"));
    }

}
