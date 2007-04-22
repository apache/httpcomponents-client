/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
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
 *
 */

package org.apache.http.impl.auth;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.HTTPAuth;
import org.apache.http.auth.MalformedChallengeException;
import org.apache.http.auth.params.AuthParams;
import org.apache.http.message.BufferedHeader;
import org.apache.http.util.CharArrayBuffer;
import org.apache.http.util.EncodingUtils;

/**
 * <p>
 * Basic authentication scheme as defined in RFC 2617.
 * </p>
 * 
 * @author <a href="mailto:remm@apache.org">Remy Maucherat</a>
 * @author Rodney Waldhoff
 * @author <a href="mailto:jsdever@apache.org">Jeff Dever</a>
 * @author Ortwin Glueck
 * @author Sean C. Sullivan
 * @author <a href="mailto:adrian@ephox.com">Adrian Sutton</a>
 * @author <a href="mailto:mbowler@GargoyleSoftware.com">Mike Bowler</a>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 */

public class BasicScheme extends RFC2617Scheme {
    
    /** Whether the basic authentication process is complete */
    private boolean complete;
    
    /**
     * Default constructor for the basic authetication scheme.
     * 
     * @since 3.0
     */
    public BasicScheme() {
        super();
        this.complete = false;
    }

    /**
     * Returns textual designation of the basic authentication scheme.
     * 
     * @return <code>basic</code>
     */
    public String getSchemeName() {
        return "basic";
    }

    /**
     * Processes the Basic challenge.
     *  
     * @param header the challenge header
     * 
     * @throws MalformedChallengeException is thrown if the authentication challenge
     * is malformed
     * 
     * @since 4.0
     */
    public void processChallenge(
            final Header header) throws MalformedChallengeException {
        super.processChallenge(header);
        this.complete = true;
    }

    /**
     * Tests if the Basic authentication process has been completed.
     * 
     * @return <tt>true</tt> if Basic authorization has been processed,
     *   <tt>false</tt> otherwise.
     * 
     * @since 3.0
     */
    public boolean isComplete() {
        return this.complete;
    }

    /**
     * Returns <tt>false</tt>. Basic authentication scheme is request based.
     * 
     * @return <tt>false</tt>.
     * 
     * @since 3.0
     */
    public boolean isConnectionBased() {
        return false;    
    }

    /**
     * Produces basic authorization header for the given set of {@link Credentials}.
     * 
     * @param credentials The set of credentials to be used for athentication
     * @param request The request being authenticated
     * @throws InvalidCredentialsException if authentication credentials
     *         are not valid or not applicable for this authentication scheme
     * @throws AuthenticationException if authorization string cannot 
     *   be generated due to an authentication failure
     * 
     * @return a basic authorization string
     * 
     * @since 4.0
     */
    public Header authenticate(
            final Credentials credentials, 
            final HttpRequest request) throws AuthenticationException {

        if (credentials == null) {
            throw new IllegalArgumentException("Credentials may not be null");
        }
        if (request == null) {
            throw new IllegalArgumentException("HTTP request may not be null");
        }
        
        String charset = AuthParams.getCredentialCharset(request.getParams());
        return authenticate(credentials, charset, isProxy());
    }
    
    /**
     * Returns a basic <tt>Authorization</tt> header value for the given 
     * {@link Credentials} and charset.
     * 
     * @param credentials The credentials to encode.
     * @param charset The charset to use for encoding the credentials
     * 
     * @return a basic authorization header
     * 
     * @since 4.0
     */
    public static Header authenticate(
            final Credentials credentials, 
            final String charset, 
            boolean proxy) {
        if (credentials == null) {
            throw new IllegalArgumentException("Credentials may not be null"); 
        }
        if (charset == null) {
            throw new IllegalArgumentException("charset may not be null");
        }

        CharArrayBuffer buffer = new CharArrayBuffer(32);
        buffer.append(credentials.getPrincipalName());
        buffer.append(":");
        buffer.append((credentials.getPassword() == null) ? "null" : credentials.getPassword());

        byte[] base64password = Base64.encodeBase64(
                EncodingUtils.getBytes(buffer.toString(), charset));
        
        buffer.clear();
        if (proxy) {
            buffer.append(HTTPAuth.PROXY_AUTH_RESP);
        } else {
            buffer.append(HTTPAuth.WWW_AUTH_RESP);
        }
        buffer.append(": Basic ");
        buffer.append(base64password, 0, base64password.length);
        
        return new BufferedHeader(buffer);
    }
    
}
