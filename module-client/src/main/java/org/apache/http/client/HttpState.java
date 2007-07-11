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

package org.apache.http.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.CookieIdentityComparator;

/**
 * <p>
 * A container for HTTP attributes that may persist from request
 * to request, such as {@link Cookie cookies} and authentication
 * {@link Credentials credentials}.
 * </p>
 * 
 * @author <a href="mailto:remm@apache.org">Remy Maucherat</a>
 * @author Rodney Waldhoff
 * @author <a href="mailto:jsdever@apache.org">Jeff Dever</a>
 * @author Sean C. Sullivan
 * @author <a href="mailto:becke@u.washington.edu">Michael Becke</a>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 * @author <a href="mailto:mbowler@GargoyleSoftware.com">Mike Bowler</a>
 * @author <a href="mailto:adrian@intencha.com">Adrian Sutton</a>
 * 
 * @version $Revision$ $Date$
 * 
 */
public class HttpState {

    // ----------------------------------------------------- Instance Variables

    /**
     * Map of {@link Credentials credentials} by realm that this 
     * HTTP state contains.
     */
    private final HashMap credMap;

    /**
     * Array of {@link Cookie cookies} that this HTTP state contains.
     */
    private final ArrayList cookies;

    private final Comparator cookieComparator;
    
    // -------------------------------------------------------- Class Variables

    /**
     * Default constructor.
     */
    public HttpState() {
        super();
        this.credMap = new HashMap();
        this.cookies = new ArrayList();
        this.cookieComparator = new CookieIdentityComparator();
    }

    // ------------------------------------------------------------- Properties

    /**
     * Adds an {@link Cookie HTTP cookie}, replacing any existing equivalent cookies.
     * If the given cookie has already expired it will not be added, but existing 
     * values will still be removed.
     * 
     * @param cookie the {@link Cookie cookie} to be added
     * 
     * @see #addCookies(Cookie[])
     * 
     */
    public synchronized void addCookie(Cookie cookie) {
        if (cookie != null) {
            // first remove any old cookie that is equivalent
            for (Iterator it = cookies.iterator(); it.hasNext();) {
                Cookie tmp = (Cookie) it.next();
                if (cookieComparator.compare(cookie, tmp) == 0) {
                    it.remove();
                    break;
                }
            }
            if (!cookie.isExpired(new Date())) {
                cookies.add(cookie);
            }
        }
    }

    /**
     * Adds an array of {@link Cookie HTTP cookies}. Cookies are added individually and 
     * in the given array order. If any of the given cookies has already expired it will 
     * not be added, but existing values will still be removed.
     * 
     * @param cookies the {@link Cookie cookies} to be added
     * 
     * @see #addCookie(Cookie)
     * 
     */
    public synchronized void addCookies(Cookie[] cookies) {
        if (cookies != null) {
            for (int i = 0; i < cookies.length; i++) {
                this.addCookie(cookies[i]);
            }
        }
    }

    /**
     * Returns an array of {@link Cookie cookies} that this HTTP
     * state currently contains.
     * 
     * @return an array of {@link Cookie cookies}.
     */
    public synchronized Cookie[] getCookies() {
        return (Cookie[]) (cookies.toArray(new Cookie[cookies.size()]));
    }

    /**
     * Removes all of {@link Cookie cookies} in this HTTP state
     * that have expired by the specified {@link java.util.Date date}. 
     * 
     * @return true if any cookies were purged.
     * 
     * @see Cookie#isExpired(Date)
     */
    public synchronized boolean purgeExpiredCookies() {
        boolean removed = false;
        Date now = new Date();
        Iterator it = cookies.iterator();
        while (it.hasNext()) {
            if (((Cookie) (it.next())).isExpired(now)) {
                it.remove();
                removed = true;
            }
        }
        return removed;
    }

    /** 
     * Sets the {@link Credentials credentials} for the given authentication 
     * scope. Any previous credentials for the given scope will be overwritten.
     * 
     * @param authscope the {@link AuthScope authentication scope}
     * @param credentials the authentication {@link Credentials credentials} 
     * for the given scope.
     * 
     * @see #getCredentials(AuthScope)
     */
    public synchronized void setCredentials(final AuthScope authscope, final Credentials credentials) {
        if (authscope == null) {
            throw new IllegalArgumentException("Authentication scope may not be null");
        }
        credMap.put(authscope, credentials);
    }

    /**
     * Find matching {@link Credentials credentials} for the given authentication scope.
     *
     * @param map the credentials hash map
     * @param token the {@link AuthScope authentication scope}
     * @return the credentials 
     * 
     */
    private static Credentials matchCredentials(final HashMap map, final AuthScope authscope) {
        // see if we get a direct hit
        Credentials creds = (Credentials)map.get(authscope);
        if (creds == null) {
            // Nope.
            // Do a full scan
            int bestMatchFactor  = -1;
            AuthScope bestMatch  = null;
            Iterator items = map.keySet().iterator();
            while (items.hasNext()) {
                AuthScope current = (AuthScope)items.next();
                int factor = authscope.match(current);
                if (factor > bestMatchFactor) {
                    bestMatchFactor = factor;
                    bestMatch = current;
                }
            }
            if (bestMatch != null) {
                creds = (Credentials)map.get(bestMatch);
            }
        }
        return creds;
    }
    
    /**
     * Get the {@link Credentials credentials} for the given authentication scope.
     *
     * @param authscope the {@link AuthScope authentication scope}
     * @return the credentials 
     * 
     * @see #setCredentials(AuthScope, Credentials)
     */
    public synchronized Credentials getCredentials(final AuthScope authscope) {
        if (authscope == null) {
            throw new IllegalArgumentException("Authentication scope may not be null");
        }
        return matchCredentials(this.credMap, authscope);
    }

    /**
     * Returns a string representation of this HTTP state.
     * 
     * @return The string representation of the HTTP state.
     * 
     * @see java.lang.Object#toString()
     */
    public synchronized String toString() {
        StringBuffer buffer = new StringBuffer();
        buffer.append(credMap);
        buffer.append(cookies);
        return buffer.toString();
    }
    
    /**
     * Clears all credentials.
     */
    public synchronized void clearCredentials() {
        this.credMap.clear();
    }
    
    /**
     * Clears all cookies.
     */
    public synchronized void clearCookies() {
        this.cookies.clear();
    }
    
    /**
     * Clears the state information (all cookies, credentials and proxy credentials).
     */
    public synchronized void clear() {
        clearCookies();
        clearCredentials();
    }
    
}
