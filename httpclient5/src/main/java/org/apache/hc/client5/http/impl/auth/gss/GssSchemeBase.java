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
package org.apache.hc.client5.http.impl.auth.gss;

import java.net.UnknownHostException;
import java.security.Principal;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.auth.AuthChallenge;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.AuthenticationException;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.InvalidCredentialsException;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.auth.gss.GssConfig;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.utils.Base64;
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
 * Common behaviour for the new mutual authentication capable {@code GSS} based authentication
 * schemes.
 *
 * This class is derived from the old {@link org.apache.hc.client5.http.impl.auth.GGSSchemeBase}
 * class, which was deprecated in 5.3.
 *
 * @since 5.6
 *
 * @see GGSSchemeBase
 */
public abstract class GssSchemeBase implements AuthScheme {

    enum State {
        UNINITIATED,
        TOKEN_READY,
        TOKEN_SENT,
        SUCCEEDED,
        FAILED,
    }

    private static final Logger LOG = LoggerFactory.getLogger(GssSchemeBase.class);
    private static final String PEER_SERVICE_NAME = "HTTP";

    // The GSS spec does not specify how long the conversation can be. This should be plenty.
    // Realistically, we get one initial token, then one maybe one more for mutual authentication.
    // TODO In the future this might need to be configurable with the upcoming IAKerb support
    private static final int MAX_GSS_CHALLENGES = 3;
    private final GssConfig config;
    private final DnsResolver dnsResolver;
    private final boolean requireMutualAuth;
    private final boolean ignoreIncompleteSecurityContext;
    private int challengesLeft = MAX_GSS_CHALLENGES;

    /** Authentication process state */
    private State state;
    private GSSCredential gssCredential;
    private GSSContext gssContext;
    private String challenge;
    private byte[] queuedToken = new byte[0];

    GssSchemeBase(final GssConfig config, final DnsResolver dnsResolver) {
        super();
        this.config = config != null ? config : GssConfig.DEFAULT;
        this.dnsResolver = dnsResolver != null ? dnsResolver : SystemDefaultDnsResolver.INSTANCE;
        this.requireMutualAuth = config.isRequireMutualAuth();
        this.ignoreIncompleteSecurityContext = config.isIgnoreIncompleteSecurityContext();
        this.state = State.UNINITIATED;
    }

