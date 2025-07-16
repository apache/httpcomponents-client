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
import java.util.Optional;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.TimeValue;

/**
 * Strategy interface to determine if the request should automatically reexecuted
 * in case of an I/O error or a recoverable response status code.
 *
 * @since 5.6
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public interface RequestReExecutionStrategy {

    /**
     * Determines if a method should be re-executed after an I/O exception
     * occurred during execution.
     *
     * @param request the request failed due to an I/O exception
     * @param exception the exception that occurred
     * @param execCount the number of times this method has been
     *                  unsuccessfully executed
     * @param context the context for the request execution
     *
     * @return a time interval to pause before the request re-execution
     *  or {@link Optional#empty()} if the request is not to be re-executed
     */
    Optional<TimeValue> reExecute(HttpRequest request, IOException exception, int execCount, HttpContext context);

    /**
     * Determines if a method should be re-executed given the response from
     * the target server.
     *
     * @param response the response from the target server
     * @param execCount the number of times this method has been
     *                  unsuccessfully executed
     * @param context the context for the request execution
     *
     * @return a time interval to pause before the request re-execution
     *  or {@link Optional#empty()} if the request is not to be re-executed
     */
    Optional<TimeValue> reExecute(HttpResponse response, int execCount, HttpContext context);

}
