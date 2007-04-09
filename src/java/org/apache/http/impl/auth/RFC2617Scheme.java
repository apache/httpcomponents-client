/*
 * $HeadeURL$
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

import java.util.HashMap;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.HTTPAuth;
import org.apache.http.auth.MalformedChallengeException;
import org.apache.http.message.BasicHeaderElement;
import org.apache.http.message.BufferedHeader;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.CharArrayBuffer;

/**
 * <p>
 * Abstract authentication scheme class that lays foundation for all
 * RFC 2617 compliant authetication schemes and provides capabilities common 
 * to all authentication schemes defined in RFC 2617.
 * </p>
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
*/
public abstract class RFC2617Scheme implements AuthScheme {

    /**
     * Authentication parameter map.
     */
    private Map params = null;

    /**
     * Default constructor for RFC2617 compliant authetication schemes.
     * 
     * @since 3.0
     */
    public RFC2617Scheme() {
        super();
    }

    /**
     * Processes the given challenge token. Some authentication schemes
     * may involve multiple challenge-response exchanges. Such schemes must be able 
     * to maintain the state information when dealing with sequential challenges 
     * 
     * @param challenge the challenge string
     * 
     * @throws MalformedChallengeException is thrown if the authentication challenge
     * is malformed
     * 
     * @since 3.0
     */
    public void processChallenge(final Header header) throws MalformedChallengeException {
        if (header == null) {
            throw new IllegalArgumentException("Header may not be null");
        }
        if (!header.getName().equalsIgnoreCase(HTTPAuth.WWW_AUTH)) {
            throw new MalformedChallengeException("Unexpected header name: " + header.getName());
        }
        CharArrayBuffer buffer;
        int pos;
        if (header instanceof BufferedHeader) {
            buffer = ((BufferedHeader) header).getBuffer();
            pos = ((BufferedHeader) header).getValuePos();
        } else {
            String s = header.getValue();
            if (s == null) {
                throw new MalformedChallengeException("Header value is null");
            }
            buffer = new CharArrayBuffer(s.length());
            buffer.append(s);
            pos = 0;
        }
        while (pos < buffer.length() && HTTP.isWhitespace(buffer.charAt(pos))) {
            pos++;
        }
        int beginIndex = pos;
        while (pos < buffer.length() && !HTTP.isWhitespace(buffer.charAt(pos))) {
            pos++;
        }
        int endIndex = pos;
        String s = buffer.substring(beginIndex, endIndex);
        if (!s.equalsIgnoreCase(getSchemeName())) {
            throw new MalformedChallengeException("Invalid scheme identifier: " + s);
        }
        HeaderElement[] elements = BasicHeaderElement.parseAll(buffer, pos, buffer.length());
        this.params = new HashMap(elements.length);
        for (int i = 0; i < elements.length; i++) {
            HeaderElement element = elements[i];
            this.params.put(element.getName(), element.getValue());
        }
    }

    /**
     * Returns authentication parameters map. Keys in the map are lower-cased.
     * 
     * @return the map of authentication parameters
     */
    protected Map getParameters() {
        return this.params;
    }

    /**
     * Returns authentication parameter with the given name, if available.
     * 
     * @param name The name of the parameter to be returned
     * 
     * @return the parameter with the given name
     */
    public String getParameter(final String name) {
        if (name == null) {
            throw new IllegalArgumentException("Parameter name may not be null"); 
        }
        if (this.params == null) {
            return null;
        }
        return (String) this.params.get(name.toLowerCase());
    }

    /**
     * Returns authentication realm. The realm may not be null.
     * 
     * @return the authentication realm
     */
    public String getRealm() {
        return getParameter("realm");
    }
    
}
