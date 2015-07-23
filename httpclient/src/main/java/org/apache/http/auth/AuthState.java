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
package org.apache.http.auth;

import java.util.Queue;

import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.util.Args;

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

    /** Available auth options */
    private Queue<AuthScheme> authOptions;

    public AuthState() {
        super();
        this.state = AuthProtocolState.UNCHALLENGED;
    }

    /**
     * Resets the auth state.
     *
     * @since 4.2
     */
    public void reset() {
        this.state = AuthProtocolState.UNCHALLENGED;
        this.authOptions = null;
        this.authScheme = null;
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
     * Returns actual {@link AuthScheme}. May be null.
     */
    public AuthScheme getAuthScheme() {
        return this.authScheme;
    }

    /**
     * Updates the auth state with {@link AuthScheme} and clears auth options.
     *
     * @param authScheme auth scheme. May not be null.
     *
     * @since 4.2
     */
    public void update(final AuthScheme authScheme) {
        Args.notNull(authScheme, "Auth scheme");
        this.authScheme = authScheme;
        this.authOptions = null;
    }

    /**
     * Returns available auth options. May be null.
     *
     * @since 4.2
     */
    public Queue<AuthScheme> getAuthOptions() {
        return this.authOptions;
    }

    /**
     * Updates the auth state with a queue of auth options.
     *
     * @param authOptions a queue of auth options. May not be null or empty.
     *
     * @since 4.2
     */
    public void update(final Queue<AuthScheme> authOptions) {
        Args.notEmpty(authOptions, "Queue of auth options");
        this.authOptions = authOptions;
        this.authScheme = null;
    }

    @Override
    public String toString() {
        final StringBuilder buffer = new StringBuilder();
        buffer.append("[").append(this.state);
        if (this.authScheme != null) {
            buffer.append(" ").append(this.authScheme);
        }
        buffer.append("]");
        return buffer.toString();
    }

}
