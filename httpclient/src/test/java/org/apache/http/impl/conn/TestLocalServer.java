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

package org.apache.http.impl.conn;

import org.apache.http.HttpClientConnection;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.localserver.LocalTestServer;
import org.apache.http.localserver.ServerTestBase;
import org.apache.http.params.DefaultedHttpParams;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 * This is more a test for the {@link LocalTestServer LocalTestServer}
 * than anything else.
 */
public class TestLocalServer extends ServerTestBase {

    @Test
    public void testEcho() throws Exception {

        final String  message = "Hello, world!";
        final String  charset = "UTF-8";
        final HttpHost target = getServerHttp();

        HttpPost request = new HttpPost("/echo/");
        request.setHeader("Host", target.getHostName());
        request.setEntity(new StringEntity(message, charset));

        HttpClientConnection conn = connectTo(target);

        httpContext.setAttribute(
                ExecutionContext.HTTP_CONNECTION, conn);
        httpContext.setAttribute(
                ExecutionContext.HTTP_TARGET_HOST, target);
        httpContext.setAttribute(
                ExecutionContext.HTTP_REQUEST, request);

        request.setParams(
                new DefaultedHttpParams(request.getParams(), defaultParams));
        httpExecutor.preProcess
            (request, httpProcessor, httpContext);
        HttpResponse response = httpExecutor.execute
            (request, conn, httpContext);
        response.setParams(
                new DefaultedHttpParams(response.getParams(), defaultParams));
        httpExecutor.postProcess
            (response, httpProcessor, httpContext);

        Assert.assertEquals("wrong status in response", HttpStatus.SC_OK,
                     response.getStatusLine().getStatusCode());

        String received = EntityUtils.toString(response.getEntity());
        conn.close();

        Assert.assertEquals("wrong echo", message, received);
    }

    @Test
    public void testRandom() throws Exception {

        final HttpHost target = getServerHttp();
        int[] sizes = new int[] {
            10, 2048, 4100, 0, -1
        };

        for (int i=0; i<sizes.length; i++) {

            String uri = "/random/" + sizes[i];
            if (sizes[i] < 0)
                uri += "/";

            HttpGet request = new HttpGet(uri);

            HttpClientConnection conn = connectTo(target);

            httpContext.setAttribute(
                    ExecutionContext.HTTP_CONNECTION, conn);
            httpContext.setAttribute(
                    ExecutionContext.HTTP_TARGET_HOST, target);
            httpContext.setAttribute(
                    ExecutionContext.HTTP_REQUEST, request);

            request.setParams(
                    new DefaultedHttpParams(request.getParams(), defaultParams));
            httpExecutor.preProcess
                (request, httpProcessor, httpContext);
            HttpResponse response = httpExecutor.execute
                (request, conn, httpContext);
            response.setParams(
                    new DefaultedHttpParams(response.getParams(), defaultParams));
            httpExecutor.postProcess
                (response, httpProcessor, httpContext);

            Assert.assertEquals("(" + sizes[i] + ") wrong status in response",
                         HttpStatus.SC_OK,
                         response.getStatusLine().getStatusCode());

            byte[] data = EntityUtils.toByteArray(response.getEntity());
            if (sizes[i] >= 0)
                Assert.assertEquals("(" + sizes[i] + ") wrong length of response",
                             sizes[i], data.length);
            conn.close();
        }
    }

}
