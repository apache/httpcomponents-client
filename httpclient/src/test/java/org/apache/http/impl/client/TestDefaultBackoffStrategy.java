/*
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package org.apache.http.impl.client;

import static org.junit.Assert.*;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.client.ConnectionBackoffStrategy;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.message.BasicHttpResponse;
import org.junit.Before;
import org.junit.Test;


public class TestDefaultBackoffStrategy {

    private DefaultBackoffStrategy impl;

    @Before
    public void setUp() {
        impl = new DefaultBackoffStrategy();
    }

    @Test
    public void isABackoffStrategy() {
        assertTrue(impl instanceof ConnectionBackoffStrategy);
    }

    @Test
    public void backsOffForSocketTimeouts() {
        assertTrue(impl.shouldBackoff(new SocketTimeoutException()));
    }

    @Test
    public void backsOffForConnectionTimeouts() {
        assertTrue(impl.shouldBackoff(new ConnectException()));
    }

    @Test
    public void doesNotBackOffForConnectionManagerTimeout() {
        assertFalse(impl.shouldBackoff(new ConnectionPoolTimeoutException()));
    }

    @Test
    public void backsOffForServiceUnavailable() {
        HttpResponse resp = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_SERVICE_UNAVAILABLE, "Service Unavailable");
        assertTrue(impl.shouldBackoff(resp));
    }

    @Test
    public void doesNotBackOffForNon503StatusCodes() {
        for(int i = 100; i <= 599; i++) {
            if (i == HttpStatus.SC_SERVICE_UNAVAILABLE) continue;
            HttpResponse resp = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                    i, "Foo");
            assertFalse(impl.shouldBackoff(resp));
        }
    }
}
