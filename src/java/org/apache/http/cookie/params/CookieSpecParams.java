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

package org.apache.http.cookie.params;

import org.apache.http.params.HttpParams;

/**
 * This class implements an adaptor around the {@link HttpParams} interface
 * to simplify manipulation of cookie management specific parameters.
 * 
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 * 
 * @version $Revision$
 * 
 * @since 4.0
 */
public final class CookieSpecParams {

    /**
     * The key used to look up the date patterns used for parsing. The String patterns are stored
     * in a {@link java.util.Collection} and must be compatible with 
     * {@link java.text.SimpleDateFormat}.
     * <p>
     * This parameter expects a value of type {@link java.util.Collection}.
     * </p>
     */
    public static final String DATE_PATTERNS = "http.protocol.cookie-datepatterns";
    
    /**
     * Defines whether {@link org.apache.commons.httpclient.Cookie cookies} should be put on 
     * a single {@link org.apache.commons.httpclient.Header response header}.
     * <p>
     * This parameter expects a value of type {@link Boolean}.
     * </p>
     */
    public static final String SINGLE_COOKIE_HEADER = "http.protocol.single-cookie-header"; 

    /**
     * Defines {@link CookieSpec cookie specification} to be used for cookie management.
     * <p>
     * This parameter expects a value of type {@link String}.
     * </p>
     */
    public static final String COOKIE_POLICY = "http.protocol.cookie-policy";
    
    /**
     * The policy that provides high degree of compatibilty 
     * with common cookie management of popular HTTP agents.
     */
    public static final String BROWSER_COMPATIBILITY = "compatibility";
    
    /** 
     * The Netscape cookie draft compliant policy. 
     */
    public static final String NETSCAPE = "netscape";

    /** 
     * The RFC 2109 compliant policy. 
     */
    public static final String RFC_2109 = "rfc2109";
    
    /** 
     * The default cookie policy. 
     */
    public static final String DEFAULT = "default";
    
    public static String getCookiePolicy(final HttpParams params) { 
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        Object param = params.getParameter(COOKIE_POLICY);
        if (param == null) {
            return DEFAULT;
        }
        return (String) param;
    }
    
    /**
     * Assigns the {@link CookiePolicy cookie policy} to be used by the 
     * {@link org.apache.commons.httpclient.HttpMethod HTTP methods} 
     * this collection of parameters applies to. 
     *
     * @param policy the {@link CookiePolicy cookie policy}
     */
    public static void setCookiePolicy(final HttpParams params, String policy) {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        params.setParameter(COOKIE_POLICY, policy);
    }

}
