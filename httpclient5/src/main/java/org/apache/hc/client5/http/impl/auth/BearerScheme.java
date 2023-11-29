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

import java.io.Serializable;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.hc.client5.http.auth.AuthChallenge;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.AuthStateCacheable;
import org.apache.hc.client5.http.auth.AuthenticationException;
import org.apache.hc.client5.http.auth.BearerToken;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.MalformedChallengeException;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Asserts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bearer authentication scheme.
 *
 * @since 5.3
 */
@AuthStateCacheable
public class BearerScheme implements AuthScheme, Serializable {

    private static final Logger LOG = LoggerFactory.getLogger(BearerScheme.class);

    private final Map<String, String> paramMap;
    private boolean complete;

    private BearerToken bearerToken;

    public BearerScheme() {
        this.paramMap = new HashMap<>();
        this.complete = false;
    }

    @Override
    public String getName() {
        return StandardAuthScheme.BEARER;
    }

    @Override
    public boolean isConnectionBased() {
        return false;
    }

    @Override
    public String getRealm() {
        return this.paramMap.get("realm");
    }

    @Override
    public void processChallenge(
            final AuthChallenge authChallenge,
            final HttpContext context) throws MalformedChallengeException {
        this.paramMap.clear();
        final List<NameValuePair> params = authChallenge.getParams();
        if (params != null) {
            for (final NameValuePair param: params) {
                this.paramMap.put(param.getName().toLowerCase(Locale.ROOT), param.getValue());
            }
            if (LOG.isDebugEnabled()) {
                final String error = paramMap.get("error");
                if (error != null) {
                    final StringBuilder buf = new StringBuilder();
                    buf.append(error);
                    final String desc = paramMap.get("error_description");
                    final String uri = paramMap.get("error_uri");
                    if (desc != null || uri != null) {
                        buf.append(" (");
                        buf.append(desc).append("; ").append(uri);
                        buf.append(")");
                    }
                    LOG.debug(buf.toString());
                }
            }
        }
        this.complete = true;
    }

    @Override
    public boolean isChallengeComplete() {
        return this.complete;
    }

    @Override
    public boolean isResponseReady(
            final HttpHost host,
            final CredentialsProvider credentialsProvider,
            final HttpContext context) throws AuthenticationException {

        Args.notNull(host, "Auth host");
        Args.notNull(credentialsProvider, "Credentials provider");

        final AuthScope authScope = new AuthScope(host, getRealm(), getName());
        final Credentials credentials = credentialsProvider.getCredentials(authScope, context);
        if (credentials instanceof BearerToken) {
            this.bearerToken = (BearerToken) credentials;
            return true;
        }

        if (LOG.isDebugEnabled()) {
            final HttpClientContext clientContext = HttpClientContext.adapt(context);
            final String exchangeId = clientContext.getExchangeId();
            LOG.debug("{} No credentials found for auth scope [{}]", exchangeId, authScope);
        }
        this.bearerToken = null;
        return false;
    }

    @Override
    public Principal getPrincipal() {
        return null;
    }

    @Override
    public String generateAuthResponse(
            final HttpHost host,
            final HttpRequest request,
            final HttpContext context) throws AuthenticationException {
        Asserts.notNull(bearerToken, "Bearer token");
        return StandardAuthScheme.BEARER + " " + bearerToken.getToken();
    }

    @Override
    public String toString() {
        return getName() + this.paramMap;
    }

}
