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

package org.apache.hc.client5.testing.async;

import java.net.InetSocketAddress;

import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.H1Config;
import org.apache.hc.core5.http.impl.HttpProcessors;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.io.ShutdownType;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.junit.Rule;
import org.junit.rules.ExternalResource;

public abstract class IntegrationTestBase extends LocalAsyncServerTestBase {

    public IntegrationTestBase(final URIScheme scheme) {
        super(scheme);
    }

    public IntegrationTestBase() {
        super(URIScheme.HTTP);
    }

    protected HttpAsyncClientBuilder clientBuilder;
    protected CloseableHttpAsyncClient httpclient;

    @Rule
    public ExternalResource clientResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            clientBuilder = HttpAsyncClientBuilder.create().setConnectionManager(connManager);
        }

        @Override
        protected void after() {
            if (httpclient != null) {
                httpclient.shutdown(ShutdownType.GRACEFUL);
                httpclient = null;
            }
        }

    };

    public HttpHost start(final HttpProcessor httpProcessor, final H1Config h1Config) throws Exception {
        server.start(httpProcessor, h1Config);
        final ListenerEndpoint listener = server.listen(new InetSocketAddress(0));
        httpclient = clientBuilder.build();
        httpclient.start();
        listener.waitFor();
        final InetSocketAddress address = (InetSocketAddress) listener.getAddress();
        return new HttpHost("localhost", address.getPort(), scheme.name());
    }

    public HttpHost start() throws Exception {
        return start(HttpProcessors.server(), H1Config.DEFAULT);
    }

}
