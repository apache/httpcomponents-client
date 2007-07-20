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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.ProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.conn.ManagedClientConnection;
import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.CookieOrigin;
import org.apache.http.cookie.CookieSpec;
import org.apache.http.cookie.CookieSpecRegistry;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.ExecutionContext;

/**
 * Request interceptor that matches cookies available in the current
 * {@link CookieStore} to the request being executed and generates 
 * corresponding cookierequest headers.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public class RequestAddCookies implements HttpRequestInterceptor {

    private static final Log LOG = LogFactory.getLog(RequestAddCookies.class);
    
    public RequestAddCookies() {
        super();
    }
    
    public void process(final HttpRequest request, final HttpContext context) 
            throws HttpException, IOException {
        if (request == null) {
            throw new IllegalArgumentException("HTTP request may not be null");
        }
        if (context == null) {
            throw new IllegalArgumentException("HTTP context may not be null");
        }
        
        // Obtain cookie store
        CookieStore cookieStore = (CookieStore) context.getAttribute(
                ClientContext.COOKIE_STORE);
        if (cookieStore == null) {
            LOG.info("Cookie store not available in HTTP context");
            return;
        }
        
        // Obtain the registry of cookie specs
        CookieSpecRegistry registry= (CookieSpecRegistry) context.getAttribute(
                ClientContext.COOKIESPEC_REGISTRY);
        if (registry == null) {
            LOG.info("CookieSpec registry not available in HTTP context");
            return;
        }
        
        // Obtain the target host (required)
        HttpHost targetHost = (HttpHost) context.getAttribute(
                ExecutionContext.HTTP_TARGET_HOST);
        if (targetHost == null) {
            throw new IllegalStateException("Target host not specified in HTTP context");
        }
        
        // Obtain the client connection (required)
        ManagedClientConnection conn = (ManagedClientConnection) context.getAttribute(
                ExecutionContext.HTTP_CONNECTION);
        if (conn == null) {
            throw new IllegalStateException("Client connection not specified in HTTP context");
        }

        String policy = HttpClientParams.getCookiePolicy(request.getParams());
        if (LOG.isDebugEnabled()) {
            LOG.debug("CookieSpec selected: " + policy);
        }
        
        URI requestURI;
        if (request instanceof HttpUriRequest) {
            requestURI = ((HttpUriRequest) request).getURI();
        } else {
            try {
                requestURI = new URI(request.getRequestLine().getUri());
            } catch (URISyntaxException ex) {
                throw new ProtocolException("Invalid request URI: " + 
                        request.getRequestLine().getUri(), ex);
            }
        }
        
        String hostName = targetHost.getHostName();
        int port = targetHost.getPort();
        if (port < 0) {
            port = conn.getRemotePort();
        }
        
        CookieOrigin cookieOrigin = new CookieOrigin(
                hostName, 
                port, 
                requestURI.getPath(),
                conn.isSecure());
        
        // Get an instance of the selected cookie policy
        CookieSpec cookieSpec = registry.getCookieSpec(policy, request.getParams());
        // Get all cookies available in the HTTP state
        Cookie[] cookies = cookieStore.getCookies();
        // Find cookies matching the given origin
        List matchedCookies = new ArrayList(cookies.length);
        for (int i = 0; i < cookies.length; i++) {
            Cookie cookie = cookies[i];
            if (cookieSpec.match(cookie, cookieOrigin)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Cookie " + cookie + " match " + cookieOrigin);
                }
                matchedCookies.add(cookie);
            }
        }
        // Generate Cookie request headers
        cookies = (Cookie[]) matchedCookies.toArray(new Cookie[matchedCookies.size()]);
        if (cookies.length > 0) {
            Header[] headers = cookieSpec.formatCookies(cookies);
            for (int i = 0; i < headers.length; i++) {
                request.addHeader(headers[i]);
            }
        }
        
        // Stick the CookieSpec and CookieOrigin instances to the HTTP context
        // so they could be obtained by the response interceptor
        context.setAttribute(ClientContext.COOKIE_SPEC, cookieSpec);
        context.setAttribute(ClientContext.COOKIE_ORIGIN, cookieOrigin);
    }
    
}
