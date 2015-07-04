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
package org.apache.http.impl.auth;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.nio.charset.Charset;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpRequest;
import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.auth.AuthChallenge;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.ChallengeType;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.MalformedChallengeException;
import org.apache.http.message.BufferedHeader;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.Args;
import org.apache.http.util.CharArrayBuffer;
import org.apache.http.util.CharsetUtils;
import org.apache.http.util.EncodingUtils;

/**
 * Basic authentication scheme as defined in RFC 2617.
 *
 * @since 4.0
 */
@NotThreadSafe
public class BasicScheme extends StandardAuthScheme {

    private static final long serialVersionUID = -1931571557597830536L;

    private transient Charset charset;
    private boolean complete;

    /**
     * @since 4.3
     */
    public BasicScheme(final Charset charset) {
        this.charset = charset != null ? charset : Consts.ASCII;
        this.complete = false;
    }

    public BasicScheme() {
        this(Consts.ASCII);
    }

    @Override
    public String getSchemeName() {
        return "basic";
    }

    public void processChallenge(
            final ChallengeType challengeType,
            final AuthChallenge authChallenge) throws MalformedChallengeException {
        update(challengeType, authChallenge);
        this.complete = true;
    }

    @Override
    public boolean isComplete() {
        return this.complete;
    }

    @Override
    public boolean isConnectionBased() {
        return false;
    }

    @Override
    public Header authenticate(
            final Credentials credentials,
            final HttpRequest request,
            final HttpContext context) throws AuthenticationException {

        Args.notNull(credentials, "Credentials");
        Args.notNull(request, "HTTP request");
        final CharArrayBuffer buffer = new CharArrayBuffer(32);
        if (isProxy()) {
            buffer.append(HttpHeaders.PROXY_AUTHORIZATION);
        } else {
            buffer.append(HttpHeaders.AUTHORIZATION);
        }
        buffer.append(": Basic ");

        final StringBuilder tmp = new StringBuilder();
        tmp.append(credentials.getUserPrincipal().getName());
        tmp.append(":");
        tmp.append((credentials.getPassword() == null) ? "null" : credentials.getPassword());

        final Base64 base64codec = new Base64(0);
        final byte[] base64password = base64codec.encode(EncodingUtils.getBytes(tmp.toString(), charset.name()));

        buffer.append(base64password, 0, base64password.length);
        return new BufferedHeader(buffer);
    }

    private void writeObject(final ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        out.writeUTF(this.charset.name());
    }

    @SuppressWarnings("unchecked")
    private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        this.charset = CharsetUtils.get(in.readUTF());
        if (this.charset == null) {
            this.charset = Consts.ASCII;
        }
    }

    private void readObjectNoData() throws ObjectStreamException {
    }

}
