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

import org.apache.hc.client5.http.ConnectionKeepAliveStrategy;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.TimeValue;
import org.junit.Assert;
import org.junit.Test;

/**
 *  Simple tests for {@link DefaultConnectionKeepAliveStrategy}.
 */
public class TestDefaultConnKeepAliveStrategy {

    @Test(expected=NullPointerException.class)
    public void testIllegalResponseArg() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final ConnectionKeepAliveStrategy keepAliveStrat = new DefaultConnectionKeepAliveStrategy();
        keepAliveStrat.getKeepAliveDuration(null, context);
    }

    @Test
    public void testNoKeepAliveHeader() throws Exception {
        final HttpClientContext context = HttpClientContext.create();
        context.setRequestConfig( RequestConfig.custom()
                .setConnectionKeepAlive(TimeValue.NEG_ONE_MILLISECOND)
                .build());
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_OK);
        final ConnectionKeepAliveStrategy keepAliveStrat = new DefaultConnectionKeepAliveStrategy();
        final TimeValue d = keepAliveStrat.getKeepAliveDuration(response, context);
        Assert.assertEquals(TimeValue.NEG_ONE_MILLISECOND, d);
    }

    @Test
    public void testEmptyKeepAliveHeader() throws Exception {
        final HttpClientContext context = HttpClientContext.create();
        context.setRequestConfig( RequestConfig.custom()
                .setConnectionKeepAlive(TimeValue.NEG_ONE_MILLISECOND)
                .build());
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_OK);
        response.addHeader("Keep-Alive", "timeout, max=20");
        final ConnectionKeepAliveStrategy keepAliveStrat = new DefaultConnectionKeepAliveStrategy();
        final TimeValue d = keepAliveStrat.getKeepAliveDuration(response, context);
        Assert.assertEquals(TimeValue.NEG_ONE_MILLISECOND, d);
    }

    @Test
    public void testInvalidKeepAliveHeader() throws Exception {
        final HttpClientContext context = HttpClientContext.create();
        context.setRequestConfig( RequestConfig.custom()
                .setConnectionKeepAlive(TimeValue.NEG_ONE_MILLISECOND)
                .build());
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_OK);
        response.addHeader("Keep-Alive", "timeout=whatever, max=20");
        final ConnectionKeepAliveStrategy keepAliveStrat = new DefaultConnectionKeepAliveStrategy();
        final TimeValue d = keepAliveStrat.getKeepAliveDuration(response, context);
        Assert.assertEquals(TimeValue.NEG_ONE_MILLISECOND, d);
    }

    @Test
    public void testKeepAliveHeader() throws Exception {
        final HttpClientContext context = HttpClientContext.create();
        context.setRequestConfig( RequestConfig.custom()
                .setConnectionKeepAlive(TimeValue.NEG_ONE_MILLISECOND)
                .build());
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_OK);
        response.addHeader("Keep-Alive", "timeout=300, max=20");
        final ConnectionKeepAliveStrategy keepAliveStrat = new DefaultConnectionKeepAliveStrategy();
        final TimeValue d = keepAliveStrat.getKeepAliveDuration(response, context);
        Assert.assertEquals(TimeValue.ofSeconds(300), d);
    }

}
