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

package org.apache.http.client.protocol;

import org.apache.http.Header;
import org.apache.http.HttpVersion;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.junit.Assert;
import org.junit.Test;

public class TestRequestExpectContinue {

    @Test
    public void testRequestExpectContinueGenerated() throws Exception {
        HttpContext context = new BasicHttpContext();
        RequestConfig config = RequestConfig.custom().setExpectContinueEnabled(true).build();
        context.setAttribute(ClientContext.REQUEST_CONFIG, config);
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        String s = "whatever";
        StringEntity entity = new StringEntity(s, "US-ASCII");
        request.setEntity(entity);
        RequestExpectContinue interceptor = new RequestExpectContinue();
        interceptor.process(request, context);
        Header header = request.getFirstHeader(HTTP.EXPECT_DIRECTIVE);
        Assert.assertNotNull(header);
        Assert.assertEquals(HTTP.EXPECT_CONTINUE, header.getValue());
    }

    @Test
    public void testRequestExpectContinueNotGenerated() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        RequestConfig config = RequestConfig.custom().setExpectContinueEnabled(false).build();
        context.setAttribute(ClientContext.REQUEST_CONFIG, config);
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        String s = "whatever";
        StringEntity entity = new StringEntity(s, "US-ASCII");
        request.setEntity(entity);
        RequestExpectContinue interceptor = new RequestExpectContinue();
        interceptor.process(request, context);
        Header header = request.getFirstHeader(HTTP.EXPECT_DIRECTIVE);
        Assert.assertNull(header);
    }

    @Test
    public void testRequestExpectContinueHTTP10() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        RequestConfig config = RequestConfig.custom().setExpectContinueEnabled(true).build();
        context.setAttribute(ClientContext.REQUEST_CONFIG, config);
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest(
                "POST", "/", HttpVersion.HTTP_1_0);
        String s = "whatever";
        StringEntity entity = new StringEntity(s, "US-ASCII");
        request.setEntity(entity);
        RequestExpectContinue interceptor = new RequestExpectContinue();
        interceptor.process(request, context);
        Header header = request.getFirstHeader(HTTP.EXPECT_DIRECTIVE);
        Assert.assertNull(header);
    }

    @Test
    public void testRequestExpectContinueZeroContent() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        RequestConfig config = RequestConfig.custom().setExpectContinueEnabled(true).build();
        context.setAttribute(ClientContext.REQUEST_CONFIG, config);
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        String s = "";
        StringEntity entity = new StringEntity(s, "US-ASCII");
        request.setEntity(entity);
        RequestExpectContinue interceptor = new RequestExpectContinue();
        interceptor.process(request, context);
        Header header = request.getFirstHeader(HTTP.EXPECT_DIRECTIVE);
        Assert.assertNull(header);
    }

    @Test
    public void testRequestExpectContinueInvalidInput() throws Exception {
        RequestExpectContinue interceptor = new RequestExpectContinue();
        try {
            interceptor.process(null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testRequestExpectContinueIgnoreNonenclosingRequests() throws Exception {
        HttpContext context = new BasicHttpContext(null);
        BasicHttpRequest request = new BasicHttpRequest("POST", "/");
        RequestExpectContinue interceptor = new RequestExpectContinue();
        interceptor.process(request, context);
        Assert.assertEquals(0, request.getAllHeaders().length);
    }

}
