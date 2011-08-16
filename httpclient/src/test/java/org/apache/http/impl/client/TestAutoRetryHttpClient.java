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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.junit.Before;
import org.junit.Test;

public class TestAutoRetryHttpClient{

    private AutoRetryHttpClient impl;

    private HttpClient mockBackend;

    private HttpHost host;

    @Before
    public void setUp() {
        mockBackend = mock(HttpClient.class);
        host = new HttpHost("foo.example.com");
    }

    @Test
    public void testDefaultRetryConfig(){
        DefaultServiceUnavailableRetryStrategy retryStrategy = new DefaultServiceUnavailableRetryStrategy();
        HttpContext context = new BasicHttpContext();
        HttpResponse response1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, 503, "Oppsie");
        assertTrue(retryStrategy.retryRequest(response1, 1, context));
        HttpResponse response2 = new BasicHttpResponse(HttpVersion.HTTP_1_1, 502, "Oppsie");
        assertFalse(retryStrategy.retryRequest(response2, 1, context));
        assertEquals(1000, retryStrategy.getRetryInterval());
    }

    @Test
    public void testNoAutoRetry() throws java.io.IOException{
        DefaultServiceUnavailableRetryStrategy retryStrategy = new DefaultServiceUnavailableRetryStrategy(2, 100);

        impl = new AutoRetryHttpClient(mockBackend,retryStrategy);

        HttpRequest req1 = new BasicHttpRequest("GET","/",HttpVersion.HTTP_1_1);
        HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_INTERNAL_SERVER_ERROR, "Internal Server Error");
        HttpResponse resp2 = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_OK, "OK");

        when(mockBackend.execute(host, req1,(HttpContext)null)).thenReturn(resp1).thenReturn(resp2);

        HttpResponse result =  impl.execute(host, req1);

        verify(mockBackend,times(1)).execute(host, req1,(HttpContext)null);

        assertEquals(resp1,result);
        assertEquals(500,result.getStatusLine().getStatusCode());
    }

    @Test
    public void testMultipleAutoRetry() throws java.io.IOException{
        DefaultServiceUnavailableRetryStrategy retryStrategy = new DefaultServiceUnavailableRetryStrategy(5, 100);

        impl = new AutoRetryHttpClient(mockBackend,retryStrategy);

        HttpRequest req1 = new BasicHttpRequest("GET","/",HttpVersion.HTTP_1_1);
        HttpResponse resp1 = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_SERVICE_UNAVAILABLE, "Service Unavailable");
        HttpResponse resp2 = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_SERVICE_UNAVAILABLE, "Service Unavailable");
        HttpResponse resp3 = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_OK, "OK");

        when(mockBackend.execute(host, req1,(HttpContext)null)).thenReturn(resp1).thenReturn(resp2).thenReturn(resp3);

        HttpResponse result =  impl.execute(host, req1);

        verify(mockBackend,times(3)).execute(host, req1,(HttpContext)null);

        assertEquals(resp3,result);
        assertEquals(200,result.getStatusLine().getStatusCode());
    }

}
