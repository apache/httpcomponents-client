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

package org.apache.hc.client5.http;

import java.io.IOException;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.TimeValue;

/**
 * Strategy interface that allows API users to plug in their own logic to
 * control whether or not a retry should automatically be done, how many times
 * it should be done and so on.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public interface HttpRequestRetryStrategy {

    /**
     * Determines if a method should be retried after an I/O exception
     * occured during execution.
     *
     * @param request the request failed due to an I/O exception
     * @param exception the exception that occurred
     * @param execCount the number of times this method has been
     *                  unsuccessfully executed
     * @param context the context for the request execution
     *
     * @return {@code true} if the request should be retried, {@code false}
     *         otherwise
     */
    boolean retryRequest(HttpRequest request, IOException exception, int execCount, HttpContext context);

    /**
     * Determines if a method should be retried given the response from
     * the target server.
     *
     * @param response the response from the target server
     * @param execCount the number of times this method has been
     *                  unsuccessfully executed
     * @param context the context for the request execution
     *
     * @return {@code true} if the request should be retried, {@code false}
     *         otherwise
     */
    boolean retryRequest(HttpResponse response, int execCount, HttpContext context);

    /**
     * Determines the retry interval between subsequent retries.
     *
     * @param response the response from the target server
     * @param execCount the number of times this method has been
     *                  unsuccessfully executed
     * @param context the context for the request execution
     *
     * @return the retry interval between subsequent retries
     */
    TimeValue getRetryInterval(HttpResponse response, int execCount, HttpContext context);

}
