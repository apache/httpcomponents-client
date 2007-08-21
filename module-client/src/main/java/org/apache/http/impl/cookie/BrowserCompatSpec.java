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

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.CookieOrigin;
import org.apache.http.cookie.MalformedCookieException;
import org.apache.http.cookie.SM;
import org.apache.http.message.BasicHeaderValueParser;
import org.apache.http.message.BufferedHeader;
import org.apache.http.util.CharArrayBuffer;


/**
 * Cookie specification that stives to closely mimic (mis)behavior of 
 * common web browser applications such as Microsoft Internet Explorer
 * and Mozilla FireFox.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 * 
 * @since 4.0 
 */
public class BrowserCompatSpec extends CookieSpecBase {
    
    /** Valid date patterns used per default */
    private static final String[] DATE_PATTERNS = new String[] {
            DateUtils.PATTERN_RFC1123,
            DateUtils.PATTERN_RFC1036,
            DateUtils.PATTERN_ASCTIME,
            "EEE, dd-MMM-yyyy HH:mm:ss z",
            "EEE, dd-MMM-yyyy HH-mm-ss z",
            "EEE, dd MMM yy HH:mm:ss z",
            "EEE dd-MMM-yyyy HH:mm:ss z",
            "EEE dd MMM yyyy HH:mm:ss z",
            "EEE dd-MMM-yyyy HH-mm-ss z",
            "EEE dd-MMM-yy HH:mm:ss z",
            "EEE dd MMM yy HH:mm:ss z",
            "EEE,dd-MMM-yy HH:mm:ss z",
            "EEE,dd-MMM-yyyy HH:mm:ss z",
            "EEE, dd-MM-yyyy HH:mm:ss z",                
        };

    private final String[] datepatterns; 
    
    /** Default constructor */
    public BrowserCompatSpec(final String[] datepatterns) {
        super();
        if (datepatterns != null) {
            this.datepatterns = (String [])datepatterns.clone();
        } else {
            this.datepatterns = DATE_PATTERNS;
        }
        registerAttribHandler("path", new BasicPathHandler());
        registerAttribHandler("domain", new BasicDomainHandler());
        registerAttribHandler("max-age", new BasicMaxAgeHandler());
        registerAttribHandler("secure", new BasicSecureHandler());
        registerAttribHandler("comment", new BasicCommentHandler());
        registerAttribHandler("expires", new BasicExpiresHandler(this.datepatterns));
    }

    /** Default constructor */
    public BrowserCompatSpec() {
        this(null);
    }
    
    public Cookie[] parse(final Header header, final CookieOrigin origin) 
            throws MalformedCookieException {
        if (header == null) {
            throw new IllegalArgumentException("Header may not be null");
        }
        if (origin == null) {
            throw new IllegalArgumentException("Cookie origin may not be null");
        }
        String headervalue = header.getValue();
        boolean isNetscapeCookie = false; 
        int i1 = headervalue.toLowerCase().indexOf("expires=");
        if (i1 != -1) {
            i1 += "expires=".length();
            int i2 = headervalue.indexOf(";", i1);
            if (i2 == -1) {
                i2 = headervalue.length(); 
            }
            try {
                DateUtils.parseDate(headervalue.substring(i1, i2), this.datepatterns);
                isNetscapeCookie = true; 
            } catch (DateParseException e) {
                // Does not look like a valid expiry date
            }
        }
        HeaderElement[] elems = null;
        if (isNetscapeCookie) {
            elems = new HeaderElement[] { BasicHeaderValueParser.parseHeaderElement(headervalue, null) };
        } else {
            elems = header.getElements();
        }
        return parse(elems, origin);
    }

    public Header[] formatCookies(final Cookie[] cookies) {
        if (cookies == null) {
            throw new IllegalArgumentException("Cookie array may not be null");
        }
        if (cookies.length == 0) {
            throw new IllegalArgumentException("Cookie array may not be empty");
        }
        CharArrayBuffer buffer = new CharArrayBuffer(20 * cookies.length);
        buffer.append(SM.COOKIE);
        buffer.append(": ");
        for (int i = 0; i < cookies.length; i++) {
            Cookie cookie = cookies[i];
            if (i > 0) {
                buffer.append("; ");
            }
            buffer.append(cookie.getName());
            buffer.append("=");
            String s = cookie.getValue();
            if (s != null) {
                buffer.append(s);
            }
        }
        return new Header[] { new BufferedHeader(buffer) };
    }
    
}
