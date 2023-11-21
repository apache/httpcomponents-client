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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.hc.client5.http.auth.AuthChallenge;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.AuthStateCacheable;
import org.apache.hc.client5.http.auth.AuthenticationException;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.MalformedChallengeException;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.utils.Base64;
import org.apache.hc.client5.http.utils.ByteArrayBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Basic authentication scheme.
 *
 * @since 4.0
 */
@AuthStateCacheable
public class BasicScheme implements AuthScheme, Serializable {

    private static final long serialVersionUID = -1931571557597830536L;

    private static final Logger LOG = LoggerFactory.getLogger(BasicScheme.class);

    private final Map<String, String> paramMap;
    private transient Charset defaultCharset;
    private transient ByteArrayBuilder buffer;
    private transient Base64 base64codec;
    private boolean complete;

    private UsernamePasswordCredentials credentials;

    /**
     * @deprecated This constructor is deprecated to enforce the use of {@link StandardCharsets#UTF_8} encoding
     * in compliance with RFC 7617 for HTTP Basic Authentication. Use the default constructor {@link #BasicScheme()} instead.
     *
     * @param charset the {@link Charset} set to be used for encoding credentials. This parameter is ignored as UTF-8 is always used.
     */
    @Deprecated
    public BasicScheme(final Charset charset) {
        this.paramMap = new HashMap<>();
        this.defaultCharset = StandardCharsets.UTF_8; // Always use UTF-8
        this.complete = false;
    }

    /**
     * Constructs a new BasicScheme with UTF-8 as the charset.
     *
     * @since 4.3
     */
    public BasicScheme() {
        this.paramMap = new HashMap<>();
        this.defaultCharset = StandardCharsets.UTF_8;
        this.complete = false;
    }

    public void initPreemptive(final Credentials credentials) {
        if (credentials != null) {
            Args.check(credentials instanceof UsernamePasswordCredentials,
                    "Unsupported credential type: " + credentials.getClass());
            this.credentials = (UsernamePasswordCredentials) credentials;
        } else {
            this.credentials = null;
        }
    }

    @Override
    public String getName() {
        return StandardAuthScheme.BASIC;
    }

    @Override
    public boolean isConnectionBased() {
        return false;
    }

    @Override
    public String getRealm() {
        return this.paramMap.get("realm");
    }

    @Override
    public void processChallenge(
            final AuthChallenge authChallenge,
            final HttpContext context) throws MalformedChallengeException {
        this.paramMap.clear();
        final List<NameValuePair> params = authChallenge.getParams();
        if (params != null) {
            for (final NameValuePair param: params) {
                this.paramMap.put(param.getName().toLowerCase(Locale.ROOT), param.getValue());
            }
        }
        this.complete = true;
    }

    @Override
    public boolean isChallengeComplete() {
        return this.complete;
    }

    @Override
    public boolean isResponseReady(
            final HttpHost host,
            final CredentialsProvider credentialsProvider,
            final HttpContext context) throws AuthenticationException {

        Args.notNull(host, "Auth host");
        Args.notNull(credentialsProvider, "CredentialsProvider");

        final AuthScope authScope = new AuthScope(host, getRealm(), getName());
        final Credentials credentials = credentialsProvider.getCredentials(
                authScope, context);
        if (credentials instanceof UsernamePasswordCredentials) {
            this.credentials = (UsernamePasswordCredentials) credentials;
            return true;
        }

        if (LOG.isDebugEnabled()) {
            final HttpClientContext clientContext = HttpClientContext.adapt(context);
            final String exchangeId = clientContext.getExchangeId();
            LOG.debug("{} No credentials found for auth scope [{}]", exchangeId, authScope);
        }
        this.credentials = null;
        return false;
    }

    @Override
    public Principal getPrincipal() {
        return null;
    }

    private void validateUsername() throws AuthenticationException {
        if (credentials == null) {
            throw new AuthenticationException("User credentials not set");
        }
        final String username = credentials.getUserName();
        for (int i = 0; i < username.length(); i++) {
            final char ch = username.charAt(i);
            if (Character.isISOControl(ch)) {
                throw new AuthenticationException("Username must not contain any control characters");
            }
            if (ch == ':') {
                throw new AuthenticationException("Username contains a colon character and is invalid");
            }
        }
    }

    private void validatePassword() throws AuthenticationException {
        if (credentials == null) {
            throw new AuthenticationException("User credentials not set");
        }
        final char[] password = credentials.getUserPassword();
        if (password != null) {
            for (final char ch : password) {
                if (Character.isISOControl(ch)) {
                    throw new AuthenticationException("Password must not contain any control characters");
                }
            }
        }
    }

    @Override
    public String generateAuthResponse(
            final HttpHost host,
            final HttpRequest request,
            final HttpContext context) throws AuthenticationException {
        validateUsername();
        validatePassword();
        if (this.buffer == null) {
            this.buffer = new ByteArrayBuilder(64);
        } else {
            this.buffer.reset();
        }
        final Charset charset = AuthSchemeSupport.parseCharset(paramMap.get("charset"), defaultCharset);
        this.buffer.charset(charset);
        this.buffer.append(this.credentials.getUserName()).append(":").append(this.credentials.getUserPassword());
        if (this.base64codec == null) {
            this.base64codec = new Base64();
        }
        final byte[] encodedCreds = this.base64codec.encode(this.buffer.toByteArray());
        this.buffer.reset();
        return StandardAuthScheme.BASIC + " " + new String(encodedCreds, 0, encodedCreds.length, StandardCharsets.US_ASCII);
    }

    private void writeObject(final ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        out.writeUTF(this.defaultCharset.name());
    }

    @SuppressWarnings("unchecked")
    private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        try {
            this.defaultCharset = Charset.forName(in.readUTF());
        } catch (final UnsupportedCharsetException ex) {
            this.defaultCharset = StandardCharsets.UTF_8;
        }
    }

    private void readObjectNoData() {
    }

    @Override
    public String toString() {
        return getName() + this.paramMap;
    }

}
