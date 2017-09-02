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
package org.apache.hc.client5.http.impl.cache;

import java.io.IOException;

import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;

public class DummyBackend implements ExecChain {

    private ClassicHttpRequest request;
    private ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
    private int executions = 0;

    public void setResponse(final ClassicHttpResponse resp) {
        response = resp;
    }

    public HttpRequest getCapturedRequest() {
        return request;
    }

    @Override
    public ClassicHttpResponse proceed(
            final ClassicHttpRequest request,
            final Scope scope) throws IOException, HttpException {
        this.request = request;
        executions++;
        return response;
    }

    public int getExecutions() {
        return executions;
    }
}
