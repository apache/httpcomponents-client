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

package org.apache.hc.client5.testing.extension.async;

import java.util.function.Consumer;

import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.util.Asserts;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestAsyncServerResources implements AfterEachCallback {

    private static final Logger LOG = LoggerFactory.getLogger(TestAsyncServerResources.class);

    private final URIScheme scheme;
    private final TestAsyncServerBootstrap serverBootstrap;

    private TestAsyncServer server;

    public TestAsyncServerResources(final URIScheme scheme, final Timeout timeout, final ServerProtocolLevel serverProtocolLevel) {
        this.scheme = scheme != null ? scheme : URIScheme.HTTP;
        this.serverBootstrap = new TestAsyncServerBootstrap(this.scheme, serverProtocolLevel)
                .setTimeout(timeout);
    }

    @Override
    public void afterEach(final ExtensionContext extensionContext) {
        LOG.debug("Shutting down test server");
        if (server != null) {
            server.shutdown(TimeValue.ofSeconds(5));
        }
    }

    public URIScheme scheme() {
        return this.scheme;
    }

    public void configureServer(final Consumer<TestAsyncServerBootstrap> serverCustomizer) {
        Asserts.check(server == null, "Server is already running and cannot be changed");
        serverCustomizer.accept(serverBootstrap);
    }

    public TestAsyncServer server() throws Exception {
        if (server == null) {
            server = serverBootstrap.build();
        }
        return server;
    }

}
