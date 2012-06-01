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
 */
package org.apache.http.impl.client;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TestDecompressingHttpClient {
    
    private DummyHttpClient backend;
    @Mock private ClientConnectionManager mockConnManager;
    @Mock private ResponseHandler<Object> mockHandler;
    private DecompressingHttpClient impl;
    private HttpUriRequest request;
    private HttpContext ctx;
    private HttpHost host;
    @Mock private HttpResponse mockResponse;
    @Mock private HttpEntity mockEntity;
    private Object handled;
    
    @Before
    public void canCreate() {
        handled = new Object();
        backend = new DummyHttpClient();
        impl = new DecompressingHttpClient(backend);
        request = new HttpGet("http://localhost:8080");
        ctx = new BasicHttpContext();
        host = new HttpHost("www.example.com");
    }
    
    @Test
    public void isAnHttpClient() {
        assertTrue(impl instanceof HttpClient);
    }
    
    @Test
    public void usesParamsFromBackend() {
        HttpParams params = new BasicHttpParams();
        backend.setParams(params);
        assertSame(params, impl.getParams());
    }
    
    @Test
    public void extractsHostNameFromUriRequest() {
        assertEquals(new HttpHost("www.example.com"), 
                impl.getHttpHost(new HttpGet("http://www.example.com/")));
    }
    
    @Test
    public void extractsHostNameAndPortFromUriRequest() {
        assertEquals(new HttpHost("www.example.com", 8080), 
                impl.getHttpHost(new HttpGet("http://www.example.com:8080/")));
    }

    @Test
    public void extractsIPAddressFromUriRequest() {
        assertEquals(new HttpHost("10.0.0.1"), 
                impl.getHttpHost(new HttpGet("http://10.0.0.1/")));
    }

    @Test
    public void extractsIPAddressAndPortFromUriRequest() {
        assertEquals(new HttpHost("10.0.0.1", 8080), 
                impl.getHttpHost(new HttpGet("http://10.0.0.1:8080/")));
    }

    @Test
    public void extractsLocalhostFromUriRequest() {
        assertEquals(new HttpHost("localhost"), 
                impl.getHttpHost(new HttpGet("http://localhost/")));
    }

    @Test
    public void extractsLocalhostAndPortFromUriRequest() {
        assertEquals(new HttpHost("localhost", 8080), 
                impl.getHttpHost(new HttpGet("http://localhost:8080/")));
    }
    
    @Test
    public void usesConnectionManagerFromBackend() {
        backend.setConnectionManager(mockConnManager);
        assertSame(mockConnManager, impl.getConnectionManager());
    }
    
    private void assertAcceptEncodingGzipAndDeflateWereAddedToRequest(HttpRequest captured) {
        boolean foundGzip = false;
        boolean foundDeflate = false;
        for(Header h : captured.getHeaders("Accept-Encoding")) {
            for(HeaderElement elt : h.getElements()) {
                if ("gzip".equals(elt.getName())) foundGzip = true;
                if ("deflate".equals(elt.getName())) foundDeflate = true;
            }
        }
        assertTrue(foundGzip);
        assertTrue(foundDeflate);
    }
    
    @Test
    public void addsAcceptEncodingHeaderToHttpUriRequest() throws Exception {
        impl.execute(request);
        assertAcceptEncodingGzipAndDeflateWereAddedToRequest(backend.getCapturedRequest());
    }

    @Test
    public void addsAcceptEncodingHeaderToHttpUriRequestWithContext() throws Exception {
        impl.execute(request, ctx);
        assertAcceptEncodingGzipAndDeflateWereAddedToRequest(backend.getCapturedRequest());
    }
    
    @Test
    public void addsAcceptEncodingHeaderToHostAndHttpRequest() throws Exception {
        impl.execute(host, request);
        assertAcceptEncodingGzipAndDeflateWereAddedToRequest(backend.getCapturedRequest());
    }
    
    @Test
    public void addsAcceptEncodingHeaderToHostAndHttpRequestWithContext() throws Exception {
        impl.execute(host, request, ctx);
        assertAcceptEncodingGzipAndDeflateWereAddedToRequest(backend.getCapturedRequest());
    }
    
    @Test
    public void addsAcceptEncodingHeaderToUriRequestWithHandler() throws Exception {
        when(mockHandler.handleResponse(isA(HttpResponse.class))).thenReturn(new Object());
        impl.execute(request, mockHandler);
        assertAcceptEncodingGzipAndDeflateWereAddedToRequest(backend.getCapturedRequest());
    }

    @Test
    public void addsAcceptEncodingHeaderToUriRequestWithHandlerAndContext() throws Exception {
        when(mockHandler.handleResponse(isA(HttpResponse.class))).thenReturn(new Object());
        impl.execute(request, mockHandler, ctx);
        assertAcceptEncodingGzipAndDeflateWereAddedToRequest(backend.getCapturedRequest());
    }

    @Test
    public void addsAcceptEncodingHeaderToRequestWithHostAndHandler() throws Exception {
        when(mockHandler.handleResponse(isA(HttpResponse.class))).thenReturn(new Object());
        impl.execute(host, request, mockHandler);
        assertAcceptEncodingGzipAndDeflateWereAddedToRequest(backend.getCapturedRequest());
    }

    @Test
    public void addsAcceptEncodingHeaderToRequestWithHostAndContextAndHandler() throws Exception {
        when(mockHandler.handleResponse(isA(HttpResponse.class))).thenReturn(new Object());
        impl.execute(host, request, mockHandler, ctx);
        assertAcceptEncodingGzipAndDeflateWereAddedToRequest(backend.getCapturedRequest());
    }

    private void mockResponseHasNoContentEncodingHeaders() {
        backend.setResponse(mockResponse);
        when(mockResponse.getAllHeaders()).thenReturn(new Header[]{});
        when(mockResponse.getHeaders("Content-Encoding")).thenReturn(new Header[]{});
        when(mockResponse.getFirstHeader("Content-Encoding")).thenReturn(null);
        when(mockResponse.getLastHeader("Content-Encoding")).thenReturn(null);
        when(mockResponse.getEntity()).thenReturn(mockEntity);
    }
    
    @Test
    public void doesNotModifyResponseBodyIfNoContentEncoding() throws Exception {
        mockResponseHasNoContentEncodingHeaders();
        assertSame(mockResponse, impl.execute(request));
        verify(mockResponse, never()).setEntity(any(HttpEntity.class));
    }
    
    @Test
    public void doesNotModifyResponseBodyIfNoContentEncodingWithContext() throws Exception {
        mockResponseHasNoContentEncodingHeaders();
        assertSame(mockResponse, impl.execute(request, ctx));
        verify(mockResponse, never()).setEntity(any(HttpEntity.class));
    }
    
    @Test
    public void doesNotModifyResponseBodyIfNoContentEncodingForHostRequest() throws Exception {
        mockResponseHasNoContentEncodingHeaders();
        assertSame(mockResponse, impl.execute(host, request));
        verify(mockResponse, never()).setEntity(any(HttpEntity.class));
    }
    
    @Test
    public void doesNotModifyResponseBodyIfNoContentEncodingForHostRequestWithContext() throws Exception {
        mockResponseHasNoContentEncodingHeaders();
        assertSame(mockResponse, impl.execute(host, request, ctx));
        verify(mockResponse, never()).setEntity(any(HttpEntity.class));
    }
    
    @Test
    public void doesNotModifyResponseBodyWithHandlerIfNoContentEncoding() throws Exception {
        mockResponseHasNoContentEncodingHeaders();
        when(mockHandler.handleResponse(mockResponse)).thenReturn(handled);
        assertSame(handled, impl.execute(request, mockHandler));
        verify(mockResponse, never()).setEntity(any(HttpEntity.class));
    }
    
    @Test
    public void doesNotModifyResponseBodyWithHandlerAndContextIfNoContentEncoding() throws Exception {
        mockResponseHasNoContentEncodingHeaders();
        when(mockHandler.handleResponse(mockResponse)).thenReturn(handled);
        assertSame(handled, impl.execute(request, mockHandler, ctx));
        verify(mockResponse, never()).setEntity(any(HttpEntity.class));
    }
    
    @Test
    public void doesNotModifyResponseBodyWithHostAndHandlerIfNoContentEncoding() throws Exception {
        mockResponseHasNoContentEncodingHeaders();
        when(mockHandler.handleResponse(mockResponse)).thenReturn(handled);
        assertSame(handled, impl.execute(host, request, mockHandler));
        verify(mockResponse, never()).setEntity(any(HttpEntity.class));
    }
    
    @Test
    public void doesNotModifyResponseBodyWithHostAndHandlerAndContextIfNoContentEncoding() throws Exception {
        mockResponseHasNoContentEncodingHeaders();
        when(mockHandler.handleResponse(mockResponse)).thenReturn(handled);
        assertSame(handled, impl.execute(host, request, mockHandler, ctx));
        verify(mockResponse, never()).setEntity(any(HttpEntity.class));
    }
    
    @Test
    public void successfullyUncompressesContent() throws Exception {
        final String plainText = "hello\n";
        HttpResponse response = getGzippedResponse(plainText);
        backend.setResponse(response);
        
        HttpResponse result = impl.execute(request);
        ByteArrayOutputStream resultBuf = new ByteArrayOutputStream();
        InputStream is = result.getEntity().getContent();
        int b;
        while((b = is.read()) != -1) {
            resultBuf.write(b);
        }
        is.close();
        assertEquals(plainText, new String(resultBuf.toByteArray()));
    }
    
    @Test
    public void uncompressedResponseHasUnknownLength() throws Exception {
        final String plainText = "hello\n";
        HttpResponse response = getGzippedResponse(plainText);
        backend.setResponse(response);
        
        HttpResponse result = impl.execute(request);
        HttpEntity entity = result.getEntity();
        assertEquals(-1, entity.getContentLength());
        EntityUtils.consume(entity);
        assertNull(result.getFirstHeader("Content-Length"));
    }

    @Test
    public void uncompressedResponseIsNotEncoded() throws Exception {
        final String plainText = "hello\n";
        HttpResponse response = getGzippedResponse(plainText);
        backend.setResponse(response);
        
        HttpResponse result = impl.execute(request);
        assertNull(result.getFirstHeader("Content-Encoding"));
    }
    
    @Test
    public void uncompressedResponseHasContentMD5Removed() throws Exception {
        final String plainText = "hello\n";
        HttpResponse response = getGzippedResponse(plainText);
        response.setHeader("Content-MD5","a checksum");
        backend.setResponse(response);
        
        HttpResponse result = impl.execute(request);
        assertNull(result.getFirstHeader("Content-MD5"));
    }
    
    @Test
    public void unencodedResponseRetainsContentMD5() throws Exception {
        final String plainText = "hello\n";
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        response.setHeader("Content-MD5","a checksum");
        response.setEntity(new ByteArrayEntity(plainText.getBytes()));
        backend.setResponse(response);
        
        HttpResponse result = impl.execute(request);
        assertNotNull(result.getFirstHeader("Content-MD5"));
    }
    
    @Test
    public void passesThroughTheBodyOfAPOST() throws Exception {
    	when(mockHandler.handleResponse(isA(HttpResponse.class))).thenReturn(new Object());
    	HttpPost post = new HttpPost("http://localhost:8080/");
    	post.setEntity(new ByteArrayEntity("hello".getBytes()));
    	impl.execute(host, post, mockHandler, ctx);
    	assertNotNull(((HttpEntityEnclosingRequest)backend.getCapturedRequest()).getEntity());
    }
    
    private HttpResponse getGzippedResponse(final String plainText)
            throws IOException {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        response.setHeader("Content-Encoding","gzip");
        response.setHeader("Content-Type","text/plain");
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        GZIPOutputStream gos = new GZIPOutputStream(buf);
        gos.write(plainText.getBytes());
        gos.close();
        ByteArrayEntity body = new ByteArrayEntity(buf.toByteArray());
        body.setContentEncoding("gzip");
        body.setContentType("text/plain");
        response.setHeader("Content-Length", "" + (int)body.getContentLength());
        response.setEntity(body);
        return response;
    }
}
