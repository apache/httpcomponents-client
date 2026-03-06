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
package org.apache.hc.client5.http.impl;

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.ssl.TLS;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Simple tests for {@link ProtocolSwitchStrategy}.
 */
class TestProtocolSwitchStrategy {

    ProtocolSwitchStrategy switchStrategy;

    @BeforeEach
    void setUp() {
        switchStrategy = new ProtocolSwitchStrategy();
    }

    @Test
    void testSwitchToTLS() throws Exception {
        final HttpResponse response1 = new BasicHttpResponse(HttpStatus.SC_SWITCHING_PROTOCOLS);
        response1.addHeader(HttpHeaders.CONNECTION, "Upgrade");
        response1.addHeader(HttpHeaders.UPGRADE, "TLS");
        Assertions.assertEquals(TLS.V_1_2.getVersion(), switchStrategy.switchProtocol(response1));

        final HttpResponse response2 = new BasicHttpResponse(HttpStatus.SC_SWITCHING_PROTOCOLS);
        response2.addHeader(HttpHeaders.CONNECTION, "Upgrade");
        response2.addHeader(HttpHeaders.UPGRADE, "TLS/1.3");
        Assertions.assertEquals(TLS.V_1_3.getVersion(), switchStrategy.switchProtocol(response2));
    }

    @Test
    void testSwitchToHTTP11AndTLS() throws Exception {
        final HttpResponse response1 = new BasicHttpResponse(HttpStatus.SC_SWITCHING_PROTOCOLS);
        response1.addHeader(HttpHeaders.CONNECTION, "Upgrade");
        response1.addHeader(HttpHeaders.UPGRADE, "TLS, HTTP/1.1");
        Assertions.assertEquals(TLS.V_1_2.getVersion(), switchStrategy.switchProtocol(response1));

        final HttpResponse response2 = new BasicHttpResponse(HttpStatus.SC_SWITCHING_PROTOCOLS);
        response2.addHeader(HttpHeaders.CONNECTION, "Upgrade");
        response2.addHeader(HttpHeaders.UPGRADE, ",, HTTP/1.1, TLS, ");
        Assertions.assertEquals(TLS.V_1_2.getVersion(), switchStrategy.switchProtocol(response2));

        final HttpResponse response3 = new BasicHttpResponse(HttpStatus.SC_SWITCHING_PROTOCOLS);
        response3.addHeader(HttpHeaders.CONNECTION, "Upgrade");
        response3.addHeader(HttpHeaders.UPGRADE, "HTTP/1.1");
        response3.addHeader(HttpHeaders.UPGRADE, "TLS/1.2");
        Assertions.assertEquals(TLS.V_1_2.getVersion(), switchStrategy.switchProtocol(response3));

        final HttpResponse response4 = new BasicHttpResponse(HttpStatus.SC_SWITCHING_PROTOCOLS);
        response4.addHeader(HttpHeaders.CONNECTION, "Upgrade");
        response4.addHeader(HttpHeaders.UPGRADE, "HTTP/1.1");
        response4.addHeader(HttpHeaders.UPGRADE, "TLS/1.2, TLS/1.3");
        Assertions.assertEquals(TLS.V_1_3.getVersion(), switchStrategy.switchProtocol(response4));
    }

    @Test
    void testSwitchInvalid() {
        final HttpResponse response1 = new BasicHttpResponse(HttpStatus.SC_SWITCHING_PROTOCOLS);
        response1.addHeader(HttpHeaders.CONNECTION, "Upgrade");
        response1.addHeader(HttpHeaders.UPGRADE, "Crap");
        Assertions.assertThrows(ProtocolException.class, () -> switchStrategy.switchProtocol(response1));

        final HttpResponse response2 = new BasicHttpResponse(HttpStatus.SC_SWITCHING_PROTOCOLS);
        response2.addHeader(HttpHeaders.CONNECTION, "Upgrade");
        response2.addHeader(HttpHeaders.UPGRADE, "TLS, huh?");
        Assertions.assertThrows(ProtocolException.class, () -> switchStrategy.switchProtocol(response2));

        final HttpResponse response3 = new BasicHttpResponse(HttpStatus.SC_SWITCHING_PROTOCOLS);
        response3.addHeader(HttpHeaders.CONNECTION, "Upgrade");
        response3.addHeader(HttpHeaders.UPGRADE, ",,,");
        Assertions.assertThrows(ProtocolException.class, () -> switchStrategy.switchProtocol(response3));
    }

