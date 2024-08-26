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

import java.net.UnknownHostException;
import java.security.Principal;

import org.apache.hc.client5.http.utils.Base64;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.auth.AuthChallenge;
import org.apache.hc.client5.http.auth.AuthSchemeV2;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.AuthenticationException;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.InvalidCredentialsException;
import org.apache.hc.client5.http.auth.KerberosConfig;
import org.apache.hc.client5.http.auth.KerberosConfig.Option;
import org.apache.hc.client5.http.auth.KerberosCredentials;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common behavior for {@code GSS} based authentication schemes.
 *
 * @since 4.2
 */
public abstract class GGSSchemeBase implements AuthSchemeV2 {

    enum State {
        UNINITIATED,
        TOKEN_READY,
        TOKEN_SENT,
        SUCCEEDED,
        FAILED,
    }

    private static final Logger LOG = LoggerFactory.getLogger(GGSSchemeBase.class);
    private static final String NO_TOKEN = "";
    private static final String KERBEROS_SCHEME = "HTTP";
    // The GSS spec does not specify how long the conversation can be.
    // Realistically, we get one initial challenge, then one for mutual auth.
    private static final int MAX_GSS_CHALLENGES = 3;
    private final KerberosConfig config;
    private final DnsResolver dnsResolver;
    private int challengesLeft=MAX_GSS_CHALLENGES;

    /** Authentication process state */
    private State state;
    private GSSCredential gssCredential;
    protected GSSContext gssContext;
    // Object field to include in toString()
    private String challenge;
    //empty array if no token is queued
    private byte[] queuedToken = new byte[0];

    GGSSchemeBase(final KerberosConfig config, final DnsResolver dnsResolver) {

        super();
//        LOG.error("GGSSchemeBase constructor called", new Exception());

        this.config = config != null ? config : KerberosConfig.DEFAULT;
        this.dnsResolver = dnsResolver != null ? dnsResolver : SystemDefaultDnsResolver.INSTANCE;
        this.state = State.UNINITIATED;
    }

    GGSSchemeBase(final KerberosConfig config) {
        this(config, SystemDefaultDnsResolver.INSTANCE);
    }

    GGSSchemeBase() {
        this(KerberosConfig.DEFAULT, SystemDefaultDnsResolver.INSTANCE);
    }

    @Override
    public String getRealm() {
        return null;
    }

