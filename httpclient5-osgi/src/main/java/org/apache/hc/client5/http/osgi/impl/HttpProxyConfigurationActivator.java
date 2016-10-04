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

import java.io.Closeable;
import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.hc.client5.http.impl.sync.CloseableHttpClient;
import org.apache.hc.client5.http.osgi.services.CachingHttpClientBuilderFactory;
import org.apache.hc.client5.http.osgi.services.HttpClientBuilderFactory;
import org.apache.hc.client5.http.osgi.services.ProxyConfiguration;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.cm.ManagedServiceFactory;

/**
 * @since 4.3
 */
public final class HttpProxyConfigurationActivator implements BundleActivator, ManagedServiceFactory {

    private static final String PROXY_SERVICE_FACTORY_NAME = "Apache HTTP Client Proxy Configuration Factory";

    private static final String PROXY_SERVICE_PID = "org.apache.hc.client5.http.proxyconfigurator";

    private static final String TRUSTED_HOSTS_SERVICE_NAME = "Apache HTTP Client Trusted Hosts Configuration";

    private static final String TRUSTED_HOSTS_PID = "org.apache.hc.client5.http.trustedhosts";

    private static final String BUILDER_FACTORY_SERVICE_NAME = "Apache HTTP Client Client Factory";

    private static final String BUILDER_FACTORY_SERVICE_PID = "org.apache.hc.client5.http.httpclientfactory";

    private static final String CACHEABLE_BUILDER_FACTORY_SERVICE_NAME = "Apache HTTP Client Caching Client Factory";

    private static final String CACHEABLE_BUILDER_FACTORY_SERVICE_PID = "org.apache.hc.client5.http.cachinghttpclientfactory";

    private ServiceRegistration<ManagedServiceFactory> configurator;

    private ServiceRegistration<ManagedService> trustedHostConfiguration;

    private ServiceRegistration<HttpClientBuilderFactory> clientFactory;

    private ServiceRegistration<CachingHttpClientBuilderFactory> cachingClientFactory;

    private BundleContext context;

    private final Map<String, ServiceRegistration<ProxyConfiguration>> registeredConfigurations = new LinkedHashMap<>();

    private final List<CloseableHttpClient> trackedHttpClients;

    public HttpProxyConfigurationActivator() {
        trackedHttpClients = new WeakList<CloseableHttpClient>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start(final BundleContext context) throws Exception {
        this.context = context;

        // ensure we receive configurations for the proxy selector
        final Hashtable<String, Object> props = new Hashtable<>();
        props.put(Constants.SERVICE_PID, getName());
        props.put(Constants.SERVICE_VENDOR, context.getBundle().getHeaders().get(Constants.BUNDLE_VENDOR));
        props.put(Constants.SERVICE_DESCRIPTION, PROXY_SERVICE_FACTORY_NAME);

        configurator = context.registerService(ManagedServiceFactory.class, this, props);

        props.clear();
        props.put(Constants.SERVICE_PID, TRUSTED_HOSTS_PID);
        props.put(Constants.SERVICE_VENDOR, context.getBundle().getHeaders().get(Constants.BUNDLE_VENDOR));
        props.put(Constants.SERVICE_DESCRIPTION, TRUSTED_HOSTS_SERVICE_NAME);
        trustedHostConfiguration = context.registerService(ManagedService.class,
                                                           new OSGiTrustedHostsConfiguration(),
                                                           props);

        final HttpClientBuilderConfigurator configurator =
                new HttpClientBuilderConfigurator(context, registeredConfigurations, trustedHostConfiguration);

        props.clear();
        props.put(Constants.SERVICE_PID, BUILDER_FACTORY_SERVICE_PID);
        props.put(Constants.SERVICE_VENDOR, context.getBundle().getHeaders().get(Constants.BUNDLE_VENDOR));
        props.put(Constants.SERVICE_DESCRIPTION, BUILDER_FACTORY_SERVICE_NAME);
        clientFactory = context.registerService(HttpClientBuilderFactory.class,
                                                new OSGiClientBuilderFactory(configurator, trackedHttpClients),
                                                props);

        props.clear();
        props.put(Constants.SERVICE_PID, CACHEABLE_BUILDER_FACTORY_SERVICE_PID);
        props.put(Constants.SERVICE_VENDOR, context.getBundle().getHeaders().get(Constants.BUNDLE_VENDOR));
        props.put(Constants.SERVICE_DESCRIPTION, CACHEABLE_BUILDER_FACTORY_SERVICE_NAME);
        cachingClientFactory = context.registerService(CachingHttpClientBuilderFactory.class,
                                                       new OSGiCachingClientBuilderFactory(configurator, trackedHttpClients),
                                                       props);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop(final BundleContext context) throws Exception {
        // unregister services
        for (final ServiceRegistration<ProxyConfiguration> registeredConfiguration : registeredConfigurations.values()) {
            safeUnregister(registeredConfiguration);
        }

        safeUnregister(configurator);
        safeUnregister(clientFactory);
        safeUnregister(cachingClientFactory);
        safeUnregister(trustedHostConfiguration);

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
        return PROXY_SERVICE_PID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updated(final String pid, final Dictionary<String, ?> config) throws ConfigurationException {
        final ServiceRegistration<ProxyConfiguration> registration = registeredConfigurations.get(pid);
        final OSGiProxyConfiguration proxyConfiguration;

        if (registration == null) {
            proxyConfiguration = new OSGiProxyConfiguration();
            final ServiceRegistration<ProxyConfiguration> configurationRegistration =
                            context.registerService(ProxyConfiguration.class,
                                                    proxyConfiguration,
                                                    config);
            registeredConfigurations.put(pid, configurationRegistration);
        } else {
            proxyConfiguration = (OSGiProxyConfiguration) context.getService(registration.getReference());
        }

        proxyConfiguration.update(config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleted(final String pid) {
        final ServiceRegistration<ProxyConfiguration> registeredConfiguration = registeredConfigurations.get(pid);
        if (safeUnregister(registeredConfiguration)) {
            registeredConfigurations.remove(pid);
        }
    }

    private static <S> boolean safeUnregister(final ServiceRegistration<S> serviceRegistration) {
        if (serviceRegistration != null) {
            serviceRegistration.unregister();
            return true;
        }
        return false;
    }

    private static void closeQuietly(final Closeable closeable) {
        try {
            closeable.close();
        } catch (final IOException e) {
            // do nothing
        }
    }

}
