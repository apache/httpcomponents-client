/*
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

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.http.annotation.Immutable;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolException;
import org.apache.http.client.CircularRedirectException;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpTrace;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.ExecutionContext;

/**
 * Default implementation of {@link RedirectStrategy}. This strategy honors the restrictions
 * on automatic redirection of entity enclosing methods such as POST and PUT imposed by the
 * HTTP specification. <tt>302 Moved Temporarily</tt>, <tt>301 Moved Permanently</tt> and
 * <tt>307 Temporary Redirect</tt> status codes will result in an automatic redirect of
 * HEAD and GET methods only. POST and PUT methods will not be automatically redirected
 * as requiring user confirmation.
 * <p/>
 * The restriction on automatic redirection of POST methods can be relaxed by using
 * {@link LaxRedirectStrategy} instead of {@link DefaultRedirectStrategy}.
 *
 * @see LaxRedirectStrategy
 * @since 4.1
 */
@Immutable
public class DefaultRedirectStrategy implements RedirectStrategy {

    private final Log log = LogFactory.getLog(getClass());

    public static final String REDIRECT_LOCATIONS = "http.protocol.redirect-locations";

    /**
     * Redirectable methods.
     */
    private static final String[] REDIRECT_METHODS = new String[] {
        HttpGet.METHOD_NAME,
        HttpHead.METHOD_NAME
    };

    public DefaultRedirectStrategy() {
        super();
    }

    public boolean isRedirected(
            final HttpRequest request,
            final HttpResponse response,
            final HttpContext context) throws ProtocolException {
        if (request == null) {
            throw new IllegalArgumentException("HTTP request may not be null");
        }
        if (response == null) {
            throw new IllegalArgumentException("HTTP response may not be null");
        }

        int statusCode = response.getStatusLine().getStatusCode();
        String method = request.getRequestLine().getMethod();
        Header locationHeader = response.getFirstHeader("location");
        switch (statusCode) {
        case HttpStatus.SC_MOVED_TEMPORARILY:
            return isRedirectable(method) && locationHeader != null;
        case HttpStatus.SC_MOVED_PERMANENTLY:
        case HttpStatus.SC_TEMPORARY_REDIRECT:
            return isRedirectable(method);
        case HttpStatus.SC_SEE_OTHER:
            return true;
        default:
            return false;
        } //end of switch
    }

    public URI getLocationURI(
            final HttpRequest request,
            final HttpResponse response,
            final HttpContext context) throws ProtocolException {
        if (request == null) {
            throw new IllegalArgumentException("HTTP request may not be null");
        }
        if (response == null) {
            throw new IllegalArgumentException("HTTP response may not be null");
        }
        if (context == null) {
            throw new IllegalArgumentException("HTTP context may not be null");
        }
        //get the location header to find out where to redirect to
        Header locationHeader = response.getFirstHeader("location");
        if (locationHeader == null) {
            // got a redirect response, but no location header
            throw new ProtocolException(
                    "Received redirect response " + response.getStatusLine()
                    + " but no location header");
        }
        String location = locationHeader.getValue();
        if (this.log.isDebugEnabled()) {
            this.log.debug("Redirect requested to location '" + location + "'");
        }

        URI uri = createLocationURI(location);

        HttpParams params = request.getParams();
        // rfc2616 demands the location value be a complete URI
        // Location       = "Location" ":" absoluteURI
        try {
            // Drop fragment
            uri = URIUtils.rewriteURI(uri);
            if (!uri.isAbsolute()) {
                if (params.isParameterTrue(ClientPNames.REJECT_RELATIVE_REDIRECT)) {
                    throw new ProtocolException("Relative redirect location '"
                            + uri + "' not allowed");
                }
                // Adjust location URI
                HttpHost target = (HttpHost) context.getAttribute(ExecutionContext.HTTP_TARGET_HOST);
                if (target == null) {
                    throw new IllegalStateException("Target host not available " +
                            "in the HTTP context");
                }
                URI requestURI = new URI(request.getRequestLine().getUri());
                URI absoluteRequestURI = URIUtils.rewriteURI(requestURI, target, true);
                uri = URIUtils.resolve(absoluteRequestURI, uri);
            }
        } catch (URISyntaxException ex) {
            throw new ProtocolException(ex.getMessage(), ex);
        }

        RedirectLocations redirectLocations = (RedirectLocations) context.getAttribute(
                REDIRECT_LOCATIONS);
        if (redirectLocations == null) {
            redirectLocations = new RedirectLocations();
            context.setAttribute(REDIRECT_LOCATIONS, redirectLocations);
        }
        if (params.isParameterFalse(ClientPNames.ALLOW_CIRCULAR_REDIRECTS)) {
            if (redirectLocations.contains(uri)) {
                throw new CircularRedirectException("Circular redirect to '" + uri + "'");
            }
        }
        redirectLocations.add(uri);
        return uri;
    }

    /**
     * @since 4.1
     */
    protected URI createLocationURI(final String location) throws ProtocolException {
        try {
            return new URI(location).normalize();
        } catch (URISyntaxException ex) {
            throw new ProtocolException("Invalid redirect URI: " + location, ex);
        }
    }

    /**
     * @since 4.2
     */
    protected boolean isRedirectable(final String method) {
        for (String m: REDIRECT_METHODS) {
            if (m.equalsIgnoreCase(method)) {
                return true;
            }
        }
        return false;
    }

    public HttpUriRequest getRedirect(
            final HttpRequest request,
            final HttpResponse response,
            final HttpContext context) throws ProtocolException {
        URI uri = getLocationURI(request, response, context);
        String method = request.getRequestLine().getMethod();
        if (method.equalsIgnoreCase(HttpHead.METHOD_NAME)) {
            return new HttpHead(uri);
        } else if (method.equalsIgnoreCase(HttpGet.METHOD_NAME)) {
            return new HttpGet(uri);
        } else {
            int status = response.getStatusLine().getStatusCode();
            if (status == HttpStatus.SC_TEMPORARY_REDIRECT) {
                if (method.equalsIgnoreCase(HttpPost.METHOD_NAME)) {
                    return copyEntity(new HttpPost(uri), request);
                } else if (method.equalsIgnoreCase(HttpPut.METHOD_NAME)) {
                    return copyEntity(new HttpPut(uri), request);
                } else if (method.equalsIgnoreCase(HttpDelete.METHOD_NAME)) {
                    return new HttpDelete(uri);
                } else if (method.equalsIgnoreCase(HttpTrace.METHOD_NAME)) {
                    return new HttpTrace(uri);
                } else if (method.equalsIgnoreCase(HttpOptions.METHOD_NAME)) {
                    return new HttpOptions(uri);
                } else if (method.equalsIgnoreCase(HttpPatch.METHOD_NAME)) {
                    return copyEntity(new HttpPatch(uri), request);
                }
            }
            return new HttpGet(uri);
        }
    }

    private HttpUriRequest copyEntity(
            final HttpEntityEnclosingRequestBase redirect, final HttpRequest original) {
        if (original instanceof HttpEntityEnclosingRequest) {
            redirect.setEntity(((HttpEntityEnclosingRequest) original).getEntity());
        }
        return redirect;
    }

}
