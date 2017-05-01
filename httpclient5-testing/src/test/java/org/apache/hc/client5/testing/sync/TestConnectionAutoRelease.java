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

package org.apache.hc.client5.testing.sync;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.io.ConnectionEndpoint;
import org.apache.hc.client5.http.io.LeaseRequest;
import org.apache.hc.client5.http.sync.methods.HttpGet;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.H1Config;
import org.apache.hc.core5.http.impl.io.DefaultBHttpServerConnection;
import org.apache.hc.core5.http.impl.io.DefaultBHttpServerConnectionFactory;
import org.apache.hc.core5.http.io.SessionOutputBuffer;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.TimeValue;
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

        this.connManager.release(endpoint, null, TimeValue.NEG_ONE_MILLISECONDS);
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

        this.connManager.release(endpoint, null, TimeValue.NEG_ONE_MILLISECONDS);
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

        this.connManager.release(endpoint, null, TimeValue.NEG_ONE_MILLISECONDS);
    }

    @Test
    public void testReleaseOnIOException() throws Exception {
        serverBootstrap.setConnectionFactory(new DefaultBHttpServerConnectionFactory(null, H1Config.DEFAULT, CharCodingConfig.DEFAULT) {

            @Override
            public DefaultBHttpServerConnection createConnection(final Socket socket) throws IOException {
                final DefaultBHttpServerConnection conn = new DefaultBHttpServerConnection(null, H1Config.DEFAULT) {

                    @Override
                    protected OutputStream createContentOutputStream(
                            final long len,
                            final SessionOutputBuffer buffer,
                            final OutputStream outputStream,
                            final Supplier<List<? extends Header>> trailers) {
                        try {
                            buffer.flush(outputStream);
                            outputStream.close();
                        } catch (final IOException ignore) {
                        }
                        return super.createContentOutputStream(len, buffer, outputStream, trailers);
                    }
                };
                conn.bind(socket);
                return conn;
            }
        });

        this.connManager.setDefaultMaxPerRoute(1);
        this.connManager.setMaxTotal(1);

        // Zero connections in the pool
        Assert.assertEquals(0, this.connManager.getTotalStats().getAvailable());

        final HttpHost target = start();

        // Get some random data
        final HttpGet httpget = new HttpGet("/random/1024");
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
            Assert.fail("IOException should have been thrown");
        } catch (final IOException expected) {

        }

        // Expect zero connections in the pool
        Assert.assertEquals(0, this.connManager.getTotalStats().getAvailable());

        // Make sure one connection is available
        final LeaseRequest connreq2 = this.connManager.lease(new HttpRoute(target), null);
        final ConnectionEndpoint endpoint = connreq2.get(250, TimeUnit.MILLISECONDS);

        this.connManager.release(endpoint, null, TimeValue.NEG_ONE_MILLISECONDS);
    }

}
