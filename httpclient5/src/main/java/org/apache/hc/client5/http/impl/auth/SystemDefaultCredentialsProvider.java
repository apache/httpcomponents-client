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
package org.apache.hc.client5.http.impl.auth;

import java.net.Authenticator;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsStore;
import org.apache.hc.client5.http.auth.NTCredentials;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;

/**
 * Implementation of {@link CredentialsStore} backed by standard
 * JRE {@link Authenticator}.
 *
 * @since 4.3
 */
@Contract(threading = ThreadingBehavior.SAFE)
public class SystemDefaultCredentialsProvider implements CredentialsStore {

    private final BasicCredentialsProvider internal;

    /**
     * Default constructor.
     */
    public SystemDefaultCredentialsProvider() {
        super();
        this.internal = new BasicCredentialsProvider();
    }

    @Override
    public void setCredentials(final AuthScope authScope, final Credentials credentials) {
        internal.setCredentials(authScope, credentials);
    }

    private static PasswordAuthentication getSystemCreds(
            final String protocol,
            final AuthScope authScope,
            final Authenticator.RequestorType requestorType,
            final HttpClientContext context) {
        final HttpRequest request = context != null ? context.getRequest() : null;
        URL targetHostURL;
        try {
            final URI uri = request != null ? request.getUri() : null;
            targetHostURL = uri != null ? uri.toURL() : null;
        } catch (final URISyntaxException | MalformedURLException ignore) {
            targetHostURL = null;
        }
        // use null addr, because the authentication fails if it does not exactly match the expected realm's host
        return Authenticator.requestPasswordAuthentication(
                authScope.getHost(),
                null,
                authScope.getPort(),
                protocol,
                authScope.getRealm(),
                authScope.getSchemeName(),
                targetHostURL,
                requestorType);
    }

    @Override
    public Credentials getCredentials(final AuthScope authScope, final HttpContext context) {
        Args.notNull(authScope, "Auth scope");
        final Credentials localcreds = internal.getCredentials(authScope, context);
        if (localcreds != null) {
            return localcreds;
        }
        final String host = authScope.getHost();
        if (host != null) {
            final HttpClientContext clientContext = context != null ? HttpClientContext.adapt(context) : null;
            final String protocol = authScope.getProtocol() != null ? authScope.getProtocol() : (authScope.getPort() == 443 ? URIScheme.HTTPS.id : URIScheme.HTTP.id);
            PasswordAuthentication systemcreds = getSystemCreds(
                    protocol, authScope, Authenticator.RequestorType.SERVER, clientContext);
            if (systemcreds == null) {
                systemcreds = getSystemCreds(
                        protocol, authScope, Authenticator.RequestorType.PROXY, clientContext);
            }
            if (systemcreds == null) {
                // Look for values given using http.proxyUser/http.proxyPassword or
                // https.proxyUser/https.proxyPassword. We cannot simply use the protocol from
                // the origin since a proxy retrieved from https.proxyHost/https.proxyPort will
                // still use http as protocol
                systemcreds = getProxyCredentials("http", authScope);
                if (systemcreds == null) {
                    systemcreds = getProxyCredentials("https", authScope);
                }
            }
            if (systemcreds != null) {
                final String domain = System.getProperty("http.auth.ntlm.domain");
                if (domain != null) {
                    return new NTCredentials(systemcreds.getUserName(), systemcreds.getPassword(), null, domain);
                }
                if (StandardAuthScheme.NTLM.equalsIgnoreCase(authScope.getSchemeName())) {
                    // Domain may be specified in a fully qualified user name
                    return new NTCredentials(
                            systemcreds.getUserName(), systemcreds.getPassword(), null, null);
                }
                return new UsernamePasswordCredentials(systemcreds.getUserName(), systemcreds.getPassword());
            }
        }
        return null;
    }

    private static PasswordAuthentication getProxyCredentials(final String protocol, final AuthScope authScope) {
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
            if (authScope.match(systemScope) >= 0) {
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
