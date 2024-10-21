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
import org.apache.hc.client5.http.auth.AuthChallenge;
import org.apache.hc.client5.http.auth.AuthExchange;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.AuthScheme2;
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
 * <p>
 * Please note that since version 5.2 this class no longer updated the authentication cache
 * bound to the execution context.
 *
 * @since 4.3
 */
@Internal
@Contract(threading = ThreadingBehavior.STATELESS)
public final class HttpAuthenticator {

    private static final Logger LOG = LoggerFactory.getLogger(HttpAuthenticator.class);

    private final AuthChallengeParser parser;

    public HttpAuthenticator() {
        this.parser = new AuthChallengeParser();
    }

    /**
     * Determines whether the given response represents an authentication challenge, and updates
     * the autheExchange status.
     *
     * @param host the hostname of the opposite endpoint.
     * @param challengeType the challenge type (target or proxy).
     * @param response the response message head.
     * @param authExchange the current authentication exchange state. Gets updated.
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
        if (checkChallenged(challengeType, response, context)) {
            return true;
        }
        switch (authExchange.getState()) {
        case CHALLENGED:
        case HANDSHAKE:
            if (LOG.isDebugEnabled()) {
                final HttpClientContext clientContext = HttpClientContext.cast(context);
                final String exchangeId = clientContext.getExchangeId();
                // The mutual auth may still fail
                LOG.debug("{} Server has accepted authorization", exchangeId);
            }
            authExchange.setState(AuthExchange.State.SUCCESS);
            break;
        case SUCCESS:
            break;
        default:
            authExchange.setState(AuthExchange.State.UNCHALLENGED);
        }
        return false;
    }

    /**
     * Determines whether the given response represents an authentication challenge, without
     * changing the AuthExchange state.
     *
     * @param challengeType the challenge type (target or proxy).
     * @param response the response message head.
     * @param context the current execution context.
     * @return {@code true} if the response message represents an authentication challenge,
     *   {@code false} otherwise.
     */
    private boolean checkChallenged(final ChallengeType challengeType, final HttpResponse response, final HttpContext context) {
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

        if (response.getCode() == challengeCode) {
            if (LOG.isDebugEnabled()) {
                final HttpClientContext clientContext = HttpClientContext.cast(context);
                final String exchangeId = clientContext.getExchangeId();
                LOG.debug("{} Authentication required", exchangeId);
            }
            return true;
        }
        return false;
    }

    /**
     * Determines if the scheme requires an auth challenge for responses that do not
     * have challenge HTTP code. (i.e whether it needs a mutual authentication token)
     *
     * @param authExchange
     * @return true is authExchange's scheme is AuthScheme2, which currently expects
     * a WWW-Authenticate header even for authorized HTTP responses
     */
    public boolean isChallengeExpected(final AuthExchange authExchange) {
        final AuthScheme authScheme = authExchange.getAuthScheme();
        if (authScheme != null && authScheme instanceof AuthScheme2) {
            return ((AuthScheme2)authScheme).isChallengeExpected();
        } else {
            return false;
        }
    }

