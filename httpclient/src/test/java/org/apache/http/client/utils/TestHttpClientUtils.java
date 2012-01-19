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
package org.apache.http.client.utils;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.localserver.BasicServerTestBase;
import org.apache.http.localserver.LocalTestServer;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;
import org.apache.http.util.EntityUtils;
import org.junit.Before;
import org.junit.Test;

public class TestHttpClientUtils extends BasicServerTestBase {

    @Before
    public void setUp() throws Exception {
        BasicHttpProcessor httpproc = new BasicHttpProcessor();
        httpproc.addInterceptor(new ResponseDate());
        httpproc.addInterceptor(new ResponseServer());
        httpproc.addInterceptor(new ResponseContent());
        httpproc.addInterceptor(new ResponseConnControl());
        this.localServer = new LocalTestServer(httpproc, null);
    }

    @Test
    public void testCloseQuietlyClient() throws Exception {
        HttpClient httpClient = new DefaultHttpClient();
        HttpClientUtils.closeQuietly(httpClient);
    }

    @Test
    public void testCloseQuietlyNullClient() throws Exception {
        HttpClient httpClient = null;
        HttpClientUtils.closeQuietly(httpClient);
    }

    @Test
    public void testCloseQuietlyClientTwice() {
        PoolingClientConnectionManager connectionManager = new PoolingClientConnectionManager();
        HttpClient httpClient = new DefaultHttpClient(connectionManager);
        HttpClientUtils.closeQuietly(httpClient);
        HttpClientUtils.closeQuietly(httpClient);
    }

    @Test
    public void testCloseQuietlyResponse() throws Exception {
        this.localServer.registerDefaultHandlers();
        this.localServer.start();
        PoolingClientConnectionManager connectionManager = new PoolingClientConnectionManager();
        HttpClient httpClient = new DefaultHttpClient(connectionManager);
        HttpHost target = getServerHttp();
        HttpResponse response = httpClient.execute(target, new HttpGet("/"));
        HttpClientUtils.closeQuietly(response);
        HttpClientUtils.closeQuietly(httpClient);
    }

    @Test
    public void testCloseQuietlyResponseNull() throws Exception {
        PoolingClientConnectionManager connectionManager = new PoolingClientConnectionManager();
        HttpClient httpClient = new DefaultHttpClient(connectionManager);
        HttpResponse response = null;
        HttpClientUtils.closeQuietly(response);
        HttpClientUtils.closeQuietly(httpClient);
    }

    @Test
    public void testCloseQuietlyResponseTwice() throws Exception {
        this.localServer.registerDefaultHandlers();
        this.localServer.start();
        PoolingClientConnectionManager connectionManager = new PoolingClientConnectionManager();
        HttpClient httpClient = new DefaultHttpClient(connectionManager);
        HttpHost target = getServerHttp();
        HttpResponse response = httpClient.execute(target, new HttpGet("/"));
        HttpClientUtils.closeQuietly(response);
        HttpClientUtils.closeQuietly(response);
        HttpClientUtils.closeQuietly(httpClient);
    }

    @Test
    public void testCloseQuietlyResponseAfterConsumeContent() throws Exception {
        this.localServer.registerDefaultHandlers();
        this.localServer.start();
        PoolingClientConnectionManager connectionManager = new PoolingClientConnectionManager();
        HttpClient httpClient = new DefaultHttpClient(connectionManager);
        HttpHost target = getServerHttp();
        HttpResponse response = httpClient.execute(target, new HttpGet("/"));
        EntityUtils.consume(response.getEntity());
        HttpClientUtils.closeQuietly(response);
        HttpClientUtils.closeQuietly(httpClient);
    }

}
