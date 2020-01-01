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
import java.util.Locale;

import org.apache.commons.codec.binary.Base64;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.auth.AuthChallenge;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.AuthenticationException;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.InvalidCredentialsException;
import org.apache.hc.client5.http.auth.KerberosConfig;
import org.apache.hc.client5.http.auth.KerberosCredentials;
import org.apache.hc.client5.http.auth.MalformedChallengeException;
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
public abstract class GGSSchemeBase implements AuthScheme {

    enum State {
        UNINITIATED,
        CHALLENGE_RECEIVED,
        TOKEN_GENERATED,
        FAILED,
    }

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final KerberosConfig config;
    private final DnsResolver dnsResolver;

    /** Authentication process state */
    private State state;
    private GSSCredential gssCredential;
    private String challenge;
    private byte[] token;

    GGSSchemeBase(final KerberosConfig config, final DnsResolver dnsResolver) {
        super();
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
    public void processChallenge(
            final AuthChallenge authChallenge,
            final HttpContext context) throws MalformedChallengeException {
        Args.notNull(authChallenge, "AuthChallenge");
        if (authChallenge.getValue() == null) {
            throw new MalformedChallengeException("Missing auth challenge");
        }
        this.challenge = authChallenge.getValue();
        if (state == State.UNINITIATED) {
            token = Base64.decodeBase64(challenge.getBytes());
            state = State.CHALLENGE_RECEIVED;
        } else {
            log.debug("Authentication already attempted");
            state = State.FAILED;
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

        final GSSContext gssContext = createGSSContext(manager, oid, serverName, gssCredential);
        if (input != null) {
            return gssContext.initSecContext(input, 0, input.length);
        } else {
            return gssContext.initSecContext(new byte[] {}, 0, 0);
        }
    }

    /**
     * @since 5.0
     */
    protected GSSContext createGSSContext(
            final GSSManager manager,
            final Oid oid,
            final GSSName serverName,
            final GSSCredential gssCredential) throws GSSException {
        final GSSContext gssContext = manager.createContext(serverName.canonicalize(oid), oid, gssCredential,
                GSSContext.DEFAULT_LIFETIME);
        gssContext.requestMutualAuth(true);
        if (config.getRequestDelegCreds() != KerberosConfig.Option.DEFAULT) {
            gssContext.requestCredDeleg(config.getRequestDelegCreds() == KerberosConfig.Option.ENABLE);
        }
        return gssContext;
    }
    /**
     * @since 4.4
     */
    protected abstract byte[] generateToken(byte[] input, String serviceName, String authServer) throws GSSException;

    @Override
    public boolean isChallengeComplete() {
        return this.state == State.TOKEN_GENERATED || this.state == State.FAILED;
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
        case CHALLENGE_RECEIVED:
            try {
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
                final String serviceName = host.getSchemeName().toUpperCase(Locale.ROOT);

                if (log.isDebugEnabled()) {
                    log.debug("init " + authServer);
                }
                token = generateToken(token, serviceName, authServer);
                state = State.TOKEN_GENERATED;
            } catch (final GSSException gsse) {
                state = State.FAILED;
                if (gsse.getMajor() == GSSException.DEFECTIVE_CREDENTIAL
                        || gsse.getMajor() == GSSException.CREDENTIALS_EXPIRED) {
                    throw new InvalidCredentialsException(gsse.getMessage(), gsse);
                }
                if (gsse.getMajor() == GSSException.NO_CRED ) {
                    throw new InvalidCredentialsException(gsse.getMessage(), gsse);
                }
                if (gsse.getMajor() == GSSException.DEFECTIVE_TOKEN
                        || gsse.getMajor() == GSSException.DUPLICATE_TOKEN
                        || gsse.getMajor() == GSSException.OLD_TOKEN) {
                    throw new AuthenticationException(gsse.getMessage(), gsse);
                }
                // other error
                throw new AuthenticationException(gsse.getMessage());
            }
        case TOKEN_GENERATED:
            final Base64 codec = new Base64(0);
            final String tokenstr = new String(codec.encode(token));
            if (log.isDebugEnabled()) {
                log.debug("Sending response '" + tokenstr + "' back to the auth server");
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
