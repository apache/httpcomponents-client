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

import java.util.ArrayList;
import java.util.List;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestRequestDefaultHeaders {

    @Test
    public void testRequestParameterCheck() throws Exception {
        final HttpContext context = new BasicHttpContext();
        final HttpRequestInterceptor interceptor = RequestDefaultHeaders.INSTANCE;
        Assertions.assertThrows(NullPointerException.class, () ->
                interceptor.process(null, null, context));
    }

    @Test
    public void testNoDefaultHeadersForConnectRequest() throws Exception {
        final HttpRequest request = new BasicHttpRequest("CONNECT", "www.somedomain.com");
        final List<Header> defheaders = new ArrayList<>();
        defheaders.add(new BasicHeader("custom", "stuff"));
        final HttpContext context = new BasicHttpContext();

        final HttpRequestInterceptor interceptor = new RequestDefaultHeaders(defheaders);
        interceptor.process(request, null, context);
        final Header header1 = request.getFirstHeader("custom");
        Assertions.assertNull(header1);
    }

    @Test
    public void testDefaultHeaders() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        request.addHeader("custom", "stuff");
        final List<Header> defheaders = new ArrayList<>();
        defheaders.add(new BasicHeader("custom", "other stuff"));
        final HttpContext context = new BasicHttpContext();

        final HttpRequestInterceptor interceptor = new RequestDefaultHeaders(defheaders);
        interceptor.process(request, null, context);
        final Header[] headers = request.getHeaders("custom");
        Assertions.assertNotNull(headers);
        Assertions.assertEquals(1, headers.length);
        Assertions.assertEquals("stuff", headers[0].getValue());
    }

}
