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

package org.apache.hc.client5.testing.sync;

import java.net.InetSocketAddress;
import java.util.function.Consumer;

import org.apache.hc.client5.testing.extension.sync.ClientProtocolLevel;
import org.apache.hc.client5.testing.extension.sync.TestClient;
import org.apache.hc.client5.testing.extension.sync.TestClientBuilder;
import org.apache.hc.client5.testing.extension.sync.TestClientResources;
import org.apache.hc.client5.testing.extension.sync.TestServer;
import org.apache.hc.client5.testing.extension.sync.TestServerBootstrap;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

abstract class AbstractIntegrationTestBase {

    public static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    @RegisterExtension
    private final TestClientResources testResources;

    protected AbstractIntegrationTestBase(final URIScheme scheme, final ClientProtocolLevel clientProtocolLevel) {
        this.testResources = new TestClientResources(scheme, clientProtocolLevel, TIMEOUT);
    }

    public URIScheme scheme() {
        return testResources.scheme();
    }

    public ClientProtocolLevel getClientProtocolLevel() {
        return testResources.getClientProtocolLevel();
    }

    public void configureServer(final Consumer<TestServerBootstrap> serverCustomizer) {
        testResources.configureServer(serverCustomizer);
    }

    public HttpHost startServer() throws Exception {
        final TestServer server = testResources.server();
        final InetSocketAddress inetSocketAddress = server.start();
        return new HttpHost(testResources.scheme().id, "localhost", inetSocketAddress.getPort());
    }

    public void configureClient(final Consumer<TestClientBuilder> clientCustomizer) {
        testResources.configureClient(clientCustomizer);
    }

    public TestClient client() throws Exception {
        return testResources.client();
    }

}
