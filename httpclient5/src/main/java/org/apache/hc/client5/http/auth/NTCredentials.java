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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.Principal;
import java.util.Locale;
import java.util.Objects;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.LangUtils;

/**
 * Microsoft Windows specific {@link Credentials} representation that includes
 * Windows specific attributes such as name of the domain the user belongs to.
 *
 * @since 4.0
 *
 * @deprecated Do not use. the NTLM authentication scheme is no longer supported.
 * Consider using Basic or Bearer authentication with TLS instead.
 *
 * @see UsernamePasswordCredentials
 * @see BearerToken
 */
@Deprecated
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class NTCredentials implements Credentials, Serializable {

    private static final long serialVersionUID = -7385699315228907265L;

    /** The user principal  */
    private final NTUserPrincipal principal;

    /** Password */
    private final char[] password;

    /** The netbios hostname the authentication request is originating from.  */
    private final String workstation;

    /** The netbios domain the authentication request is against */
    private final String netbiosDomain;

    /**
     * Constructor.
     * @param userName The user name.  This should not include the domain to authenticate with.
     * For example: "user" is correct whereas "DOMAIN&#x5c;user" is not.
     * @param password The password.
     * @param workstation The workstation the authentication request is originating from.
     * Essentially, the computer name for this machine.
     * @param domain The domain to authenticate within.
     */
    public NTCredentials(
            final String userName,
            final char[] password,
            final String workstation,
            final String domain) {
        this(password, userName, domain, convertDomain(domain));
    }

    /**
     * Constructor.
     * @param userName The user name.  This should not include the domain to authenticate with.
     * For example: "user" is correct whereas "DOMAIN&#x5c;user" is not.
     * @param password The password.
     * @param workstation The netbios workstation name that the authentication request is originating from.
     * Essentially, the computer name for this machine.
     * @param domain The domain to authenticate within.
     * @param netbiosDomain The netbios version of the domain name.
     */
    public NTCredentials(
            final String userName,
            final char[] password,
            final String workstation,
            final String domain,
            final String netbiosDomain) {
        this(password, userName,  domain, netbiosDomain);
    }

    /**
     * Constructor to create an instance of NTCredentials.
     *
     * @param password      The password to use for authentication. Must not be null.
     * @param userName      The user name for authentication. This should not include the domain to authenticate with.
     *                      For example: "user" is correct whereas "DOMAIN&#x5c;user" is not. Must not be null.
     * @param domain        The domain to authenticate within. Can be null.
     * @param netbiosDomain An alternative representation of the domain name in NetBIOS format. Can be null.
     *                      This parameter is provided to accommodate specific scenarios that require the NetBIOS version of the domain name.
     *                      <p>
     *                      This constructor creates a new instance of NTCredentials, determining the workstation name at runtime
     *                      using the {@link #getWorkstationName()} method. The workstation name will be converted to uppercase
     *                      using the {@link java.util.Locale#ROOT} locale.
     */
    public NTCredentials(
            final char[] password,
            final String userName,
            final String domain,
            final String netbiosDomain) {
        super();
        Args.notNull(userName, "User name");
        this.principal = new NTUserPrincipal(domain, userName);
        this.password = password;
        this.workstation = getWorkstationName().toUpperCase(Locale.ROOT);
        this.netbiosDomain = netbiosDomain;
    }


    @Override
    public Principal getUserPrincipal() {
        return this.principal;
    }

    public String getUserName() {
        return this.principal.getUsername();
    }

    @Override
    public char[] getPassword() {
        return this.password;
    }

    /**
     * Retrieves the name to authenticate with.
     *
     * @return String the domain these credentials are intended to authenticate with.
     */
    public String getDomain() {
        return this.principal.getDomain();
    }

    /**
    * Retrieves the netbios domain to authenticate with.
    * @return String the netbios domain name.
    */
    public String getNetbiosDomain() {
        return this.netbiosDomain;
    }

    /**
     * Retrieves the netbios workstation name of the computer originating the request.
     *
     * @return String the netbios workstation the user is logged into.
     */
    public String getWorkstation() {
        return this.workstation;
    }

    @Override
    public int hashCode() {
        int hash = LangUtils.HASH_SEED;
        hash = LangUtils.hashCode(hash, this.principal);
        hash = LangUtils.hashCode(hash, this.workstation);
        hash = LangUtils.hashCode(hash, this.netbiosDomain);
        return hash;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof NTCredentials) {
            final NTCredentials that = (NTCredentials) o;
            return Objects.equals(this.principal, that.principal)
                    && Objects.equals(this.workstation, that.workstation)
                    && Objects.equals(this.netbiosDomain, that.netbiosDomain);
        }
        return false;
    }

    @Override
    public String toString() {
        final StringBuilder buffer = new StringBuilder();
        buffer.append("[principal: ");
        buffer.append(this.principal);
        buffer.append("][workstation: ");
        buffer.append(this.workstation);
        buffer.append("][netbiosDomain: ");
        buffer.append(this.netbiosDomain);
        buffer.append("]");
        return buffer.toString();
    }

    /** Strip dot suffix from a name */
    private static String stripDotSuffix(final String value) {
        if (value == null) {
            return null;
        }
        final int index = value.indexOf('.');
        if (index != -1) {
            return value.substring(0, index);
        }
        return value;
    }

    /** Convert domain to standard form */
    private static String convertDomain(final String domain) {
        final String returnString = stripDotSuffix(domain);
        return returnString == null ? returnString : returnString.toUpperCase(Locale.ROOT);
    }


    /**
     * Retrieves the workstation name of the computer originating the request.
     * This method attempts to get the local host name using the InetAddress class.
     * If it fails to retrieve the host name due to an UnknownHostException, it returns "localhost" as a fallback.
     *
     * @return The unqualified workstation name as a String.
     */
    private static String getWorkstationName() {
        try {
            final InetAddress addr = InetAddress.getLocalHost();
            final String hostName = addr.getHostName();
            // Ensure the hostname is unqualified by removing any domain part
            return stripDotSuffix(hostName);
        } catch (final UnknownHostException e) {
            return "localhost";
        }
    }

}
