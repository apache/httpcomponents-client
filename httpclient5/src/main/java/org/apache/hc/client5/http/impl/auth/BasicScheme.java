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

import org.apache.commons.codec.binary.Base64;
import org.apache.hc.client5.http.auth.AuthChallenge;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.AuthStateCacheable;
import org.apache.hc.client5.http.auth.AuthenticationException;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.MalformedChallengeException;
import org.apache.hc.client5.http.utils.ByteArrayBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;

/**
 * Basic authentication scheme as defined in RFC 2617.
 *
 * @since 4.0
 */
@AuthStateCacheable
public class BasicScheme implements AuthScheme, Serializable {

    private static final long serialVersionUID = -1931571557597830536L;

    private final Map<String, String> paramMap;
    private transient Charset charset;
    private transient ByteArrayBuilder buffer;
    private transient Base64 base64codec;
    private boolean complete;

    private String username;
    private char[] password;

    /**
     * @since 4.3
     */
    public BasicScheme(final Charset charset) {
        this.paramMap = new HashMap<>();
        this.charset = charset != null ? charset : StandardCharsets.US_ASCII;
        this.complete = false;
    }

    public BasicScheme() {
        this(StandardCharsets.US_ASCII);
    }

    public void initPreemptive(final Credentials credentials) {
        if (credentials != null) {
            this.username = credentials.getUserPrincipal().getName();
            this.password = credentials.getPassword();
        } else {
            this.username = null;
            this.password = null;
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

        final Credentials credentials = credentialsProvider.getCredentials(
                new AuthScope(host, getRealm(), getName()), context);
        if (credentials != null) {
            this.username = credentials.getUserPrincipal().getName();
            this.password = credentials.getPassword();
            return true;
        }
        this.username = null;
        this.password = null;
        return false;
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
        if (this.buffer == null) {
            this.buffer = new ByteArrayBuilder(64).charset(this.charset);
        } else {
            this.buffer.reset();
        }
        this.buffer.append(this.username).append(":").append(this.password);
        if (this.base64codec == null) {
            this.base64codec = new Base64(0);
        }
        final byte[] encodedCreds = this.base64codec.encode(this.buffer.toByteArray());
        this.buffer.reset();
        return StandardAuthScheme.BASIC + " " + new String(encodedCreds, 0, encodedCreds.length, StandardCharsets.US_ASCII);
    }

    private void writeObject(final ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        out.writeUTF(this.charset.name());
    }

    @SuppressWarnings("unchecked")
    private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        try {
            this.charset = Charset.forName(in.readUTF());
        } catch (final UnsupportedCharsetException ex) {
            this.charset = StandardCharsets.US_ASCII;
        }
    }

    private void readObjectNoData() {
    }

    @Override
    public String toString() {
        return getName() + this.paramMap.toString();
    }

}