    private void dispose() {
        // remove sensitive information from memory
        // cleaning up the credential is the caller's job
        try {
            if (gssContext != null) {
                gssContext.dispose();
            }
        } catch (final Exception e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Exception caught while calling gssContext.dispose()", e);
            }
        }
    }

    GssSchemeBase(final GssConfig config) {
        this(config, SystemDefaultDnsResolver.INSTANCE);
    }

    GssSchemeBase() {
        this(GssConfig.DEFAULT, SystemDefaultDnsResolver.INSTANCE);
    }

    @Override
    public String getRealm() {
        return null;
    }

    // Required by AuthScheme for backwards compatibility
    @Override
    public void processChallenge(final AuthChallenge authChallenge,
            final HttpContext context ) {
        // If this gets called, then AuthScheme was changed in an incompatible way
        throw new UnsupportedOperationException();
    }

    // The AuthScheme API maps awkwardly to GSSAPI, where proccessChallange and generateAuthResponse
    // map to the same single method call. Hence the generated token is only stored in this method.
    @Override
    public void processChallenge(
            final HttpHost host,
            final boolean challenged,
            final AuthChallenge authChallenge,
            final HttpContext context
            ) throws AuthenticationException {

        if (challengesLeft-- <= 0 ) {
            if (LOG.isWarnEnabled()) {
                final HttpClientContext clientContext = HttpClientContext.cast(context);
                final String exchangeId = clientContext.getExchangeId();
                LOG.warn("{} GSS error: too many challenges received. Infinite loop ?", exchangeId);
            }
            state = State.FAILED;
            return;
        }

        final byte[] challengeToken = (authChallenge == null) ? null : Base64.decodeBase64(authChallenge.getValue());

        final String gssHostname;
        String hostname = host.getHostName();
        if (config.isUseCanonicalHostname()) {
            try {
                 hostname = dnsResolver.resolveCanonicalHostname(host.getHostName());
            } catch (final UnknownHostException ignore) {
                if (LOG.isWarnEnabled()) {
                    final HttpClientContext clientContext = HttpClientContext.cast(context);
                    final String exchangeId = clientContext.getExchangeId();
                    LOG.warn("{} Could not canonicalize hostname {}, using as is.", exchangeId, host.getHostName());
                }
            }
        }
        if (config.isAddPort()) {
            gssHostname = hostname + ":" + host.getPort();
        } else {
            gssHostname = hostname;
        }

        if (LOG.isDebugEnabled()) {
            final HttpClientContext clientContext = HttpClientContext.cast(context);
            final String exchangeId = clientContext.getExchangeId();
            LOG.debug("{} GSS init {}", exchangeId, gssHostname);
        }
        try {
            switch (state) {
            case UNINITIATED:
                setGssCredential(HttpClientContext.cast(context).getCredentialsProvider(), host, context);
                if (challengeToken == null) {
                    queuedToken = generateToken(challengeToken, PEER_SERVICE_NAME, gssHostname);
                    state = State.TOKEN_READY;
                } else {
                    if (LOG.isDebugEnabled()) {
                        final HttpClientContext clientContext = HttpClientContext.cast(context);
                        final String exchangeId = clientContext.getExchangeId();
                        LOG.debug("{} Internal GSS error: token received when none was sent yet: {}", exchangeId, challengeToken);
                    }
                    state = State.FAILED;
                }
                break;
            case TOKEN_SENT:
                if (challengeToken == null) {
                    if (!challenged && ignoreIncompleteSecurityContext) {
                        // Got a Non 401/407 code without a challenge. Old non RFC compliant server.
                        if (LOG.isWarnEnabled()) {
                            final HttpClientContext clientContext = HttpClientContext.cast(context);
                            final String exchangeId = clientContext.getExchangeId();
                            LOG.warn("{} GSS Context is not established, but continuing because GssConfig.ignoreIncompleteSecurityContext is true.", exchangeId);
                        }
                        state = State.SUCCEEDED;
                        break;
                    } else {
                        if (LOG.isDebugEnabled()) {
                            final HttpClientContext clientContext = HttpClientContext.cast(context);
                            final String exchangeId = clientContext.getExchangeId();
                            LOG.debug("{} Did not receive required challenge.",
                                exchangeId);
                        }
                        state = State.FAILED;
                        throw new AuthenticationException(
                                "Did not receive required challenge.");
                    }
                }
                queuedToken = generateToken(challengeToken, PEER_SERVICE_NAME, gssHostname);
                if (challenged) {
                    state = State.TOKEN_READY;
                } else if (!gssContext.isEstablished()) {
                    if (LOG.isDebugEnabled()) {
                        final HttpClientContext clientContext = HttpClientContext.cast(context);
                        final String exchangeId = clientContext.getExchangeId();
                        LOG.debug("{} GSSContext is not established.", exchangeId);
                    }
                    state = State.FAILED;
                    // TODO should we have specific exception(s) for these ?
                    throw new AuthenticationException(
                            "GSSContext is not established.");
                } else if (!gssContext.getMutualAuthState()) {
                    if (requireMutualAuth) {
                        if (LOG.isDebugEnabled()) {
                            final HttpClientContext clientContext = HttpClientContext.cast(context);
                            final String exchangeId = clientContext.getExchangeId();
                            LOG.debug("{} requireMutualAuth is true but GSSContext mutualAuthState is false",
                                exchangeId);
                        }
                        state = State.FAILED;
                        throw new AuthenticationException(
                                "requireMutualAuth is true but GSSContext mutualAuthState is false");
                    } else {
                        if (LOG.isDebugEnabled()) {
                            final HttpClientContext clientContext = HttpClientContext.cast(context);
                            final String exchangeId = clientContext.getExchangeId();
                            LOG.debug("{} GSSContext MutualAuthState is false, but continuing because GssConfig.requireMutualAuth is false.",
                                exchangeId);
                        }
                        state = State.SUCCEEDED;
                    }
                } else {
                    state = State.SUCCEEDED;
                }
                break;
            default:
                final State prevState = state;
                state = State.FAILED;
                throw new IllegalStateException("Illegal state: " + prevState);
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
        } finally {
            if ((state == State.FAILED || state == State.SUCCEEDED) && gssContext != null) {
                dispose();
            }
        }
    }

    protected GSSManager getManager() {
        return GSSManager.getInstance();
    }

    /**
     * @since 4.4
     */
    protected byte[] generateGSSToken(
            final byte[] input, final Oid oid, final String gssServiceName, final String gssHostname) throws GSSException {
        final GSSManager manager = getManager();
        final GSSName peerName = manager.createName(gssServiceName + "@" + gssHostname, GSSName.NT_HOSTBASED_SERVICE);

        if (gssContext == null) {
            gssContext = createGSSContext(manager, oid, peerName, gssCredential);
        }
        if (input != null) {
            return gssContext.initSecContext(input, 0, input.length);
        }
        return gssContext.initSecContext(new byte[] {}, 0, 0);
    }

    /**
     * @since 5.0
     */
    protected GSSContext createGSSContext(
            final GSSManager manager,
            final Oid oid,
            final GSSName peerName,
            final GSSCredential gssCredential) throws GSSException {
        final GSSContext gssContext = manager.createContext(peerName.canonicalize(oid), oid, gssCredential,
                GSSContext.DEFAULT_LIFETIME);
        gssContext.requestMutualAuth(config.isRequestMutualAuth());
        gssContext.requestCredDeleg(config.isRequestDelegCreds());
        return gssContext;
    }

    /**
     * @since 4.4
     */
    protected abstract byte[] generateToken(byte[] input, String gssServiceName, String gssHostname) throws GSSException;

    @Override
    public boolean isChallengeComplete() {
        // For the mutual authentication response, this is should technically return true.
        // However, the HttpAuthenticator immediately fails the authentication
        // process if we return true, so we only return true here if the authentication has failed.
        return this.state == State.FAILED;
    }

    @Override
    public boolean isChallengeExpected() {
        return state == State.TOKEN_SENT;
    }

    @Override
    public boolean isResponseReady(
            final HttpHost host,
            final CredentialsProvider credentialsProvider,
            final HttpContext context) throws AuthenticationException {

        Args.notNull(host, "Auth host");
        Args.notNull(credentialsProvider, "CredentialsProvider");

        return true;
    }

    protected void setGssCredential(final CredentialsProvider credentialsProvider,
            final HttpHost host,
            final HttpContext context) {
        final Credentials credentials =
                credentialsProvider.getCredentials(new AuthScope(host, null, getName()), context);
        if (credentials instanceof org.apache.hc.client5.http.auth.gss.GssCredentials) {
            this.gssCredential =
                    ((org.apache.hc.client5.http.auth.gss.GssCredentials) credentials)
                            .getGSSCredential();
        } else {
            this.gssCredential = null;
        }
    }

    @Override
    public Principal getPrincipal() {
        return null;
    }

    // Format the queued token and update the state.
    // All token processing is done in processChallenge()
    @Override
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
                final HttpClientContext clientContext = HttpClientContext.cast(context);
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
