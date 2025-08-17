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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.apache.hc.client5.http.HttpRequestRetryStrategy;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.util.TimeValue;
import org.junit.jupiter.api.Test;

class TooEarlyRetryStrategyTest {

    @Test
    void retriesOnceOn425ForIdempotent() {
        final TooEarlyRetryStrategy strat = new TooEarlyRetryStrategy(true);

        final HttpResponse resp425 = mock(HttpResponse.class);
        when(resp425.getCode()).thenReturn(HttpStatus.SC_TOO_EARLY);

        final HttpRequest req = mock(HttpRequest.class);
        when(req.getMethod()).thenReturn("GET");

        final HttpCoreContext ctx = HttpCoreContext.create();
        ctx.setRequest(req);

        assertTrue(strat.retryRequest(resp425, 1, ctx));
        // verify flag set
        assertEquals(Boolean.TRUE,
                ctx.getAttribute(TooEarlyRetryStrategy.DISABLE_EARLY_DATA_ATTR));
    }


    @Test
    void doesNotRetryNonIdempotent() {
        final TooEarlyRetryStrategy strat = new TooEarlyRetryStrategy(true);

        final HttpResponse resp425 = mock(HttpResponse.class);
        when(resp425.getCode()).thenReturn(HttpStatus.SC_TOO_EARLY);

        final HttpRequest req = mock(HttpRequest.class);
        when(req.getMethod()).thenReturn("POST");

        final HttpContext ctx = mock(HttpContext.class);
        when(ctx.getAttribute(HttpCoreContext.HTTP_REQUEST)).thenReturn(req);

        assertFalse(strat.retryRequest(resp425, 1, ctx));
    }

    @Test
    void maxRetriesRespected() {
        final TooEarlyRetryStrategy strat = new TooEarlyRetryStrategy(1, true, (HttpRequestRetryStrategy) null);

        final HttpResponse resp425 = mock(HttpResponse.class);
        when(resp425.getCode()).thenReturn(HttpStatus.SC_TOO_EARLY);

        final HttpRequest req = mock(HttpRequest.class);
        when(req.getMethod()).thenReturn("GET");

        final HttpContext ctx = mock(HttpContext.class);
        when(ctx.getAttribute(HttpCoreContext.HTTP_REQUEST)).thenReturn(req);

        assertFalse(strat.retryRequest(resp425, 2, ctx), "execCount > maxRetries");
    }

    @Test
    void retryAfterDeltaSeconds() {
        final TooEarlyRetryStrategy strat = new TooEarlyRetryStrategy(true);

        final HttpResponse resp = mock(HttpResponse.class);
        when(resp.getCode()).thenReturn(HttpStatus.SC_TOO_MANY_REQUESTS);
        final Header h = mock(Header.class);
        when(h.getValue()).thenReturn("3");
        when(resp.getFirstHeader("Retry-After")).thenReturn(h);

        final TimeValue tv = strat.getRetryInterval(resp, 1, mock(HttpContext.class));
        assertEquals(3000L, tv.toMilliseconds());
    }

    @Test
    void retryAfterHttpDate() {
        final TooEarlyRetryStrategy strat = new TooEarlyRetryStrategy(true);

        final ZonedDateTime future = ZonedDateTime.now().plusSeconds(2);
        final String httpDate = future.format(DateTimeFormatter.RFC_1123_DATE_TIME);

        final HttpResponse resp = mock(HttpResponse.class);
        when(resp.getCode()).thenReturn(HttpStatus.SC_SERVICE_UNAVAILABLE);
        final Header h = mock(Header.class);
        when(h.getValue()).thenReturn(httpDate);
        when(resp.getFirstHeader("Retry-After")).thenReturn(h);

        final TimeValue tv = strat.getRetryInterval(resp, 1, mock(HttpContext.class));
        assertTrue(tv.toMilliseconds() >= 1000L, "expected ~2s backoff");
    }

    @Test
    void delegateForExceptionsHonored() {
        final HttpRequestRetryStrategy delegate = mock(HttpRequestRetryStrategy.class);
        when(delegate.retryRequest(any(HttpRequest.class), any(IOException.class), anyInt(), any(HttpContext.class)))
                .thenReturn(true);

        final TooEarlyRetryStrategy strat = new TooEarlyRetryStrategy(1, true, delegate);

        assertTrue(strat.retryRequest(mock(HttpRequest.class), new IOException("boom"), 1, mock(HttpContext.class)));
        verify(delegate).retryRequest(any(HttpRequest.class), any(IOException.class), anyInt(), any(HttpContext.class));
    }
}
