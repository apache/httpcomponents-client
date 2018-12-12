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
package org.apache.http.impl.client;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.http.HttpHost;
import org.apache.http.annotation.Contract;
import org.apache.http.annotation.ThreadingBehavior;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.util.Args;

/**
 * Implementation of {@link CredentialsProvider} backed by standard
 * JRE {@link Authenticator}.
 *
 * @since 4.3
 */
@Contract(threading = ThreadingBehavior.SAFE)
public class SystemDefaultCredentialsProvider implements CredentialsProvider {

    private static final Map<String, String> SCHEME_MAP;

    static {
        SCHEME_MAP = new ConcurrentHashMap<String, String>();
        SCHEME_MAP.put(AuthSchemes.BASIC.toUpperCase(Locale.ROOT), "Basic");
        SCHEME_MAP.put(AuthSchemes.DIGEST.toUpperCase(Locale.ROOT), "Digest");
        SCHEME_MAP.put(AuthSchemes.NTLM.toUpperCase(Locale.ROOT), "NTLM");
        SCHEME_MAP.put(AuthSchemes.SPNEGO.toUpperCase(Locale.ROOT), "SPNEGO");
        SCHEME_MAP.put(AuthSchemes.KERBEROS.toUpperCase(Locale.ROOT), "Kerberos");
    }

    private static String translateScheme(final String key) {
        if (key == null) {
            return null;
        }
        final String s = SCHEME_MAP.get(key);
        return s != null ? s : key;
    }

    private final BasicCredentialsProvider internal;

    /**
     * Default constructor.
     */
    public SystemDefaultCredentialsProvider() {
        super();
        this.internal = new BasicCredentialsProvider();
    }

    @Override
    public void setCredentials(final AuthScope authscope, final Credentials credentials) {
        internal.setCredentials(authscope, credentials);
    }

    private static PasswordAuthentication getSystemCreds(
            final String protocol,
            final AuthScope authscope,
            final Authenticator.RequestorType requestorType) {
        return Authenticator.requestPasswordAuthentication(
                authscope.getHost(),
                null,
                authscope.getPort(),
                protocol,
                null,
                translateScheme(authscope.getScheme()),
                null,
                requestorType);
    }

    @Override
    public Credentials getCredentials(final AuthScope authscope) {
        Args.notNull(authscope, "Auth scope");
        final Credentials localcreds = internal.getCredentials(authscope);
        if (localcreds != null) {
            return localcreds;
        }
        final String host = authscope.getHost();
        if (host != null) {
            final HttpHost origin = authscope.getOrigin();
            final String protocol = origin != null ? origin.getSchemeName() : (authscope.getPort() == 443 ? "https" : "http");
            PasswordAuthentication systemcreds = getSystemCreds(protocol, authscope, Authenticator.RequestorType.SERVER);
            if (systemcreds == null) {
                systemcreds = getSystemCreds(protocol, authscope, Authenticator.RequestorType.PROXY);
            }
            if (systemcreds == null) {
                // Look for values given using http.proxyUser/http.proxyPassword or
                // https.proxyUser/https.proxyPassword. We cannot simply use the protocol from
                // the origin since a proxy retrieved from https.proxyHost/https.proxyPort will
                // still use http as protocol
                systemcreds = getProxyCredentials("http", authscope);
                if (systemcreds == null) {
                    systemcreds = getProxyCredentials("https", authscope);
                }
            }
            if (systemcreds != null) {
                final String domain = System.getProperty("http.auth.ntlm.domain");
                if (domain != null) {
                    return new NTCredentials(
                            systemcreds.getUserName(),
                            new String(systemcreds.getPassword()),
                            null, domain);
                }
                return AuthSchemes.NTLM.equalsIgnoreCase(authscope.getScheme())
                                // Domain may be specified in a fully qualified user name
                                ? new NTCredentials(systemcreds.getUserName(),
                                                new String(systemcreds.getPassword()), null, null)
                                : new UsernamePasswordCredentials(systemcreds.getUserName(),
                                                new String(systemcreds.getPassword()));
            }
        }
        return null;
    }

    private static PasswordAuthentication getProxyCredentials(final String protocol, final AuthScope authscope) {
        final String proxyHost = System.getProperty(protocol + ".proxyHost");
        if (proxyHost == null) {
            return null;
        }
        final String proxyPort = System.getProperty(protocol + ".proxyPort");
        if (proxyPort == null) {
            return null;
        }

        try {
            final AuthScope systemScope = new AuthScope(proxyHost, Integer.parseInt(proxyPort));
            if (authscope.match(systemScope) >= 0) {
                final String proxyUser = System.getProperty(protocol + ".proxyUser");
                if (proxyUser == null) {
                    return null;
                }
                final String proxyPassword = System.getProperty(protocol + ".proxyPassword");

                return new PasswordAuthentication(proxyUser,
                        proxyPassword != null ? proxyPassword.toCharArray() : new char[] {});
            }
        } catch (final NumberFormatException ex) {
        }

        return null;
    }

    @Override
    public void clear() {
        internal.clear();
    }

}
