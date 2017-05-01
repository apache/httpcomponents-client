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

package org.apache.hc.client5.http.impl.async;

import java.io.IOException;

import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;

class AsyncExecChainElement {

    private final AsyncExecChainHandler handler;
    private final AsyncExecChainElement next;

    AsyncExecChainElement(final AsyncExecChainHandler handler, final AsyncExecChainElement next) {
        this.handler = handler;
        this.next = next;
    }

    public void execute(
            final HttpRequest request,
            final AsyncEntityProducer entityProducer,
            final AsyncExecChain.Scope scope,
            final AsyncExecCallback asyncExecCallback) throws HttpException, IOException {
        handler.execute(request, entityProducer, scope, new AsyncExecChain() {

            @Override
            public void proceed(
                    final HttpRequest request,
                    final AsyncEntityProducer entityProducer,
                    final AsyncExecChain.Scope scope,
                    final AsyncExecCallback asyncExecCallback) throws HttpException, IOException {
                next.execute(request, entityProducer, scope, asyncExecCallback);
            }

        }, asyncExecCallback);

    }

    @Override
    public String toString() {
        return "{" +
                "handler=" + handler.getClass() +
                ", next=" + (next != null ? next.handler.getClass() : "null") +
                '}';
    }

}