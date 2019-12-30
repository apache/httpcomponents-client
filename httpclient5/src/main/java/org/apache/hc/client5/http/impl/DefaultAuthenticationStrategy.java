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

package org.apache.hc.client5.http.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.hc.client5.http.AuthenticationStrategy;
import org.apache.hc.client5.http.auth.AuthChallenge;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.auth.ChallengeType;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of {@link AuthenticationStrategy}
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public class DefaultAuthenticationStrategy implements AuthenticationStrategy {

    private final Logger log = LoggerFactory.getLogger(getClass());

    public static final DefaultAuthenticationStrategy INSTANCE = new DefaultAuthenticationStrategy();

    private static final List<String> DEFAULT_SCHEME_PRIORITY =
        Collections.unmodifiableList(Arrays.asList(
                StandardAuthScheme.SPNEGO,
                StandardAuthScheme.KERBEROS,
                StandardAuthScheme.NTLM,
                StandardAuthScheme.DIGEST,
                StandardAuthScheme.BASIC));

    @Override
    public List<AuthScheme> select(
            final ChallengeType challengeType,
            final Map<String, AuthChallenge> challenges,
            final HttpContext context) {
        Args.notNull(challengeType, "ChallengeType");
        Args.notNull(challenges, "Map of auth challenges");
        Args.notNull(context, "HTTP context");
        final HttpClientContext clientContext = HttpClientContext.adapt(context);

        final List<AuthScheme> options = new ArrayList<>();
        final Lookup<AuthSchemeFactory> registry = clientContext.getAuthSchemeRegistry();
        if (registry == null) {
            this.log.debug("Auth scheme registry not set in the context");
            return options;
        }
        final RequestConfig config = clientContext.getRequestConfig();
        Collection<String> authPrefs = challengeType == ChallengeType.TARGET ?
                config.getTargetPreferredAuthSchemes() : config.getProxyPreferredAuthSchemes();
        if (authPrefs == null) {
            authPrefs = DEFAULT_SCHEME_PRIORITY;
        }
        if (this.log.isDebugEnabled()) {
            this.log.debug("Authentication schemes in the order of preference: " + authPrefs);
        }

        for (final String schemeName: authPrefs) {
            final AuthChallenge challenge = challenges.get(schemeName.toLowerCase(Locale.ROOT));
            if (challenge != null) {
                final AuthSchemeFactory authSchemeFactory = registry.lookup(schemeName);
                if (authSchemeFactory == null) {
                    if (this.log.isWarnEnabled()) {
                        this.log.warn("Authentication scheme " + schemeName + " not supported");
                        // Try again
                    }
                    continue;
                }
                final AuthScheme authScheme = authSchemeFactory.create(context);
                options.add(authScheme);
            } else {
                if (this.log.isDebugEnabled()) {
                    this.log.debug("Challenge for " + schemeName + " authentication scheme not available");
                }
            }
        }
        return options;
    }

}
