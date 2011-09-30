/*
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.auth;

import java.util.Queue;

import org.apache.http.annotation.NotThreadSafe;


/**
 * This class provides detailed information about the state of the authentication process.
 *
 * @since 4.0
 */
@NotThreadSafe
public class AuthState {

    /** Actual state of authentication protocol */
    private AuthProtocolState state;

    /** Actual authentication scheme */
    private AuthScheme authScheme;

    /** Actual authentication scope */
    private AuthScope authScope;

    /** Credentials selected for authentication */
    private Credentials credentials;

    private Queue<AuthOption> authOptions;

    /**
     * Default constructor.
     *
     */
    public AuthState() {
        super();
        this.state = AuthProtocolState.UNCHALLENGED;
    }

    /**
     * Invalidates the authentication state by resetting its parameters.
     */
    public void invalidate() {
        this.state = AuthProtocolState.UNCHALLENGED;
        this.authScheme = null;
        this.authScope = null;
        this.credentials = null;
    }

    @Deprecated
    public boolean isValid() {
        return this.authScheme != null;
    }

    /**
     * Assigns the given {@link AuthScheme authentication scheme}.
     *
     * @param authScheme the {@link AuthScheme authentication scheme}
     */
    public void setAuthScheme(final AuthScheme authScheme) {
        if (authScheme == null) {
            invalidate();
            return;
        }
        this.authScheme = authScheme;
    }

    /**
     * Returns the {@link AuthScheme authentication scheme}.
     *
     * @return {@link AuthScheme authentication scheme}
     */
    public AuthScheme getAuthScheme() {
        return this.authScheme;
    }

    /**
     * @since 4.2
     */
    public AuthProtocolState getState() {
        return this.state;
    }

    /**
     * @since 4.2
     */
    public void setState(final AuthProtocolState state) {
        this.state = state != null ? state : AuthProtocolState.UNCHALLENGED;
    }

    /**
     * Returns user {@link Credentials} selected for authentication if available
     *
     * @return user credentials if available, <code>null</code otherwise
     */
    public Credentials getCredentials() {
        return this.credentials;
    }

    /**
     * Sets user {@link Credentials} to be used for authentication
     *
     * @param credentials User credentials
     */
    public void setCredentials(final Credentials credentials) {
        this.credentials = credentials;
    }

    /**
     * Returns actual {@link AuthScope} if available
     *
     * @return actual authentication scope if available, <code>null</code otherwise
     *
     * @deprecated use {@link #isChallenged()}
     */
    @Deprecated
    public AuthScope getAuthScope() {
        return this.authScope;
    }

    /**
     * Sets actual {@link AuthScope}.
     *
     * @param authScope Authentication scope
     *
     * @deprecated use {@link #setChallenged()} or {@link #setUnchallenged()}.
     */
    @Deprecated
    public void setAuthScope(final AuthScope authScope) {
        this.authScope = authScope;
    }

    /**
     * Returns available authentication options.
     *
     * @return authentication options, if available, <code>null</null> otherwise.
     */
    public Queue<AuthOption> getAuthOptions() {
        return this.authOptions;
    }

    /**
     * Sets authentication options to select from when authenticating.
     *
     * @param authOptions authentication options
     */
    public void setAuthOptions(final Queue<AuthOption> authOptions) {
        this.authOptions = authOptions != null && !authOptions.isEmpty() ? authOptions : null;
    }

    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder();
        buffer.append("state:").append(this.state).append(";");
        if (this.authScheme != null) {
            buffer.append("auth scheme:").append(this.authScheme.getSchemeName()).append(";");
        }
        if (this.credentials != null) {
            buffer.append("credentials present");
        }
        return buffer.toString();
    }

}
