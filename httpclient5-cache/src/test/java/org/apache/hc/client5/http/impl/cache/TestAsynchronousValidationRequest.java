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
package org.apache.hc.client5.http.impl.cache;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.cache.HeaderConstants;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.sync.methods.HttpExecutionAware;
import org.apache.hc.client5.http.sync.methods.HttpGet;
import org.apache.hc.client5.http.impl.sync.RoutedHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.Before;
import org.junit.Test;

@SuppressWarnings({"boxing","static-access"}) // test code
public class TestAsynchronousValidationRequest {

    private AsynchronousValidator mockParent;
    private CachingExec mockClient;
    private HttpHost host;
    private HttpRoute route;
    private RoutedHttpRequest request;
    private HttpClientContext context;
    private HttpExecutionAware mockExecAware;
    private HttpCacheEntry mockCacheEntry;
    private ClassicHttpResponse mockResponse;

    @Before
    public void setUp() {
        mockParent = mock(AsynchronousValidator.class);
        mockClient = mock(CachingExec.class);
        host = new HttpHost("foo.example.com", 80);
        route = new HttpRoute(host);
        request = RoutedHttpRequest.adapt(new HttpGet("/"), route);
        context = HttpClientContext.create();
        mockExecAware = mock(HttpExecutionAware.class);
        mockCacheEntry = mock(HttpCacheEntry.class);
        mockResponse = mock(ClassicHttpResponse.class);
    }

    @Test
    public void testRunCallsCachingClientAndRemovesIdentifier() throws Exception {
        final String identifier = "foo";

        final AsynchronousValidationRequest impl = new AsynchronousValidationRequest(
                mockParent, mockClient, request, context, mockExecAware, mockCacheEntry,
                identifier, 0);

        when(
                mockClient.revalidateCacheEntry(
                        request, context, mockExecAware, mockCacheEntry)).thenReturn(mockResponse);
        when(mockResponse.getCode()).thenReturn(200);

        impl.run();

        verify(mockClient).revalidateCacheEntry(
                request, context, mockExecAware, mockCacheEntry);
        verify(mockParent).markComplete(identifier);
        verify(mockParent).jobSuccessful(identifier);
    }

    @Test
    public void testRunReportsJobFailedForServerError() throws Exception {
        final String identifier = "foo";

        final AsynchronousValidationRequest impl = new AsynchronousValidationRequest(
                mockParent, mockClient, request, context, mockExecAware, mockCacheEntry,
                identifier, 0);

        when(
                mockClient.revalidateCacheEntry(
                        request, context, mockExecAware, mockCacheEntry)).thenReturn(mockResponse);
        when(mockResponse.getCode()).thenReturn(200);

        impl.run();

        verify(mockClient).revalidateCacheEntry(
                request, context, mockExecAware, mockCacheEntry);
        verify(mockParent).markComplete(identifier);
        verify(mockParent).jobSuccessful(identifier);
    }

    @Test
    public void testRunReportsJobFailedForStaleResponse() throws Exception {
        final String identifier = "foo";
        final Header[] warning = new Header[] {new BasicHeader(HeaderConstants.WARNING, "110 localhost \"Response is stale\"")};

        final AsynchronousValidationRequest impl = new AsynchronousValidationRequest(
                mockParent, mockClient, request, context, mockExecAware, mockCacheEntry,
                identifier, 0);

        when(
                mockClient.revalidateCacheEntry(
                        request, context, mockExecAware, mockCacheEntry)).thenReturn(mockResponse);
        when(mockResponse.getCode()).thenReturn(200);
        when(mockResponse.getHeaders(HeaderConstants.WARNING)).thenReturn(warning);

        impl.run();

        verify(mockClient).revalidateCacheEntry(
                request, context, mockExecAware, mockCacheEntry);
        verify(mockResponse).getHeaders(HeaderConstants.WARNING);
        verify(mockParent).markComplete(identifier);
        verify(mockParent).jobFailed(identifier);
    }

    @Test
    public void testRunGracefullyHandlesProtocolException() throws Exception {
        final String identifier = "foo";

        final AsynchronousValidationRequest impl = new AsynchronousValidationRequest(
                mockParent, mockClient, request, context, mockExecAware, mockCacheEntry,
                identifier, 0);

        when(
                mockClient.revalidateCacheEntry(
                        request, context, mockExecAware, mockCacheEntry)).thenThrow(
                new ProtocolException());

        impl.run();

        verify(mockClient).revalidateCacheEntry(
                request, context, mockExecAware, mockCacheEntry);
        verify(mockParent).markComplete(identifier);
        verify(mockParent).jobFailed(identifier);
    }

    @Test
    public void testRunGracefullyHandlesIOException() throws Exception {
        final String identifier = "foo";

        final AsynchronousValidationRequest impl = new AsynchronousValidationRequest(
                mockParent, mockClient, request, context, mockExecAware, mockCacheEntry,
                identifier, 0);

        when(
                mockClient.revalidateCacheEntry(
                        request, context, mockExecAware, mockCacheEntry)).thenThrow(
                                new IOException());

        impl.run();

        verify(mockClient).revalidateCacheEntry(
                request, context, mockExecAware, mockCacheEntry);
        verify(mockParent).markComplete(identifier);
        verify(mockParent).jobFailed(identifier);
    }

    @Test
    public void testRunGracefullyHandlesRuntimeException() throws Exception {
        final String identifier = "foo";

        final AsynchronousValidationRequest impl = new AsynchronousValidationRequest(
                mockParent, mockClient, request, context, mockExecAware, mockCacheEntry,
                identifier, 0);

        when(
                mockClient.revalidateCacheEntry(
                        request, context, mockExecAware, mockCacheEntry)).thenThrow(
                                new RuntimeException());

        impl.run();

        verify(mockClient).revalidateCacheEntry(
                request, context, mockExecAware, mockCacheEntry);
        verify(mockParent).markComplete(identifier);
        verify(mockParent).jobFailed(identifier);
    }
}
