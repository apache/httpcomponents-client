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

package org.apache.http.impl.client;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AUTH;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthSchemeRegistry;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.MalformedChallengeException;
import org.apache.http.client.AuthenticationHandler;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.message.BufferedHeader;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.CharArrayBuffer;

/**
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 */
public class DefaultAuthenticationHandler implements AuthenticationHandler {

    private static final Log LOG = LogFactory.getLog(DefaultAuthenticationHandler.class);
    
    private static List DEFAULT_SCHEME_PRIORITY = Arrays.asList(new String[] {
            "digest",
            "basic"
    });
    
    public DefaultAuthenticationHandler() {
        super();
    }
    
    public boolean isTargetAuthenticationRequested(
            final HttpResponse response, 
            final HttpContext context) {
        if (response == null) {
            throw new IllegalArgumentException("HTTP response may not be null");
        }
        int status = response.getStatusLine().getStatusCode();
        return status == HttpStatus.SC_UNAUTHORIZED;
    }

    public boolean isProxyAuthenticationRequested(
            final HttpResponse response, 
            final HttpContext context) {
        if (response == null) {
            throw new IllegalArgumentException("HTTP response may not be null");
        }
        int status = response.getStatusLine().getStatusCode();
        return status == HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED;
    }

    protected Map parseChallenges(
            final Header[] headers) throws MalformedChallengeException {
        
        Map map = new HashMap(headers.length);
        for (int i = 0; i < headers.length; i++) {
            Header header = headers[i];
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
            map.put(s.toLowerCase(), header);
        }
        return map;
    }
    
    public Map getTargetChallenges(
            final HttpResponse response, 
            final HttpContext context) throws MalformedChallengeException {
        if (response == null) {
            throw new IllegalArgumentException("HTTP response may not be null");
        }
        Header[] headers = response.getHeaders(AUTH.WWW_AUTH);
        return parseChallenges(headers);
    }

    public Map getProxyChallenges(
            final HttpResponse response, 
            final HttpContext context) throws MalformedChallengeException {
        if (response == null) {
            throw new IllegalArgumentException("HTTP response may not be null");
        }
        Header[] headers = response.getHeaders(AUTH.PROXY_AUTH);
        return parseChallenges(headers);
    }

    public AuthScheme selectScheme(
            final Map challenges, 
            final HttpResponse response,
            final HttpContext context) throws AuthenticationException {
        
        AuthSchemeRegistry registry = (AuthSchemeRegistry) context.getAttribute(
                HttpClientContext.AUTHSCHEME_REGISTRY);
        if (registry == null) {
            throw new IllegalStateException("AuthScheme registry not set in HTTP context");
        }
        
        HttpParams params = response.getParams();
        Collection authPrefs = (Collection) params.getParameter(
                HttpClientParams.AUTH_SCHEME_PRIORITY);
        if (authPrefs == null || authPrefs.isEmpty()) {
            authPrefs = DEFAULT_SCHEME_PRIORITY;    
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Supported authentication schemes in the order of preference: " 
                + authPrefs);
        }

        AuthScheme authScheme = null;
        for (Iterator it = authPrefs.iterator(); it.hasNext(); ) {
            String id = (String) it.next();
            Header challenge = (Header) challenges.get(id.toLowerCase()); 
            if (challenge != null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug(id + " authentication scheme selected");
                }
                try {
                    authScheme = registry.getAuthScheme(id, params);
                } catch (IllegalStateException e) {
                    throw new AuthenticationException(e.getMessage());
                }
                break;
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Challenge for " + id + " authentication scheme not available");
                    // Try again
                }
            }
        }
        if (authScheme == null) {
            // If none selected, something is wrong
            throw new AuthenticationException(
                "Unable to respond to any of these challenges: "
                    + challenges);
        }
        return authScheme;
    }

}
