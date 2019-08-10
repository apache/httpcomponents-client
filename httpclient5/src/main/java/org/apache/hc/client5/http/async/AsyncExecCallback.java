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
package org.apache.hc.client5.http.async;

import java.io.IOException;

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;

/**
 * AsyncExecCallback methods represent response processing events
 * in the client side request execution chain.
 *
 * @since 5.0
 */
public interface AsyncExecCallback {

    /**
     * Triggered to signal receipt of a response message head sent by the server
     * in response to the request being executed.
     *
     * @param response the response message head.
     * @param entityDetails the response entity details or {@code null} if the response
     *                      does not enclose an entity.
     * @return the data consumer to be used for processing of incoming response message body.
     */
    AsyncDataConsumer handleResponse(
            HttpResponse response,
            EntityDetails entityDetails) throws HttpException, IOException;

    /**
     * Triggered to signal receipt of an intermediate response message.
     *
     * @param response the intermediate response message.
     */
    void handleInformationResponse(HttpResponse response) throws HttpException, IOException;

    /**
     * Triggered to signal completion of the message exchange.
     * <p>
     * Implementations of this message are expected to perform resource deallocation
     * allocated in the course of the request execution and response processing.
     * </p>
     */
    void completed();

    /**
     * Triggered to signal a failure occurred during the message exchange.
     * <p>
     * Implementations of this message are expected to perform resource deallocation
     * allocated in the course of the request execution and response processing.
     * </p>
     */
    void failed(Exception cause);

}
