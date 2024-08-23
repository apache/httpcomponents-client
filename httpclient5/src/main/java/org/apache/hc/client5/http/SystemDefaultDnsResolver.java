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
package org.apache.hc.client5.http;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * DNS resolver that uses the default OS implementation for resolving host names.
 *
 * @since 4.2
 */
public class SystemDefaultDnsResolver implements DnsResolver {

    /**
     * Default instance of {@link SystemDefaultDnsResolver}.
     */
    public static final SystemDefaultDnsResolver INSTANCE = new SystemDefaultDnsResolver();

    @Override
    public InetAddress[] resolve(final String host) throws UnknownHostException {
        try {
            // Try resolving using the default resolver
            return InetAddress.getAllByName(host);
        } catch (final UnknownHostException e) {
            // If default resolver fails, try stripping the IPv6 zone ID and resolving again
            String strippedHost = null;
            if (host.charAt(0) == '[') {
                final int i = host.lastIndexOf('%');
                if (i != -1) {
                    strippedHost = host.substring(0, i) + "]";
                }
            }
            if (strippedHost != null) {
                return InetAddress.getAllByName(strippedHost);
            }
            throw e;
        }
    }

    @Override
    public String resolveCanonicalHostname(final String host) throws UnknownHostException {
        if (host == null) {
            return null;
        }
        final InetAddress in = InetAddress.getByName(host);
        final String canonicalServer = in.getCanonicalHostName();
        if (in.getHostAddress().contentEquals(canonicalServer)) {
            return host;
        }
        return canonicalServer;
    }
}
