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
package org.apache.http.osgi.impl;

import java.io.Closeable;
import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.osgi.services.HttpClientBuilderFactory;
import org.apache.http.osgi.services.ProxyConfiguration;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;

/**
 * @since 4.3
 */
public final class HttpProxyConfigurationActivator implements BundleActivator, ManagedServiceFactory {

    private static final String SERVICE_FACTORY_NAME = "Apache HTTP Client Proxy Configuration Factory";

    private static final String SERVICE_PID = "org.apache.http.proxyconfigurator";

    private ServiceRegistration configurator;

    private ServiceRegistration clientFactory;

    private BundleContext context;

    private final Map<String, ServiceRegistration> registeredConfigurations = new LinkedHashMap<String, ServiceRegistration>();

    private final List<CloseableHttpClient> trackedHttpClients = new LinkedList<CloseableHttpClient>();

    /**
     * {@inheritDoc}
     */
    @Override
    public void start(final BundleContext context) throws Exception {
        this.context = context;

        // ensure we receive configurations for the proxy selector
        final Hashtable<String, Object> props = new Hashtable<String, Object>();
        props.put(Constants.SERVICE_PID, getName());
        props.put(Constants.SERVICE_VENDOR, context.getBundle().getHeaders(Constants.BUNDLE_VENDOR));
        props.put(Constants.SERVICE_DESCRIPTION, SERVICE_FACTORY_NAME);

        configurator = context.registerService(ManagedServiceFactory.class.getName(), this, props);
        clientFactory = context.registerService(HttpClientBuilderFactory.class.getName(),
                                                new OSGiClientBuilderFactory(context, registeredConfigurations, trackedHttpClients),
                                                props);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop(final BundleContext context) throws Exception {
        // unregister services
        for (final ServiceRegistration registeredConfiguration : registeredConfigurations.values()) {
            registeredConfiguration.unregister();
        }

        // unregister service factory
        if (configurator != null) {
            configurator.unregister();
        }

        if (clientFactory != null) {
            clientFactory.unregister();
        }

        // ensure all http clients - generated with the - are terminated
        for (final CloseableHttpClient client : trackedHttpClients) {
            if (null != client) {
                closeQuietly(client);
            }
        }

        // remove all tracked services
        registeredConfigurations.clear();
        // remove all tracked created clients
        trackedHttpClients.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return SERVICE_PID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updated(final String pid, @SuppressWarnings("rawtypes") final Dictionary config) throws ConfigurationException {
        final ServiceRegistration registration = registeredConfigurations.get(pid);
        OSGiProxyConfiguration proxyConfiguration;

        if (registration == null) {
            proxyConfiguration = new OSGiProxyConfiguration();
            final ServiceRegistration configurationRegistration = context.registerService(ProxyConfiguration.class.getName(),
                                                                                    proxyConfiguration,
                                                                                    config);
            registeredConfigurations.put(pid, configurationRegistration);
        } else {
            proxyConfiguration = (OSGiProxyConfiguration) context.getService(registration.getReference());
        }

        @SuppressWarnings("unchecked") // data type is known
        final
        Dictionary<String, Object> properties = config;
        proxyConfiguration.update(properties);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleted(final String pid) {
        final ServiceRegistration registeredConfiguration = registeredConfigurations.get(pid);
        if (null != registeredConfiguration) {
            registeredConfiguration.unregister();
            registeredConfigurations.remove(pid);
        }
    }

    private static void closeQuietly(final Closeable closeable) {
        try {
            closeable.close();
        } catch (final IOException e) {
            // do nothing
        }
    }

}
