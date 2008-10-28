/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
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

package org.apache.http.impl.cookie;

import java.util.List;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.CookieOrigin;
import org.apache.http.cookie.CookieSpec;
import org.apache.http.cookie.MalformedCookieException;
import org.apache.http.cookie.SM;
import org.apache.http.cookie.SetCookie2;

/**
 * 'Meta' cookie specification that selects a cookie policy depending
 * on the format of the cookie(s)
 * 
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @since 4.0
 */
public class BestMatchSpec implements CookieSpec {

    private final String[] datepatterns;
    private final boolean oneHeader;
    
    private RFC2965Spec strict;
    private RFC2109Spec obsoleteStrict;
    private BrowserCompatSpec compat;
    private NetscapeDraftSpec netscape;

    public BestMatchSpec(final String[] datepatterns, boolean oneHeader) {
        super();
        this.datepatterns = datepatterns;
        this.oneHeader = oneHeader;
    }

    public BestMatchSpec() {
        this(null, false);
    }

    private RFC2965Spec getStrict() {
        if (this.strict == null) {
             this.strict = new RFC2965Spec(this.datepatterns, this.oneHeader);
        }
        return strict;
    }

    private RFC2109Spec getObsoleteStrict() {
        if (this.obsoleteStrict == null) {
             this.obsoleteStrict = new RFC2109Spec(this.datepatterns, this.oneHeader);
        }
        return obsoleteStrict;
    }

    private BrowserCompatSpec getCompat() {
        if (this.compat == null) {
            this.compat = new BrowserCompatSpec(this.datepatterns);
        }
        return compat;
    }

    private NetscapeDraftSpec getNetscape() {
        if (this.netscape == null) {
            String[] patterns = this.datepatterns;
            if (patterns == null) {
                patterns = BrowserCompatSpec.DATE_PATTERNS;
            }
            this.netscape = new NetscapeDraftSpec(patterns);
        }
        return netscape;
    }

    public List<Cookie> parse(
            final Header header, 
            final CookieOrigin origin) throws MalformedCookieException {
        if (header == null) {
            throw new IllegalArgumentException("Header may not be null");
        }
        if (origin == null) {
           throw new IllegalArgumentException("Cookie origin may not be null");
        }
        HeaderElement[] helems = header.getElements();
        boolean versioned = false;
        boolean netscape = false;
        for (HeaderElement helem: helems) {
            if (helem.getParameterByName("version") != null) {
                versioned = true;
            }
            if (helem.getParameterByName("expires") != null) {
               netscape = true;
            }
        }
        // Do we have a cookie with a version attribute?
        if (versioned) {
            if (SM.SET_COOKIE2.equals(header.getName())) {
                return getStrict().parse(helems, origin);
            } else {
                return getObsoleteStrict().parse(helems, origin);
            }
        } else if (netscape) {
            // Need to parse the header again,
            // because Netscape draft cannot handle
            // comma separators
            return getNetscape().parse(header, origin);
        } else {
            return getCompat().parse(helems, origin);
        }
    }

    public void validate(
            final Cookie cookie, 
            final CookieOrigin origin) throws MalformedCookieException {
        if (cookie == null) {
            throw new IllegalArgumentException("Cookie may not be null");
        }
        if (origin == null) {
            throw new IllegalArgumentException("Cookie origin may not be null");
        }
        if (cookie.getVersion() > 0) {
            if (cookie instanceof SetCookie2) {
                getStrict().validate(cookie, origin);
            } else {
                getObsoleteStrict().validate(cookie, origin);
            }
        } else {
            getCompat().validate(cookie, origin);
        }
    }

    public boolean match(final Cookie cookie, final CookieOrigin origin) {
        if (cookie == null) {
            throw new IllegalArgumentException("Cookie may not be null");
        }
        if (origin == null) {
            throw new IllegalArgumentException("Cookie origin may not be null");
        }
        if (cookie.getVersion() > 0) {
            if (cookie instanceof SetCookie2) {
                return getStrict().match(cookie, origin);
            } else {
                return getObsoleteStrict().match(cookie, origin);
            }
        } else {
            return getCompat().match(cookie, origin);
        }
    }

    public List<Header> formatCookies(final List<Cookie> cookies) {
        if (cookies == null) {
            throw new IllegalArgumentException("List of cookie may not be null");
        }
        int version = Integer.MAX_VALUE;
        boolean isSetCookie2 = true; 
        for (Cookie cookie: cookies) {
            if (!(cookie instanceof SetCookie2)) {
                isSetCookie2 = false;
            }
            if (cookie.getVersion() < version) {
                version = cookie.getVersion();
            }
        }
        if (version > 0) {
            if (isSetCookie2) {
                return getStrict().formatCookies(cookies);
            } else {
                return getObsoleteStrict().formatCookies(cookies);
            }
        } else {
            return getCompat().formatCookies(cookies);
        }
    }

    public int getVersion() {
        return getStrict().getVersion();
    }

    public Header getVersionHeader() {
        return getStrict().getVersionHeader();
    }

}