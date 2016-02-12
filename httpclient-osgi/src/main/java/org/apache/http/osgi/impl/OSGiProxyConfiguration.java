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

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.apache.http.osgi.impl.PropertiesUtils.to;

import java.util.Dictionary;

import org.apache.http.osgi.services.ProxyConfiguration;

/**
 * @since 4.3
 */
public final class OSGiProxyConfiguration implements ProxyConfiguration {

    /**
     * Property indicating whether this particular proxy is enabled (shall be used or not). Defaults to true.
     */
    private static final String PROPERTYNAME_PROXY_ENABLED = "proxy.enabled";

    private static final Boolean PROPERTYDEFAULT_PROXY_ENABLED = Boolean.TRUE;

    /**
     * Property representing the hostname of the proxy. Defaults to empty.
     */
    private static final String PROPERTYNAME_PROXY_HOSTNAME = "proxy.host";

    private static final String PROPERTYDEFAULT_PROXY_HOSTNAME = "";

    /**
     * Property representing the port of the proxy. Defaults to 0.
     */
    private static final String PROPERTYNAME_PROXY_PORT = "proxy.port";

    private static final Integer PROPERTYDEFAULT_PROXY_PORT = Integer.valueOf(0);

    /**
     * Property representing the username to authenticate with towards the proxy. Defaults to empty.
     */
    private static final String PROPERTYNAME_PROXY_USERNAME = "proxy.user";

    private static final String PROPERTYDEFAULT_PROXY_USERNAME = "";

    /**
     * Property representing the password to authenticate with towards the proxy. Defaults to empty.
     */
    private static final String PROPERTYNAME_PROXY_PASSWORD = "proxy.password";

    private static final String PROPERTYDEFAULT_PROXY_PASSWORD = "";

    /**
     * A multivalue property representing host patterns for which no proxy shall be used. By default localhost is
     * excluded.
     */
    private static final String PROPERTYNAME_PROXY_EXCEPTIONS = "proxy.exceptions";

    private static final String[] PROPERTYDEFAULT_PROXY_EXCEPTIONS = new String[]{"localhost", "127.0.0.1"};

    private Boolean enabled = Boolean.FALSE; // fewer boxing conversions needed when stored as an object

    private String hostname;

    private Integer port = Integer.valueOf(0); // fewer boxing conversions needed when stored as an object

    private String username;

    private String password;

    private String[] proxyExceptions;

    @Override
    public boolean isEnabled() {
        return enabled.booleanValue();
    }

    @Override
    public String getHostname() {
        return hostname;
    }

    @Override
    public int getPort() {
        return port.intValue();
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String[] getProxyExceptions() {
        return proxyExceptions;
    }

    public void update(final Dictionary<String, Object> config) {
        enabled = to(config.get(PROPERTYNAME_PROXY_ENABLED), boolean.class, PROPERTYDEFAULT_PROXY_ENABLED);
        hostname = to(config.get(PROPERTYNAME_PROXY_HOSTNAME), String.class, PROPERTYDEFAULT_PROXY_HOSTNAME);
        port = to(config.get(PROPERTYNAME_PROXY_PORT), int.class, PROPERTYDEFAULT_PROXY_PORT);
        username = to(config.get(PROPERTYNAME_PROXY_USERNAME), String.class, PROPERTYDEFAULT_PROXY_USERNAME);
        password = to(config.get(PROPERTYNAME_PROXY_PASSWORD), String.class, PROPERTYDEFAULT_PROXY_PASSWORD);
        proxyExceptions = to(config.get(PROPERTYNAME_PROXY_EXCEPTIONS), String[].class, PROPERTYDEFAULT_PROXY_EXCEPTIONS);
    }

    @Override
    public String toString() {
        return format("ProxyConfiguration [enabled=%s, hostname=%s, port=%s, username=%s, password=%s, proxyExceptions=%s]",
                      enabled, hostname, port, username, password, asList(proxyExceptions));
    }

}