    @Override
    // The AuthScheme API maps awkwardly to GSSAPI, where proccessChallange and generateAuthResponse
    // map to the same single method call.
    public void processChallenge(
            final HttpHost host,
            final AuthChallenge authChallenge,
            final HttpContext context,
            final boolean authenticated) throws AuthenticationException {
        Args.notNull(authChallenge, "AuthChallenge");

        if (challengesLeft-- < 0 ) {
            final HttpClientContext clientContext = HttpClientContext.adapt(context);
            final String exchangeId = clientContext.getExchangeId();
            //Fixme check log level
            LOG.error("{} GSS error: too many challenges received. Infinite loop ?", exchangeId);
            state = State.FAILED;
            return;
        }

        this.challenge = authChallenge.getValue() != null ? authChallenge.getValue() : NO_TOKEN;
//        LOG.error("XXXX Challenge:"+ challenge);
//        LOG.error("XXXX State:"+ state);
        final HttpClientContext clientContextX = HttpClientContext.adapt(context);
        final String exchangeIdX = clientContextX.getExchangeId();
//        LOG.error("XXXX GSSSCheme:"+ this);
//        LOG.error("XXXX GSSSCheme:"+ System.identityHashCode(this));
//        LOG.error("XXXX", new Exception("for tracking"));


        final byte[] challangeToken = Base64.decodeBase64(challenge.getBytes());
        //Simplistic test case
//        byte[] challangeToken = Base64.decodeBase64(challenge.getBytes());
//
//        if (authenticated) {
//            challangeToken = new byte[0];
//        }
        
        final String authServer;
        String hostname = host.getHostName();
        if (config.getUseCanonicalHostname() != KerberosConfig.Option.DISABLE){
            try {
                 hostname = dnsResolver.resolveCanonicalHostname(host.getHostName());
            } catch (final UnknownHostException ignore){
            }
        }
        if (config.getStripPort() != KerberosConfig.Option.DISABLE) {
            authServer = hostname;
        } else {
            authServer = hostname + ":" + host.getPort();
        }

        if (LOG.isDebugEnabled()) {
            final HttpClientContext clientContext = HttpClientContext.adapt(context);
            final String exchangeId = clientContext.getExchangeId();
            LOG.debug("{} init {}", exchangeId, authServer);
        }
        try {
            queuedToken = generateToken(challangeToken, KERBEROS_SCHEME, authServer);
            switch (state) {
            case UNINITIATED:
                if (challenge != NO_TOKEN) {
                    final HttpClientContext clientContext = HttpClientContext.adapt(context);
                    final String exchangeId = clientContext.getExchangeId();
                    // This should never happen
                    LOG.error("{} Internal GSS error: inconstent state", exchangeId);
                    state = State.FAILED;
                    throw new AuthenticationException("Internal GSS error: inconstent state challenge:" +challenge);
                } else {
                    state = State.TOKEN_READY;
                }
                break;
            case TOKEN_SENT:
                if (!authenticated) {
                    state = State.TOKEN_READY;
                    // We have received a challenge, and computed the response successfully
                } else {
                    // Authenticated. We should only reach this if requestMutualAuth is set.
                    // We distinguish these cases only for the Exception message
                    if (config.getRequestMutualAuth() != Option.ENABLE) {
                        // This would be a bug in our code
                        throw new AuthenticationException(
                                "Internal error: requestMutualAuth state is inconsistent");
                    }
                    if (!gssContext.isEstablished()) {
                        // The mutual auth state was not set on the context, even though we have
                        // requested it. This catches the case when the server simply has ignored
                        // our request. If the token was sent, but could not processed, then
                        // initSecContext() in generateToken() would have already thrown an
                        // exception
                        final HttpClientContext clientContext = HttpClientContext.adapt(context);
                        final String exchangeId = clientContext.getExchangeId();
                        LOG.warn(
                            "{} GSSContext is not established ",
                            exchangeId);
                        state = State.FAILED;
                        throw new AuthenticationException(
                                "requireMutualAuth is set but GSSContext is not established");
                    }
                    if (!gssContext.getMutualAuthState()) {
                            // The mutual auth state was not set on the context, even though we have
                            // requested it. This catches the case when the server simply has ignored
                            // our request. If the token was sent, but could not processed, then
                            // initSecContext() in generateToken() would have already thrown an
                            // exception
                            final HttpClientContext clientContext = HttpClientContext.adapt(context);
                            final String exchangeId = clientContext.getExchangeId();
                            LOG.warn(
                                "{} GSSContext is not established requireMutualAuth is set",
                                exchangeId);
                            state = State.FAILED;
                            throw new AuthenticationException(
                                    "requireMutualAuth is set but GSSContext mutualAuthState is not set");
                    }
                    LOG.error("XXXX mutual auth state successfully verified");
                    state = State.SUCCEEDED;
                }
                break;
            default:
                state = State.FAILED;
                throw new IllegalStateException("Illegal state: " + state);

            }
        } catch (final GSSException gsse) {
            state = State.FAILED;
            if (gsse.getMajor() == GSSException.DEFECTIVE_CREDENTIAL
                    || gsse.getMajor() == GSSException.CREDENTIALS_EXPIRED) {
                throw new InvalidCredentialsException(gsse.getMessage(), gsse);
            }
            if (gsse.getMajor() == GSSException.NO_CRED) {
                throw new InvalidCredentialsException(gsse.getMessage(), gsse);
            }
            if (gsse.getMajor() == GSSException.DEFECTIVE_TOKEN
                    || gsse.getMajor() == GSSException.DUPLICATE_TOKEN
                    || gsse.getMajor() == GSSException.OLD_TOKEN) {
                throw new AuthenticationException(gsse.getMessage(), gsse);
            }
            // other error
            throw new AuthenticationException(gsse.getMessage(), gsse);
        }
    }

