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

import java.util.Map;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.osgi.services.ProxyConfiguration;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

/**
 * @since 4.3
 */
final class OSGiCredentialsProvider implements CredentialsProvider {

    private final BundleContext bundleContext;

    private final Map<String, ServiceRegistration> registeredConfigurations;

    public OSGiCredentialsProvider(
            final BundleContext bundleContext,
            final Map<String, ServiceRegistration> registeredConfigurations) {
        this.bundleContext = bundleContext;
        this.registeredConfigurations = registeredConfigurations;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCredentials(final AuthScope authscope, final Credentials credentials) {
        // do nothing, not used in this version
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Credentials getCredentials(final AuthScope authscope) {
        // iterate over all active proxy configurations at the moment of getting the credential
        for (final ServiceRegistration registration : registeredConfigurations.values()) {
            final Object proxyConfigurationObject = bundleContext.getService(registration.getReference());
            if (proxyConfigurationObject != null) {
                final ProxyConfiguration proxyConfiguration = (ProxyConfiguration) proxyConfigurationObject;
                if (proxyConfiguration.isEnabled()) {
                    final AuthScope actual = new AuthScope(proxyConfiguration.getHostname(), proxyConfiguration.getPort());
                    if (authscope.match(actual) >= 12) {
                        return new UsernamePasswordCredentials(proxyConfiguration.getUsername(), proxyConfiguration.getPassword());
                    }
                }
            }
        }
        // credentials no longer available!
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        // do nothing, not used in this version
    }

}
