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

import java.nio.charset.StandardCharsets;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.Assert;
import org.junit.Test;

public class TestRequestExpectContinue {

    @Test
    public void testRequestExpectContinueGenerated() throws Exception {
        final HttpClientContext context = HttpClientContext.create();
        final RequestConfig config = RequestConfig.custom().setExpectContinueEnabled(true).build();
        context.setAttribute(HttpClientContext.REQUEST_CONFIG, config);
        final ClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        final String s = "whatever";
        final StringEntity entity = new StringEntity(s, StandardCharsets.US_ASCII);
        request.setEntity(entity);
        final RequestExpectContinue interceptor = new RequestExpectContinue();
        interceptor.process(request, request.getEntity(), context);
        final Header header = request.getFirstHeader(HttpHeaders.EXPECT);
        Assert.assertNotNull(header);
        Assert.assertEquals(HeaderElements.CONTINUE, header.getValue());
    }

    @Test
    public void testRequestExpectContinueNotGenerated() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final RequestConfig config = RequestConfig.custom().setExpectContinueEnabled(false).build();
        context.setAttribute(HttpClientContext.REQUEST_CONFIG, config);
        final ClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        final String s = "whatever";
        final StringEntity entity = new StringEntity(s, StandardCharsets.US_ASCII);
        request.setEntity(entity);
        final RequestExpectContinue interceptor = new RequestExpectContinue();
        interceptor.process(request, null, context);
        final Header header = request.getFirstHeader(HeaderElements.CONTINUE);
        Assert.assertNull(header);
    }

    @Test
    public void testRequestExpectContinueHTTP10() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final RequestConfig config = RequestConfig.custom().setExpectContinueEnabled(true).build();
        context.setAttribute(HttpClientContext.REQUEST_CONFIG, config);
        final ClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        request.setVersion(HttpVersion.HTTP_1_0);
        final String s = "whatever";
        final StringEntity entity = new StringEntity(s, StandardCharsets.US_ASCII);
        request.setEntity(entity);
        final RequestExpectContinue interceptor = new RequestExpectContinue();
        interceptor.process(request, null, context);
        final Header header = request.getFirstHeader(HeaderElements.CONTINUE);
        Assert.assertNull(header);
    }

    @Test
    public void testRequestExpectContinueZeroContent() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final RequestConfig config = RequestConfig.custom().setExpectContinueEnabled(true).build();
        context.setAttribute(HttpClientContext.REQUEST_CONFIG, config);
        final ClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        final String s = "";
        final StringEntity entity = new StringEntity(s, StandardCharsets.US_ASCII);
        request.setEntity(entity);
        final RequestExpectContinue interceptor = new RequestExpectContinue();
        interceptor.process(request, null, context);
        final Header header = request.getFirstHeader(HeaderElements.CONTINUE);
        Assert.assertNull(header);
    }

    @Test
    public void testRequestExpectContinueInvalidInput() throws Exception {
        final RequestExpectContinue interceptor = new RequestExpectContinue();
        try {
            interceptor.process(null, null, null);
            Assert.fail("NullPointerException should have been thrown");
        } catch (final NullPointerException ex) {
            // expected
        }
    }

    @Test
    public void testRequestExpectContinueIgnoreNonenclosingRequests() throws Exception {
        final HttpContext context = new BasicHttpContext(null);
        final ClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        final RequestExpectContinue interceptor = new RequestExpectContinue();
        interceptor.process(request, null, context);
        Assert.assertEquals(0, request.getHeaders().length);
    }

}