    protected GSSManager getManager() {
        return GSSManager.getInstance();
    }

    /**
     * @since 4.4
     */
    protected byte[] generateGSSToken(
            final byte[] input, final Oid oid, final String serviceName, final String authServer) throws GSSException {
        final GSSManager manager = getManager();
        final GSSName serverName = manager.createName(serviceName + "@" + authServer, GSSName.NT_HOSTBASED_SERVICE);

        gssContext = getGSSContext(manager, oid, serverName, gssCredential);
        if (input != null) {
            return gssContext.initSecContext(input, 0, input.length);
        } else {
            return gssContext.initSecContext(new byte[] {}, 0, 0);
        }
    }

    /**
     * @since 5.0
     */
    protected GSSContext getGSSContext(
            final GSSManager manager,
            final Oid oid,
            final GSSName serverName,
            final GSSCredential gssCredential) throws GSSException {
        //TODO Could/Should we create this in the constructor ?
        if (gssContext != null) {
            return gssContext;
        }
        final GSSContext gssContext = manager.createContext(serverName.canonicalize(oid), oid, gssCredential,
                GSSContext.DEFAULT_LIFETIME);
        gssContext.requestMutualAuth(true);
        if (config.getRequestDelegCreds() != KerberosConfig.Option.DEFAULT) {
            gssContext.requestCredDeleg(config.getRequestDelegCreds() == KerberosConfig.Option.ENABLE);
        }
        if (config.getRequestMutualAuth() != KerberosConfig.Option.DEFAULT) {
            gssContext.requestMutualAuth(config.getRequestMutualAuth() == KerberosConfig.Option.ENABLE);
        }
        return gssContext;
    }
    /**
     * @since 4.4
     */
    protected abstract byte[] generateToken(byte[] input, String serviceName, String authServer) throws GSSException;

    @Override
    public boolean isChallengeComplete() {
        // For the mutual auth case, this is should technically return true.
        // However, the de-facto behaviour is that returning true immediately fails the authentication
        // process, so we only return if the auth has already failed.
        return this.state == State.FAILED;
    }

    @Override
    public boolean isChallengeExpected() {
        return state == State.TOKEN_SENT && config.getRequestMutualAuth() == Option.ENABLE;
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
        if (credentials instanceof KerberosCredentials) {
            this.gssCredential = ((KerberosCredentials) credentials).getGSSCredential();
        } else {
            this.gssCredential = null;
        }
        return true;
    }

    @Override
    public Principal getPrincipal() {
        return null;
    }

    @Override
    // Format the queued token and update the state.
    // All processing is done in processChallenge()
    public String generateAuthResponse(
            final HttpHost host,
            final HttpRequest request,
            final HttpContext context) throws AuthenticationException {
        Args.notNull(host, "HTTP host");
        Args.notNull(request, "HTTP request");
        switch (state) {
        case UNINITIATED:
            throw new AuthenticationException(getName() + " authentication has not been initiated");
        case FAILED:
            throw new AuthenticationException(getName() + " authentication has failed");
        case SUCCEEDED:
            return null;
        case TOKEN_READY:
            state = State.TOKEN_SENT;
            final Base64 codec = new Base64(0);
            final String tokenstr = new String(codec.encode(queuedToken));
            if (LOG.isDebugEnabled()) {
                final HttpClientContext clientContext = HttpClientContext.adapt(context);
                final String exchangeId = clientContext.getExchangeId();
                LOG.debug("{} Sending GSS response '{}' back to the auth server", exchangeId, tokenstr);
            }
            return StandardAuthScheme.SPNEGO + " " + tokenstr;
        default:
            throw new IllegalStateException("Illegal state: " + state);
        }
    }

    @Override
    public String toString() {
        return getName() + "{" + this.state + " " + challenge + '}';
    }

}
