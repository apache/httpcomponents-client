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

import java.util.function.Consumer;

import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Asserts;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestClientResources implements AfterEachCallback {

    private static final Logger LOG = LoggerFactory.getLogger(TestClientResources.class);

    private final URIScheme scheme;
    private final Timeout timeout;
    private final ClientProtocolLevel clientProtocolLevel;
    private final TestServerBootstrap serverBootstrap;
    private final TestClientBuilder clientBuilder;

    private TestServer server;
    private TestClient client;

    public TestClientResources(final URIScheme scheme, final ClientProtocolLevel clientProtocolLevel, final Timeout timeout) {
        this.scheme = scheme != null ? scheme : URIScheme.HTTP;
        this.timeout = timeout;
        this.clientProtocolLevel = clientProtocolLevel != null ? clientProtocolLevel : ClientProtocolLevel.STANDARD;
        this.serverBootstrap = new TestServerBootstrap(this.scheme)
                .setTimeout(this.timeout);
        switch (this.clientProtocolLevel) {
            case MINIMAL:
                this.clientBuilder = new MinimalTestClientBuilder();
                break;
            default:
                this.clientBuilder = new StandardTestClientBuilder();
        }
        this.clientBuilder.setTimeout(timeout);
    }

    @Override
    public void afterEach(final ExtensionContext extensionContext) {
        LOG.debug("Shutting down test server");

        if (client != null) {
            client.close(CloseMode.GRACEFUL);
        }
        if (server != null) {
            server.shutdown(CloseMode.IMMEDIATE);
        }
    }

    public URIScheme scheme() {
        return this.scheme;
    }

    public ClientProtocolLevel getClientProtocolLevel() {
        return clientProtocolLevel;
    }

    public void configureServer(final Consumer<TestServerBootstrap> serverCustomizer) {
        Asserts.check(server == null, "Server is already running and cannot be changed");
        serverCustomizer.accept(serverBootstrap);
    }

    public TestServer server() throws Exception {
        if (server == null) {
            server = serverBootstrap.build();
        }
        return server;
    }

    public void configureClient(final Consumer<TestClientBuilder> clientCustomizer) {
        Asserts.check(client == null, "Client is already running and cannot be changed");
        clientCustomizer.accept(clientBuilder);
    }

    public TestClient client() throws Exception {
        if (client == null) {
            client = clientBuilder.build();
        }
        return client;
    }

}
