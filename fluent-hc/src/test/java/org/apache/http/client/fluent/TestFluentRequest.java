/*
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.client.fluent;

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.fluent.header.ContentType;
import org.apache.http.client.fluent.header.DateUtils;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.entity.StringEntity;
import org.apache.http.localserver.LocalTestServer;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestFluentRequest {
    private static class SimpleService implements HttpRequestHandler {

        public SimpleService() {
            super();
        }

        public void handle(final HttpRequest request,
                final HttpResponse response, final HttpContext context)
                throws HttpException, IOException {
            response.setStatusCode(HttpStatus.SC_OK);
            StringEntity entity = new StringEntity("Whatever");
            response.setEntity(entity);
        }
    }

    private LocalTestServer localServer;

    private URI getLocalServerURI() {
        int hostPort = localServer.getServiceAddress().getPort();
        String hostAddr = localServer.getServiceAddress().getAddress()
                .getHostAddress();
        URI uri;
        try {
            uri = new URI("http", null, hostAddr, hostPort, null, null, null);
            return uri;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Before
    public void setUp() throws Exception {
        localServer = new LocalTestServer(null, null);
        localServer.registerDefaultHandlers();
        localServer.start();
        localServer.register("*", new SimpleService());
    }

    @After
    public void tearDown() throws Exception {
        if (localServer != null)
            localServer.stop();
    }

    @Test
    public void testCacheControl() {
        FluentRequest req = new FluentRequest("http://www.apache.org/");
        String cacheControl = "no-cache";
        req.setCacheControl(cacheControl);
        assertEquals(cacheControl, req.getCacheControl());
        assertEquals(req.getFirstHeader("Cache-Control").getValue(),
                req.getCacheControl());
    }

    @Test
    public void testConnectionTimeout() throws Exception {
        // TODO how to delay the response from the localServer?
        int timeout = 1;
        URI uri = getLocalServerURI();
        FluentRequest req = new FluentRequest(uri);
        req.setConnectionTimeout(timeout);
        assertEquals(timeout, req.getConnectionTimeout());
        try {
            req.exec();
            // TODO: Delay local server's response
            // fail("ConnectTimeoutException exception is expected.");
        } catch (Exception e) {
            if (!(e instanceof ConnectTimeoutException)) {
                throw e;
            }
        }
    }

    @Test
    public void testContentCharset() {
        URI uri = getLocalServerURI();
        FluentRequest req = new FluentRequest(uri);
        String charset = "UTF-8";
        req.setContentCharset(charset);
        assertEquals(charset, req.getContentCharset());
        assertEquals(
                req.getLocalParams().getParameter(
                        CoreProtocolPNames.HTTP_CONTENT_CHARSET),
                req.getContentCharset());
    }

    @Test
    public void testContentLength() {
        int contentLength = 1000;
        FluentRequest req = new FluentRequest("http://www.apache.org/");
        req.setContentLength(contentLength);
        assertEquals(contentLength, req.getContentLength());
    }

    @Test
    public void testContentType() {
        String contentType = ContentType.HTML;
        FluentRequest req = new FluentRequest("http://www.apache.org/");
        req.setContentType(contentType);
        assertEquals(contentType, req.getContentType());
    }

    @Test
    public void testDate() {
        Date date = new Date();
        String dateValue = DateUtils.format(date);
        FluentRequest req = new FluentRequest("http://www.apache.org/");
        req.setDate(date);
        assertEquals(dateValue, req.getDate());
    }

    @Test
    public void testElementCharset() {
        String charset = "UTF-8";
        FluentRequest req = new FluentRequest("http://www.apache.org/");
        req.setElementCharset(charset);
        assertEquals(charset, req.getElementCharset());
    }

    @Test
    public void testExec() throws ClientProtocolException, IOException,
            URISyntaxException {
        URI uri = getLocalServerURI();
        FluentRequest req = new FluentRequest(uri);
        FluentResponse resp = req.exec();
        assertEquals(HttpStatus.SC_OK, resp.getStatusCode());
    }

    @Test
    public void testFluentRequestHttpUriRequest() {
        String uriString = "http://www.apache.org/";
        HttpUriRequest httpRequest = new HttpGet(uriString);
        FluentRequest req = new FluentRequest(httpRequest);
        assertEquals(uriString, req.getURI().toASCIIString());
        assertEquals("GET", req.getMethod().toUpperCase());
    }

    @Test
    public void testFluentRequestString() {
        String uriString = "http://www.apache.org/";
        FluentRequest req = new FluentRequest(uriString);
        assertEquals(uriString, req.getURI().toASCIIString());
        assertEquals("GET", req.getMethod().toUpperCase());
    }

    @Test
    public void testFluentRequestStringFluentHttpMethod() {
        String uriString = "http://www.apache.org/";
        FluentRequest req = new FluentRequest(uriString,
                FluentHttpMethod.POST_METHOD);
        assertEquals(uriString, req.getURI().toASCIIString());
        assertEquals("POST", req.getMethod().toUpperCase());
    }

    @Test
    public void testFluentRequestURI() throws URISyntaxException {
        String uriString = "http://www.apache.org/";
        URI uri = new URI(uriString);
        FluentRequest req = new FluentRequest(uri);
        assertEquals(req.getURI(), uri);
        assertEquals("GET", req.getMethod().toUpperCase());
    }

    @Test
    public void testFluentRequestURIFluentHttpMethod()
            throws URISyntaxException {
        String uriString = "http://www.apache.org/";
        URI uri = new URI(uriString);
        FluentRequest req = new FluentRequest(uri, FluentHttpMethod.HEAD_METHOD);
        assertEquals(req.getURI(), uri);
        assertEquals("HEAD", req.getMethod().toUpperCase());
    }

    @Test
    public void testGetHttpMethod() {
        FluentHttpMethod method = FluentHttpMethod.POST_METHOD;
        FluentRequest req = new FluentRequest("http://www.apache.org/", method);
        assertEquals(method, req.getHttpMethod());
    }

    @Test
    public void testGetURI() {
        URI uri = getLocalServerURI();
        FluentRequest req = new FluentRequest(uri);
        assertEquals(uri, req.getURI());
    }

    @Test
    public void testHttpVersion() {
        HttpVersion procVersion = HttpVersion.HTTP_1_1;
        FluentRequest req = new FluentRequest("http://www.apache.org/");
        req.setHttpVersion(procVersion);
        assertEquals(procVersion, req.getHttpVersion());
    }

    @Test
    public void testIfModifiedSince() {
        Date date = new Date();
        String dateValue = DateUtils.format(date);
        FluentRequest req = new FluentRequest("http://www.apache.org/");
        req.setIfModifiedSince(date);
        assertEquals(dateValue, req.getIfModifiedSince());
    }

    @Test
    public void testIfUnmodifiedSince() {
        Date date = new Date();
        String dateValue = DateUtils.format(date);
        FluentRequest req = new FluentRequest("http://www.apache.org/");
        req.setIfUnmodifiedSince(date);
        assertEquals(dateValue, req.getIfUnmodifiedSince());
    }

    @Test
    public void testIsUseExpectContinue() {
        boolean ueCont = true;
        FluentRequest req = new FluentRequest("http://www.apache.org/");
        req.setUseExpectContinue(ueCont);
        assertEquals(ueCont, req.isUseExpectContinue());
    }

    @Test
    public void testRemoveAuth() {
        // fail("Not yet implemented");
    }

    @Test
    public void testRemoveProxy() {
        // fail("Not yet implemented");
    }

    @Test
    public void testSetAuthCredentials() {
        // fail("Not yet implemented");
    }

    @Test
    public void testSetAuthStringString() {
        // fail("Not yet implemented");
    }

    @Test
    public void testSetAuthStringStringStringString() {
        // fail("Not yet implemented");
    }

    @Test
    public void testSetCredentialProvider() {
        // fail("Not yet implemented");
    }

    @Test
    public void testSetEntity() {
        // fail("Not yet implemented");
    }

    @Test
    public void testSetHTMLFormEntity() {
        // fail("Not yet implemented");
    }

    @Test
    public void testSetParams() {
        // fail("Not yet implemented");
    }

    @Test
    public void testSetProxyAuthCredentials() {
        // fail("Not yet implemented");
    }

    @Test
    public void testSetProxyAuthStringString() {
        // fail("Not yet implemented");
    }

    @Test
    public void testSetProxyAuthStringStringStringString() {
        // fail("Not yet implemented");
    }

    @Test
    public void testSetProxyStringInt() {
        // fail("Not yet implemented");
    }

    @Test
    public void testSetProxyStringIntStringString() {
        // fail("Not yet implemented");
    }

    @Test
    public void testSocketTimeout() throws Exception {
        // TODO how to delay the response from the localServer?
        int timeout = 1;
        URI uri = getLocalServerURI();
        FluentRequest req = new FluentRequest(uri);
        req.setSocketTimeout(timeout);
        assertEquals(timeout, req.getSocketTimeout());
        try {
            req.exec();
            // TODO: Delay local server's response
            // fail("SocketTimeoutException exception is expected.");
        } catch (Exception e) {
            if (!(e instanceof SocketTimeoutException)) {
                throw e;
            }
        }
    }

    @Test
    public void testStrictTransferEncoding() {
        boolean stEnc = true;
        FluentRequest req = new FluentRequest("http://www.apache.org/");
        req.setStrictTransferEncoding(stEnc);
        assertEquals(stEnc, req.isStrictTransferEncoding());
    }

    @Test
    public void testUserAgent() {
        String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_7_1) AppleWebKit/535.1 (KHTML, like Gecko) Chrome/13.0.782.112 Safari/535.1";
        FluentRequest req = new FluentRequest("http://www.apache.org/");
        req.setUserAgent(userAgent);
        assertEquals(userAgent, req.getUserAgent());
    }

    @Test
    public void testWaitForContinue() {
        int wait = 1000;
        FluentRequest req = new FluentRequest("http://www.apache.org/");
        req.setWaitForContinue(wait);
        assertEquals(wait, req.getWaitForContinue());
    }

}
