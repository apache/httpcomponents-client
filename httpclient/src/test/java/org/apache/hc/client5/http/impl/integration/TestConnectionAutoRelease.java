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

package org.apache.hc.client5.http.impl.integration;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.ConnectionPoolTimeoutException;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.io.ConnectionRequest;
import org.apache.hc.client5.http.localserver.LocalServerTestBase;
import org.apache.hc.client5.http.methods.HttpGet;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.MalformedChunkCodingException;
import org.apache.hc.core5.http.entity.BasicHttpEntity;
import org.apache.hc.core5.http.entity.EntityUtils;
import org.apache.hc.core5.http.impl.io.DefaultBHttpServerConnection;
import org.apache.hc.core5.http.io.HttpClientConnection;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.pool.PoolStats;
import org.junit.Assert;
import org.junit.Test;

public class TestConnectionAutoRelease extends LocalServerTestBase {

    @Test
    public void testReleaseOnEntityConsumeContent() throws Exception {
        this.connManager.setDefaultMaxPerRoute(1);
        this.connManager.setMaxTotal(1);

        // Zero connections in the pool
        PoolStats stats = this.connManager.getTotalStats();
        Assert.assertEquals(0, stats.getAvailable());

        final HttpHost target = start();
        // Get some random data
        final HttpGet httpget = new HttpGet("/random/20000");
        final HttpResponse response = this.httpclient.execute(target, httpget);

        ConnectionRequest connreq = this.connManager.requestConnection(new HttpRoute(target), null);
        try {
            connreq.get(250, TimeUnit.MILLISECONDS);
            Assert.fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (final ConnectionPoolTimeoutException expected) {
        }

        final HttpEntity e = response.getEntity();
        Assert.assertNotNull(e);
        EntityUtils.consume(e);

        // Expect one connection in the pool
        stats = this.connManager.getTotalStats();
        Assert.assertEquals(1, stats.getAvailable());

        // Make sure one connection is available
        connreq = this.connManager.requestConnection(new HttpRoute(target), null);
        final HttpClientConnection conn = connreq.get(250, TimeUnit.MILLISECONDS);

        this.connManager.releaseConnection(conn, null, -1, null);
    }

    @Test
    public void testReleaseOnEntityWriteTo() throws Exception {
        this.connManager.setDefaultMaxPerRoute(1);
        this.connManager.setMaxTotal(1);

        // Zero connections in the pool
        PoolStats stats = this.connManager.getTotalStats();
        Assert.assertEquals(0, stats.getAvailable());

        final HttpHost target = start();
        // Get some random data
        final HttpGet httpget = new HttpGet("/random/20000");
        final HttpResponse response = this.httpclient.execute(target, httpget);

        ConnectionRequest connreq = this.connManager.requestConnection(new HttpRoute(target), null);
        try {
            connreq.get(250, TimeUnit.MILLISECONDS);
            Assert.fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (final ConnectionPoolTimeoutException expected) {
        }

        final HttpEntity e = response.getEntity();
        Assert.assertNotNull(e);
        final ByteArrayOutputStream outsteam = new ByteArrayOutputStream();
        e.writeTo(outsteam);

        // Expect one connection in the pool
        stats = this.connManager.getTotalStats();
        Assert.assertEquals(1, stats.getAvailable());

        // Make sure one connection is available
        connreq = this.connManager.requestConnection(new HttpRoute(target), null);
        final HttpClientConnection conn = connreq.get(250, TimeUnit.MILLISECONDS);

        this.connManager.releaseConnection(conn, null, -1, null);
    }

    @Test
    public void testReleaseOnAbort() throws Exception {
        this.connManager.setDefaultMaxPerRoute(1);
        this.connManager.setMaxTotal(1);

        // Zero connections in the pool
        final PoolStats stats = this.connManager.getTotalStats();
        Assert.assertEquals(0, stats.getAvailable());

        final HttpHost target = start();

        // Get some random data
        final HttpGet httpget = new HttpGet("/random/20000");
        final HttpResponse response = this.httpclient.execute(target, httpget);

        ConnectionRequest connreq = this.connManager.requestConnection(new HttpRoute(target), null);
        try {
            connreq.get(250, TimeUnit.MILLISECONDS);
            Assert.fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (final ConnectionPoolTimeoutException expected) {
        }

        final HttpEntity e = response.getEntity();
        Assert.assertNotNull(e);
        httpget.abort();

        // Expect zero connections in the pool
        Assert.assertEquals(0, this.connManager.getTotalStats().getAvailable());

        // Make sure one connection is available
        connreq = this.connManager.requestConnection(new HttpRoute(target), null);
        final HttpClientConnection conn = connreq.get(250, TimeUnit.MILLISECONDS);

        this.connManager.releaseConnection(conn, null, -1, null);
    }

    @Test
    public void testReleaseOnIOException() throws Exception {
        this.serverBootstrap.registerHandler("/dropdead", new HttpRequestHandler() {

            @Override
            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                final BasicHttpEntity entity = new BasicHttpEntity() {

                    @Override
                    public void writeTo(
                            final OutputStream outstream) throws IOException {
                        final byte[] tmp = new byte[5];
                        outstream.write(tmp);
                        outstream.flush();

                        // do something comletely ugly in order to trigger
                        // MalformedChunkCodingException
                        final DefaultBHttpServerConnection conn = (DefaultBHttpServerConnection)
                                context.getAttribute(HttpCoreContext.HTTP_CONNECTION);
                        try {
                            conn.sendResponseHeader(response);
                        } catch (final HttpException ignore) {
                        }
                    }

                };
                entity.setChunked(true);
                response.setEntity(entity);
            }

        });

        this.connManager.setDefaultMaxPerRoute(1);
        this.connManager.setMaxTotal(1);

        // Zero connections in the pool
        Assert.assertEquals(0, this.connManager.getTotalStats().getAvailable());

        final HttpHost target = start();

        // Get some random data
        final HttpGet httpget = new HttpGet("/dropdead");
        final HttpResponse response = this.httpclient.execute(target, httpget);

        ConnectionRequest connreq = this.connManager.requestConnection(new HttpRoute(target), null);
        try {
            connreq.get(250, TimeUnit.MILLISECONDS);
            Assert.fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (final ConnectionPoolTimeoutException expected) {
        }

        final HttpEntity e = response.getEntity();
        Assert.assertNotNull(e);
        // Read the content
        try {
            EntityUtils.toByteArray(e);
            Assert.fail("MalformedChunkCodingException should have been thrown");
        } catch (final MalformedChunkCodingException expected) {

        }

        // Expect zero connections in the pool
        Assert.assertEquals(0, this.connManager.getTotalStats().getAvailable());

        // Make sure one connection is available
        connreq = this.connManager.requestConnection(new HttpRoute(target), null);
        final HttpClientConnection conn = connreq.get(250, TimeUnit.MILLISECONDS);

        this.connManager.releaseConnection(conn, null, -1, null);
    }

}
