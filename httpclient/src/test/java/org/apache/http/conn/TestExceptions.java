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

package org.apache.http.conn;

import org.apache.http.HttpHost;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.net.InetAddress;

/**
 * Unit tests for exceptions.
 * Trivial, but it looks better in the Clover reports.
 */
public class TestExceptions {

    @Test
    public void testConnectTimeoutExceptionNullMessage() {
        final ConnectTimeoutException ctx = new ConnectTimeoutException();
        Assert.assertNull(ctx.getMessage());
    }

    @Test
    public void testConnectTimeoutExceptionSimpleMessage() {
        final ConnectTimeoutException ctx = new ConnectTimeoutException("sample exception message");
        Assert.assertEquals("sample exception message", ctx.getMessage());
    }

    @Test
    public void testConnectTimeoutExceptionFromNullCause() {
        final ConnectTimeoutException ctx = new ConnectTimeoutException(null, null);
        Assert.assertEquals("Connect to remote host timed out", ctx.getMessage());
    }

    @Test
    public void testConnectTimeoutExceptionFromCause() {
        final IOException cause = new IOException("something awful");
        final ConnectTimeoutException ctx = new ConnectTimeoutException(cause, null);
        Assert.assertEquals("Connect to remote host failed: something awful", ctx.getMessage());
    }

    @Test
    public void testConnectTimeoutExceptionFromCauseAndHost() {
        final HttpHost target = new HttpHost("localhost");
        final IOException cause = new IOException();
        final ConnectTimeoutException ctx = new ConnectTimeoutException(cause, target);
        Assert.assertEquals("Connect to localhost timed out", ctx.getMessage());
    }

    @Test
    public void testConnectTimeoutExceptionFromCauseHostAndRemoteAddress() throws Exception {
        final HttpHost target = new HttpHost("localhost");
        final InetAddress remoteAddress = InetAddress.getByAddress(new byte[] {1,2,3,4});
        final IOException cause = new IOException();
        final ConnectTimeoutException ctx = new ConnectTimeoutException(cause, target, remoteAddress);
        Assert.assertEquals("Connect to localhost [/1.2.3.4] timed out", ctx.getMessage());
    }

    @Test
    public void testHttpHostConnectExceptionFromNullCause() {
        final HttpHostConnectException ctx = new HttpHostConnectException(null, null,
                (InetAddress [])null);
        Assert.assertEquals("Connect to remote host refused", ctx.getMessage());
    }

    @Test
    public void testHttpHostConnectExceptionFromCause() {
        final IOException cause = new IOException("something awful");
        final HttpHostConnectException ctx = new HttpHostConnectException(cause, null);
        Assert.assertEquals("Connect to remote host failed: something awful", ctx.getMessage());
    }

    @Test
    public void testHttpHostConnectExceptionFromCauseAndHost() {
        final HttpHost target = new HttpHost("localhost");
        final IOException cause = new IOException();
        final HttpHostConnectException ctx = new HttpHostConnectException(cause, target);
        Assert.assertEquals("Connect to localhost refused", ctx.getMessage());
    }

    @Test
    public void testHttpHostConnectExceptionFromCauseHostAndRemoteAddress() throws Exception {
        final HttpHost target = new HttpHost("localhost");
        final InetAddress remoteAddress1 = InetAddress.getByAddress(new byte[] {1,2,3,4});
        final InetAddress remoteAddress2 = InetAddress.getByAddress(new byte[] {5,6,7,8});
        final IOException cause = new IOException();
        final HttpHostConnectException ctx = new HttpHostConnectException(cause, target,
                remoteAddress1, remoteAddress2);
        Assert.assertEquals("Connect to localhost [/1.2.3.4, /5.6.7.8] refused", ctx.getMessage());
    }

    @Test
    public void testConnectionPoolTimeoutException() {
        final String msg = "sample exception message";
        ConnectionPoolTimeoutException cptx = new ConnectionPoolTimeoutException(msg);
        Assert.assertFalse(!cptx.toString().contains(msg));
        Assert.assertSame(msg, cptx.getMessage());

        cptx = new ConnectionPoolTimeoutException();
        Assert.assertNull(cptx.getMessage());
    }

}
