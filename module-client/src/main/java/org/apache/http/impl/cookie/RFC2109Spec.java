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

import java.util.Arrays;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.cookie.ClientCookie;
import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.CookieOrigin;
import org.apache.http.cookie.CookiePathComparator;
import org.apache.http.cookie.MalformedCookieException;
import org.apache.http.cookie.SM;
import org.apache.http.message.BufferedHeader;
import org.apache.http.util.CharArrayBuffer;

/**
 * RFC 2109 compliant cookie policy
 *
 * @author  B.C. Holmes
 * @author <a href="mailto:jericho@thinkfree.com">Park, Sung-Gu</a>
 * @author <a href="mailto:dsale@us.britannica.com">Doug Sale</a>
 * @author Rod Waldhoff
 * @author dIon Gillard
 * @author Sean C. Sullivan
 * @author <a href="mailto:JEvans@Cyveillance.com">John Evans</a>
 * @author Marc A. Saegesser
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 * @author <a href="mailto:mbowler@GargoyleSoftware.com">Mike Bowler</a>
 * 
 * @since 4.0 
 */

public class RFC2109Spec extends CookieSpecBase {

    private final static CookiePathComparator PATH_COMPARATOR = new CookiePathComparator(); 
    
    private final static String[] DATE_PATTERNS = {
        DateUtils.PATTERN_RFC1123,
        DateUtils.PATTERN_RFC1036,
        DateUtils.PATTERN_ASCTIME 
    }; 
    
    private final String[] datepatterns; 
    private final boolean oneHeader;
    
    /** Default constructor */
    public RFC2109Spec(final String[] datepatterns, boolean oneHeader) {
        super();
        if (datepatterns != null) {
            this.datepatterns = (String [])datepatterns.clone();
        } else {
            this.datepatterns = DATE_PATTERNS;
        }
        this.oneHeader = oneHeader;
        registerAttribHandler(ClientCookie.VERSION_ATTR, new RFC2109VersionHandler());
        registerAttribHandler(ClientCookie.PATH_ATTR, new BasicPathHandler());
        registerAttribHandler(ClientCookie.DOMAIN_ATTR, new RFC2109DomainHandler());
        registerAttribHandler(ClientCookie.MAX_AGE_ATTR, new BasicMaxAgeHandler());
        registerAttribHandler(ClientCookie.SECURE_ATTR, new BasicSecureHandler());
        registerAttribHandler(ClientCookie.COMMENT_ATTR, new BasicCommentHandler());
        registerAttribHandler(ClientCookie.EXPIRES_ATTR, new BasicExpiresHandler(
                this.datepatterns));
    }

    /** Default constructor */
    public RFC2109Spec() {
        this(null, false);
    }
    
    public Cookie[] parse(final Header header, final CookieOrigin origin) 
            throws MalformedCookieException {
        if (header == null) {
            throw new IllegalArgumentException("Header may not be null");
        }
        if (origin == null) {
            throw new IllegalArgumentException("Cookie origin may not be null");
        }
        HeaderElement[] elems = header.getElements();
        return parse(elems, origin);
    }

    public void validate(final Cookie cookie, final CookieOrigin origin) 
            throws MalformedCookieException {
        if (cookie == null) {
            throw new IllegalArgumentException("Cookie may not be null");
        }
        String name = cookie.getName();
        if (name.indexOf(' ') != -1) {
            throw new MalformedCookieException("Cookie name may not contain blanks");
        }
        if (name.startsWith("$")) {
            throw new MalformedCookieException("Cookie name may not start with $");
        }
        super.validate(cookie, origin);
    }

    public Header[] formatCookies(final Cookie[] cookies) {
        if (cookies == null) {
            throw new IllegalArgumentException("Cookie array may not be null");
        }
        if (cookies.length == 0) {
            throw new IllegalArgumentException("Cookie array may not be empty");
        }
        Arrays.sort(cookies, PATH_COMPARATOR);
        if (this.oneHeader) {
            return doFormatOneHeader(cookies);
        } else {
            return doFormatManyHeaders(cookies);
        }
    }

    private Header[] doFormatOneHeader(final Cookie[] cookies) {
        int version = Integer.MAX_VALUE;
        // Pick the lowest common denominator
        for (int i = 0; i < cookies.length; i++) {
            Cookie cookie = cookies[i];
            if (cookie.getVersion() < version) {
                version = cookie.getVersion();
            }
        }
        CharArrayBuffer buffer = new CharArrayBuffer(40 * cookies.length);
        buffer.append(SM.COOKIE);
        buffer.append(": ");
        buffer.append("$Version=");
        buffer.append(Integer.toString(version));
        for (int i = 0; i < cookies.length; i++) {
            buffer.append("; ");
            Cookie cookie = cookies[i];
            formatCookieAsVer(buffer, cookie, version);
        }
        return new Header[] { new BufferedHeader(buffer) };
    }

    private Header[] doFormatManyHeaders(final Cookie[] cookies) {
        Header[] headers = new Header[cookies.length]; 
        for (int i = 0; i < cookies.length; i++) {
            Cookie cookie = cookies[i];
            int version = cookie.getVersion();
            CharArrayBuffer buffer = new CharArrayBuffer(40);
            buffer.append("Cookie: ");
            buffer.append("$Version=");
            buffer.append(Integer.toString(version));
            buffer.append("; ");
            formatCookieAsVer(buffer, cookies[i], version);
            headers[i] = new BufferedHeader(buffer);
        }
        return headers;
    }
    
    /**
     * Return a name/value string suitable for sending in a <tt>"Cookie"</tt>
     * header as defined in RFC 2109 for backward compatibility with cookie
     * version 0
     * @param buffer The char array buffer to use for output
     * @param param The parameter.
     * @param version The cookie version 
     */
    private void formatParamAsVer(final CharArrayBuffer buffer, 
            final String name, final String value, int version) {
        buffer.append(name);
        buffer.append("=");
        if (value != null) {
            if (version > 0) {
                buffer.append('\"');
                buffer.append(value);
                buffer.append('\"');
            } else {
                buffer.append(value);
            }
        }
    }

    /**
     * Return a string suitable for sending in a <tt>"Cookie"</tt> header 
     * as defined in RFC 2109 for backward compatibility with cookie version 0
     * @param buffer The char array buffer to use for output
     * @param cookie The {@link Cookie} to be formatted as string
     * @param version The version to use.
     */
    private void formatCookieAsVer(final CharArrayBuffer buffer, 
            final Cookie cookie, int version) {
        formatParamAsVer(buffer, cookie.getName(), cookie.getValue(), version);
        if (cookie.getPath() != null) {
            if (cookie instanceof ClientCookie 
                    && ((ClientCookie) cookie).containsAttribute(ClientCookie.PATH_ATTR)) {
                buffer.append("; ");
                formatParamAsVer(buffer, "$Path", cookie.getPath(), version);
            }
        }
        if (cookie.getDomain() != null) {
            if (cookie instanceof ClientCookie 
                    && ((ClientCookie) cookie).containsAttribute(ClientCookie.DOMAIN_ATTR)) {
                buffer.append("; ");
                formatParamAsVer(buffer, "$Domain", cookie.getDomain(), version);
            }
        }
    }
    
}
