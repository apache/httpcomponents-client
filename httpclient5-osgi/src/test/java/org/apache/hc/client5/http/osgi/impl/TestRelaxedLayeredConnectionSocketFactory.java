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
package org.apache.hc.client5.http.osgi.impl;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.hc.client5.http.socket.LayeredConnectionSocketFactory;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;

public class TestRelaxedLayeredConnectionSocketFactory {

    @Test
    public void testTrustedAllConnections() throws Exception {
        final HttpContext context = new BasicHttpContext();

        final Dictionary<String, Object> config = new Hashtable<>();
        config.put("trustedhosts.enabled", Boolean.TRUE);
        config.put("trustedhosts.trustAll", Boolean.TRUE);
        config.put("trustedhosts.hosts", new String[]{});
        final LayeredConnectionSocketFactory socketFactory = getLayeredConnectionSocketFactory(config);
        final Socket socket = socketFactory.createSocket(context);
        final Socket secureSocket = socketFactory.createLayeredSocket(socket, "localhost", 9999, context);
        assertSame(socket, secureSocket);
    }

    @Test
    public void testTrustedConnections() throws Exception {
        final HttpContext context = new BasicHttpContext();
        final Dictionary<String, Object> config = new Hashtable<>();
        config.put("trustedhosts.enabled", Boolean.TRUE);
        config.put("trustedhosts.trustAll", Boolean.FALSE);
        config.put("trustedhosts.hosts", new String[]{ "localhost" });
        final LayeredConnectionSocketFactory socketFactory = getLayeredConnectionSocketFactory(config);

        final Socket socket = socketFactory.createSocket(context);
        final Socket localSecureSocket = socketFactory.createLayeredSocket(socket, "localhost", 9999, context);
        assertSame(socket, localSecureSocket);

        final Socket apacheSecureSocket = socketFactory.createLayeredSocket(socket, "www.apache.org", 9999, context);
        assertNotSame(socket, apacheSecureSocket);
    }

    @Test
    public void testNotEabledConfiguration() throws Exception {
        final HttpContext context = new BasicHttpContext();

        final Dictionary<String, Object> config = new Hashtable<>();
        config.put("trustedhosts.enabled", Boolean.TRUE);
        config.put("trustedhosts.trustAll", Boolean.FALSE);
        config.put("trustedhosts.hosts", new String[]{});
        final LayeredConnectionSocketFactory socketFactory = getLayeredConnectionSocketFactory(config);
        final Socket socket = socketFactory.createSocket(context);
        final Socket secureSocket = socketFactory.createLayeredSocket(socket, "localhost", 9999, context);
        assertNotSame(socket, secureSocket);
    }

    private LayeredConnectionSocketFactory getLayeredConnectionSocketFactory(final Dictionary<String, ?> config) {
        final ServiceReference<ManagedService> reference = mock(ServiceReference.class);
        final ServiceRegistration<ManagedService> registration = mock(ServiceRegistration.class);
        when(registration.getReference()).thenReturn(reference);
        final BundleContext bundleContext = mock(BundleContext.class);
        final OSGiTrustedHostsConfiguration configuration = new OSGiTrustedHostsConfiguration();
        try {
            configuration.updated(config);
        } catch (ConfigurationException e) {
            // it doesn't happen in tests
        }
        when(bundleContext.getService(reference)).thenReturn(configuration);

        final Socket socket = mock(Socket.class);
        final Socket secureSocket = mock(Socket.class);
        final LayeredConnectionSocketFactory defaultSocketFactory = new LayeredConnectionSocketFactory() {

            @Override
            public Socket createSocket(final HttpContext context) throws IOException {
                return socket;
            }

            @Override
            public Socket connectSocket(final int connectTimeout,
                                        final Socket sock,
                                        final HttpHost host,
                                        final InetSocketAddress remoteAddress,
                                        final InetSocketAddress localAddress,
                                        final HttpContext context ) throws IOException {
                // not needed in this version
                return socket;
            }

            @Override
            public Socket createLayeredSocket(final Socket socket,
                                              final String target,
                                              final int port,
                                              final HttpContext context) throws IOException {
                return secureSocket;
            }

        };

        return new RelaxedLayeredConnectionSocketFactory(bundleContext, registration, defaultSocketFactory);
    }

}
