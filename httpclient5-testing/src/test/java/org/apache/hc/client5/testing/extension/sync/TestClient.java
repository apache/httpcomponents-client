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

package org.apache.hc.client5.testing.extension.sync;

import java.io.IOException;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Args;

public class TestClient extends CloseableHttpClient {

    private final CloseableHttpClient client;
    private final HttpClientConnectionManager connectionManager;

    public TestClient(final CloseableHttpClient client,
                      final HttpClientConnectionManager connectionManager) {
        this.client = Args.notNull(client, "Client");
        this.connectionManager = Args.notNull(connectionManager, "Connection manager");
    }

    @Override
    public void close(final CloseMode closeMode) {
        client.close(closeMode);
    }

    @Override
    public void close() throws IOException {
        client.close();
    }

    @Override
    protected CloseableHttpResponse doExecute(final HttpHost target, final ClassicHttpRequest request, final HttpContext context) throws IOException {
        return CloseableHttpResponse.adapt(client.executeOpen(target, request, context));
    }

    @SuppressWarnings("unchecked")
    public <T extends CloseableHttpClient> T getImplementation() {
        return (T) client;
    }

    @SuppressWarnings("unchecked")
    public <T extends HttpClientConnectionManager> T getConnectionManager() {
        return (T) connectionManager;
    }

}
