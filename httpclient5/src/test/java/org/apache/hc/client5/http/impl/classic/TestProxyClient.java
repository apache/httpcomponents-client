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

package org.apache.hc.client5.http.impl.classic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.Socket;

import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.io.ManagedHttpClientConnection;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.http.io.HttpConnectionFactory;
import org.junit.jupiter.api.Test;

class TestProxyClient {

    @Test
    void testTunnelWithInvalidPort() throws IOException {
        // Mock dependencies
        final HttpConnectionFactory<ManagedHttpClientConnection> connFactory = mock(HttpConnectionFactory.class);
        final ManagedHttpClientConnection managedConnection = mock(ManagedHttpClientConnection.class);
        when(connFactory.createConnection(null)).thenReturn(managedConnection);

        final HttpRequestExecutor requestExecutor = mock(HttpRequestExecutor.class);
        final ClassicHttpResponse response = mock(ClassicHttpResponse.class);
        when(response.getCode()).thenReturn(200);
        try {
            when(requestExecutor.execute(any(), any(), any())).thenReturn(response);
        } catch (final IOException | HttpException e) {
            fail("Shouldn't fail");
        }

        final RequestConfig requestConfig = RequestConfig.DEFAULT;

        final ProxyClient client = new ProxyClient(connFactory, null, null, requestConfig);

        final HttpHost proxy = new HttpHost("proxy.example.com", 8080);
        final HttpHost target = new HttpHost("target.example.com", -1); // Invalid port
        final Credentials credentials = new UsernamePasswordCredentials("user", "pass".toCharArray());

        assertThrows(IllegalArgumentException.class, () -> client.tunnel(proxy, target, credentials));
    }

    @Test
    void testSuccessfulTunnel() throws IOException, HttpException {
        // Mock dependencies
        final HttpConnectionFactory<ManagedHttpClientConnection> connFactory = mock(HttpConnectionFactory.class);

        final ManagedHttpClientConnection managedConnection = mock(ManagedHttpClientConnection.class);
        when(managedConnection.isOpen()).thenReturn(true); // Always return true for isOpen()
        when(connFactory.createConnection(null)).thenReturn(managedConnection);

        final ClassicHttpResponse mockResponse = mock(ClassicHttpResponse.class);
        when(mockResponse.getCode()).thenReturn(200); // Successful response
        when(managedConnection.receiveResponseHeader()).thenReturn(mockResponse);

        final HttpRequestExecutor mockRequestExecutor = mock(HttpRequestExecutor.class);
        when(mockRequestExecutor.execute(any(), any(), any())).thenReturn(mockResponse);

        final Socket mockSocket = mock(Socket.class);
        when(managedConnection.getSocket()).thenReturn(mockSocket);

        final RequestConfig requestConfig = RequestConfig.DEFAULT;

        final ProxyClient client = new ProxyClient(connFactory, null, null, requestConfig);

        final HttpHost proxy = new HttpHost("proxy.example.com", 8080);
        final HttpHost target = new HttpHost("target.example.com", 80); // Valid port
        final Credentials credentials = new UsernamePasswordCredentials("user", "pass".toCharArray());

        final Socket resultSocket = client.tunnel(proxy, target, credentials);
        assertNotNull(resultSocket, "Expected a valid socket object");
        assertEquals(mockSocket, resultSocket, "Expected the mock socket to be returned");
    }

}