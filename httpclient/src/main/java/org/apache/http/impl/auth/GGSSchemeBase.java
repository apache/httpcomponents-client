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
 */

package org.apache.http.impl.auth;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.auth.AUTH;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.ContextAwareAuthScheme;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.InvalidCredentialsException;
import org.apache.http.auth.MalformedChallengeException;
import org.apache.http.message.BufferedHeader;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.CharArrayBuffer;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;

/**
 * @since 4.2
 */
public abstract class GGSSchemeBase extends AuthSchemeBase {

    enum State {
        UNINITIATED,
        CHALLENGE_RECEIVED,
        TOKEN_GENERATED,
        FAILED,
    }

    private final Log log = LogFactory.getLog(getClass());

    private final Base64 base64codec;
    private final boolean stripPort;

    /** Authentication process state */
    private State state;

    /** base64 decoded challenge **/
    private byte[] token;

    GGSSchemeBase(boolean stripPort) {
        super();
        this.base64codec = new Base64(0);
        this.stripPort = stripPort;
        this.state = State.UNINITIATED;
    }

    GGSSchemeBase() {
        this(false);
    }

    protected GSSManager getManager() {
        return GSSManager.getInstance();
    }

    protected byte[] generateGSSToken(
            final byte[] input, final Oid oid, final String authServer) throws GSSException {
        byte[] token = input;
        if (token == null) {
            token = new byte[0];
        }
        GSSManager manager = getManager();
        GSSName serverName = manager.createName("HTTP@" + authServer, GSSName.NT_HOSTBASED_SERVICE);
        GSSContext gssContext = manager.createContext(
                serverName.canonicalize(oid), oid, null, GSSContext.DEFAULT_LIFETIME);
        gssContext.requestMutualAuth(true);
        gssContext.requestCredDeleg(true);
        return gssContext.initSecContext(token, 0, token.length);
    }

    protected abstract byte[] generateToken(
            byte[] input, final String authServer) throws GSSException;

    public boolean isComplete() {
        return this.state == State.TOKEN_GENERATED || this.state == State.FAILED;
    }

    /**
     * @deprecated (4.2) Use {@link ContextAwareAuthScheme#authenticate(Credentials, HttpRequest, org.apache.http.protocol.HttpContext)}
     */
    @Deprecated
    public Header authenticate(
            final Credentials credentials,
            final HttpRequest request) throws AuthenticationException {
        return authenticate(credentials, request, null);
    }

    @Override
    public Header authenticate(
            final Credentials credentials,
            final HttpRequest request,
            final HttpContext context) throws AuthenticationException {
        if (request == null) {
            throw new IllegalArgumentException("HTTP request may not be null");
        }
        switch (state) {
        case UNINITIATED:
            throw new AuthenticationException(getSchemeName() + " authentication has not been initiated");
        case FAILED:
            throw new AuthenticationException(getSchemeName() + " authentication has failed");
        case CHALLENGE_RECEIVED:
            try {
                String key = null;
                if (isProxy()) {
                    key = ExecutionContext.HTTP_PROXY_HOST;
                } else {
                    key = ExecutionContext.HTTP_TARGET_HOST;
                }
                HttpHost host = (HttpHost) context.getAttribute(key);
                if (host == null) {
                    throw new AuthenticationException("Authentication host is not set " +
                            "in the execution context");
                }
                String authServer;
                if (!this.stripPort && host.getPort() > 0) {
                    authServer = host.toHostString();
                } else {
                    authServer = host.getHostName();
                }

                if (log.isDebugEnabled()) {
                    log.debug("init " + authServer);
                }
                token = generateToken(token, authServer);
                state = State.TOKEN_GENERATED;
            } catch (GSSException gsse) {
                state = State.FAILED;
                if (gsse.getMajor() == GSSException.DEFECTIVE_CREDENTIAL
                        || gsse.getMajor() == GSSException.CREDENTIALS_EXPIRED)
                    throw new InvalidCredentialsException(gsse.getMessage(), gsse);
                if (gsse.getMajor() == GSSException.NO_CRED )
                    throw new InvalidCredentialsException(gsse.getMessage(), gsse);
                if (gsse.getMajor() == GSSException.DEFECTIVE_TOKEN
                        || gsse.getMajor() == GSSException.DUPLICATE_TOKEN
                        || gsse.getMajor() == GSSException.OLD_TOKEN)
                    throw new AuthenticationException(gsse.getMessage(), gsse);
                // other error
                throw new AuthenticationException(gsse.getMessage());
            }
        case TOKEN_GENERATED:
            String tokenstr = new String(base64codec.encode(token));
            if (log.isDebugEnabled()) {
                log.debug("Sending response '" + tokenstr + "' back to the auth server");
            }
            CharArrayBuffer buffer = new CharArrayBuffer(32);
            if (isProxy()) {
                buffer.append(AUTH.PROXY_AUTH_RESP);
            } else {
                buffer.append(AUTH.WWW_AUTH_RESP);
            }
            buffer.append(": Negotiate ");
            buffer.append(tokenstr);
            return new BufferedHeader(buffer);
        default:
            throw new IllegalStateException("Illegal state: " + state);
        }
    }

    @Override
    protected void parseChallenge(
            final CharArrayBuffer buffer,
            int beginIndex, int endIndex) throws MalformedChallengeException {
        String challenge = buffer.substringTrimmed(beginIndex, endIndex);
        if (log.isDebugEnabled()) {
            log.debug("Received challenge '" + challenge + "' from the auth server");
        }
        if (state == State.UNINITIATED) {
            token = Base64.decodeBase64(challenge.getBytes());
            state = State.CHALLENGE_RECEIVED;
        } else {
            log.debug("Authentication already attempted");
            state = State.FAILED;
        }
    }

}
