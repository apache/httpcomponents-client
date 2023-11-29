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
 * Simple {@link Credentials} representation based on a user name / password
 * pair.
 *
 * @since 4.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class UsernamePasswordCredentials implements Credentials, Serializable {

    private static final long serialVersionUID = 243343858802739403L;

    private final Principal principal;
    private final char[] password;

    /**
     * The constructor with the username and password arguments.
     *
     * @param principal the user principal
     * @param password the password
     *
     * @since 5.3
     *
     * @see BasicUserPrincipal
     * @see NTUserPrincipal
     */
    public UsernamePasswordCredentials(final Principal principal, final char[] password) {
        super();
        this.principal = Args.notNull(principal, "User principal");
        this.password = password;
    }

    /**
     * The constructor with the username and password arguments.
     *
     * @param username the user name
     * @param password the password
     */
    public UsernamePasswordCredentials(final String username, final char[] password) {
        this(new BasicUserPrincipal(username), password);
    }

    @Override
    public Principal getUserPrincipal() {
        return this.principal;
    }

    public String getUserName() {
        return this.principal.getName();
    }

    /**
     * @since 5.3
     */
    public char[] getUserPassword() {
        return password;
    }

    /**
     * @deprecated Use {@link #getUserPassword()}.
     */
    @Deprecated
    @Override
    public char[] getPassword() {
        return password;
    }

    @Override
    public int hashCode() {
        return this.principal.hashCode();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof UsernamePasswordCredentials) {
            final UsernamePasswordCredentials that = (UsernamePasswordCredentials) o;
            return Objects.equals(this.principal, that.principal);
        }
        return false;
    }

    @Override
    public String toString() {
        return this.principal.toString();
    }

}

