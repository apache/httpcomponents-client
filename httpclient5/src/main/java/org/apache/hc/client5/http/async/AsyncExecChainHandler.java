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

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;

/**
 * Abstract request execution handler in an asynchronous client side request execution
 * chain. Handlers can either be a decorator around another element that implements
 * a cross cutting aspect or a self-contained executor capable of producing a response
 * for the given request.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public interface AsyncExecChainHandler {

    /**
     * Executes the actual HTTP request. The handler can choose to return
     * a response message immediately inside the call or asynchronously
     * at some later point or delegate request execution to the next
     * element in the execution chain.
     *
     * @param request the actual request.
     * @param entityProducer the request entity producer or {@code null} if the request
     *                      does not enclose an entity.
     * @param scope the execution scope .
     * @param chain the next element in the request execution chain.
     * @param asyncExecCallback the execution callback.
     */
    void execute(
            HttpRequest request,
            AsyncEntityProducer entityProducer,
            AsyncExecChain.Scope scope,
            AsyncExecChain chain,
            AsyncExecCallback asyncExecCallback) throws HttpException, IOException;

}
