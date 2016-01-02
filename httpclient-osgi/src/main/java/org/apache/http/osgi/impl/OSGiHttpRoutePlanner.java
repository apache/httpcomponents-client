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

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.impl.conn.DefaultRoutePlanner;
import org.apache.http.osgi.services.ProxyConfiguration;
import org.apache.http.protocol.HttpContext;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

/**
 * @since 4.3
 */
final class OSGiHttpRoutePlanner extends DefaultRoutePlanner {

    private static final String DOT = ".";

    /**
     * The IP mask pattern against which hosts are matched.
     */
    public static final Pattern IP_MASK_PATTERN = Pattern.compile("^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
                                                                  "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
                                                                  "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
                                                                  "([01]?\\d\\d?|2[0-4]\\d|25[0-5])$");

    private final BundleContext bundleContext;

    private final Map<String, ServiceRegistration> registeredConfigurations;

    public OSGiHttpRoutePlanner(
            final BundleContext bundleContext,
            final Map<String, ServiceRegistration> registeredConfigurations) {
        super(null);
        this.bundleContext = bundleContext;
        this.registeredConfigurations = registeredConfigurations;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected HttpHost determineProxy(final HttpHost target, final HttpRequest request, final HttpContext context) throws HttpException {
        ProxyConfiguration proxyConfiguration = null;
        HttpHost proxyHost = null;
        for (final ServiceRegistration registration : registeredConfigurations.values()) {
            final Object proxyConfigurationObject = bundleContext.getService(registration.getReference());
            if (proxyConfigurationObject != null) {
                proxyConfiguration = (ProxyConfiguration) proxyConfigurationObject;
                if (proxyConfiguration.isEnabled()) {
                    for (final String exception : proxyConfiguration.getProxyExceptions()) {
                        if (createMatcher(exception).matches(target.getHostName())) {
                            return null;
                        }
                    }
                    if (null == proxyHost) {
                        proxyHost = new HttpHost(proxyConfiguration.getHostname(), proxyConfiguration.getPort());
                    }
                }
            }
        }

        return proxyHost;
    }

    private static HostMatcher createMatcher(final String name) {
        final NetworkAddress na = NetworkAddress.parse(name);
        if (na != null) {
            return new IPAddressMatcher(na);
        }

        if (name.startsWith(DOT)) {
            return new DomainNameMatcher(name);
        }

        return new HostNameMatcher(name);
    }

    private static interface HostMatcher {

        boolean matches(String host);

    }

    private static class HostNameMatcher implements HostMatcher {

        private final String hostName;

        HostNameMatcher(final String hostName) {
            this.hostName = hostName;
        }

        @Override
        public boolean matches(final String host) {
            return hostName.equalsIgnoreCase(host);
        }
    }

    private static class DomainNameMatcher implements HostMatcher {

        private final String domainName;

        DomainNameMatcher(final String domainName) {
            this.domainName = domainName.toLowerCase(Locale.ROOT);
        }

        @Override
        public boolean matches(final String host) {
            return host.toLowerCase(Locale.ROOT).endsWith(domainName);
        }
    }

    private static class IPAddressMatcher implements HostMatcher {

        private final NetworkAddress address;

        IPAddressMatcher(final NetworkAddress address) {
            this.address = address;
        }

        @Override
        public boolean matches(final String host) {
            final NetworkAddress hostAddress = NetworkAddress.parse(host);
            return hostAddress != null && address.address == (hostAddress.address & address.mask);
        }

    }

    private static class NetworkAddress {

        final int address;

        final int mask;

        static NetworkAddress parse(final String adrSpec) {

            if (null != adrSpec) {
                final Matcher nameMatcher = IP_MASK_PATTERN.matcher(adrSpec);
                if (nameMatcher.matches()) {
                    try {
                        final int i1 = toInt(nameMatcher.group(1), 255);
                        final int i2 = toInt(nameMatcher.group(2), 255);
                        final int i3 = toInt(nameMatcher.group(3), 255);
                        final int i4 = toInt(nameMatcher.group(4), 255);
                        final int ip = i1 << 24 | i2 << 16 | i3 << 8 | i4;

                        int mask = toInt(nameMatcher.group(4), 32);
                        mask = (mask == 32) ? -1 : -1 - (-1 >>> mask);

                        return new NetworkAddress(ip, mask);
                    } catch (final NumberFormatException nfe) {
                        // not expected after the pattern match !
                    }
                }
            }

            return null;
        }

        private static int toInt(final String value, final int max) {
            if (value == null || value.isEmpty()) {
                return max;
            }

            int number = Integer.parseInt(value);
            if (number > max) {
                number = max;
            }
            return number;
        }

        NetworkAddress(final int address, final int mask) {
            this.address = address;
            this.mask = mask;
        }

    }

}
