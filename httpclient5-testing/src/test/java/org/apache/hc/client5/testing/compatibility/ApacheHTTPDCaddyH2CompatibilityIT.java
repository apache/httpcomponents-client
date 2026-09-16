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
package org.apache.hc.client5.testing.compatibility;

import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.testing.compatibility.async.H2OverH2TunnelCompatibilityTest;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class ApacheHTTPDCaddyH2CompatibilityIT {

    private static final Network NETWORK = Network.newNetwork();
    static final GenericContainer<?> HTTPD_CONTAINER = ContainerImages.apacheHttpD(NETWORK);
    static final GenericContainer<?> CADDY = ContainerImages.caddyH2Proxy(NETWORK);

    @BeforeAll
    static void startContainers() {
        HTTPD_CONTAINER.start();
        CADDY.start();
    }

    static HttpHost targetInternalTlsHost() {
        return new HttpHost(URIScheme.HTTPS.id, ContainerImages.WEB_SERVER, ContainerImages.HTTPS_PORT);
    }

    static HttpHost h2ProxyContainerHost() {
        return new HttpHost(URIScheme.HTTPS.id, CADDY.getHost(), CADDY.getMappedPort(ContainerImages.H2_PROXY_PORT));
    }

    static HttpHost h2ProxyPwProtectedContainerHost() {
        return new HttpHost(URIScheme.HTTPS.id, CADDY.getHost(), CADDY.getMappedPort(ContainerImages.H2_PROXY_PW_PROTECTED_PORT));
    }

    @AfterAll
    static void cleanup() {
        CADDY.close();
        HTTPD_CONTAINER.close();
        NETWORK.close();
    }

    @Nested
    @DisplayName("H2 client: TLS, connection via H2 proxy (H2-over-H2 tunnel)")
    class AsyncViaH2Proxy extends H2OverH2TunnelCompatibilityTest {

        public AsyncViaH2Proxy() throws Exception {
            super(targetInternalTlsHost(), h2ProxyContainerHost(), null);
        }

    }

    @Nested
    @DisplayName("H2 client: TLS, connection via password protected H2 proxy (H2-over-H2 tunnel)")
    class AsyncViaPwProtectedH2Proxy extends H2OverH2TunnelCompatibilityTest {

        public AsyncViaPwProtectedH2Proxy() throws Exception {
            super(
                    targetInternalTlsHost(),
                    h2ProxyPwProtectedContainerHost(),
                    new UsernamePasswordCredentials("caddy", "nopassword".toCharArray()));
        }

    }

}
