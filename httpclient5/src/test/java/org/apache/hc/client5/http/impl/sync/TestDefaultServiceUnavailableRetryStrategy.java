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

import java.util.Date;

import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestDefaultServiceUnavailableRetryStrategy {

    private DefaultServiceUnavailableRetryStrategy impl;

    @Before
    public void setup() {
        this.impl = new DefaultServiceUnavailableRetryStrategy(3, 1234);
    }

    @Test
    public void testBasics() throws Exception {
        final HttpResponse response1 = new BasicHttpResponse(503, "Oppsie");
        Assert.assertTrue(this.impl.retryRequest(response1, 1, null));
        Assert.assertTrue(this.impl.retryRequest(response1, 2, null));
        Assert.assertTrue(this.impl.retryRequest(response1, 3, null));
        Assert.assertFalse(this.impl.retryRequest(response1, 4, null));
        final HttpResponse response2 = new BasicHttpResponse(500, "Big Time Oppsie");
        Assert.assertFalse(this.impl.retryRequest(response2, 1, null));

        Assert.assertEquals(1234, this.impl.getRetryInterval(response1, null));
    }

    @Test
    public void testRetryAfterHeaderAsLong() throws Exception {
        final HttpResponse response = new BasicHttpResponse(503, "Oppsie");
        response.setHeader(HttpHeaders.RETRY_AFTER, "321");

        Assert.assertEquals(321000, this.impl.getRetryInterval(response, null));
    }

    @Test
    public void testRetryAfterHeaderAsDate() throws Exception {
        this.impl = new DefaultServiceUnavailableRetryStrategy(3, 1);
        final HttpResponse response = new BasicHttpResponse(503, "Oppsie");

        response.setHeader(HttpHeaders.RETRY_AFTER, DateUtils.formatDate(new Date(System.currentTimeMillis() + 100000L)));

        Assert.assertTrue(this.impl.getRetryInterval(response, null) > 1);
    }

    @Test
    public void testRetryAfterHeaderAsPastDate() throws Exception {
        final HttpResponse response = new BasicHttpResponse(503, "Oppsie");

        response.setHeader(HttpHeaders.RETRY_AFTER, DateUtils.formatDate(new Date(System.currentTimeMillis() - 100000L)));

        Assert.assertEquals(0, this.impl.getRetryInterval(response, null));
    }

    @Test
    public void testInvalidRetryAfterHeader() throws Exception {
        final DefaultServiceUnavailableRetryStrategy impl = new DefaultServiceUnavailableRetryStrategy(3, 1234);

        final HttpResponse response = new BasicHttpResponse(503, "Oppsie");
        response.setHeader(HttpHeaders.RETRY_AFTER, "Stuff");

        Assert.assertEquals(1234, impl.getRetryInterval(response, null));
    }

}
