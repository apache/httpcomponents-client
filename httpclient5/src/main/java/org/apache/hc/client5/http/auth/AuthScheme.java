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
package org.apache.hc.client5.http.auth;

import java.security.Principal;

import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * This interface represents an abstract challenge-response oriented authentication scheme.
 * <p>
 * Authentication schemes can be either request or connection based. The former are
 * expected to provide an authorization response with each request message while the latter
 * is executed only once and applies to the underlying connection for its entire life span.
 * Care must be taken when re-using connections authorized through a connection based
 * authentication scheme and they may carry a particular security context and be authorized
 * for a particular user identity. It is important that such schemes always provide
 * the user identity they represent through the {@link #getPrincipal()} method.
 * </p>
 * <p>
 * Authentication scheme are expected to transition through a series of standard phases or
 * states.
 * </p>
 * <p>
 * Authentication scheme starts off its life cycle with no context and no specific state.
 * </p>
 * <p>
 * The {@link #processChallenge(AuthChallenge, HttpContext)} method is called  to
 * process an authentication challenge received either from the target server or a proxy.
 * The authentication scheme transitions to CHALLENGED state and is expected to validate
 * the token passed to it as a parameter and initialize its internal state based on
 * challenge details. Standard authentication schemes are expected to provide a realm
 * attribute in the challenge. {@link #getRealm()} can be called to obtain an identifier
 * of the realm that requires authorization.
 * </p>
 * <p>
 * Once the challenge has been fully processed the {@link #isResponseReady(HttpHost,
 * CredentialsProvider, HttpContext)} method to determine whether the scheme is capable of
 * generating a authorization response based on its current state and it holds user credentials
 * required to do so. If this method returns {@code false} the authentication is considered
 * to be in FAILED state and no authorization response. Otherwise the scheme is considered
 * to be in RESPONSE_READY state.
 * </p>
 * <p>
 * Once the scheme is ready to respond to the challenge the {@link #generateAuthResponse(
 * HttpHost, HttpRequest, HttpContext)} method to generate a response token, which will
 * be sent to the opposite endpoint in the subsequent request message.
 * </p>
 * <p>
 * Certain non-standard schemes may involve multiple challenge / response exchanges to
 * fully establish a shared context and complete the authentication process. Authentication
 * schemes are required to return {@code true} {@link #isChallengeComplete()} once the
 * handshake is considered complete.
 * </p>
 * <p>
 * The authentication scheme is considered successfully completed and in SUCCESS state
 * if the opposite endpoint accepts the request message containing the authorization
 * response and responds with a message indicating no authentication failure .
 * If the opposite endpoint sends status code 401 or 407 in response to a request message
 * containing the terminal authorization response, the scheme is considered unsuccessful
 * and in FAILED state.
 * </p>
 *
 * @since 4.0
 */
public interface AuthScheme {

    /**
     * Returns textual designation of the given authentication scheme.
     *
     * @return the name of the given authentication scheme
     */
    String getName();

    /**
     * Determines if the authentication scheme is expected to provide an authorization response
     * on a per connection basis instead of the standard per request basis
     *
     * @return {@code true} if the scheme is connection based, {@code false}
     * if the scheme is request based.
     */
    boolean isConnectionBased();

    /**
     * Processes the given auth challenge. Some authentication schemes may involve multiple
     * challenge-response exchanges. Such schemes must be able to maintain internal state
     * when dealing with sequential challenges
     *
     * @param authChallenge the auth challenge
     * @param context HTTP context
     * @throws MalformedChallengeException in case the auth challenge is incomplete,
     * malformed or otherwise invalid.
     * @since 5.0
     */
    void processChallenge(
            AuthChallenge authChallenge,
            HttpContext context) throws MalformedChallengeException;

    /**
     * Authentication process may involve a series of challenge-response exchanges.
     * This method tests if the authorization process has been fully completed (either
     * successfully or unsuccessfully), that is, all the required authorization
     * challenges have been processed in their entirety.
     *
     * @return {@code true} if the authentication process has been completed,
     * {@code false} otherwise.
     *
     * @since 5.0
     */
    boolean isChallengeComplete();

    /**
     * Returns authentication realm. If the concept of an authentication
     * realm is not applicable to the given authentication scheme, returns
     * {@code null}.
     *
     * @return the authentication realm
     */
    String getRealm();

    /**
     * Determines whether or not an authorization response can be generated based on
     * the actual authentication state. Generally the outcome of this method will depend
     * upon availability of user credentials necessary to produce an authorization
     * response.
     *
     * @param credentialsProvider The credentials to be used for authentication
     * @param context HTTP context
     * @throws AuthenticationException if authorization string cannot
     *   be generated due to an authentication failure
     *
     * @return {@code true} if an authorization response can be generated and
     * the authentication handshake can proceed, {@code false} otherwise.
     *
     * @since 5.0
     */
    boolean isResponseReady(
            HttpHost host,
            CredentialsProvider credentialsProvider,
            HttpContext context) throws AuthenticationException;

    /**
     * Returns {@link Principal} whose credentials are used to generate
     * an authentication response. Connection based schemes are required
     * to return a user {@link Principal} if authorization applies to
     * for the entire life span of connection.
     * @return user principal
     *
     * @see #isConnectionBased()
     *
     * @since 5.0
     */
    Principal getPrincipal();

    /**
     * Generates an authorization response based on the current state. Some authentication
     * schemes may need to load user credentials required to generate an authorization
     * response from a {@link CredentialsProvider} prior to this method call.
     *
     * @param request The request being authenticated
     * @param context HTTP context
     * @throws AuthenticationException if authorization string cannot
     *   be generated due to an authentication failure
     *
     * @return authorization header
     *
     * @see #isResponseReady(HttpHost, CredentialsProvider, HttpContext)
     *
     * @since 5.0
     */
    String generateAuthResponse(
            HttpHost host,
            HttpRequest request,
            HttpContext context) throws AuthenticationException;

}
