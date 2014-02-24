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
package org.apache.http.client.utils;

import java.io.IOException;
import java.io.InputStream;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.Test;
import org.mockito.Mockito;

@SuppressWarnings("boxing") // test code
public class TestHttpClientUtils {

    @Test
    public void testCloseQuietlyResponseNull() throws Exception {
        final HttpResponse response = null;
        HttpClientUtils.closeQuietly(response);
    }

    @Test
    public void testCloseQuietlyResponseEntityNull() throws Exception {
        final HttpResponse response = Mockito.mock(HttpResponse.class);
        HttpClientUtils.closeQuietly(response);
        Mockito.verify(response).getEntity();
    }

    @Test
    public void testCloseQuietlyResponseEntityNonStreaming() throws Exception {
        final HttpResponse response = Mockito.mock(HttpResponse.class);
        final HttpEntity entity = Mockito.mock(HttpEntity.class);
        Mockito.when(response.getEntity()).thenReturn(entity);
        Mockito.when(entity.isStreaming()).thenReturn(Boolean.FALSE);
        HttpClientUtils.closeQuietly(response);
        Mockito.verify(entity, Mockito.never()).getContent();
    }

    @Test
    public void testCloseQuietlyResponseEntity() throws Exception {
        final HttpResponse response = Mockito.mock(HttpResponse.class);
        final HttpEntity entity = Mockito.mock(HttpEntity.class);
        final InputStream instream = Mockito.mock(InputStream.class);
        Mockito.when(response.getEntity()).thenReturn(entity);
        Mockito.when(entity.isStreaming()).thenReturn(Boolean.TRUE);
        Mockito.when(entity.getContent()).thenReturn(instream);
        HttpClientUtils.closeQuietly(response);
        Mockito.verify(instream).close();
    }

    @Test
    public void testCloseQuietlyResponseIgnoreIOError() throws Exception {
        final HttpResponse response = Mockito.mock(HttpResponse.class);
        final HttpEntity entity = Mockito.mock(HttpEntity.class);
        final InputStream instream = Mockito.mock(InputStream.class);
        Mockito.when(response.getEntity()).thenReturn(entity);
        Mockito.when(entity.getContent()).thenReturn(instream);
        Mockito.doThrow(new IOException()).when(instream).close();
        HttpClientUtils.closeQuietly(response);
    }

    @Test
    public void testCloseQuietlyCloseableResponseNull() throws Exception {
        final CloseableHttpResponse response = null;
        HttpClientUtils.closeQuietly(response);
    }

    @Test
    public void testCloseQuietlyCloseableResponseEntityNull() throws Exception {
        final CloseableHttpResponse response = Mockito.mock(CloseableHttpResponse.class);
        HttpClientUtils.closeQuietly(response);
        Mockito.verify(response).getEntity();
        Mockito.verify(response).close();
    }

    @Test
    public void testCloseQuietlyCloseableResponseEntityNonStreaming() throws Exception {
        final CloseableHttpResponse response = Mockito.mock(CloseableHttpResponse.class);
        final HttpEntity entity = Mockito.mock(HttpEntity.class);
        Mockito.when(response.getEntity()).thenReturn(entity);
        Mockito.when(entity.isStreaming()).thenReturn(Boolean.FALSE);
        HttpClientUtils.closeQuietly(response);
        Mockito.verify(entity, Mockito.never()).getContent();
        Mockito.verify(response).close();
    }

    @Test
    public void testCloseQuietlyCloseableResponseEntity() throws Exception {
        final CloseableHttpResponse response = Mockito.mock(CloseableHttpResponse.class);
        final HttpEntity entity = Mockito.mock(HttpEntity.class);
        final InputStream instream = Mockito.mock(InputStream.class);
        Mockito.when(response.getEntity()).thenReturn(entity);
        Mockito.when(entity.isStreaming()).thenReturn(Boolean.TRUE);
        Mockito.when(entity.getContent()).thenReturn(instream);
        HttpClientUtils.closeQuietly(response);
        Mockito.verify(instream).close();
        Mockito.verify(response).close();
    }

    @Test
    public void testCloseQuietlyCloseableResponseIgnoreIOError() throws Exception {
        final CloseableHttpResponse response = Mockito.mock(CloseableHttpResponse.class);
        final HttpEntity entity = Mockito.mock(HttpEntity.class);
        final InputStream instream = Mockito.mock(InputStream.class);
        Mockito.when(response.getEntity()).thenReturn(entity);
        Mockito.when(entity.getContent()).thenReturn(instream);
        Mockito.doThrow(new IOException()).when(instream).close();
        HttpClientUtils.closeQuietly(response);
    }

    @Test
    public void testCloseQuietlyHttpClientNull() throws Exception {
        final CloseableHttpClient httpclient = null;
        HttpClientUtils.closeQuietly(httpclient);
    }

    @Test
    public void testCloseQuietlyHttpClient() throws Exception {
        final CloseableHttpClient httpclient = Mockito.mock(CloseableHttpClient.class);
        HttpClientUtils.closeQuietly(httpclient);
        Mockito.verify(httpclient).close();
    }

    @Test
    public void testCloseQuietlyCloseableHttpClientIgnoreIOError() throws Exception {
        final CloseableHttpClient httpclient = Mockito.mock(CloseableHttpClient.class);
        Mockito.doThrow(new IOException()).when(httpclient).close();
        HttpClientUtils.closeQuietly(httpclient);
    }

}
