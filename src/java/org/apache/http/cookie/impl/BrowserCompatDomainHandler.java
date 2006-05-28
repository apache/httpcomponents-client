/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Copyright 2002-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
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
package org.apache.http.cookie.impl;

import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.CookieAttributeHandler;
import org.apache.http.cookie.CookieOrigin;
import org.apache.http.cookie.MalformedCookieException;

public class BrowserCompatDomainHandler implements CookieAttributeHandler {

    public BrowserCompatDomainHandler() {
        super();
    }
    
    public void parse(final Cookie cookie, final String value) 
            throws MalformedCookieException {
        if (cookie == null) {
            throw new IllegalArgumentException("Cookie may not be null");
        }
        if (value == null) {
            throw new MalformedCookieException("Missing value for domain attribute");
        }
        if (value.trim().equals("")) {
            throw new MalformedCookieException("Blank value for domain attribute");
        }
        cookie.setDomain(value);
        cookie.setDomainAttributeSpecified(true);
    }

    public void validate(final Cookie cookie, final CookieOrigin origin) 
            throws MalformedCookieException {
        if (cookie == null) {
            throw new IllegalArgumentException("Cookie may not be null");
        }
        if (origin == null) {
            throw new IllegalArgumentException("Cookie origin may not be null");
        }
        // Validate the cookies domain attribute.  NOTE:  Domains without 
        // any dots are allowed to support hosts on private LANs that don't 
        // have DNS names.  Since they have no dots, to domain-match the 
        // request-host and domain must be identical for the cookie to sent 
        // back to the origin-server.
        String host = origin.getHost();
        if (host.indexOf(".") >= 0) {
            // Not required to have at least two dots.  RFC 2965.
            // A Set-Cookie2 with Domain=ajax.com will be accepted.

            // domain must match host
            if (!host.endsWith(cookie.getDomain())) {
                String s = cookie.getDomain();
                if (s.startsWith(".")) {
                    s = s.substring(1, s.length());
                }
                if (!host.equals(s)) { 
                    throw new MalformedCookieException(
                        "Illegal domain attribute \"" + cookie.getDomain() 
                        + "\". Domain of origin: \"" + host + "\"");
                }
            }
        } else {
            if (!host.equals(cookie.getDomain())) {
                throw new MalformedCookieException(
                    "Illegal domain attribute \"" + cookie.getDomain() 
                    + "\". Domain of origin: \"" + host + "\"");
            }
        }
    }
    
    public boolean match(final Cookie cookie, final CookieOrigin origin) {
        if (cookie == null) {
            throw new IllegalArgumentException("Cookie may not be null");
        }
        if (origin == null) {
            throw new IllegalArgumentException("Cookie origin may not be null");
        }
        String host = origin.getHost();
        String domain = cookie.getDomain();
        if (domain == null) {
            return false;
        }
        if (host.equals(domain)) {
            return true;
        }
        if (!domain.startsWith(".")) {
            domain = "." + domain;
        }
        return host.endsWith(domain) || host.equals(domain.substring(1));
    }
    
}