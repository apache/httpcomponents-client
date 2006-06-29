/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Copyright 2002-2006 The Apache Software Foundation
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

package org.apache.http.cookie;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.cookie.params.CookieSpecParams;
import org.apache.http.params.HttpParams;

/**
 * Cookie management policy class. The cookie policy provides corresponding
 * cookie management interfrace for a given type or version of cookie. 
 * 
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 * @author <a href="mailto:mbowler@GargoyleSoftware.com">Mike Bowler</a>
 *
 * @since 2.0
 */
public class CookiePolicy {

    private static Map SPECS = Collections.synchronizedMap(new HashMap());
    
    private CookiePolicy() {
    }
    
    /**
     * Registers a {@link CookieSpecFactory} with the given identifier. 
     * If a specification with the given ID already exists it will be overridden.  
     * This ID is the same one used to retrieve the {@link CookieSpecFactory} 
     * from {@link #getCookieSpec(String)}.
     * 
     * @param id the identifier for this specification
     * @param factory the {@link CookieSpecFactory} class to register
     * 
     * @see #getCookieSpec(String)
     * 
     * @since 3.0
     */
    public static void register(final String id, final CookieSpecFactory factory) {
         if (id == null) {
             throw new IllegalArgumentException("Id may not be null");
         }
        if (factory == null) {
            throw new IllegalArgumentException("Cookie spec factory may not be null");
        }
        SPECS.put(id.toLowerCase(), factory);
    }

    /**
     * Unregisters the {@link CookieSpecFactory} with the given ID.
     * 
     * @param id the ID of the {@link CookieSpec cookie specification} to unregister
     * 
     * @since 3.0
     */
    public static void unregister(final String id) {
         if (id == null) {
             throw new IllegalArgumentException("Id may not be null");
         }
         SPECS.remove(id.toLowerCase());
    }

    /**
     * Gets the {@link CookieSpec cookie specification} with the given ID.
     * 
     * @param id the {@link CookieSpec cookie specification} ID
     * @param params the {@link HttpParams HTTP parameters} for the cookie
     *  specification. 
     * 
     * @return {@link CookieSpec cookie specification}
     * 
     * @throws IllegalStateException if a policy with the ID cannot be found
     * 
     * @since 4.0
     */
    public static CookieSpec getCookieSpec(final String id, final HttpParams params) 
        throws IllegalStateException {

        if (id == null) {
            throw new IllegalArgumentException("Id may not be null");
        }
        CookieSpecFactory factory = (CookieSpecFactory) SPECS.get(id.toLowerCase());
        if (factory != null) {
            return factory.newInstance(params);
        } else {
            throw new IllegalStateException("Unsupported cookie spec " + id);
        }
    } 

    /**
     * Gets the {@link CookieSpec cookie specification} based on the given
     * HTTP parameters. The cookie specification ID will be obtained from
     * the HTTP parameters.
     * 
     * @param params the {@link HttpParams HTTP parameters} for the cookie
     *  specification. 
     * 
     * @return {@link CookieSpec cookie specification}
     * 
     * @throws IllegalStateException if a policy with the ID cannot be found
     * 
     * @see CookieSpecParams#getCookiePolicy(HttpParams)
     * 
     * @since 4.0
     */
    public static CookieSpec getCookieSpec(final HttpParams params) 
        throws IllegalStateException {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        return getCookieSpec(CookieSpecParams.getCookiePolicy(params), params);
    } 

    /**
     * Gets the {@link CookieSpec cookie specification} with the given ID.
     * 
     * @param id the {@link CookieSpec cookie specification} ID
     * 
     * @return {@link CookieSpec cookie specification}
     * 
     * @throws IllegalStateException if a policy with the ID cannot be found
     * 
     * @since 3.0
     */
    public static CookieSpec getCookieSpec(final String id) 
        throws IllegalStateException {
        return getCookieSpec(id, null);
    } 

    /**
     * Obtains the currently registered cookie policy names.
     * 
     * Note that the DEFAULT policy (if present) is likely to be the same
     * as one of the other policies, but does not have to be.
     * 
     * @return array of registered cookie policy names
     * 
     * @since 3.1
     */
    public static String[] getRegisteredCookieSpecs(){
            return (String[]) SPECS.keySet().toArray(new String [SPECS.size()]); 
    }
    
}
