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

import java.security.Principal;

import org.apache.hc.client5.http.auth.AuthChallenge;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.AuthenticationException;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.MalformedChallengeException;
import org.apache.hc.client5.http.auth.NTCredentials;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;

/**
 * NTLM is a proprietary authentication scheme developed by Microsoft
 * and optimized for Windows platforms.
 *
 * @since 4.0
 */
public final class NTLMScheme implements AuthScheme {

    enum State {
        UNINITIATED,
        CHALLENGE_RECEIVED,
        MSG_TYPE1_GENERATED,
        MSG_TYPE2_RECEVIED,
        MSG_TYPE3_GENERATED,
        FAILED,
    }

    private final NTLMEngine engine;

    private State state;
    private String challenge;
    private NTCredentials credentials;

    public NTLMScheme(final NTLMEngine engine) {
        super();
        Args.notNull(engine, "NTLM engine");
        this.engine = engine;
        this.state = State.UNINITIATED;
    }

    /**
     * @since 4.3
     */
    public NTLMScheme() {
        this(new NTLMEngineImpl());
    }

    @Override
    public String getName() {
        return StandardAuthScheme.NTLM;
    }

    @Override
    public boolean isConnectionBased() {
        return true;
    }

    @Override
    public String getRealm() {
        return null;
    }

    @Override
    public void processChallenge(
            final AuthChallenge authChallenge,
            final HttpContext context) throws MalformedChallengeException {
        Args.notNull(authChallenge, "AuthChallenge");

        this.challenge = authChallenge.getValue();
        if (this.challenge == null || this.challenge.isEmpty()) {
            if (this.state == State.UNINITIATED) {
                this.state = State.CHALLENGE_RECEIVED;
            } else {
                this.state = State.FAILED;
            }
        } else {
            if (this.state.compareTo(State.MSG_TYPE1_GENERATED) < 0) {
                this.state = State.FAILED;
                throw new MalformedChallengeException("Out of sequence NTLM response message");
            } else if (this.state == State.MSG_TYPE1_GENERATED) {
                this.state = State.MSG_TYPE2_RECEVIED;
            }
        }
    }

    @Override
    public boolean isResponseReady(
            final HttpHost host,
            final CredentialsProvider credentialsProvider,
            final HttpContext context) throws AuthenticationException {

        Args.notNull(host, "Auth host");
        Args.notNull(credentialsProvider, "CredentialsProvider");

        final Credentials credentials = credentialsProvider.getCredentials(
                new AuthScope(host, null, getName()), context);
        if (credentials instanceof NTCredentials) {
            this.credentials = (NTCredentials) credentials;
            return true;
        }
        return false;
    }

    @Override
    public Principal getPrincipal() {
        return this.credentials != null ? this.credentials.getUserPrincipal() : null;
    }

    @Override
    public String generateAuthResponse(
            final HttpHost host,
            final HttpRequest request,
            final HttpContext context) throws AuthenticationException {
        if (this.credentials == null) {
            throw new AuthenticationException("NT credentials not available");
        }
        final String response;
        if (this.state == State.FAILED) {
            throw new AuthenticationException("NTLM authentication failed");
        } else if (this.state == State.CHALLENGE_RECEIVED) {
            response = this.engine.generateType1Msg(
                    this.credentials.getNetbiosDomain(),
                    this.credentials.getWorkstation());
            this.state = State.MSG_TYPE1_GENERATED;
        } else if (this.state == State.MSG_TYPE2_RECEVIED) {
            response = this.engine.generateType3Msg(
                    this.credentials.getUserName(),
                    this.credentials.getPassword(),
                    this.credentials.getNetbiosDomain(),
                    this.credentials.getWorkstation(),
                    this.challenge);
            this.state = State.MSG_TYPE3_GENERATED;
        } else {
            throw new AuthenticationException("Unexpected state: " + this.state);
        }
        return StandardAuthScheme.NTLM + " " + response;
    }

    @Override
    public boolean isChallengeComplete() {
        return this.state == State.MSG_TYPE3_GENERATED || this.state == State.FAILED;
    }

    @Override
    public String toString() {
        return getName() + "{" + this.state + " " + challenge + '}';
    }

}
