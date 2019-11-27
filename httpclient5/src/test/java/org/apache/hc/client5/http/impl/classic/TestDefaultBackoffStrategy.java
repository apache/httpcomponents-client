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
package org.apache.hc.client5.http.impl.classic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import org.apache.hc.core5.http.ConnectionRequestTimeoutException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.junit.Before;
import org.junit.Test;


public class TestDefaultBackoffStrategy {

    private DefaultBackoffStrategy impl;

    @Before
    public void setUp() {
        impl = new DefaultBackoffStrategy();
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
        assertFalse(impl.shouldBackoff(new ConnectionRequestTimeoutException()));
    }

    @Test
    public void backsOffForServiceUnavailable() {
        final HttpResponse resp = new BasicHttpResponse(HttpStatus.SC_SERVICE_UNAVAILABLE, "Service Unavailable");
        assertTrue(impl.shouldBackoff(resp));
    }

    @Test
    public void backsOffForTooManyRequests() {
        final HttpResponse resp = new BasicHttpResponse(HttpStatus.SC_TOO_MANY_REQUESTS, "Too Many Requests");
        assertTrue(impl.shouldBackoff(resp));
    }

    @Test
    public void doesNotBackOffForNon429And503StatusCodes() {
        for(int i = 100; i <= 599; i++) {
            if (i== HttpStatus.SC_TOO_MANY_REQUESTS || i == HttpStatus.SC_SERVICE_UNAVAILABLE) {
                continue;
            }
            final HttpResponse resp = new BasicHttpResponse(i, "Foo");
            assertFalse(impl.shouldBackoff(resp));
        }
    }
}
