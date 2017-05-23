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

package org.apache.hc.client5.http.impl.sync;

import java.util.Date;

import org.apache.hc.client5.http.sync.ServiceUnavailableRetryStrategy;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;

/**
 * Default implementation of the {@link ServiceUnavailableRetryStrategy} interface.
 * that retries {@code 503} (Service Unavailable) responses for a fixed number of times
 * at a fixed interval.
 *
 * @since 4.2
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class DefaultServiceUnavailableRetryStrategy implements ServiceUnavailableRetryStrategy {

    /**
     * Maximum number of allowed retries if the server responds with a HTTP code
     * in our retry code list. Default value is 1.
     */
    private final int maxRetries;

    /**
     * Retry interval between subsequent requests, in milliseconds. Default
     * value is 1 second.
     */
    private final long defaultRetryInterval;

    public DefaultServiceUnavailableRetryStrategy(final int maxRetries, final int defaultRetryInterval) {
        super();
        Args.positive(maxRetries, "Max retries");
        Args.positive(defaultRetryInterval, "Retry interval");
        this.maxRetries = maxRetries;
        this.defaultRetryInterval = defaultRetryInterval;
    }

    public DefaultServiceUnavailableRetryStrategy() {
        this(1, 1000);
    }

    @Override
    public boolean retryRequest(final HttpResponse response, final int executionCount, final HttpContext context) {
        return executionCount <= maxRetries && response.getCode() == HttpStatus.SC_SERVICE_UNAVAILABLE;
    }

    @Override
    public long getRetryInterval(final HttpResponse response, final HttpContext context) {
        final Header header = response.getFirstHeader(HttpHeaders.RETRY_AFTER);
        if (header != null) {
            final String value = header.getValue();
            try {
                return Long.parseLong(value) * 1000;
            } catch (final NumberFormatException ignore) {
                final Date date = DateUtils.parseDate(value);
                if (date != null) {
                    final long n = date.getTime() - System.currentTimeMillis();
                    return n > 0 ? n : 0;
                }
            }
        }
        return this.defaultRetryInterval;
    }

}
