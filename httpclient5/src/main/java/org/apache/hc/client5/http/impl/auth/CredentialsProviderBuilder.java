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

import java.util.HashMap;
import java.util.Map;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.util.Args;

/**
 * {@link CredentialsProvider} builder.
 *
 * @since 5.2
 */
public final class CredentialsProviderBuilder {

    private final Map<AuthScope, Credentials> credMap;

    public static CredentialsProviderBuilder create() {
        return new CredentialsProviderBuilder();
    }

    public CredentialsProviderBuilder() {
        super();
        this.credMap = new HashMap<>();
    }

    public CredentialsProviderBuilder add(final AuthScope authScope, final Credentials credentials) {
        Args.notNull(authScope, "Host");
        credMap.put(authScope, credentials);
        return this;
    }

    public CredentialsProviderBuilder add(final AuthScope authScope, final String username, final char[] password) {
        Args.notNull(authScope, "Host");
        credMap.put(authScope, new UsernamePasswordCredentials(username, password));
        return this;
    }

    public CredentialsProviderBuilder add(final HttpHost httpHost, final Credentials credentials) {
        Args.notNull(httpHost, "Host");
        credMap.put(new AuthScope(httpHost), credentials);
        return this;
    }

    public CredentialsProviderBuilder add(final HttpHost httpHost, final String username, final char[] password) {
        Args.notNull(httpHost, "Host");
        credMap.put(new AuthScope(httpHost), new UsernamePasswordCredentials(username, password));
        return this;
    }

    public CredentialsProvider build() {
        if (credMap.size() == 0) {
            return new BasicCredentialsProvider();
        } else if (credMap.size() == 1) {
            final Map.Entry<AuthScope, Credentials> entry = credMap.entrySet().iterator().next();
            return new SingleCredentialsProvider(entry.getKey(), entry.getValue());
        } else {
            return new FixedCredentialsProvider(credMap);
        }
    }

    static class Entry {

        final AuthScope authScope;
        final Credentials credentials;

        Entry(final AuthScope authScope, final Credentials credentials) {
            this.authScope = authScope;
            this.credentials = credentials;
        }

    }

}
