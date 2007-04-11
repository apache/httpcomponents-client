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

package org.apache.http.auth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.auth.DigestScheme;

/**
 * Authentication policy class. The Authentication policy provides corresponding
 * authentication scheme interfrace for a given type of authorization challenge. 
 * <p>The following specifications are provided:
 *  <ul>
 *   <li><tt>Basic</tt>: Basic authentication scheme as defined in RFC2617
 *           (considered inherently insecure, but most widely supported)
 *   <li><tt>Digest</tt>: Digest authentication scheme as defined in RFC2617
 *   <li><tt>NTLM</tt>: The NTLM scheme is a proprietary Microsoft Windows 
 *           Authentication protocol (considered to be the most secure among
 *           currently supported authentication schemes)
 *  </ul>
 * 
 * @author <a href="mailto:oleg@ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * @since 3.0
 */
public abstract class AuthPolicy {

    private static final HashMap   SCHEMES     = new HashMap();
    private static final ArrayList SCHEME_LIST = new ArrayList();
    
    /**
     * The key used to look up the list of IDs of supported {@link AuthScheme 
     * authentication schemes} in their order of preference. The scheme IDs are 
     * stored in a {@link java.util.Collection} as {@link java.lang.String}s. 
     * 
     * <p>
     * If several schemes are returned in the <tt>WWW-Authenticate</tt> 
     * or <tt>Proxy-Authenticate</tt> header, this parameter defines which
     * {@link AuthScheme authentication schemes} takes precedence over others.
     * The first item in the collection represents the most preferred 
     * {@link AuthScheme authentication scheme}, the last item represents the ID 
     * of the least preferred one.
     * </p>
     * 
     * @see org.apache.commons.httpclient.params.DefaultHttpParams
     */
    public static final String AUTH_SCHEME_PRIORITY = "http.auth.scheme-priority";

    /**
     * The NTLM scheme is a proprietary Microsoft Windows Authentication 
     * protocol (considered to be the most secure among currently supported 
     * authentication schemes).
     */
    public static final String NTLM = "NTLM";
    
    /** 
     * Digest authentication scheme as defined in RFC2617.
     */
    public static final String DIGEST = "Digest";

    /** 
     * Basic authentication scheme as defined in RFC2617 (considered inherently
     * insecure, but most widely supported)
     */
    public static final String BASIC = "Basic";
    
    static {
//        AuthPolicy.registerAuthScheme(NTLM,   NTLMScheme.class);
        AuthPolicy.registerAuthScheme(DIGEST, DigestScheme.class);
        AuthPolicy.registerAuthScheme(BASIC,  BasicScheme.class);
    }
    
    /** Log object. */
    protected static final Log LOG = LogFactory.getLog(AuthPolicy.class);

    /**
     * Registers a class implementing an {@link AuthScheme authentication scheme} with 
     * the given identifier. If a class with the given ID already exists it will be overridden.  
     * This ID is the same one used to retrieve the {@link AuthScheme authentication scheme} 
     * from {@link #getAuthScheme(String)}.
     * 
     * <p>
     * Please note that custom authentication preferences, if used, need to be updated accordingly 
     * for the new {@link AuthScheme authentication scheme} to take effect.
     * </p>    
     * 
     * @param id the identifier for this scheme
     * @param clazz the class to register
     * 
     * @see #getAuthScheme(String)
     * @see #AUTH_SCHEME_PRIORITY
     */
    public static synchronized void registerAuthScheme(final String id, Class clazz) {
         if (id == null) {
             throw new IllegalArgumentException("Id may not be null");
         }
        if (clazz == null) {
            throw new IllegalArgumentException("Authentication scheme class may not be null");
        }
        SCHEMES.put(id.toLowerCase(), clazz);
        SCHEME_LIST.add(id.toLowerCase());
    }

    /**
     * Unregisters the class implementing an {@link AuthScheme authentication scheme} with 
     * the given ID.
     * 
     * @param id the ID of the class to unregister
     */
    public static synchronized void unregisterAuthScheme(final String id) {
         if (id == null) {
             throw new IllegalArgumentException("Id may not be null");
         }
        SCHEMES.remove(id.toLowerCase());
        SCHEME_LIST.remove(id.toLowerCase());
    }

    /**
     * Gets the {@link AuthScheme authentication scheme} with the given ID.
     * 
     * @param id the {@link AuthScheme authentication scheme} ID
     * 
     * @return {@link AuthScheme authentication scheme}
     * 
     * @throws IllegalStateException if a scheme with the ID cannot be found
     */
    public static synchronized AuthScheme getAuthScheme(final String id) 
        throws IllegalStateException {

        if (id == null) {
            throw new IllegalArgumentException("Id may not be null");
        }
        Class clazz = (Class)SCHEMES.get(id.toLowerCase());
        if (clazz != null) {
            try {
                return (AuthScheme)clazz.newInstance();
            } catch (Exception e) {
                LOG.error("Error initializing authentication scheme: " + id, e);
                throw new IllegalStateException(id + 
                    " authentication scheme implemented by " +
                    clazz.getName() + " could not be initialized");
            }
        } else {
            throw new IllegalStateException("Unsupported authentication scheme " + id);
        }
    } 

    /**
     * Returns a list containing all registered {@link AuthScheme authentication 
     * schemes} in their default order.
     * 
     * @return {@link AuthScheme authentication scheme}
     */
    public static synchronized List getDefaultAuthPrefs() {
        return (List)SCHEME_LIST.clone(); 
    } 
}
