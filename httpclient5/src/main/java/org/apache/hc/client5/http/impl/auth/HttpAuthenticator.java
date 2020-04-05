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
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;

import org.apache.hc.client5.http.AuthenticationStrategy;
import org.apache.hc.client5.http.auth.AuthCache;
import org.apache.hc.client5.http.auth.AuthChallenge;
import org.apache.hc.client5.http.auth.AuthExchange;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.AuthStateCacheable;
import org.apache.hc.client5.http.auth.AuthenticationException;
import org.apache.hc.client5.http.auth.ChallengeType;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.MalformedChallengeException;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.FormattedHeader;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.ParserCursor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Asserts;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class that implements commons aspects of the client side HTTP authentication.
 *
 * @since 4.3
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public final class HttpAuthenticator {

    private final Logger log;
    private final AuthChallengeParser parser;

    @Internal
    public HttpAuthenticator(final Logger log) {
        super();
        this.log = log != null ? log : LoggerFactory.getLogger(getClass());
        this.parser = new AuthChallengeParser();
    }

    public HttpAuthenticator() {
        this(null);
    }

    /**
     * Determines whether the given response represents an authentication challenge.
     *
     * @param host the hostname of the opposite endpoint.
     * @param challengeType the challenge type (target or proxy).
     * @param response the response message head.
     * @param authExchange the current authentication exchange state.
     * @param context the current execution context.
     * @return {@code true} if the response message represents an authentication challenge,
     *   {@code false} otherwise.
     */
    public boolean isChallenged(
            final HttpHost host,
            final ChallengeType challengeType,
            final HttpResponse response,
            final AuthExchange authExchange,
            final HttpContext context) {
        final int challengeCode;
        switch (challengeType) {
            case TARGET:
                challengeCode = HttpStatus.SC_UNAUTHORIZED;
                break;
            case PROXY:
                challengeCode = HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED;
                break;
            default:
                throw new IllegalStateException("Unexpected challenge type: " + challengeType);
        }

        final HttpClientContext clientContext = HttpClientContext.adapt(context);

        if (response.getCode() == challengeCode) {
            this.log.debug("Authentication required");
            if (authExchange.getState() == AuthExchange.State.SUCCESS) {
                clearCache(host, clientContext);
            }
            return true;
        }
        switch (authExchange.getState()) {
        case CHALLENGED:
        case HANDSHAKE:
            this.log.debug("Authentication succeeded");
            authExchange.setState(AuthExchange.State.SUCCESS);
            updateCache(host, authExchange.getAuthScheme(), clientContext);
            break;
        case SUCCESS:
            break;
        default:
            authExchange.setState(AuthExchange.State.UNCHALLENGED);
        }
        return false;
    }

    /**
     * Updates the {@link AuthExchange} state based on the challenge presented in the response message
     * using the given {@link AuthenticationStrategy}.
     *
     * @param host the hostname of the opposite endpoint.
     * @param challengeType the challenge type (target or proxy).
     * @param response the response message head.
     * @param authStrategy the authentication strategy.
     * @param authExchange the current authentication exchange state.
     * @param context the current execution context.
     * @return {@code true} if the authentication state has been updated,
     *   {@code false} if unchanged.
     */
    public boolean updateAuthState(
            final HttpHost host,
            final ChallengeType challengeType,
            final HttpResponse response,
            final AuthenticationStrategy authStrategy,
            final AuthExchange authExchange,
            final HttpContext context) {

        if (this.log.isDebugEnabled()) {
            this.log.debug(host.toHostString() + " requested authentication");
        }

        final HttpClientContext clientContext = HttpClientContext.adapt(context);

        final Header[] headers = response.getHeaders(
                challengeType == ChallengeType.PROXY ? HttpHeaders.PROXY_AUTHENTICATE : HttpHeaders.WWW_AUTHENTICATE);
        final Map<String, AuthChallenge> challengeMap = new HashMap<>();
        for (final Header header: headers) {
            final CharArrayBuffer buffer;
            final int pos;
            if (header instanceof FormattedHeader) {
                buffer = ((FormattedHeader) header).getBuffer();
                pos = ((FormattedHeader) header).getValuePos();
            } else {
                final String s = header.getValue();
                if (s == null) {
                    continue;
                }
                buffer = new CharArrayBuffer(s.length());
                buffer.append(s);
                pos = 0;
            }
            final ParserCursor cursor = new ParserCursor(pos, buffer.length());
            final List<AuthChallenge> authChallenges;
            try {
                authChallenges = parser.parse(challengeType, buffer, cursor);
            } catch (final ParseException ex) {
                if (this.log.isWarnEnabled()) {
                    this.log.warn("Malformed challenge: " + header.getValue());
                }
                continue;
            }
            for (final AuthChallenge authChallenge: authChallenges) {
                final String schemeName = authChallenge.getSchemeName().toLowerCase(Locale.ROOT);
                if (!challengeMap.containsKey(schemeName)) {
                    challengeMap.put(schemeName, authChallenge);
                }
            }
        }
        if (challengeMap.isEmpty()) {
            this.log.debug("Response contains no valid authentication challenges");
            clearCache(host, clientContext);
            authExchange.reset();
            return false;
        }

        switch (authExchange.getState()) {
            case FAILURE:
                return false;
            case SUCCESS:
                authExchange.reset();
                break;
            case CHALLENGED:
            case HANDSHAKE:
                Asserts.notNull(authExchange.getAuthScheme(), "AuthScheme");
            case UNCHALLENGED:
                final AuthScheme authScheme = authExchange.getAuthScheme();
                if (authScheme != null) {
                    final String schemeName = authScheme.getName();
                    final AuthChallenge challenge = challengeMap.get(schemeName.toLowerCase(Locale.ROOT));
                    if (challenge != null) {
                        this.log.debug("Authorization challenge processed");
                        try {
                            authScheme.processChallenge(challenge, context);
                        } catch (final MalformedChallengeException ex) {
                            if (this.log.isWarnEnabled()) {
                                this.log.warn(ex.getMessage());
                            }
                            clearCache(host, clientContext);
                            authExchange.reset();
                            return false;
                        }
                        if (authScheme.isChallengeComplete()) {
                            this.log.debug("Authentication failed");
                            clearCache(host, clientContext);
                            authExchange.reset();
                            authExchange.setState(AuthExchange.State.FAILURE);
                            return false;
                        }
                        authExchange.setState(AuthExchange.State.HANDSHAKE);
                        return true;
                    }
                    authExchange.reset();
                    // Retry authentication with a different scheme
                }
        }

        final List<AuthScheme> preferredSchemes = authStrategy.select(challengeType, challengeMap, context);
        final CredentialsProvider credsProvider = clientContext.getCredentialsProvider();
        if (credsProvider == null) {
            this.log.debug("Credentials provider not set in the context");
            return false;
        }

        final Queue<AuthScheme> authOptions = new LinkedList<>();
        this.log.debug("Selecting authentication options");
        for (final AuthScheme authScheme: preferredSchemes) {
            try {
                final String schemeName = authScheme.getName();
                final AuthChallenge challenge = challengeMap.get(schemeName.toLowerCase(Locale.ROOT));
                authScheme.processChallenge(challenge, context);
                if (authScheme.isResponseReady(host, credsProvider, context)) {
                    authOptions.add(authScheme);
                }
            } catch (final AuthenticationException | MalformedChallengeException ex) {
                if (this.log.isWarnEnabled()) {
                    this.log.warn(ex.getMessage());
                }
            }
        }
        if (!authOptions.isEmpty()) {
            if (this.log.isDebugEnabled()) {
                this.log.debug("Selected authentication options: " + authOptions);
            }
            authExchange.reset();
            authExchange.setState(AuthExchange.State.CHALLENGED);
            authExchange.setOptions(authOptions);
            return true;
        }
        return false;
    }

    /**
     * Generates a response to the authentication challenge based on the actual {@link AuthExchange} state
     * and adds it to the given {@link HttpRequest} message .
     *
     * @param host the hostname of the opposite endpoint.
     * @param challengeType the challenge type (target or proxy).
     * @param request the request message head.
     * @param authExchange the current authentication exchange state.
     * @param context the current execution context.
     */
    public void addAuthResponse(
            final HttpHost host,
            final ChallengeType challengeType,
            final HttpRequest request,
            final AuthExchange authExchange,
            final HttpContext context) {
        AuthScheme authScheme = authExchange.getAuthScheme();
        switch (authExchange.getState()) {
        case FAILURE:
            return;
        case SUCCESS:
            Asserts.notNull(authScheme, "AuthScheme");
            if (authScheme.isConnectionBased()) {
                return;
            }
            break;
        case HANDSHAKE:
            Asserts.notNull(authScheme, "AuthScheme");
            break;
        case CHALLENGED:
            final Queue<AuthScheme> authOptions = authExchange.getAuthOptions();
            if (authOptions != null) {
                while (!authOptions.isEmpty()) {
                    authScheme = authOptions.remove();
                    authExchange.select(authScheme);
                    if (this.log.isDebugEnabled()) {
                        this.log.debug("Generating response to an authentication challenge using "
                                + authScheme.getName() + " scheme");
                    }
                    try {
                        final String authResponse = authScheme.generateAuthResponse(host, request, context);
                        final Header header = new BasicHeader(
                                challengeType == ChallengeType.TARGET ? HttpHeaders.AUTHORIZATION : HttpHeaders.PROXY_AUTHORIZATION,
                                authResponse);
                        request.addHeader(header);
                        break;
                    } catch (final AuthenticationException ex) {
                        if (this.log.isWarnEnabled()) {
                            this.log.warn(authScheme + " authentication error: " + ex.getMessage());
                        }
                    }
                }
                return;
            }
            Asserts.notNull(authScheme, "AuthScheme");
        default:
        }
        if (authScheme != null) {
            try {
                final String authResponse = authScheme.generateAuthResponse(host, request, context);
                final Header header = new BasicHeader(
                        challengeType == ChallengeType.TARGET ? HttpHeaders.AUTHORIZATION : HttpHeaders.PROXY_AUTHORIZATION,
                        authResponse);
                request.addHeader(header);
            } catch (final AuthenticationException ex) {
                if (this.log.isErrorEnabled()) {
                    this.log.error(authScheme + " authentication error: " + ex.getMessage());
                }
            }
        }
    }

    private void updateCache(final HttpHost host, final AuthScheme authScheme, final HttpClientContext clientContext) {
        final boolean cachable = authScheme.getClass().getAnnotation(AuthStateCacheable.class) != null;
        if (cachable) {
            AuthCache authCache = clientContext.getAuthCache();
            if (authCache == null) {
                authCache = new BasicAuthCache();
                clientContext.setAuthCache(authCache);
            }
            if (this.log.isDebugEnabled()) {
                this.log.debug("Caching '" + authScheme.getName() + "' auth scheme for " + host);
            }
            authCache.put(host, authScheme);
        }
    }

    private void clearCache(final HttpHost host, final HttpClientContext clientContext) {

        final AuthCache authCache = clientContext.getAuthCache();
        if (authCache != null) {
            if (this.log.isDebugEnabled()) {
                this.log.debug("Clearing cached auth scheme for " + host);
            }
            authCache.remove(host);
        }
    }

}
