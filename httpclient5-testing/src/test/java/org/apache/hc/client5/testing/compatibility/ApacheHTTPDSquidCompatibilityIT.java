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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import javax.security.auth.Subject;

import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.testing.compatibility.async.CachingHttpAsyncClientCompatibilityTest;
import org.apache.hc.client5.testing.compatibility.async.HttpAsyncClientCompatibilityTest;
import org.apache.hc.client5.testing.compatibility.async.HttpAsyncClientHttp1CompatibilityTest;
import org.apache.hc.client5.testing.compatibility.async.HttpAsyncClientProxyCompatibilityTest;
import org.apache.hc.client5.testing.compatibility.spnego.SpnegoTestUtil;
import org.apache.hc.client5.testing.compatibility.sync.CachingHttpClientCompatibilityTest;
import org.apache.hc.client5.testing.compatibility.sync.HttpClientCompatibilityTest;
import org.apache.hc.client5.testing.compatibility.sync.HttpClientCompatibilityTest2;
import org.apache.hc.client5.testing.compatibility.sync.HttpClientProxyCompatibilityTest;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class ApacheHTTPDSquidCompatibilityIT {

    private static Network NETWORK = Network.newNetwork();
    private static final Path KEYTAB_DIR = SpnegoTestUtil.createKeytabDir();

    @Container
    static final GenericContainer<?> KDC = ContainerImages.KDC(NETWORK, KEYTAB_DIR);
    @Container
    static final GenericContainer<?> HTTPD_CONTAINER = ContainerImages.apacheHttpD(NETWORK, KEYTAB_DIR, KDC);
    @Container
    static final GenericContainer<?> SQUID = ContainerImages.squid(NETWORK, KEYTAB_DIR, KDC);

    private static Path KRB5_CONF_PATH;
    private static Subject spnegoSubject;

    @BeforeAll
    static void init() throws IOException {
        KRB5_CONF_PATH = SpnegoTestUtil.prepareKrb5Conf(KDC.getHost() + ":" + KDC.getMappedPort(ContainerImages.KDC_PORT));
        spnegoSubject = SpnegoTestUtil.loginFromKeytab("testclient", KEYTAB_DIR.resolve("testclient.keytab"));
    }

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

    static HttpHost proxyAuthenticatedContainerHost() {
        return new HttpHost(URIScheme.HTTP.id, SQUID.getHost(), SQUID.getMappedPort(ContainerImages.PROXY_PW_PROTECTED_PORT));
    }

    @AfterAll
    static void cleanup() {
        try {
            // Let tail -F for squid logs catch up
            Thread.sleep(5 * 1000);
        } catch (final InterruptedException e) {
            // Do nothring
        }
        SQUID.close();
        HTTPD_CONTAINER.close();
        KDC.close();
        NETWORK.close();
        try {
            Files.delete(KRB5_CONF_PATH);
            Files.delete(KRB5_CONF_PATH.getParent());
            try ( Stream<Path> dirStream = Files.walk(KEYTAB_DIR)) {
                dirStream
                    .filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .forEach(File::delete);
            }
        } catch (final IOException e) {
            //We leave some files around in tmp
        }
    }

    @Nested
    @DisplayName("Classic client: HTTP/1.1, plain, password, direct connection")
    class ClassicDirectHttpPw extends HttpClientCompatibilityTest {

        public ClassicDirectHttpPw() throws Exception {
            super(targetContainerHost(),
                new UsernamePasswordCredentials("testuser", "nopassword".toCharArray()),
                null,
                null);
        }

    }

    @Nested
    @DisplayName("Classic client: HTTP/1.1, plain, SPNEGO, direct connection")
    class ClassicDirectHttpSpnego extends HttpClientCompatibilityTest {

        public ClassicDirectHttpSpnego() throws Exception {
            super(targetContainerHost(),
                SpnegoTestUtil.createCredentials(spnegoSubject),
                null,
                null);
        }

    }

    @Nested
    @DisplayName("Classic client: HTTP/1.1, plain, SPNEGO, direct connection, doAs")
    class ClassicDirectHttpSpnegDoAs extends HttpClientCompatibilityTest2 {

        public ClassicDirectHttpSpnegDoAs() throws Exception {
            super(targetContainerHost(),
                null,
                null,
                null,
                spnegoSubject);
        }

    }
    
    @Nested
    @DisplayName("Classic client: HTTP/1.1, plain, password, connection via proxy")
    class ClassicViaProxyHttp extends HttpClientCompatibilityTest {

        public ClassicViaProxyHttp() throws Exception {
            super(targetInternalHost(),
                new UsernamePasswordCredentials("testuser", "nopassword".toCharArray()),
                proxyContainerHost(),
                null);
        }

    }

    @Nested
    @DisplayName("Classic client: HTTP/1.1, TLS, password, direct connection")
    class ClassicDirectHttpTlsPw extends HttpClientCompatibilityTest {

        public ClassicDirectHttpTlsPw() throws Exception {
            super(targetContainerTlsHost(),
                new UsernamePasswordCredentials("testuser", "nopassword".toCharArray()),
                null,
                null);
        }

    }

    @Nested
    @DisplayName("Classic client: HTTP/1.1, TLS, SPNEGO, direct connection")
    class ClassicDirectHttpTlsSpnego extends HttpClientCompatibilityTest {

        public ClassicDirectHttpTlsSpnego() throws Exception {
            super(targetContainerTlsHost(),
                SpnegoTestUtil.createCredentials(spnegoSubject),
                null,
                null);
        }

    }

    @Nested
    @DisplayName("Classic client: HTTP/1.1, TLS, password, connection via proxy (tunnel)")
    class ClassicViaProxyHttpTls extends HttpClientCompatibilityTest {

        public ClassicViaProxyHttpTls() throws Exception {
            super(targetInternalTlsHost(),
                new UsernamePasswordCredentials("testuser", "nopassword".toCharArray()),
                proxyContainerHost(),
                null);
        }

    }

    @Nested
    @DisplayName("Classic client: HTTP/1.1, TLS, password, connection via password protected proxy (tunnel)")
    class ClassicViaPwProtectedProxyHttpTls extends HttpClientCompatibilityTest {

        public ClassicViaPwProtectedProxyHttpTls() throws Exception {
            super(targetInternalTlsHost(),
                new UsernamePasswordCredentials("testuser", "nopassword".toCharArray()),
                proxyAuthenticatedContainerHost(),
                new UsernamePasswordCredentials("squid", "nopassword".toCharArray()));
        }

    }

    @Nested
    @DisplayName("Classic client: HTTP/1.1, TLS, password, connection via SPNEGO protected proxy (tunnel)")
    class ClassicViaSpnegoProtectedProxyHttpTls extends HttpClientCompatibilityTest {

        public ClassicViaSpnegoProtectedProxyHttpTls() throws Exception {
            super(targetInternalTlsHost(),
                new UsernamePasswordCredentials("testuser", "nopassword".toCharArray()),
                proxyAuthenticatedContainerHost(),
                SpnegoTestUtil.createCredentials(spnegoSubject));
        }

    }

    @Nested
    @DisplayName("Async client: HTTP/1.1, plain, password, direct connection")
    class AsyncDirectHttp1Pw extends HttpAsyncClientHttp1CompatibilityTest {

        public AsyncDirectHttp1Pw() throws Exception {
            super(targetContainerHost(),
                new UsernamePasswordCredentials("testuser", "nopassword".toCharArray()),
                null,
                null);
        }

    }

    @Nested
    @DisplayName("Async client: HTTP/1.1, plain, SPNEGO, direct connection")
    class AsyncDirectHttp1Spnego extends HttpAsyncClientHttp1CompatibilityTest {

        public AsyncDirectHttp1Spnego() throws Exception {
            super(targetContainerHost(),
                SpnegoTestUtil.createCredentials(spnegoSubject),
                null,
                null);
        }

    }

    @Nested
    @DisplayName("Async client: HTTP/1.1, plain, SPNEGO, direct connection DOAS")
    class AsyncDirectHttp1SpnegoDoAs extends HttpAsyncClientHttp1CompatibilityTest {

        public AsyncDirectHttp1SpnegoDoAs() throws Exception {
            super(targetContainerHost(),
                SpnegoTestUtil.createCredentials(spnegoSubject),
                null,
                null,
                spnegoSubject);
        }

    }
    
    @Nested
    @DisplayName("Async client: HTTP/1.1, plain, password, connection via proxy")
    class AsyncViaProxyHttp1 extends HttpAsyncClientHttp1CompatibilityTest {

        public AsyncViaProxyHttp1() throws Exception {
            super(targetInternalHost(),
                new UsernamePasswordCredentials("testuser", "nopassword".toCharArray()),
                proxyContainerHost(),
                null);
        }

    }

    @Nested
    @DisplayName("Async client: HTTP/1.1, plain, password, connection via password protected proxy")
    class AsyncViaPwProtectedProxyHttp1Pw extends HttpAsyncClientHttp1CompatibilityTest {

        public AsyncViaPwProtectedProxyHttp1Pw() throws Exception {
            super(targetInternalHost(),
                new UsernamePasswordCredentials("testuser", "nopassword".toCharArray()),
                proxyAuthenticatedContainerHost(),
                new UsernamePasswordCredentials("squid", "nopassword".toCharArray()));
        }

    }

    @Nested
    @DisplayName("Async client: HTTP/1.1, plain, passwsord, connection via SPNEGO protected proxy")
    class AsyncViaPwProtectedProxyHttp1Spnego extends HttpAsyncClientHttp1CompatibilityTest {

        public AsyncViaPwProtectedProxyHttp1Spnego() throws Exception {
            super(targetInternalHost(),
                new UsernamePasswordCredentials("testuser", "nopassword".toCharArray()),
                proxyAuthenticatedContainerHost(),
                SpnegoTestUtil.createCredentials(spnegoSubject));
        }

    }

    @Nested
    @DisplayName("Async client: HTTP/1.1, TLS, password, direct connection")
    class AsyncDirectHttp1TlsPw extends HttpAsyncClientHttp1CompatibilityTest {

        public AsyncDirectHttp1TlsPw() throws Exception {
            super(targetContainerTlsHost(),
                new UsernamePasswordCredentials("testuser", "nopassword".toCharArray()),
                null,
                null);
        }

    }

    @Nested
    @DisplayName("Async client: HTTP/1.1, TLS, SPNEGO, direct connection")
    class AsyncDirectHttp1TlsSpnego extends HttpAsyncClientHttp1CompatibilityTest {

        public AsyncDirectHttp1TlsSpnego() throws Exception {
            super(targetContainerTlsHost(),
                SpnegoTestUtil.createCredentials(spnegoSubject),
                null,
                null);
        }

    }

    @Nested
    @DisplayName("Async client: HTTP/1.1, TLS, password, connection via proxy (tunnel)")
    class AsyncViaProxyHttp1Tls extends HttpAsyncClientHttp1CompatibilityTest {

        public AsyncViaProxyHttp1Tls() throws Exception {
            super(targetInternalTlsHost(),
                new UsernamePasswordCredentials("testuser", "nopassword".toCharArray()),
                proxyContainerHost(),
                null);
        }

    }

    @Nested
    @DisplayName("Async client: HTTP/1.1, TLS, password, connection via password protected proxy (tunnel)")
    class AsyncViaPwProtectedProxyHttp1TlsPw extends HttpAsyncClientHttp1CompatibilityTest {

        public AsyncViaPwProtectedProxyHttp1TlsPw() throws Exception {
            super(targetInternalTlsHost(),
                new UsernamePasswordCredentials("testuser", "nopassword".toCharArray()),
                proxyAuthenticatedContainerHost(),
                new UsernamePasswordCredentials("squid", "nopassword".toCharArray()));
        }

    }

    @Nested
    @DisplayName("Async client: HTTP/1.1, TLS, password, connection via SPNEGO protected proxy (tunnel)")
    class AsyncViaPwProtectedProxyHttp1TlsSpnego extends HttpAsyncClientHttp1CompatibilityTest {

        public AsyncViaPwProtectedProxyHttp1TlsSpnego() throws Exception {
            super(targetInternalTlsHost(),
                new UsernamePasswordCredentials("testuser", "nopassword".toCharArray()),
                proxyAuthenticatedContainerHost(),
                SpnegoTestUtil.createCredentials(spnegoSubject));
        }

    }

    @Nested
    @DisplayName("Async client: HTTP/2, plain, password, direct connection")
    class AsyncDirectHttp2Pw extends HttpAsyncClientCompatibilityTest {

        public AsyncDirectHttp2Pw() throws Exception {
            super(HttpVersionPolicy.FORCE_HTTP_2, targetContainerHost(),
                new UsernamePasswordCredentials("testuser", "nopassword".toCharArray()),
                null,
                null);
        }

    }

    @Nested
    @DisplayName("Async client: HTTP/2, plain, SPNEGO, direct connection")
    class AsyncDirectHttp2Spnego extends HttpAsyncClientCompatibilityTest {

        public AsyncDirectHttp2Spnego() throws Exception {
            super(HttpVersionPolicy.FORCE_HTTP_2,
                targetContainerHost(),
                SpnegoTestUtil.createCredentials(spnegoSubject),
                null,
                null);
        }

    }

    @Nested
    @DisplayName("Async client: HTTP/2, TLS, password, direct connection")
    class AsyncDirectHttp2TlsPw extends HttpAsyncClientCompatibilityTest {

        public AsyncDirectHttp2TlsPw() throws Exception {
            super(HttpVersionPolicy.FORCE_HTTP_2,
                targetContainerTlsHost(),
                new UsernamePasswordCredentials("testuser", "nopassword".toCharArray()),
                null,
                null);
        }

    }

    @Nested
    @DisplayName("Async client: HTTP/2, TLS, SPNEGO, direct connection")
    class AsyncDirectHttp2TlsSpnego extends HttpAsyncClientCompatibilityTest {

        public AsyncDirectHttp2TlsSpnego() throws Exception {
            super(HttpVersionPolicy.FORCE_HTTP_2,
                targetContainerTlsHost(),
                SpnegoTestUtil.createCredentials(spnegoSubject),
                null,
                null);
        }

    }
    @Nested
    @DisplayName("Async client: HTTP/2, TLS, password, connection via proxy (tunnel)")
    class AsyncViaProxyHttp2Tls extends HttpAsyncClientCompatibilityTest {

        public AsyncViaProxyHttp2Tls() throws Exception {
            super(HttpVersionPolicy.FORCE_HTTP_2,
                targetInternalTlsHost(),
                new UsernamePasswordCredentials("testuser", "nopassword".toCharArray()),
                proxyContainerHost(), null);
        }

    }

    @Nested
    @DisplayName("Async client: HTTP/2, TLS, password, connection via password protected proxy (tunnel)")
    class AsyncViaPwProtectedProxyHttp2Tls extends HttpAsyncClientCompatibilityTest {

        public AsyncViaPwProtectedProxyHttp2Tls() throws Exception {
            super(HttpVersionPolicy.FORCE_HTTP_2,
                targetInternalTlsHost(),
                new UsernamePasswordCredentials("testuser", "nopassword".toCharArray()),
                proxyAuthenticatedContainerHost(),
                new UsernamePasswordCredentials("squid", "nopassword".toCharArray()));
        }

    }

    @Nested
    @DisplayName("Async client: HTTP/2, TLS, password, connection via SPNEGO protected proxy (tunnel)")
    class AsyncViaPwProtectedProxyHttp2TlsSpnego extends HttpAsyncClientCompatibilityTest {

        public AsyncViaPwProtectedProxyHttp2TlsSpnego() throws Exception {
            super(HttpVersionPolicy.FORCE_HTTP_2,
                targetInternalTlsHost(),
                new UsernamePasswordCredentials("testuser", "nopassword".toCharArray()),
                proxyAuthenticatedContainerHost(),
                SpnegoTestUtil.createCredentials(spnegoSubject));
        }

    }

    @Nested
    @DisplayName("Async client: protocol negotiate, TLS, password, connection via proxy (tunnel)")
    class AsyncViaProxyHttpNegotiateTls extends HttpAsyncClientCompatibilityTest {

        public AsyncViaProxyHttpNegotiateTls() throws Exception {
            super(HttpVersionPolicy.NEGOTIATE,
                targetInternalTlsHost(),
                new UsernamePasswordCredentials("testuser", "nopassword".toCharArray()),
                proxyContainerHost(),
                null);
        }

    }

    @Nested
    @DisplayName("Async client: protocol negotiate, TLS, password, connection via password protected proxy (tunnel)")
    class AsyncViaPwProtectedProxyHttpNegotiateTls extends HttpAsyncClientCompatibilityTest {

        public AsyncViaPwProtectedProxyHttpNegotiateTls() throws Exception {
            super(HttpVersionPolicy.NEGOTIATE,
                targetInternalTlsHost(),
                new UsernamePasswordCredentials("testuser", "nopassword".toCharArray()),
                proxyAuthenticatedContainerHost(),
                new UsernamePasswordCredentials("squid", "nopassword".toCharArray()));
        }

    }

    @Nested
    @DisplayName("Async client: protocol negotiate, TLS, password, connection via SPNEGO protected proxy (tunnel)")
    class AsyncViaPwProtectedProxyHttpNegotiateTlsSpnego extends HttpAsyncClientCompatibilityTest {

        public AsyncViaPwProtectedProxyHttpNegotiateTlsSpnego() throws Exception {
            super(HttpVersionPolicy.NEGOTIATE,
                targetInternalTlsHost(),
                new UsernamePasswordCredentials("testuser", "nopassword".toCharArray()),
                proxyAuthenticatedContainerHost(),
                SpnegoTestUtil.createCredentials(spnegoSubject));
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
            super(targetInternalTlsHost(), proxyAuthenticatedContainerHost());
        }

    }

    @Nested
    @DisplayName("Async client: HTTP/1.1, connection via password protected proxy")
    class AsyncClientProxy extends HttpAsyncClientProxyCompatibilityTest {

        public AsyncClientProxy() throws Exception {
            super(targetInternalTlsHost(), proxyAuthenticatedContainerHost());
        }

    }

}
