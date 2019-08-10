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

import java.util.Queue;

import org.apache.hc.core5.util.Args;

/**
 * This class represents the actual state of authentication handshake including the current {@link AuthScheme}
 * used for request authorization as well as a collection of backup authentication options if available.
 *
 * @since 4.5
 */
public class AuthExchange {

    public enum State {

        UNCHALLENGED, CHALLENGED, HANDSHAKE, FAILURE, SUCCESS

    }

    private State state;
    private AuthScheme authScheme;
    private Queue<AuthScheme> authOptions;

    public AuthExchange() {
        super();
        this.state = State.UNCHALLENGED;
    }

    public void reset() {
        this.state = State.UNCHALLENGED;
        this.authOptions = null;
        this.authScheme = null;
    }

    public State getState() {
        return this.state;
    }

    public void setState(final State state) {
        this.state = state != null ? state : State.UNCHALLENGED;
    }

    /**
     * Returns actual {@link AuthScheme}. May be null.
     */
    public AuthScheme getAuthScheme() {
        return this.authScheme;
    }

    /**
     * Returns {@code true} if the actual authentication scheme is connection based.
     */
    public boolean isConnectionBased() {
        return this.authScheme != null && this.authScheme.isConnectionBased();
    }

    /**
     * Resets the auth state with {@link AuthScheme} and clears auth options.
     *
     * @param authScheme auth scheme. May not be null.
     */
    public void select(final AuthScheme authScheme) {
        Args.notNull(authScheme, "Auth scheme");
        this.authScheme = authScheme;
        this.authOptions = null;
    }

    /**
     * Returns available auth options. May be null.
     */
    public Queue<AuthScheme> getAuthOptions() {
        return this.authOptions;
    }

    /**
     * Updates the auth state with a queue of auth options.
     *
     * @param authOptions a queue of auth options. May not be null or empty.
     */
    public void setOptions(final Queue<AuthScheme> authOptions) {
        Args.notEmpty(authOptions, "Queue of auth options");
        this.authOptions = authOptions;
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
