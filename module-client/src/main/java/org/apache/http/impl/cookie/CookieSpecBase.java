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

import java.util.Iterator;

import org.apache.http.HeaderElement;
import org.apache.http.NameValuePair;
import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.CookieAttributeHandler;
import org.apache.http.cookie.CookieOrigin;
import org.apache.http.cookie.MalformedCookieException;

/**
 * Cookie management functions shared by all specification.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 * 
 * @since 4.0 
 */
public abstract class CookieSpecBase extends AbstractCookieSpec {
    
    protected static String getDefaultPath(final CookieOrigin origin) {
        String defaultPath = origin.getPath();    
        int lastSlashIndex = defaultPath.lastIndexOf('/');
        if (lastSlashIndex >= 0) {
            if (lastSlashIndex == 0) {
                //Do not remove the very first slash
                lastSlashIndex = 1;
            }
            defaultPath = defaultPath.substring(0, lastSlashIndex);
        }
        return defaultPath;
    }

    protected static String getDefaultDomain(final CookieOrigin origin) {
        return origin.getHost();
    }
    
    protected Cookie[] parse(final HeaderElement[] elems, final CookieOrigin origin)
                throws MalformedCookieException {
        Cookie[] cookies = new Cookie[elems.length];
        for (int i = 0; i < elems.length; i++) {
            HeaderElement headerelement = elems[i];

            String name = headerelement.getName();
            String value = headerelement.getValue();
            if (name == null || name.equals("")) {
                throw new MalformedCookieException("Cookie name may not be empty");
            }
            
            Cookie cookie = new BasicCookie(name, value);
            cookie.setPath(getDefaultPath(origin));
            cookie.setDomain(getDefaultDomain(origin));
            
            // cycle through the parameters
            NameValuePair[] attribs = headerelement.getParameters();
            for (int j = attribs.length - 1; j >= 0; j--) {
                NameValuePair attrib = attribs[j];
                String s = attrib.getName().toLowerCase();
                CookieAttributeHandler handler = findAttribHandler(s);
                if (handler != null) {
                    handler.parse(cookie, attrib.getValue());
                }
            }
            cookies[i] = cookie;
        }
        return cookies;
    }

    public void validate(final Cookie cookie, final CookieOrigin origin)
            throws MalformedCookieException {
        if (cookie == null) {
            throw new IllegalArgumentException("Cookie may not be null");
        }
        if (origin == null) {
            throw new IllegalArgumentException("Cookie origin may not be null");
        }
        for (Iterator i = getAttribHandlerIterator(); i.hasNext();) {
            CookieAttributeHandler handler = (CookieAttributeHandler) i.next();
            handler.validate(cookie, origin);
        }
    }

    public boolean match(final Cookie cookie, final CookieOrigin origin) {
        if (cookie == null) {
            throw new IllegalArgumentException("Cookie may not be null");
        }
        if (origin == null) {
            throw new IllegalArgumentException("Cookie origin may not be null");
        }
        for (Iterator i = getAttribHandlerIterator(); i.hasNext();) {
            CookieAttributeHandler handler = (CookieAttributeHandler) i.next();
            if (!handler.match(cookie, origin)) {
                return false;
            }
        }
        return true;
    }

}
