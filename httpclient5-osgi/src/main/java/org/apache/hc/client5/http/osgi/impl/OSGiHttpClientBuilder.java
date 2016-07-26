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

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.sync.CloseableHttpClient;
import org.apache.hc.client5.http.impl.sync.HttpClientBuilder;
import org.apache.hc.client5.http.osgi.services.ProxyConfiguration;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.LayeredConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ManagedService;

/**
 * @since 4.3
 */
final class OSGiHttpClientBuilder extends HttpClientBuilder {

    private final Collection<CloseableHttpClient> trackedHttpClients;

    public OSGiHttpClientBuilder(
            final BundleContext bundleContext,
            final Map<String, ServiceRegistration<ProxyConfiguration>> registeredConfigurations,
            final ServiceRegistration<ManagedService> trustedHostConfiguration,
            final List<CloseableHttpClient> trackedHttpClients) {
        this.trackedHttpClients = trackedHttpClients;
        setDefaultCredentialsProvider(
                new OSGiCredentialsProvider(bundleContext, registeredConfigurations));
        setRoutePlanner(
                new OSGiHttpRoutePlanner(bundleContext, registeredConfigurations));
        final LayeredConnectionSocketFactory defaultSocketFactory = getSocketFactory();
        setConnectionManager(new PoolingHttpClientConnectionManager(RegistryBuilder.<ConnectionSocketFactory>create()
                                                                    .register("http", PlainConnectionSocketFactory.INSTANCE)
                                                                    .register("https", new RelaxedLayeredConnectionSocketFactory(bundleContext, trustedHostConfiguration, defaultSocketFactory))
                                                                    .build()));
    }

    @Override
    public CloseableHttpClient build() {
        final CloseableHttpClient httpClient = super.build();
        synchronized (trackedHttpClients) {
            trackedHttpClients.add(httpClient);
        }
        return httpClient;
    }

}
