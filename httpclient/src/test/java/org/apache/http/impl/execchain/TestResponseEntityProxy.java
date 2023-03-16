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

package org.apache.http.impl.execchain;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.impl.io.ChunkedInputStream;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.io.SessionInputBufferImpl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

public class TestResponseEntityProxy {

    @Mock
    private HttpResponse response;
    @Mock
    private ConnectionHolder connectionHolder;
    @Mock
    private HttpEntity entity;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        Mockito.when(entity.isStreaming()).thenReturn(Boolean.TRUE);
        Mockito.when(response.getEntity()).thenReturn(entity);
    }

    @Test
    public void testGetTrailersWithNoChunkedInputStream() throws Exception {
        final ByteArrayInputStream inputStream = new ByteArrayInputStream("Test payload".getBytes());
        Mockito.when(entity.getContent()).thenReturn(inputStream);
        final ArgumentCaptor<ResponseEntityProxy> responseEntityProxyArgumentCaptorEntityArgumentCaptor = ArgumentCaptor.forClass(ResponseEntityProxy.class);

        ResponseEntityProxy.enchance(response, connectionHolder);

        Mockito.verify(response).setEntity(responseEntityProxyArgumentCaptorEntityArgumentCaptor.capture());
        final ResponseEntityProxy wrappedEntity = responseEntityProxyArgumentCaptorEntityArgumentCaptor.getValue();

        final InputStream is = wrappedEntity.getContent();
        while (is.read() != -1) {} // read until the end
        final List<Header> trailers = wrappedEntity.getTrailers();

        Assert.assertTrue(trailers.isEmpty());
    }

    @Test
    public void testGetTrailersWithChunkedInputStream() throws Exception {
        final SessionInputBufferImpl sessionInputBuffer = new SessionInputBufferImpl(new HttpTransportMetricsImpl(), 100);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream("0\r\nX-Test-Trailer-Header: test\r\n".getBytes());
        sessionInputBuffer.bind(inputStream);
        final ChunkedInputStream chunkedInputStream = new ChunkedInputStream(sessionInputBuffer);


        Mockito.when(entity.getContent()).thenReturn(chunkedInputStream);
        final ArgumentCaptor<ResponseEntityProxy> responseEntityProxyArgumentCaptor = ArgumentCaptor.forClass(ResponseEntityProxy.class);

        ResponseEntityProxy.enchance(response, connectionHolder);

        Mockito.verify(response).setEntity(responseEntityProxyArgumentCaptor.capture());
        final ResponseEntityProxy wrappedEntity = responseEntityProxyArgumentCaptor.getValue();

        final InputStream is = wrappedEntity.getContent();
        while (is.read() != -1) {} // consume the stream so it can reach to trailers and parse
        final List<Header> trailers = wrappedEntity.getTrailers();

        Assert.assertEquals(1, trailers.size());
        final Header trailer = trailers.get(0);
        Assert.assertEquals("X-Test-Trailer-Header", trailer.getName());
        Assert.assertEquals("test", trailer.getValue());
    }
}