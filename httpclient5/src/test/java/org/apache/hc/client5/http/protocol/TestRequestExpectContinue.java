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

package org.apache.hc.client5.http.protocol;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.apache.hc.client5.http.config.ExpectContinueTrigger;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpConnectionMetrics;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.impl.BasicEndpointDetails;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestRequestExpectContinue {

    @Test
    void testRequestExpectContinueGenerated() throws Exception {
        final HttpClientContext context = HttpClientContext.create();
        final RequestConfig config = RequestConfig.custom().setExpectContinueEnabled(true).build();
        context.setRequestConfig(config);
        final ClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        final String s = "whatever";
        final StringEntity entity = new StringEntity(s, StandardCharsets.US_ASCII);
        request.setEntity(entity);
        final RequestExpectContinue interceptor = new RequestExpectContinue();
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.EXPECT);
        Assertions.assertNotNull(header);
        Assertions.assertEquals(HeaderElements.CONTINUE, header.getValue());
    }

    @Test
    void testRequestExpectContinueNotGenerated() throws Exception {
        final HttpClientContext context = HttpClientContext.create();
        final RequestConfig config = RequestConfig.custom().setExpectContinueEnabled(false).build();
        context.setRequestConfig(config);
        final ClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        final String s = "whatever";
        final StringEntity entity = new StringEntity(s, StandardCharsets.US_ASCII);
        request.setEntity(entity);
        final RequestExpectContinue interceptor = new RequestExpectContinue();
        interceptor.process(request, null, context);
        final Header header = request.getFirstHeader(HeaderElements.CONTINUE);
        Assertions.assertNull(header);
    }

    @Test
    void testRequestExpectContinueHTTP10() throws Exception {
        final HttpClientContext context = HttpClientContext.create();
        final RequestConfig config = RequestConfig.custom().setExpectContinueEnabled(true).build();
        context.setRequestConfig(config);
        final ClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        request.setVersion(HttpVersion.HTTP_1_0);
        final String s = "whatever";
        final StringEntity entity = new StringEntity(s, StandardCharsets.US_ASCII);
        request.setEntity(entity);
        final RequestExpectContinue interceptor = new RequestExpectContinue();
        interceptor.process(request, null, context);
        final Header header = request.getFirstHeader(HeaderElements.CONTINUE);
        Assertions.assertNull(header);
    }

    @Test
    void testRequestExpectContinueZeroContent() throws Exception {
        final HttpClientContext context = HttpClientContext.create();
        final RequestConfig config = RequestConfig.custom().setExpectContinueEnabled(true).build();
        context.setRequestConfig(config);
        final ClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        final String s = "";
        final StringEntity entity = new StringEntity(s, StandardCharsets.US_ASCII);
        request.setEntity(entity);
        final RequestExpectContinue interceptor = new RequestExpectContinue();
        interceptor.process(request, null, context);
        final Header header = request.getFirstHeader(HeaderElements.CONTINUE);
        Assertions.assertNull(header);
    }

    @Test
    void testRequestExpectContinueInvalidInput() {
        final RequestExpectContinue interceptor = new RequestExpectContinue();
        Assertions.assertThrows(NullPointerException.class, () -> interceptor.process(null, null, null));
    }

    @Test
    void testRequestExpectContinueIgnoreNonenclosingRequests() throws Exception {
        final HttpClientContext context = HttpClientContext.create();
        final ClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        final RequestExpectContinue interceptor = new RequestExpectContinue();
        interceptor.process(request, null, context);
        Assertions.assertEquals(0, request.getHeaders().length);
    }


    @Test
    void testRequestExpectContinueIfReused() throws Exception {
        final HttpClientContext context = HttpClientContext.create();
        final RequestConfig config = RequestConfig.custom()
                .setExpectContinueEnabled(true)
                .setExpectContinueTrigger(ExpectContinueTrigger.IF_REUSED)
                .build();
        context.setRequestConfig(config);

        final HttpConnectionMetrics metrics = new HttpConnectionMetrics() {
            @Override
            public long getRequestCount() {
                return 1;
            }

            @Override
            public long getResponseCount() {
                return 0;
            }

            @Override
            public long getSentBytesCount() {
                return 0;
            }

            @Override
            public long getReceivedBytesCount() {
                return 0;
            }
        };

        final BasicEndpointDetails reused = new BasicEndpointDetails(
                new InetSocketAddress("localhost", 0),
                new InetSocketAddress("localhost", 80),
                metrics,
                Timeout.ofSeconds(30));
        context.setEndpointDetails(reused);

        final ClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        request.setEntity(new StringEntity("data", StandardCharsets.US_ASCII));

        new RequestExpectContinue().process(request, request.getEntity(), context);

        final Header header = request.getFirstHeader(HttpHeaders.EXPECT);
        Assertions.assertNotNull(header);
        Assertions.assertEquals(HeaderElements.CONTINUE, header.getValue());
    }

    @Test
    void testNoExpectContinueFreshConnectionWithIfReused() throws Exception {
        final HttpClientContext context = HttpClientContext.create();
        final RequestConfig cfg = RequestConfig.custom()
                .setExpectContinueEnabled(true)
                .setExpectContinueTrigger(ExpectContinueTrigger.IF_REUSED)
                .build();
        context.setRequestConfig(cfg);

        // fresh endpoint: requestCount == 0
        context.setEndpointDetails(new BasicEndpointDetails(
                new InetSocketAddress("localhost", 0),
                new InetSocketAddress("localhost", 80),
                null,
                Timeout.ofSeconds(30)));

        final ClassicHttpRequest req = new BasicClassicHttpRequest("POST", "/");
        req.setEntity(new StringEntity("data", StandardCharsets.US_ASCII));

        new RequestExpectContinue().process(req, req.getEntity(), context);

        Assertions.assertNull(req.getFirstHeader(HttpHeaders.EXPECT));
    }

    @Test
    void testHeaderAlreadyPresentIsNotDuplicated() throws Exception {
        final HttpClientContext context = HttpClientContext.create();
        final RequestConfig cfg = RequestConfig.custom()
                .setExpectContinueEnabled(true)
                .build();                      // default trigger = ALWAYS
        context.setRequestConfig(cfg);

        final ClassicHttpRequest req = new BasicClassicHttpRequest("POST", "/");
        req.setEntity(new StringEntity("data", StandardCharsets.US_ASCII));
        req.addHeader(HttpHeaders.EXPECT, HeaderElements.CONTINUE); // pre-existing

        new RequestExpectContinue().process(req, req.getEntity(), context);

        final Header[] headers = req.getHeaders(HttpHeaders.EXPECT);
        Assertions.assertEquals(1, headers.length);                 // no duplicates
    }
}
