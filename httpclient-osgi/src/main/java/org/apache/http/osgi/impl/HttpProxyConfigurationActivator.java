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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.osgi.services.CachingHttpClientBuilderFactory;
import org.apache.http.osgi.services.HttpClientBuilderFactory;
import org.apache.http.osgi.services.ProxyConfiguration;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;

/**
 * @since 4.3
 */
public final class HttpProxyConfigurationActivator implements BundleActivator, ManagedServiceFactory {

    private static final String PROXY_SERVICE_FACTORY_NAME = "Apache HTTP Client Proxy Configuration Factory";

    private static final String PROXY_SERVICE_PID = "org.apache.http.proxyconfigurator";

    private static final String BUILDER_FACTORY_SERVICE_NAME = "Apache HTTP Client Client Factory";

    private static final String BUILDER_FACTORY_SERVICE_PID = "org.apache.http.httpclientfactory";

    private static final String CACHEABLE_BUILDER_FACTORY_SERVICE_NAME = "Apache HTTP Client Caching Client Factory";

    private static final String CACHEABLE_BUILDER_FACTORY_SERVICE_PID = "org.apache.http.cachinghttpclientfactory";

    private ServiceRegistration configurator;

    private ServiceRegistration clientFactory;

    private ServiceRegistration cachingClientFactory;

    private BundleContext context;

    private final Map<String, ServiceRegistration> registeredConfigurations = new LinkedHashMap<String, ServiceRegistration>();

    private final List<ProxyConfiguration> proxyConfigurations = new CopyOnWriteArrayList<ProxyConfiguration>();

    private final HttpClientTracker httpClientTracker = new HttpClientTracker();

    /**
     * {@inheritDoc}
     */
    @Override
    public void start(final BundleContext context) throws Exception {
        this.context = context;

        // ensure we receive configurations for the proxy selector
        final Hashtable<String, Object> props = new Hashtable<String, Object>();
        props.put(Constants.SERVICE_PID, getName());
        props.put(Constants.SERVICE_VENDOR, context.getBundle().getHeaders().get(Constants.BUNDLE_VENDOR));
        props.put(Constants.SERVICE_DESCRIPTION, PROXY_SERVICE_FACTORY_NAME);

        configurator = context.registerService(ManagedServiceFactory.class.getName(), this, props);

        final HttpClientBuilderConfigurator configurator = new HttpClientBuilderConfigurator(proxyConfigurations);

        props.clear();
        props.put(Constants.SERVICE_PID, BUILDER_FACTORY_SERVICE_PID);
        props.put(Constants.SERVICE_VENDOR, context.getBundle().getHeaders().get(Constants.BUNDLE_VENDOR));
        props.put(Constants.SERVICE_DESCRIPTION, BUILDER_FACTORY_SERVICE_NAME);
        clientFactory = context.registerService(HttpClientBuilderFactory.class.getName(),
                                                new OSGiHttpClientBuilderFactory(configurator, httpClientTracker),
                                                props);

        props.clear();
        props.put(Constants.SERVICE_PID, CACHEABLE_BUILDER_FACTORY_SERVICE_PID);
        props.put(Constants.SERVICE_VENDOR, context.getBundle().getHeaders().get(Constants.BUNDLE_VENDOR));
        props.put(Constants.SERVICE_DESCRIPTION, CACHEABLE_BUILDER_FACTORY_SERVICE_NAME);
        cachingClientFactory = context.registerService(CachingHttpClientBuilderFactory.class.getName(),
                                                       new OSGiCachingHttpClientBuilderFactory(configurator, httpClientTracker),
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
        // remove all tracked services
        registeredConfigurations.clear();

        // unregister service factory
        if (configurator != null) {
            configurator.unregister();
        }

        if (clientFactory != null) {
            clientFactory.unregister();
        }

        if (cachingClientFactory != null) {
            cachingClientFactory.unregister();
        }

        // ensure all http clients are closed
        httpClientTracker.closeAll();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return PROXY_SERVICE_PID;
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
            proxyConfigurations.add(proxyConfiguration);
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
        final ServiceRegistration registration = registeredConfigurations.remove(pid);
        if (registration != null) {
            final ServiceReference ref = registration.getReference();
            final ProxyConfiguration config = (ProxyConfiguration) context.getService(ref);
            proxyConfigurations.remove(config);
            context.ungetService(ref);
            registration.unregister();
        }
    }

    private static void closeQuietly(final Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (final IOException e) {
                // do nothing
            }
        }
    }

    static class HttpClientTracker {

        private final List<CloseableHttpClient> trackedHttpClients = new WeakList<CloseableHttpClient>();

        synchronized void track(final CloseableHttpClient client) {
            trackedHttpClients.add(client);
        }

        synchronized void closeAll() {
            for (final CloseableHttpClient client : trackedHttpClients) {
                closeQuietly(client);
            }
            trackedHttpClients.clear();
        }
    }
}
