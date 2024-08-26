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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;

import org.apache.hc.client5.http.AuthenticationStrategy;
import org.apache.hc.client5.http.auth.AuthChallenge;
import org.apache.hc.client5.http.auth.AuthExchange;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.AuthSchemeV2;
import org.apache.hc.client5.http.auth.AuthenticationException;
import org.apache.hc.client5.http.auth.ChallengeType;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.MalformedChallengeException;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.annotation.Contract;
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
@Contract(threading = ThreadingBehavior.STATELESS)
public final class HttpAuthenticator {

    private static final Logger LOG = LoggerFactory.getLogger(HttpAuthenticator.class);

    private final AuthChallengeParser parser;

    public HttpAuthenticator() {
        this.parser = new AuthChallengeParser();
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
        final String exchangeId = clientContext.getExchangeId();

        if (response.getCode() == challengeCode) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} Authentication required", exchangeId);
            }
            return true;
        }
        switch (authExchange.getState()) {
        case CHALLENGED:
        case HANDSHAKE:
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} Authentication succeeded", exchangeId);
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

    public boolean isChallengeExpected(final AuthExchange authExchange) {
        final AuthScheme authScheme = authExchange.getAuthScheme();
        if (authScheme != null && authScheme instanceof AuthSchemeV2) {
            return ((AuthSchemeV2)authScheme).isChallengeExpected();
        }
        return false;
    }

    // Externalized to avoid having to re-parse the challenges
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
                    final HttpClientContext clientContext = HttpClientContext.adapt(context);
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
     * @throws AuthenticationException if the server has accepted our authorisation, but we could
     * not authenticate the server
     */
    public boolean updateAuthState(
            final HttpHost host,
            final ChallengeType challengeType,
            final HttpResponse response,
            final AuthenticationStrategy authStrategy,
            final AuthExchange authExchange,
            final HttpContext context,
            final boolean authenticated) throws AuthenticationException {

        final HttpClientContext clientContext = HttpClientContext.adapt(context);
        final String exchangeId = clientContext.getExchangeId();

        if (LOG.isDebugEnabled()) {
            LOG.debug("{} {} requested authentication", exchangeId, host.toHostString());
        }

        final Map<String, AuthChallenge> challengeMap = extractChallengeMap(challengeType, response, clientContext);

        if (challengeMap.isEmpty()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} Response contains no valid authentication challenges", exchangeId);
            }
            authExchange.reset();
            return false;
        }

        switch (authExchange.getState()) {
            case FAILURE:
                return false;
            case SUCCESS:
                if (!authenticated) {
                    authExchange.reset();
                    break;
                }
            case CHALLENGED:
                // fall through
            case HANDSHAKE:
                Asserts.notNull(authExchange.getAuthScheme(), "AuthScheme");
                // fall through
            case UNCHALLENGED:
                final AuthScheme authScheme = authExchange.getAuthScheme();
                // This is skipped for the first challenge, because the auth scheme hasn't been set yet
                if (authScheme != null) {
                    final String schemeName = authScheme.getName();
                    final AuthChallenge challenge = challengeMap.get(schemeName.toLowerCase(Locale.ROOT));
                    if (challenge != null) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("{} Authorization challenge processed", exchangeId);
                        }
                        try {
                            if (authScheme instanceof AuthSchemeV2) {
                                ((AuthSchemeV2)authScheme).processChallenge(host, challenge, context, authenticated);
                            } else {
                                authScheme.processChallenge(challenge, context);
                            }
                        } catch (final AuthenticationException | MalformedChallengeException ex) {
                            if (LOG.isWarnEnabled()) {
                                LOG.warn("Exception processing Challange{}", exchangeId, ex);
                            }
//                            LOG.error("Exception processing Challange{}", exchangeId, ex);

                            authExchange.reset();
                            authExchange.setState(AuthExchange.State.FAILURE);
                            if (authenticated) {
                                // This is the mutual authentication error case
                                if (ex instanceof AuthenticationException) {
                                    throw (AuthenticationException)ex;
                                } else {
                                    // Shouldn't MalformedChallengeException be a child of AuthenticationException ?
                                    //FIXME Make sure to reset the response in the caller
                                    throw new AuthenticationException("MalformedChallengeException while processing challenge", ex);
                                }
                            }
                        }
                        // At this point the Scheme has not yet replied to the challenge, so complete
                        // means either that is has already failed. This is not true for AuthSchemeV2,
                        // so care must be taken 
                        if (authScheme.isChallengeComplete()) {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("{} Authentication failed", exchangeId);
                            }
                            authExchange.reset();
                            authExchange.setState(AuthExchange.State.FAILURE);
                            return false;
                        }
                        if (authenticated) {
                            // There are no more challanges sent after the 200 message, 
                            // and if we get here, then the mutual auth phase has succeeded.
                            authExchange.setState(AuthExchange.State.SUCCESS);
                            return false;
                        } else {
                            authExchange.setState(AuthExchange.State.HANDSHAKE);
                        }
                        // In case of multiple challenges, we only reply to the first one
                        // in the preference list for . All other challenges are ignored.
                        return true;
                    }
                    authExchange.reset();
                    // Retry authentication with a different scheme
                }
        }

        // This runs if we haven't returned above because of unset autScheme
        final List<AuthScheme> preferredSchemes = authStrategy.select(challengeType, challengeMap, context);
        final CredentialsProvider credsProvider = clientContext.getCredentialsProvider();
        if (credsProvider == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} Credentials provider not set in the context", exchangeId);
            }
            return false;
        }

        // This is active during the first call, and is used to set up authContext for the
        // next passes
        final Queue<AuthScheme> authOptions = new LinkedList<>();
        if (LOG.isDebugEnabled()) {
            LOG.debug("{} Selecting authentication options", exchangeId);
        }
        for (final AuthScheme authScheme: preferredSchemes) {
            try {
                final String schemeName = authScheme.getName();
                final AuthChallenge challenge = challengeMap.get(schemeName.toLowerCase(Locale.ROOT));
                if (authScheme instanceof AuthSchemeV2) {
                    ((AuthSchemeV2)authScheme).processChallenge(host, challenge, context, authenticated);
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
        final HttpClientContext clientContext = HttpClientContext.adapt(context);
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
                //FIXME This is the first pass generating the initial auth response
                Iterator<AuthScheme> it = authOptions.iterator();
                while (it.hasNext()) {
                    authScheme = it.next();
                    // Why even bother removing if we break anyway ?
//                    if (!(authScheme instanceof AuthSchemeV2)) {
                        // Non V2 AuthSchemes are basically stateless. 
                        // To avoid possible problems with state handling, remove
                        // them, just like it was done originally
//                        it.remove();
//                    }
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
                    } catch (final AuthenticationException ex) {
                        // FIXME This is where should return an exception if the request cannot be processed
                        // FIXME What to do if there are multiple schemes, and only some fail ?
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
        // This is for the SUCCESS and HANDSHAKE state, Which AFAICT never did happen, as
        // the authSchemes were removed on the first response anyway
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