    public Map<String, AuthChallenge> extractChallengeMap(final ChallengeType challengeType,
            final HttpResponse response, final HttpClientContext context) {
        final Header[] headers =
                response.getHeaders(
                    challengeType == ChallengeType.PROXY ? HttpHeaders.PROXY_AUTHENTICATE
                            : HttpHeaders.WWW_AUTHENTICATE);
        final Map<String, AuthChallenge> challengeMap = new HashMap<>();
        for (final Header header : headers) {
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
                if (LOG.isWarnEnabled()) {
                    final HttpClientContext clientContext = HttpClientContext.cast(context);
                    final String exchangeId = clientContext.getExchangeId();
                    LOG.warn("{} Malformed challenge: {}", exchangeId, header.getValue());
                }
                continue;
            }
            for (final AuthChallenge authChallenge : authChallenges) {
                final String schemeName = authChallenge.getSchemeName().toLowerCase(Locale.ROOT);
                if (!challengeMap.containsKey(schemeName)) {
                    challengeMap.put(schemeName, authChallenge);
                }
            }
        }
        return challengeMap;
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
     * @return {@code true} if the request needs-to be re-sent ,
     *   {@code false} if the authentication is complete (successful or not).
     *
     * @throws AuthenticationException if the AuthScheme throws one. In most cases this indicates a
     * client side problem, as final server error responses are simply returned.
     * @throws MalformedChallengeException if the AuthScheme throws one. In most cases this indicates a
     * client side problem, as final server error responses are simply returned.
     */
    public boolean updateAuthState(
            final HttpHost host,
            final ChallengeType challengeType,
            final HttpResponse response,
            final AuthenticationStrategy authStrategy,
            final AuthExchange authExchange,
            final HttpContext context) throws AuthenticationException, MalformedChallengeException {

        final HttpClientContext clientContext = HttpClientContext.cast(context);
        final String exchangeId = clientContext.getExchangeId();
        final boolean challenged = checkChallenged(challengeType, response, context);
        final boolean isChallengeExpected = isChallengeExpected(authExchange);

        if (LOG.isDebugEnabled()) {
            LOG.debug("{} {} requested authentication", exchangeId, host.toHostString());
        }

        final Map<String, AuthChallenge> challengeMap = extractChallengeMap(challengeType, response, clientContext);

        if (challengeMap.isEmpty()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} Response contains no valid authentication challenges", exchangeId);
            }
            if (!isChallengeExpected) {
                authExchange.reset();
                return false;
            }
        }

        switch (authExchange.getState()) {
            case FAILURE:
                return false;
            case SUCCESS:
                if (!isChallengeExpected) {
                    authExchange.reset();
                    break;
                }
                // otherwise fall through
            case CHALLENGED:
                // fall through
            case HANDSHAKE:
                Asserts.notNull(authExchange.getAuthScheme(), "AuthScheme");
                // fall through
            case UNCHALLENGED:
                final AuthScheme authScheme = authExchange.getAuthScheme();
                // AuthScheme is only set if we have already sent an auth response, either
                // because we have received a challenge for it, or preemptively.
                if (authScheme != null) {
                    final String schemeName = authScheme.getName();
                    final AuthChallenge challenge = challengeMap.get(schemeName.toLowerCase(Locale.ROOT));
                    if (challenge != null || isChallengeExpected) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("{} Processing authorization challenge {}", exchangeId, challenge);
                        }
                        try {
                            if (authScheme instanceof AuthScheme2) {
                                ((AuthScheme2)authScheme).processChallenge(host, challenge, context, challenged);
                            } else {
                                authScheme.processChallenge(challenge, context);
                            }
                        } catch (final AuthenticationException | MalformedChallengeException ex) {
                            if (LOG.isWarnEnabled()) {
                                LOG.warn("Exception processing Challange {}", exchangeId, ex);
                            }
                            authExchange.reset();
                            authExchange.setState(AuthExchange.State.FAILURE);
                            if (!challenged) {
                                throw ex;
                            }
                        }
                        if (authScheme.isChallengeComplete()) {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("{} Authentication failed", exchangeId);
                            }
                            authExchange.reset();
                            authExchange.setState(AuthExchange.State.FAILURE);
                            return false;
                        }
                        if (!challenged) {
                            // There are no more challanges sent after the 200 message,
                            // and if we get here, then the mutual auth phase has succeeded.
                            authExchange.setState(AuthExchange.State.SUCCESS);
                            return false;
                        } else {
                            authExchange.setState(AuthExchange.State.HANDSHAKE);
                        }
                        return true;
                    }
                    authExchange.reset();
                    // Retry authentication with a different scheme
                }
        }

        // We reach this if we fell through above because the authScheme has not yet been set, or if
        // we receive a 401/407 response for an unexpected scheme. Normally this processes the first
        // 401/407 response
        final List<AuthScheme> preferredSchemes = authStrategy.select(challengeType, challengeMap, context);
        final CredentialsProvider credsProvider = clientContext.getCredentialsProvider();
        if (credsProvider == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} Credentials provider not set in the context", exchangeId);
            }
            return false;
        }

        final Queue<AuthScheme> authOptions = new LinkedList<>();
        if (LOG.isDebugEnabled()) {
            LOG.debug("{} Selecting authentication options", exchangeId);
        }
        for (final AuthScheme authScheme: preferredSchemes) {
            // We only respond to the the first successfully processed challenge. However, the
            // original AuthScheme API does not really process the challenge at this point, so we need
            // to process/store each challenge here anyway.
            try {
                final String schemeName = authScheme.getName();
                final AuthChallenge challenge = challengeMap.get(schemeName.toLowerCase(Locale.ROOT));
                if (authScheme instanceof AuthScheme2) {
                    ((AuthScheme2)authScheme).processChallenge(host, challenge, context, challenged);
                } else {
                    authScheme.processChallenge(challenge, context);
                }
                if (authScheme.isResponseReady(host, credsProvider, context)) {
                    authOptions.add(authScheme);
                }
            } catch (final AuthenticationException | MalformedChallengeException ex) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("Exception while processing Challange", ex);
                }
            }
        }
        if (!authOptions.isEmpty()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} Selected authentication options: {}", exchangeId, authOptions);
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
        final HttpClientContext clientContext = HttpClientContext.cast(context);
        final String exchangeId = clientContext.getExchangeId();
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
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("{} Generating response to an authentication challenge using {} scheme",
                                exchangeId, authScheme.getName());
                    }
                    try {
                        final String authResponse = authScheme.generateAuthResponse(host, request, context);
                        if (authResponse != null) {
                            final Header header = new BasicHeader(
                                    challengeType == ChallengeType.TARGET ? HttpHeaders.AUTHORIZATION : HttpHeaders.PROXY_AUTHORIZATION,
                                    authResponse);
                            request.addHeader(header);
                        }
                        break;
                    } catch (final AuthenticationException ex ) {
                        if (LOG.isWarnEnabled()) {
                            LOG.warn("{} {} authentication error: {}", exchangeId, authScheme, ex.getMessage());
                        }
                    }
                }
                return;
            }
            Asserts.notNull(authScheme, "AuthScheme");
        default:
        }
        // This is the SUCCESS and HANDSHAKE states, same as the initial response.
        // This only happens if the NEGOTIATE handshake requires multiple requests, which is
        // defined in the RFC, but unlikely in practice.
        if (authScheme != null) {
            try {
                final String authResponse = authScheme.generateAuthResponse(host, request, context);
                final Header header = new BasicHeader(
                        challengeType == ChallengeType.TARGET ? HttpHeaders.AUTHORIZATION : HttpHeaders.PROXY_AUTHORIZATION,
                        authResponse);
                request.addHeader(header);
            } catch (final AuthenticationException ex) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("{} {} authentication error: {}", exchangeId, authScheme, ex.getMessage());
                }
            }
        }
    }

}