    @Test
    void testNullToken() throws ProtocolException {
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_SWITCHING_PROTOCOLS);
        response.addHeader(HttpHeaders.CONNECTION, "Upgrade");
        response.addHeader(HttpHeaders.UPGRADE, "TLS,");
        response.addHeader(HttpHeaders.UPGRADE, null);
        Assertions.assertEquals(TLS.V_1_2.getVersion(), switchStrategy.switchProtocol(response));
    }

    @Test
    void testWhitespaceOnlyToken() throws ProtocolException {
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_SWITCHING_PROTOCOLS);
        response.addHeader(HttpHeaders.CONNECTION, "Upgrade");
        response.addHeader(HttpHeaders.UPGRADE, "   , TLS");
        Assertions.assertEquals(TLS.V_1_2.getVersion(), switchStrategy.switchProtocol(response));
    }

    @Test
    void testUnsupportedTlsVersion() throws Exception {
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_SWITCHING_PROTOCOLS);
        response.addHeader(HttpHeaders.CONNECTION, "Upgrade");
        response.addHeader(HttpHeaders.UPGRADE, "TLS/1.4");
        Assertions.assertEquals(new ProtocolVersion("TLS", 1, 4), switchStrategy.switchProtocol(response));
    }

    @Test
    void testUnsupportedTlsMajorVersion() throws Exception {
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_SWITCHING_PROTOCOLS);
        response.addHeader(HttpHeaders.CONNECTION, "Upgrade");
        response.addHeader(HttpHeaders.UPGRADE, "TLS/2.0");
        Assertions.assertEquals(new ProtocolVersion("TLS", 2, 0), switchStrategy.switchProtocol(response));
    }

    @Test
    void testUnsupportedHttpVersion() {
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_SWITCHING_PROTOCOLS);
        response.addHeader(HttpHeaders.CONNECTION, "Upgrade");
        response.addHeader(HttpHeaders.UPGRADE, "HTTP/2.0");
        final ProtocolException ex = Assertions.assertThrows(ProtocolException.class, () ->
                switchStrategy.switchProtocol(response));
        Assertions.assertEquals("Unsupported protocol or HTTP version: HTTP/2.0", ex.getMessage());
    }

    @Test
    void testInvalidTlsFormat() {
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_SWITCHING_PROTOCOLS);
        response.addHeader(HttpHeaders.CONNECTION, "Upgrade");
        response.addHeader(HttpHeaders.UPGRADE, "TLS/abc");
        final ProtocolException ex = Assertions.assertThrows(ProtocolException.class, () ->
                switchStrategy.switchProtocol(response));
        Assertions.assertEquals("Invalid TLS major version number; error at offset 7: <TLS/abc>", ex.getMessage());
    }

    @Test
    void testHttp11Only() {
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_SWITCHING_PROTOCOLS);
        response.addHeader(HttpHeaders.CONNECTION, "Upgrade");
        response.addHeader(HttpHeaders.UPGRADE, "HTTP/1.1");
        final ProtocolException ex = Assertions.assertThrows(ProtocolException.class, () ->
                switchStrategy.switchProtocol(response));
        Assertions.assertEquals("Invalid protocol switch response: no TLS version found", ex.getMessage());
    }

    @Test
    void testSwitchToTlsValid_TLS_1_2() throws Exception {
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_SWITCHING_PROTOCOLS);
        response.addHeader(HttpHeaders.CONNECTION, "Upgrade");
        response.addHeader(HttpHeaders.UPGRADE, "TLS/1.2");
        final ProtocolVersion result = switchStrategy.switchProtocol(response);
        Assertions.assertEquals(TLS.V_1_2.getVersion(), result);
    }

    @Test
    void testSwitchToTlsValid_TLS_1_0() throws Exception {
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_SWITCHING_PROTOCOLS);
        response.addHeader(HttpHeaders.CONNECTION, "Upgrade");
        response.addHeader(HttpHeaders.UPGRADE, "TLS/1.0");
        final ProtocolVersion result = switchStrategy.switchProtocol(response);
        Assertions.assertEquals(TLS.V_1_0.getVersion(), result);
    }

    @Test
    void testSwitchToTlsValid_TLS_1_1() throws Exception {
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_SWITCHING_PROTOCOLS);
        response.addHeader(HttpHeaders.CONNECTION, "Upgrade");
        response.addHeader(HttpHeaders.UPGRADE, "TLS/1.1");
        final ProtocolVersion result = switchStrategy.switchProtocol(response);
        Assertions.assertEquals(TLS.V_1_1.getVersion(), result);
    }

    @Test
    void testInvalidTlsFormat_NoSlash() {
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_SWITCHING_PROTOCOLS);
        response.addHeader(HttpHeaders.CONNECTION, "Upgrade");
        response.addHeader(HttpHeaders.UPGRADE, "TLSv1");
        final ProtocolException ex = Assertions.assertThrows(ProtocolException.class, () ->
                switchStrategy.switchProtocol(response));
        Assertions.assertEquals("Unsupported or invalid protocol: TLSv1", ex.getMessage());
    }

    @Test
    void testSwitchToTlsValid_TLS_1() throws Exception {
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_SWITCHING_PROTOCOLS);
        response.addHeader(HttpHeaders.CONNECTION, "Upgrade");
        response.addHeader(HttpHeaders.UPGRADE, "TLS/1");
        final ProtocolVersion result = switchStrategy.switchProtocol(response);
        Assertions.assertEquals(TLS.V_1_0.getVersion(), result);
    }

    @Test
    void testInvalidTlsFormat_MissingMajor() {
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_SWITCHING_PROTOCOLS);
        response.addHeader(HttpHeaders.CONNECTION, "Upgrade");
        response.addHeader(HttpHeaders.UPGRADE, "TLS/.1");
        final ProtocolException ex = Assertions.assertThrows(ProtocolException.class, () ->
                switchStrategy.switchProtocol(response));
        Assertions.assertEquals("Invalid TLS major version number; error at offset 4: <TLS/.1>", ex.getMessage());
    }

    @Test
    void testMultipleHttp11Tokens() {
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_SWITCHING_PROTOCOLS);
        response.addHeader(HttpHeaders.CONNECTION, "Upgrade");
        response.addHeader(HttpHeaders.UPGRADE, "HTTP/1.1, HTTP/1.1");
        final ProtocolException ex = Assertions.assertThrows(ProtocolException.class, () ->
                switchStrategy.switchProtocol(response));
        Assertions.assertEquals("Invalid protocol switch response: no TLS version found", ex.getMessage());
    }

    @Test
    void testMixedInvalidAndValidTokens() {
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_SWITCHING_PROTOCOLS);
        response.addHeader(HttpHeaders.CONNECTION, "Upgrade");
        response.addHeader(HttpHeaders.UPGRADE, "Crap, TLS/1.2, Invalid");
        final ProtocolException ex = Assertions.assertThrows(ProtocolException.class, () ->
                switchStrategy.switchProtocol(response));
        Assertions.assertEquals("Unsupported or invalid protocol: Crap", ex.getMessage());
    }

    @Test
    void testInvalidTlsFormat_NoProtocolName() {
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_SWITCHING_PROTOCOLS);
        response.addHeader(HttpHeaders.CONNECTION, "Upgrade");
        response.addHeader(HttpHeaders.UPGRADE, ",,/1.1");
        final ProtocolException ex = Assertions.assertThrows(ProtocolException.class, () ->
                switchStrategy.switchProtocol(response));
        Assertions.assertEquals("Invalid protocol; error at offset 2: <,,/1.1>", ex.getMessage());
    }

    @Test
    void testMissingConnectionUpgradeRejected() {
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_SWITCHING_PROTOCOLS);
        response.addHeader(HttpHeaders.UPGRADE, "TLS/1.2");

        final ProtocolException ex = Assertions.assertThrows(ProtocolException.class, () ->
                switchStrategy.switchProtocol(response));
        Assertions.assertEquals("Invalid protocol switch response: missing Connection: Upgrade", ex.getMessage());
    }

    @Test
    void testConnectionWithoutUpgradeTokenRejected() {
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_SWITCHING_PROTOCOLS);
        response.addHeader(HttpHeaders.CONNECTION, "keep-alive");
        response.addHeader(HttpHeaders.UPGRADE, "TLS/1.2");

        final ProtocolException ex = Assertions.assertThrows(ProtocolException.class, () ->
                switchStrategy.switchProtocol(response));
        Assertions.assertEquals("Invalid protocol switch response: missing Connection: Upgrade", ex.getMessage());
    }

    @Test
    void testConnectionWithUpgradeTokenInListAccepted() throws Exception {
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_SWITCHING_PROTOCOLS);
        response.addHeader(HttpHeaders.CONNECTION, "keep-alive, Upgrade");
        response.addHeader(HttpHeaders.UPGRADE, "TLS/1.2");

        Assertions.assertEquals(TLS.V_1_2.getVersion(), switchStrategy.switchProtocol(response));
    }

}