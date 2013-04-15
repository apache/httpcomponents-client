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

import java.util.Locale;
import java.util.Map;
import java.util.Queue;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthProtocolState;
import org.apache.http.auth.AuthOption;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.MalformedChallengeException;
import org.apache.http.client.AuthenticationStrategy;
import org.apache.http.protocol.HttpContext;

public class HttpAuthenticator {

    private final Log log;

    public HttpAuthenticator(final Log log) {
        super();
        this.log = log != null ? log : LogFactory.getLog(getClass());
    }

    public HttpAuthenticator() {
        this(null);
    }

    public boolean isAuthenticationRequested(
            final HttpHost host,
            final HttpResponse response,
            final AuthenticationStrategy authStrategy,
            final AuthState authState,
            final HttpContext context) {
        if (authStrategy.isAuthenticationRequested(host, response, context)) {
            if (authState.getState() == AuthProtocolState.SUCCESS) {
                authStrategy.authFailed(host, authState.getAuthScheme(), context);
            }
            this.log.debug("Authentication required");
            return true;
        } else {
            switch (authState.getState()) {
            case CHALLENGED:
            case HANDSHAKE:
                this.log.debug("Authentication succeeded");
                authState.setState(AuthProtocolState.SUCCESS);
                authStrategy.authSucceeded(host, authState.getAuthScheme(), context);
                break;
            case SUCCESS:
                break;
            default:
                authState.setState(AuthProtocolState.UNCHALLENGED);
            }
            return false;
        }
    }

    public boolean authenticate(
            final HttpHost host,
            final HttpResponse response,
            final AuthenticationStrategy authStrategy,
            final AuthState authState,
            final HttpContext context) {
        try {
            if (this.log.isDebugEnabled()) {
                this.log.debug(host.toHostString() + " requested authentication");
            }
            Map<String, Header> challenges = authStrategy.getChallenges(host, response, context);
            if (challenges.isEmpty()) {
                this.log.debug("Response contains no authentication challenges");
                return false;
            }

            AuthScheme authScheme = authState.getAuthScheme();
            switch (authState.getState()) {
            case FAILURE:
                return false;
            case SUCCESS:
                authState.reset();
                break;
            case CHALLENGED:
            case HANDSHAKE:
                if (authScheme == null) {
                    this.log.debug("Auth scheme is null");
                    authStrategy.authFailed(host, null, context);
                    authState.reset();
                    authState.setState(AuthProtocolState.FAILURE);
                    return false;
                }
            case UNCHALLENGED:
                if (authScheme != null) {
                    String id = authScheme.getSchemeName();
                    Header challenge = challenges.get(id.toLowerCase(Locale.US));
                    if (challenge != null) {
                        this.log.debug("Authorization challenge processed");
                        authScheme.processChallenge(challenge);
                        if (authScheme.isComplete()) {
                            this.log.debug("Authentication failed");
                            authStrategy.authFailed(host, authState.getAuthScheme(), context);
                            authState.reset();
                            authState.setState(AuthProtocolState.FAILURE);
                            return false;
                        } else {
                            authState.setState(AuthProtocolState.HANDSHAKE);
                            return true;
                        }
                    } else {
                        authState.reset();
                        // Retry authentication with a different scheme
                    }
                }
            }
            Queue<AuthOption> authOptions = authStrategy.select(challenges, host, response, context);
            if (authOptions != null && !authOptions.isEmpty()) {
                if (this.log.isDebugEnabled()) {
                    this.log.debug("Selected authentication options: " + authOptions);
                }
                authState.setState(AuthProtocolState.CHALLENGED);
                authState.update(authOptions);
                return true;
            } else {
                return false;
            }
        } catch (MalformedChallengeException ex) {
            if (this.log.isWarnEnabled()) {
                this.log.warn("Malformed challenge: " +  ex.getMessage());
            }
            authState.reset();
            return false;
        }
    }

}
