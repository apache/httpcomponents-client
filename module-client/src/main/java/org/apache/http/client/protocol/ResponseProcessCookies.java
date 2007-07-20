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

package org.apache.http.client.protocol;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.client.HttpState;
import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.CookieOrigin;
import org.apache.http.cookie.CookieSpec;
import org.apache.http.cookie.MalformedCookieException;
import org.apache.http.cookie.SM;
import org.apache.http.protocol.HttpContext;

/**
 * Response interceptor that populates the current {@link HttpState} with data 
 * contained in response cookies received in the given the HTTP response.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public class ResponseProcessCookies implements HttpResponseInterceptor {

    private static final Log LOG = LogFactory.getLog(ResponseProcessCookies.class);
    
    public ResponseProcessCookies() {
        super();
    }
    
    public void process(final HttpResponse response, final HttpContext context) 
            throws HttpException, IOException {
        if (response == null) {
            throw new IllegalArgumentException("HTTP request may not be null");
        }
        if (context == null) {
            throw new IllegalArgumentException("HTTP context may not be null");
        }
        
        // Obtain HTTP state
        HttpState state = (HttpState) context.getAttribute(
                ClientContext.HTTP_STATE);
        if (state == null) {
            LOG.info("HTTP state not available in HTTP context");
            return;
        }
        // Obtain actual CookieSpec instance
        CookieSpec cookieSpec = (CookieSpec) context.getAttribute(
                ClientContext.COOKIE_SPEC);
        if (cookieSpec == null) {
            LOG.info("CookieSpec not available in HTTP context");
            return;
        }
        // Obtain actual CookieOrigin instance
        CookieOrigin cookieOrigin = (CookieOrigin) context.getAttribute(
                ClientContext.COOKIE_ORIGIN);
        if (cookieOrigin == null) {
            LOG.info("CookieOrigin not available in HTTP context");
            return;
        }
        Header[] headers = response.getHeaders(SM.SET_COOKIE);
        processCookies(headers, cookieSpec, cookieOrigin, state);
    }
     
    private static void processCookies(
            final Header[] headers, 
            final CookieSpec cookieSpec,
            final CookieOrigin cookieOrigin,
            final HttpState state) {
        for (int i = 0; i < headers.length; i++) {
            Header header = headers[i];
            try {
                Cookie[] cookies = cookieSpec.parse(header, cookieOrigin);
                for (int c = 0; c < cookies.length; c++) {
                    Cookie cookie = cookies[c];
                    try {
                        cookieSpec.validate(cookie, cookieOrigin);
                        state.addCookie(cookie);
                        
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Cookie accepted: \""
                                    + cookie + "\". ");
                        }
                    } catch (MalformedCookieException ex) {
                        if (LOG.isWarnEnabled()) {
                            LOG.warn("Cookie rejected: \""
                                    + cookie + "\". " + ex.getMessage());
                        }
                    }
                }
            } catch (MalformedCookieException ex) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("Invalid cookie header: \""
                            + header + "\". " + ex.getMessage());
                }
            }
        }
    }
    
}
