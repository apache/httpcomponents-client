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
package org.apache.hc.client5.http.impl.sync;

import java.io.IOException;
import java.net.UnknownHostException;

import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.client5.http.sync.methods.HttpGet;
import org.apache.hc.client5.http.sync.methods.HttpUriRequest;
import org.junit.Assert;
import org.junit.Test;


@SuppressWarnings("boxing") // test class
public class TestDefaultHttpRequestRetryHandler {

    @Test
    public void noRetryOnConnectTimeout() throws Exception {
        final HttpUriRequest request = new HttpGet("/");

        final DefaultHttpRequestRetryHandler retryHandler = new DefaultHttpRequestRetryHandler();
        Assert.assertEquals(3, retryHandler.getRetryCount());

        Assert.assertFalse(retryHandler.retryRequest(request, new ConnectTimeoutException(), 1, null));
    }

    @Test
    public void noRetryOnUnknownHost() throws Exception {
        final HttpUriRequest request = new HttpGet("/");

        final DefaultHttpRequestRetryHandler retryHandler = new DefaultHttpRequestRetryHandler();

        Assert.assertFalse(retryHandler.retryRequest(request, new UnknownHostException(), 1, null));
    }

    @Test
    public void noRetryOnAbortedRequests() throws Exception{
        final HttpUriRequest request = new HttpGet("/");
        request.abort();

        final DefaultHttpRequestRetryHandler retryHandler = new DefaultHttpRequestRetryHandler();

        Assert.assertFalse(retryHandler.retryRequest(request, new IOException(), 3, null));
    }

    @Test
    public void retryOnNonAbortedRequests() throws Exception{
        final HttpUriRequest request = new HttpGet("/");

        final DefaultHttpRequestRetryHandler retryHandler = new DefaultHttpRequestRetryHandler();

        Assert.assertTrue(retryHandler.retryRequest(request, new IOException(), 3, null));
    }

    @Test
    public void noRetryOnConnectionTimeout() throws Exception{
        final HttpUriRequest request = new HttpGet("/");

        final DefaultHttpRequestRetryHandler retryHandler = new DefaultHttpRequestRetryHandler();

        Assert.assertFalse(retryHandler.retryRequest(request, new ConnectTimeoutException(), 3, null));
    }

}
