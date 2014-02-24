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

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.http.HttpClientConnection;
import org.apache.http.conn.HttpClientConnectionManager;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

@SuppressWarnings({"static-access"}) // test code
public class TestConnectionHolder {

    private Log log;
    private HttpClientConnectionManager mgr;
    private HttpClientConnection conn;
    private ConnectionHolder connHolder;

    @Before
    public void setup() {
        log = Mockito.mock(Log.class);
        mgr = Mockito.mock(HttpClientConnectionManager.class);
        conn = Mockito.mock(HttpClientConnection.class);
        connHolder = new ConnectionHolder(log, mgr, conn);
    }

    @Test
    public void testAbortConnection() throws Exception {
        connHolder.abortConnection();

        Assert.assertTrue(connHolder.isReleased());

        Mockito.verify(conn).shutdown();
        Mockito.verify(mgr).releaseConnection(conn, null, 0, TimeUnit.MILLISECONDS);

        connHolder.abortConnection();

        Mockito.verify(conn, Mockito.times(1)).shutdown();
        Mockito.verify(mgr, Mockito.times(1)).releaseConnection(
                Mockito.<HttpClientConnection>any(),
                Mockito.anyObject(),
                Mockito.anyLong(),
                Mockito.<TimeUnit>any());
    }

    @Test
    public void testAbortConnectionIOError() throws Exception {
        Mockito.doThrow(new IOException()).when(conn).shutdown();

        connHolder.abortConnection();

        Assert.assertTrue(connHolder.isReleased());

        Mockito.verify(conn).shutdown();
        Mockito.verify(mgr).releaseConnection(conn, null, 0, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testCancell() throws Exception {
        Assert.assertTrue(connHolder.cancel());

        Assert.assertTrue(connHolder.isReleased());

        Mockito.verify(conn).shutdown();
        Mockito.verify(mgr).releaseConnection(conn, null, 0, TimeUnit.MILLISECONDS);

        Assert.assertFalse(connHolder.cancel());

        Mockito.verify(conn, Mockito.times(1)).shutdown();
        Mockito.verify(mgr, Mockito.times(1)).releaseConnection(
                Mockito.<HttpClientConnection>any(),
                Mockito.anyObject(),
                Mockito.anyLong(),
                Mockito.<TimeUnit>any());
    }

    @Test
    public void testReleaseConnectionReusable() throws Exception {
        connHolder.setState("some state");
        connHolder.setValidFor(100, TimeUnit.SECONDS);
        connHolder.markReusable();

        connHolder.releaseConnection();

        Assert.assertTrue(connHolder.isReleased());

        Mockito.verify(conn, Mockito.never()).close();
        Mockito.verify(mgr).releaseConnection(conn, "some state", 100, TimeUnit.SECONDS);

        connHolder.releaseConnection();

        Mockito.verify(mgr, Mockito.times(1)).releaseConnection(
                Mockito.<HttpClientConnection>any(),
                Mockito.anyObject(),
                Mockito.anyLong(),
                Mockito.<TimeUnit>any());
    }

    @Test
    public void testReleaseConnectionNonReusable() throws Exception {
        connHolder.setState("some state");
        connHolder.setValidFor(100, TimeUnit.SECONDS);
        connHolder.markNonReusable();

        connHolder.releaseConnection();

        Assert.assertTrue(connHolder.isReleased());

        Mockito.verify(conn, Mockito.times(1)).close();
        Mockito.verify(mgr).releaseConnection(conn, null, 0, TimeUnit.MILLISECONDS);

        connHolder.releaseConnection();

        Mockito.verify(mgr, Mockito.times(1)).releaseConnection(
                Mockito.<HttpClientConnection>any(),
                Mockito.anyObject(),
                Mockito.anyLong(),
                Mockito.<TimeUnit>any());
    }

}
