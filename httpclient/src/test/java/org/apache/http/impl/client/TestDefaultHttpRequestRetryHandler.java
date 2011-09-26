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
package org.apache.http.impl.client;

import org.apache.http.conn.ConnectTimeoutException;
import java.io.IOException;
import java.net.UnknownHostException;

import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.ExecutionContext;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import org.junit.Assert;
import org.junit.Test;


@SuppressWarnings("boxing") // test class
public class TestDefaultHttpRequestRetryHandler {

    @Test
    public void noRetryOnConnectTimeout() throws Exception {
        HttpContext context = mock(HttpContext.class);
        HttpUriRequest request = mock(HttpUriRequest.class);

        DefaultHttpRequestRetryHandler retryHandler = new DefaultHttpRequestRetryHandler();
        Assert.assertEquals(3, retryHandler.getRetryCount());

        when(request.isAborted()).thenReturn(Boolean.FALSE);
        when(context.getAttribute(ExecutionContext.HTTP_REQUEST)).thenReturn(request);

        Assert.assertFalse(retryHandler.retryRequest(new ConnectTimeoutException(), 1, context));
    }

    @Test
    public void noRetryOnUnknownHost() throws Exception {
        HttpContext context = mock(HttpContext.class);
        HttpUriRequest request = mock(HttpUriRequest.class);

        DefaultHttpRequestRetryHandler retryHandler = new DefaultHttpRequestRetryHandler();

        when(request.isAborted()).thenReturn(Boolean.FALSE);
        when(context.getAttribute(ExecutionContext.HTTP_REQUEST)).thenReturn(request);

        Assert.assertFalse(retryHandler.retryRequest(new UnknownHostException(), 1, context));
    }

    @Test
    public void noRetryOnAbortedRequests() throws Exception{
        HttpContext context = mock(HttpContext.class);
        HttpUriRequest request = mock(HttpUriRequest.class);

        DefaultHttpRequestRetryHandler retryHandler = new DefaultHttpRequestRetryHandler();

        when(request.isAborted()).thenReturn(Boolean.TRUE);
        when(context.getAttribute(ExecutionContext.HTTP_REQUEST)).thenReturn(request);

        Assert.assertFalse(retryHandler.retryRequest(new IOException(),3,context));
    }

    @Test
    public void retryOnNonAbortedRequests() throws Exception{

        HttpContext context = mock(HttpContext.class);
        HttpUriRequest request = mock(HttpUriRequest.class);

        DefaultHttpRequestRetryHandler retryHandler = new DefaultHttpRequestRetryHandler();

        when(request.isAborted()).thenReturn(Boolean.FALSE);
        when(context.getAttribute(ExecutionContext.HTTP_REQUEST)).thenReturn(request);

        Assert.assertTrue(retryHandler.retryRequest(new IOException(),3,context));
    }

    @Test
    public void noRetryOnConnectionTimeout() throws Exception{

        HttpContext context = mock(HttpContext.class);
        HttpUriRequest request = mock(HttpUriRequest.class);

        DefaultHttpRequestRetryHandler retryHandler = new DefaultHttpRequestRetryHandler();

        when(request.isAborted()).thenReturn(false);
        when(context.getAttribute(ExecutionContext.HTTP_REQUEST)).thenReturn(request);

        Assert.assertFalse(retryHandler.retryRequest(new ConnectTimeoutException(),3,context));
    }

}
