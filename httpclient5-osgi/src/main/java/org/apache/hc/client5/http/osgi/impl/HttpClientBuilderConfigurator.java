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

import static org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory.getSocketFactory;

import java.util.List;

import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.sync.HttpClientBuilder;
import org.apache.hc.client5.http.osgi.services.ProxyConfiguration;
import org.apache.hc.client5.http.osgi.services.TrustedHostsConfiguration;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;

final class HttpClientBuilderConfigurator {

    private final OSGiCredentialsProvider credentialsProvider;

    private final OSGiHttpRoutePlanner routePlanner;

    private final Registry<ConnectionSocketFactory> socketFactoryRegistry;

    HttpClientBuilderConfigurator(
            final List<ProxyConfiguration> proxyConfigurations,
            final TrustedHostsConfiguration trustedHostsConfiguration) {
        credentialsProvider = new OSGiCredentialsProvider(proxyConfigurations);
        routePlanner = new OSGiHttpRoutePlanner(proxyConfigurations);
        socketFactoryRegistry = createSocketFactoryRegistry(trustedHostsConfiguration);
    }

    <T extends HttpClientBuilder> T configure(final T clientBuilder) {
        clientBuilder
                .setDefaultCredentialsProvider(credentialsProvider)
                .setRoutePlanner(routePlanner)
                .setConnectionManager(new PoolingHttpClientConnectionManager(socketFactoryRegistry));
        return clientBuilder;
    }

    private Registry<ConnectionSocketFactory> createSocketFactoryRegistry(
            final TrustedHostsConfiguration trustedHostsConfiguration) {
        return RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.INSTANCE)
                .register("https", createSocketFactory(trustedHostsConfiguration))
                .build();
    }

    private ConnectionSocketFactory createSocketFactory(
            final TrustedHostsConfiguration trustedHostsConfiguration) {
        return new RelaxedLayeredConnectionSocketFactory(trustedHostsConfiguration, getSocketFactory());
    }
}
