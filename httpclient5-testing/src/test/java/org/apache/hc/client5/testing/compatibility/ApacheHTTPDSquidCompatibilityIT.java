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
import org.apache.hc.client5.testing.compatibility.async.CachingHttpAsyncClientCompatibilityTest;
import org.apache.hc.client5.testing.compatibility.async.HttpAsyncClientCompatibilityTest;
import org.apache.hc.client5.testing.compatibility.async.HttpAsyncClientHttp1CompatibilityTest;
import org.apache.hc.client5.testing.compatibility.async.HttpAsyncClientProxyCompatibilityTest;
import org.apache.hc.client5.testing.compatibility.sync.CachingHttpClientCompatibilityTest;
import org.apache.hc.client5.testing.compatibility.sync.HttpClientCompatibilityTest;
import org.apache.hc.client5.testing.compatibility.sync.HttpClientProxyCompatibilityTest;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class ApacheHTTPDSquidCompatibilityIT {

    private static Network NETWORK = Network.newNetwork();
    @Container
    static final GenericContainer<?> HTTPD_CONTAINER = ContainerImages.apacheHttpD(NETWORK);
    @Container
    static final GenericContainer<?> SQUID = ContainerImages.squid(NETWORK);

    static HttpHost targetContainerHost() {
        return new HttpHost(URIScheme.HTTP.id, HTTPD_CONTAINER.getHost(), HTTPD_CONTAINER.getMappedPort(ContainerImages.HTTP_PORT));
    }

    static HttpHost targetInternalHost() {
        return new HttpHost(URIScheme.HTTP.id, ContainerImages.WEB_SERVER, ContainerImages.HTTP_PORT);
    }

    static HttpHost targetContainerTlsHost() {
        return new HttpHost(URIScheme.HTTPS.id, HTTPD_CONTAINER.getHost(), HTTPD_CONTAINER.getMappedPort(ContainerImages.HTTPS_PORT));
    }

    static HttpHost targetInternalTlsHost() {
        return new HttpHost(URIScheme.HTTPS.id, ContainerImages.WEB_SERVER, ContainerImages.HTTPS_PORT);
    }

    static HttpHost proxyContainerHost() {
        return new HttpHost(URIScheme.HTTP.id, SQUID.getHost(), SQUID.getMappedPort(ContainerImages.PROXY_PORT));
    }

    static HttpHost proxyPwProtectedContainerHost() {
        return new HttpHost(URIScheme.HTTP.id, SQUID.getHost(), SQUID.getMappedPort(ContainerImages.PROXY_PW_PROTECTED_PORT));
    }

    @AfterAll
    static void cleanup() {
        SQUID.close();
        HTTPD_CONTAINER.close();
        NETWORK.close();
    }

    @Nested
    @DisplayName("Classic client: HTTP/1.1, plain, direct connection")
    class ClassicDirectHttp extends HttpClientCompatibilityTest {

        public ClassicDirectHttp() throws Exception {
            super(targetContainerHost(), null, null);
        }

    }

    @Nested
    @DisplayName("Classic client: HTTP/1.1, plain, connection via proxy")
    class ClassicViaProxyHttp extends HttpClientCompatibilityTest {

        public ClassicViaProxyHttp() throws Exception {
            super(targetInternalHost(), proxyContainerHost(), null);
        }

    }

    @Nested
    @DisplayName("Classic client: HTTP/1.1, plain, connection via password protected proxy")
    class ClassicViaPwProtectedProxyHttp extends HttpClientCompatibilityTest {

        public ClassicViaPwProtectedProxyHttp() throws Exception {
            super(targetInternalHost(), proxyPwProtectedContainerHost(), new UsernamePasswordCredentials("squid", "nopassword".toCharArray()));
        }

    }

    @Nested
    @DisplayName("Classic client: HTTP/1.1, TLS, direct connection")
    class ClassicDirectHttpTls extends HttpClientCompatibilityTest {

        public ClassicDirectHttpTls() throws Exception {
            super(targetContainerTlsHost(), null, null);
        }

    }

    @Nested
    @DisplayName("Classic client: HTTP/1.1, TLS, connection via proxy (tunnel)")
    class ClassicViaProxyHttpTls extends HttpClientCompatibilityTest {

        public ClassicViaProxyHttpTls() throws Exception {
            super(targetInternalTlsHost(), proxyContainerHost(), null);
        }

    }

    @Nested
    @DisplayName("Classic client: HTTP/1.1, TLS, connection via password protected proxy (tunnel)")
    class ClassicViaPwProtectedProxyHttpTls extends HttpClientCompatibilityTest {

        public ClassicViaPwProtectedProxyHttpTls() throws Exception {
            super(targetInternalTlsHost(), proxyPwProtectedContainerHost(), new UsernamePasswordCredentials("squid", "nopassword".toCharArray()));
        }

    }

    @Nested
    @DisplayName("Async client: HTTP/1.1, plain, direct connection")
    class AsyncDirectHttp1 extends HttpAsyncClientHttp1CompatibilityTest {

        public AsyncDirectHttp1() throws Exception {
            super(targetContainerHost(), null, null);
        }

    }

    @Nested
    @DisplayName("Async client: HTTP/1.1, plain, connection via proxy")
    class AsyncViaProxyHttp1 extends HttpAsyncClientHttp1CompatibilityTest {

        public AsyncViaProxyHttp1() throws Exception {
            super(targetInternalHost(), proxyContainerHost(), null);
        }

    }

    @Nested
    @DisplayName("Async client: HTTP/1.1, plain, connection via password protected proxy")
    class AsyncViaPwProtectedProxyHttp1 extends HttpAsyncClientHttp1CompatibilityTest {

        public AsyncViaPwProtectedProxyHttp1() throws Exception {
            super(targetInternalHost(), proxyPwProtectedContainerHost(), new UsernamePasswordCredentials("squid", "nopassword".toCharArray()));
        }

    }

    @Nested
    @DisplayName("Async client: HTTP/1.1, TLS, direct connection")
    class AsyncDirectHttp1Tls extends HttpAsyncClientHttp1CompatibilityTest {

        public AsyncDirectHttp1Tls() throws Exception {
            super(targetContainerTlsHost(), null, null);
        }

    }

    @Nested
    @DisplayName("Async client: HTTP/1.1, TLS, connection via proxy (tunnel)")
    class AsyncViaProxyHttp1Tls extends HttpAsyncClientHttp1CompatibilityTest {

        public AsyncViaProxyHttp1Tls() throws Exception {
            super(targetInternalTlsHost(), proxyContainerHost(), null);
        }

    }

    @Nested
    @DisplayName("Async client: HTTP/1.1, TLS, connection via password protected proxy (tunnel)")
    class AsyncViaPwProtectedProxyHttp1Tls extends HttpAsyncClientHttp1CompatibilityTest {

        public AsyncViaPwProtectedProxyHttp1Tls() throws Exception {
            super(targetInternalTlsHost(), proxyPwProtectedContainerHost(), new UsernamePasswordCredentials("squid", "nopassword".toCharArray()));
        }

    }

    @Nested
    @DisplayName("Async client: HTTP/2, plain, direct connection")
    class AsyncDirectHttp2 extends HttpAsyncClientCompatibilityTest {

        public AsyncDirectHttp2() throws Exception {
            super(HttpVersionPolicy.FORCE_HTTP_2, targetContainerHost(), null, null);
        }

    }

    @Nested
    @DisplayName("Async client: HTTP/2, TLS, direct connection")
    class AsyncDirectHttp2Tls extends HttpAsyncClientCompatibilityTest {

        public AsyncDirectHttp2Tls() throws Exception {
            super(HttpVersionPolicy.FORCE_HTTP_2, targetContainerTlsHost(), null, null);
        }

    }

    @Nested
    @DisplayName("Async client: HTTP/2, TLS, connection via proxy (tunnel)")
    class AsyncViaProxyHttp2Tls extends HttpAsyncClientCompatibilityTest {

        public AsyncViaProxyHttp2Tls() throws Exception {
            super(HttpVersionPolicy.FORCE_HTTP_2, targetInternalTlsHost(), proxyContainerHost(), null);
        }

    }

    @Nested
    @DisplayName("Async client: HTTP/2, TLS, connection via password protected proxy (tunnel)")
    class AsyncViaPwProtectedProxyHttp2Tls extends HttpAsyncClientCompatibilityTest {

        public AsyncViaPwProtectedProxyHttp2Tls() throws Exception {
            super(HttpVersionPolicy.FORCE_HTTP_2, targetInternalTlsHost(), proxyPwProtectedContainerHost(), new UsernamePasswordCredentials("squid", "nopassword".toCharArray()));
        }

    }

    @Nested
    @DisplayName("Async client: protocol negotiate, TLS, connection via proxy (tunnel)")
    class AsyncViaProxyHttpNegotiateTls extends HttpAsyncClientCompatibilityTest {

        public AsyncViaProxyHttpNegotiateTls() throws Exception {
            super(HttpVersionPolicy.NEGOTIATE, targetInternalTlsHost(), proxyContainerHost(), null);
        }

    }

    @Nested
    @DisplayName("Async client: protocol negotiate, TLS, connection via password protected proxy (tunnel)")
    class AsyncViaPwProtectedProxyHttpNegotiateTls extends HttpAsyncClientCompatibilityTest {

        public AsyncViaPwProtectedProxyHttpNegotiateTls() throws Exception {
            super(HttpVersionPolicy.NEGOTIATE, targetInternalTlsHost(), proxyPwProtectedContainerHost(), new UsernamePasswordCredentials("squid", "nopassword".toCharArray()));
        }

    }

    @Nested
    @DisplayName("Classic client: HTTP/1.1, caching, plain, direct connection")
    class ClassicCachingHttp extends CachingHttpClientCompatibilityTest {

        public ClassicCachingHttp() throws Exception {
            super(targetContainerHost());
        }

    }

    @Nested
    @DisplayName("Classic client: HTTP/1.1, caching, TLS, direct connection")
    class ClassicCachingHttpTls extends CachingHttpClientCompatibilityTest {

        public ClassicCachingHttpTls() throws Exception {
            super(targetContainerTlsHost());
        }

    }

    @Nested
    @DisplayName("Async client: HTTP/1.1, caching, plain, direct connection")
    class AsyncCachingHttp1 extends CachingHttpAsyncClientCompatibilityTest {

        public AsyncCachingHttp1() throws Exception {
            super(HttpVersionPolicy.FORCE_HTTP_1, targetContainerHost());
        }

    }

    @Nested
    @DisplayName("Async client: HTTP/1.1, caching, TLS, direct connection")
    class AsyncCachingHttp1Tls extends CachingHttpAsyncClientCompatibilityTest {

        public AsyncCachingHttp1Tls() throws Exception {
            super(HttpVersionPolicy.FORCE_HTTP_1, targetContainerTlsHost());
        }

    }

    @Nested
    @DisplayName("Async client HTTP/2, caching, plain, direct connection")
    class AsyncCachingHttp2 extends CachingHttpAsyncClientCompatibilityTest {

        public AsyncCachingHttp2() throws Exception {
            super(HttpVersionPolicy.FORCE_HTTP_2, targetContainerHost());
        }

    }

    @Nested
    @DisplayName("Async client: HTTP/2, caching, TLS, direct connection")
    class AsyncCachingHttp2Tls extends CachingHttpAsyncClientCompatibilityTest {

        public AsyncCachingHttp2Tls() throws Exception {
            super(HttpVersionPolicy.FORCE_HTTP_2, targetContainerTlsHost());
        }

    }

    @Nested
    @DisplayName("Classic client: HTTP/1.1, connection via password protected proxy")
    class HttpClientProxy extends HttpClientProxyCompatibilityTest {

        public HttpClientProxy() throws Exception {
            super(targetInternalTlsHost(), proxyPwProtectedContainerHost());
        }

    }

    @Nested
    @DisplayName("Async client: HTTP/1.1, connection via password protected proxy")
    class AsyncClientProxy extends HttpAsyncClientProxyCompatibilityTest {

        public AsyncClientProxy() throws Exception {
            super(targetInternalTlsHost(), proxyPwProtectedContainerHost());
        }

    }

}
