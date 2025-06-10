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
import java.nio.file.Path;
import java.util.function.Consumer;

import org.apache.hc.client5.testing.extension.async.ClientProtocolLevel;
import org.apache.hc.client5.testing.extension.async.ServerProtocolLevel;
import org.apache.hc.client5.testing.extension.async.TestAsyncClient;
import org.apache.hc.client5.testing.extension.async.TestAsyncClientBuilder;
import org.apache.hc.client5.testing.extension.async.TestAsyncResources;
import org.apache.hc.client5.testing.extension.async.TestAsyncServer;
import org.apache.hc.client5.testing.extension.async.TestAsyncServerBootstrap;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

abstract class AbstractIntegrationTestBase {

    public static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    @RegisterExtension
    private final TestAsyncResources testResources;
    private final boolean useUnixDomainSocket;

    protected AbstractIntegrationTestBase(final URIScheme scheme, final ClientProtocolLevel clientProtocolLevel, final ServerProtocolLevel serverProtocolLevel) {
        this(scheme, clientProtocolLevel, serverProtocolLevel, false);
    }

    protected AbstractIntegrationTestBase(
        final URIScheme scheme, final ClientProtocolLevel clientProtocolLevel,
        final ServerProtocolLevel serverProtocolLevel, final boolean useUnixDomainSocket) {
        this.testResources = new TestAsyncResources(scheme, clientProtocolLevel, serverProtocolLevel, TIMEOUT);
        this.useUnixDomainSocket = useUnixDomainSocket;
    }

    public URIScheme scheme() {
        return testResources.scheme();
    }

    public ServerProtocolLevel getServerProtocolLevel() {
        return testResources.getServerProtocolLevel();
    }

    public ClientProtocolLevel getClientProtocolLevel() {
        return testResources.getClientProtocolLevel();
    }

    public void configureServer(final Consumer<TestAsyncServerBootstrap> serverCustomizer) {
        testResources.configureServer(serverCustomizer);
    }

    public HttpHost startServer() throws Exception {
        final TestAsyncServer server = testResources.server();
        final InetSocketAddress inetSocketAddress = server.start();
        if (useUnixDomainSocket) {
            testResources.udsProxy().start();
        }
        return new HttpHost(testResources.scheme().id, "localhost", inetSocketAddress.getPort());
    }

    public void configureClient(final Consumer<TestAsyncClientBuilder> clientCustomizer) {
        testResources.configureClient(clientCustomizer);
    }

    public TestAsyncClient startClient() throws Exception {
        if (useUnixDomainSocket) {
            final Path socketPath = getUnixDomainSocket();
            testResources.configureClient(builder -> {
                builder.setUnixDomainSocket(socketPath);
            });
        }
        final TestAsyncClient client = testResources.client();
        client.start();
        return client;
    }

    public Path getUnixDomainSocket() throws Exception {
        if (useUnixDomainSocket) {
            return testResources.udsProxy().getSocketPath();
        }
        return null;
    }
}
