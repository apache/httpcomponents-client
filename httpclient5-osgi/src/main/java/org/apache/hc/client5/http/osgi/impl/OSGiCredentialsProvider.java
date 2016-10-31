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

import java.util.List;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsStore;
import org.apache.hc.client5.http.auth.NTCredentials;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.osgi.services.ProxyConfiguration;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @since 4.3
 */
final class OSGiCredentialsProvider implements CredentialsStore {

    private static final Logger log = LogManager.getLogger(OSGiCredentialsProvider.class);

    private static final int HOST_AND_PORT_MATCH = 12;

    private static final String BASIC_SCHEME_NAME = "BASIC";

    private static final String NTLM_SCHEME_NAME = "NTLM";

    private final List<ProxyConfiguration> proxyConfigurations;

    OSGiCredentialsProvider(final List<ProxyConfiguration> proxyConfigurations) {
        this.proxyConfigurations = proxyConfigurations;
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
    public Credentials getCredentials(final AuthScope authScope, final HttpContext context) {
        // iterate over all active proxy configurations at the moment of getting the credential
        for (final ProxyConfiguration config : proxyConfigurations) {
            if (config.isEnabled() && isSuitable(config, authScope)) {
                final String scheme = authScope.getScheme();
                if (BASIC_SCHEME_NAME.equals(scheme)) {
                    return new UsernamePasswordCredentials(config.getUsername(), config.getPassword().toCharArray());
                } else if (NTLM_SCHEME_NAME.equals(scheme)) {
                    return createNTCredentials(config);
                } else {
                    log.debug("credentials requested for unsupported authentication scheme " + scheme);
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

    // suitable configurations match at least the host and port of the AuthScope
    private boolean isSuitable(final ProxyConfiguration config, final AuthScope authScope) {
        return authScope.match(new AuthScope(config.getHostname(), config.getPort())) >= HOST_AND_PORT_MATCH;
    }

    private static Credentials createNTCredentials(final ProxyConfiguration config) {
        final String domainAndUsername = config.getUsername();
        final String username;
        final String domain;
        final int index = domainAndUsername.indexOf("\\");
        if (index > -1) {
            username = domainAndUsername.substring(index + 1);
            domain = domainAndUsername.substring(0, index);
        } else {
            username = domainAndUsername;
            domain = null;
        }
        return new NTCredentials(username, config.getPassword().toCharArray(), null, domain);
    }

}
