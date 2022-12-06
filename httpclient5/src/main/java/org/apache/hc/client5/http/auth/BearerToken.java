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

import java.io.Serializable;
import java.security.Principal;
import java.util.Objects;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.util.Args;

/**
 * Opaque token {@link Credentials} usually representing a set of claims, often encrypted
 * or signed. The JWT (JSON Web Token) is among most widely used tokens used at the time
 * of writing.
 *
 * @since 5.3
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class BearerToken implements Credentials, Serializable {

    private final String token;

    public BearerToken(final String token) {
        super();
        this.token = Args.notBlank(token, "Token");
    }

    @Override
    public Principal getUserPrincipal() {
        return null;
    }

    /**
     * @deprecated Do not use.
     */
    @Deprecated
    @Override
    public char[] getPassword() {
        return null;
    }

    public String getToken() {
        return token;
    }

    @Override
    public int hashCode() {
        return token.hashCode();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof BearerToken) {
            final BearerToken that = (BearerToken) o;
            return Objects.equals(this.token, that.token);
        }
        return false;
    }

}

