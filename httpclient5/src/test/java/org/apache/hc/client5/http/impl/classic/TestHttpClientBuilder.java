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

import java.io.IOException;

import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecChainHandler;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.junit.Test;

public class TestHttpClientBuilder {

    @Test
    public void testAddInterceptorFirstDoesNotThrow() throws IOException {
        // HTTPCLIENT-2083
        HttpClients.custom()
                .addExecInterceptorFirst("first", NopExecChainHandler.INSTANCE)
                .build()
                .close();
    }

    @Test
    public void testAddInterceptorLastDoesNotThrow() throws IOException {
        // HTTPCLIENT-2083
        HttpClients.custom()
                .addExecInterceptorLast("last", NopExecChainHandler.INSTANCE)
                .build()
                .close();
    }

    enum NopExecChainHandler implements ExecChainHandler {
        INSTANCE;

        @Override
        public ClassicHttpResponse execute(
                final ClassicHttpRequest request,
                final ExecChain.Scope scope,
                final ExecChain chain) throws IOException, HttpException {
            return chain.proceed(request, scope);
        }
    }
}