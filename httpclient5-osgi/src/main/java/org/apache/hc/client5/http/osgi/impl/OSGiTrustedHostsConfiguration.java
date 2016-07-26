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

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.apache.hc.client5.http.osgi.impl.PropertiesUtils.to;

import java.util.Dictionary;

import org.apache.hc.client5.http.osgi.services.TrustedHostsConfiguration;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;

/**
 * @since 5.0-alpha2
 */
final class OSGiTrustedHostsConfiguration implements ManagedService, TrustedHostsConfiguration {

    /**
     * Property indicating whether this particular configuration is enabled (shall be used or not). Defaults to true.
     */
    private static final String PROPERTYNAME_TRUSTEDHOSTS_ENABLED = "trustedhosts.enabled";

    private static final Boolean PROPERTYDEFAULT_TRUSTEDHOSTS_ENABLED = Boolean.TRUE;

    /**
     * Property indicating whether this particular configuration . Defaults to false.
     */
    private static final String PROPERTYNAME_TRUST_ALL = "trustedhosts.trustAll";

    private static final Boolean PROPERTYDEFAULT_TRUST_ALL = Boolean.FALSE;

    /**
     * A multivalue property representing host patterns which is an acceptable match with the server's authentication scheme.
     * By default <code>localhost</code> (<code>127.0.0.1</code>) is trusted.
     */
    private static final String PROPERTYNAME_TRUSTED_HOSTS = "trustedhosts.hosts";

    private static final String[] PROPERTYDEFAULT_TRUSTED_HOSTS = new String[]{ "localhost", "127.0.0.1" };

    private Boolean enabled = PROPERTYDEFAULT_TRUSTEDHOSTS_ENABLED; // fewer boxing conversions needed when stored as an object

    private Boolean trustAll = PROPERTYDEFAULT_TRUST_ALL; // fewer boxing conversions needed when stored as an object

    private String[] trustedHosts;

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean trustAll() {
        return trustAll;
    }

    @Override
    public String[] getTrustedHosts() {
        return trustedHosts;
    }

    @Override
    public void updated(final Dictionary<String, ?> config) throws ConfigurationException {
        if (config == null) {
            return;
        }

        enabled = to(config.get(PROPERTYNAME_TRUSTEDHOSTS_ENABLED), boolean.class, PROPERTYDEFAULT_TRUSTEDHOSTS_ENABLED);
        trustAll = to(config.get(PROPERTYNAME_TRUST_ALL), boolean.class, PROPERTYDEFAULT_TRUST_ALL);
        trustedHosts = to(config.get(PROPERTYNAME_TRUSTED_HOSTS), String[].class, PROPERTYDEFAULT_TRUSTED_HOSTS);
    }

    @Override
    public String toString() {
        return format("ProxyConfiguration [enabled=%s, trustAll=%s, trustedHosts=%s]",
                      enabled, trustAll, asList(trustedHosts));
    }

}
