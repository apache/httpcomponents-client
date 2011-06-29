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

package org.apache.http.impl.client;

import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.client.ServiceUnavailableRetryStrategy;
import org.apache.http.protocol.HttpContext;

/**
 * Default implementation for the <code>ServiceUnavailableRetryStrategy</code>
 * interface.
 *
 */
public class DefaultServiceUnavailableRetryStrategy implements ServiceUnavailableRetryStrategy {

    private List<Integer> retryResponseCodes = new ArrayList<Integer>();

    /**
     * Maximum number of allowed retries if the server responds with a HTTP code
     * in our retry code list. Default value is 1.
     */
    private int maxRetries = 1;

    /**
     * Retry interval between subsequent requests, in milliseconds. Default
     * value is 1 second.
     */
    private long retryInterval = 1000;

    /**
     * Multiplying factor for continuous errors situations returned by the
     * server-side. Each retry attempt will multiply this factor with the retry
     * interval. Default value is 1, which means each retry interval will be
     * constant.
     */
    private int retryFactor = 1;

    public void addResponseCodeForRetry(int responseCode) {
        retryResponseCodes.add(responseCode);
    }

    public boolean retryRequest(final HttpResponse response, int executionCount, final HttpContext context) {
        return executionCount <= maxRetries && retryResponseCodes.contains(
                response.getStatusLine().getStatusCode());
    }

    /**
     * @return The maximum number of allowed auto-retries in case the server
     *         response code is contained in this retry strategy. Default value
     *         is 1, meaning no-retry.
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        if (maxRetries < 1) {
            throw new IllegalArgumentException(
                    "MaxRetries should be greater than 1");
        }
        this.maxRetries = maxRetries;
    }

    /**
     * @return The interval between the subsequent auto-retries. Default value
     *         is 1000 ms, meaning there is 1 second X
     *         <code>getRetryFactor()</code> between the subsequent auto
     *         retries.
     *
     */
    public long getRetryInterval() {
        return retryInterval;
    }

    public void setRetryInterval(long retryInterval) {
        if (retryInterval < 1) {
            throw new IllegalArgumentException(
                    "Retry interval should be greater than 1");
        }
        this.retryInterval = retryInterval;
    }

    /**
     * @return the multiplying factor for continuous errors situations returned
     *         by the server-side. Each retry attempt will multiply this factor
     *         with the retry interval. default value is 1, meaning the retry
     *         intervals are constant.
     */
    public int getRetryFactor() {
        return retryFactor;
    }

    public void setRetryFactor(int factor) {
        if (factor < 1) {
            throw new IllegalArgumentException(
                    "Retry factor should be greater than 1");
        }
        this.retryFactor = factor;
    }

}
