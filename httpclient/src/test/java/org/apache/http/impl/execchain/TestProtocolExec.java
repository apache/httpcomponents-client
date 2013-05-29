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
package org.apache.http.impl.execchain;

import org.apache.http.HttpHost;
import org.apache.http.client.methods.HttpExecutionAware;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.protocol.HttpProcessor;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestProtocolExec {

    private ClientExecChain requestExecutor;
    private HttpProcessor httpProcessor;
    private ProtocolExec protocolExec;
    private HttpClientContext context;
    private HttpRequestWrapper request;
    private HttpExecutionAware execAware;
    private HttpRoute route;

    @Before
    public void setup() throws Exception {
        requestExecutor = Mockito.mock(ClientExecChain.class);
        httpProcessor = Mockito.mock(HttpProcessor.class);
        protocolExec = new ProtocolExec(requestExecutor, httpProcessor);
        route = new HttpRoute(new HttpHost("foo", 8080));
        context = new HttpClientContext();
        execAware = Mockito.mock(HttpExecutionAware.class);
    }

    @Test
    public void testHostHeaderWhenNonUriRequest() throws Exception {
        request = HttpRequestWrapper.wrap(new BasicHttpRequest("GET", "http://bar/test"));
        protocolExec.execute(route, request, context, execAware);
        // ProtocolExect should have extracted the host from request URI
        Assert.assertEquals(new HttpHost("bar", -1, "http"), context.getTargetHost());
    }

    @Test
    public void testHostHeaderWhenNonUriRequestAndInvalidUri() throws Exception {
        request = HttpRequestWrapper.wrap(new BasicHttpRequest("GET", "http://bar/test|"));
        protocolExec.execute(route, request, context, execAware);
        // ProtocolExect should have fall back to physical host as request URI
        // is not parseable
        Assert.assertEquals(new HttpHost("foo", 8080, "http"), context.getTargetHost());
    }

}
