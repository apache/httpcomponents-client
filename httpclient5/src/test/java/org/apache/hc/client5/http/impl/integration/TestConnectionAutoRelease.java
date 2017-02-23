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
import java.util.concurrent.TimeoutException;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.io.ConnectionEndpoint;
import org.apache.hc.client5.http.io.LeaseRequest;
import org.apache.hc.client5.http.localserver.LocalServerTestBase;
import org.apache.hc.client5.http.sync.methods.HttpGet;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.MalformedChunkCodingException;
import org.apache.hc.core5.http.impl.io.DefaultBHttpServerConnection;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.BasicHttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.junit.Assert;
import org.junit.Test;

public class TestConnectionAutoRelease extends LocalServerTestBase {

    @Test
    public void testReleaseOnEntityConsumeContent() throws Exception {
        this.connManager.setDefaultMaxPerRoute(1);
        this.connManager.setMaxTotal(1);

        // Zero connections in the pool
        Assert.assertEquals(0, this.connManager.getTotalStats().getAvailable());

        final HttpHost target = start();
        // Get some random data
        final HttpGet httpget = new HttpGet("/random/20000");
        final ClassicHttpResponse response = this.httpclient.execute(target, httpget);

        final LeaseRequest connreq1 = this.connManager.lease(new HttpRoute(target), null);
        try {
            connreq1.get(250, TimeUnit.MILLISECONDS);
            Assert.fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (final TimeoutException expected) {
        }

        final HttpEntity e = response.getEntity();
        Assert.assertNotNull(e);
        EntityUtils.consume(e);

        // Expect one connection in the pool
        Assert.assertEquals(1, this.connManager.getTotalStats().getAvailable());

        // Make sure one connection is available
        final LeaseRequest connreq2 = this.connManager.lease(new HttpRoute(target), null);
        final ConnectionEndpoint endpoint = connreq2.get(250, TimeUnit.MILLISECONDS);

        this.connManager.release(endpoint, null, -1, null);
    }

    @Test
    public void testReleaseOnEntityWriteTo() throws Exception {
        this.connManager.setDefaultMaxPerRoute(1);
        this.connManager.setMaxTotal(1);

        // Zero connections in the pool
        Assert.assertEquals(0, this.connManager.getTotalStats().getAvailable());

        final HttpHost target = start();
        // Get some random data
        final HttpGet httpget = new HttpGet("/random/20000");
        final ClassicHttpResponse response = this.httpclient.execute(target, httpget);

        final LeaseRequest connreq1 = this.connManager.lease(new HttpRoute(target), null);
        try {
            connreq1.get(250, TimeUnit.MILLISECONDS);
            Assert.fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (final TimeoutException expected) {
        }

        final HttpEntity e = response.getEntity();
        Assert.assertNotNull(e);
        final ByteArrayOutputStream outsteam = new ByteArrayOutputStream();
        e.writeTo(outsteam);

        // Expect one connection in the pool
        Assert.assertEquals(1, this.connManager.getTotalStats().getAvailable());

        // Make sure one connection is available
        final LeaseRequest connreq2 = this.connManager.lease(new HttpRoute(target), null);
        final ConnectionEndpoint endpoint = connreq2.get(250, TimeUnit.MILLISECONDS);

        this.connManager.release(endpoint, null, -1, null);
    }

    @Test
    public void testReleaseOnAbort() throws Exception {
        this.connManager.setDefaultMaxPerRoute(1);
        this.connManager.setMaxTotal(1);

        // Zero connections in the pool
        Assert.assertEquals(0, this.connManager.getTotalStats().getAvailable());

        final HttpHost target = start();

        // Get some random data
        final HttpGet httpget = new HttpGet("/random/20000");
        final ClassicHttpResponse response = this.httpclient.execute(target, httpget);

        final LeaseRequest connreq1 = this.connManager.lease(new HttpRoute(target), null);
        try {
            connreq1.get(250, TimeUnit.MILLISECONDS);
            Assert.fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (final TimeoutException expected) {
        }

        final HttpEntity e = response.getEntity();
        Assert.assertNotNull(e);
        httpget.abort();

        // Expect zero connections in the pool
        Assert.assertEquals(0, this.connManager.getTotalStats().getAvailable());

        // Make sure one connection is available
        final LeaseRequest connreq2 = this.connManager.lease(new HttpRoute(target), null);
        final ConnectionEndpoint endpoint = connreq2.get(250, TimeUnit.MILLISECONDS);

        this.connManager.release(endpoint, null, -1, null);
    }

    @Test
    public void testReleaseOnIOException() throws Exception {
        this.serverBootstrap.registerHandler("/dropdead", new HttpRequestHandler() {

            @Override
            public void handle(
                    final ClassicHttpRequest request,
                    final ClassicHttpResponse response,
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
        final ClassicHttpResponse response = this.httpclient.execute(target, httpget);

        final LeaseRequest connreq1 = this.connManager.lease(new HttpRoute(target), null);
        try {
            connreq1.get(250, TimeUnit.MILLISECONDS);
            Assert.fail("ConnectionPoolTimeoutException should have been thrown");
        } catch (final TimeoutException expected) {
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
        final LeaseRequest connreq2 = this.connManager.lease(new HttpRoute(target), null);
        final ConnectionEndpoint endpoint = connreq2.get(250, TimeUnit.MILLISECONDS);

        this.connManager.release(endpoint, null, -1, null);
    }

}
