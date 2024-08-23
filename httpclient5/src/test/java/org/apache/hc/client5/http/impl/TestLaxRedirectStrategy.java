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

package org.apache.hc.client5.http.impl;

import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestLaxRedirectStrategy {
    @Test
    void testIsRedirectedWithHttpGet() {
        testIsRedirected(new HttpGet("/get"), true);
    }

    @Test
    void testIsRedirectedWithHttpPost() {
        testIsRedirected(new HttpPost("/post"), true);
    }

    @Test
    void testIsRedirectedWithHttpHead() {
        testIsRedirected(new HttpHead("/head"), true);
    }

    @Test
    void testIsRedirectedWithHttpDelete() {
        testIsRedirected(new HttpDelete("/delete"), true);
    }

    @Test
    void testIsRedirectedWithNonRedirectMethod() {
        testIsRedirected(new HttpPut("/put"), false);
    }

    private void testIsRedirected(final HttpRequest request, final boolean expected) {
        final LaxRedirectStrategy strategy = new LaxRedirectStrategy();
        final HttpResponse response = mock(HttpResponse.class);
        final HttpContext context = mock(HttpContext.class);

        // Mock the response to simulate a redirect with a Location header
        when(response.getCode()).thenReturn(HttpStatus.SC_MOVED_TEMPORARILY);
        when(response.containsHeader("Location")).thenReturn(true);
        when(response.getFirstHeader("location")).thenReturn(mock(org.apache.hc.core5.http.Header.class));
        when(response.getFirstHeader("location").getValue()).thenReturn("http://localhost/redirect");

        assertEquals(expected, strategy.isRedirected(request, response, context));
    }
}