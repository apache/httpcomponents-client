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

import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * This is an improved version of the {@link AuthScheme} interface, amended to be able to handle
 * a conversation involving multiple challenge-response transactions and adding the ability to check
 * the results of a final token sent together with the successful HTTP request as required by
 * RFC 4559 and RFC 7546.
 *
 * @since 5.5
 */
public interface AuthScheme2 extends AuthScheme {

    /**
     * Processes the given auth challenge. Some authentication schemes may involve multiple
     * challenge-response exchanges. Such schemes must be able to maintain internal state
     * when dealing with sequential challenges.
     *
     * The {@link AuthScheme} interface  implicitly assumes that that the token passed here is
     * simply stored in this method, and the actual authentication takes place in
     * {@link org.apache.hc.client5.http.auth.AuthScheme#generateAuthResponse(HttpHost, HttpRequest, HttpContext) generateAuthResponse }
     * and/or {@link org.apache.hc.client5.http.auth.AuthScheme#isResponseReady(HttpHost, HttpRequest, HttpContext) generateAuthResponse },
     * as only those methods receive the HttpHost, and only those can throw an
     * AuthenticationException.
     *
     * This new methods signature makes it possible to process the token and throw an
     * AuthenticationException immediately even when no response is sent (i.e. processing the mutual
     * authentication response)
     *
     * When {@link  isChallengeExpected} returns true, but no challenge was sent, then this method must
     * be called with a null {@link AuthChallenge} so that the Scheme can handle this situation.
     *
     * @param host HTTP host
     * @param authChallenge the auth challenge or null if no challenge was received
     * @param context HTTP context
     * @param challenged true if the response was unauthorised (401/407)
     * @throws AuthenticationException in case the authentication process is unsuccessful.
     * @since 5.5
     */
    void processChallenge(
            HttpHost host,
            AuthChallenge authChallenge,
            HttpContext context,
            boolean challenged) throws AuthenticationException;

    /**
     * The old processChallenge signature is unfit for use in AuthScheme2.
     * If the old signature is sufficient for a scheme, then it should implement {@link AuthScheme}
     * instead AuthScheme2.
     */
    @Override
    default void processChallenge(
            AuthChallenge authChallenge,
            HttpContext context) throws MalformedChallengeException {
        throw new UnsupportedOperationException("on AuthScheme2 implementations only the four "
                + "argument processChallenge method can be called");
    }

    /**
     * Indicates that the even authorized (i.e. not 401 or 407) responses must be processed
     * by this Scheme.
     *
     * The original AuthScheme interface only processes unauthorised responses.
     * This method indicates that non unauthorised responses are expected to contain challenges
     * and must be processed by the Scheme.
     * This is required to implement the SPENGO RFC and Kerberos mutual authentication.
     *
     * @return true if responses with non 401/407 response codes must be processed by the scheme.
     * @since 5.5
     */
    boolean isChallengeExpected();

}
