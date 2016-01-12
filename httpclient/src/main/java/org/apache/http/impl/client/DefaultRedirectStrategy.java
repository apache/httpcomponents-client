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
import java.util.Locale;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hc.core5.annotation.Immutable;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Asserts;
import org.apache.hc.core5.util.TextUtils;
import org.apache.http.client.CircularRedirectException;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.client.utils.URIUtils;

/**
 * Default implementation of {@link RedirectStrategy}. This strategy honors the restrictions
 * on automatic redirection of entity enclosing methods such as POST and PUT imposed by the
 * HTTP specification. {@code 302 Moved Temporarily}, {@code 301 Moved Permanently} and
 * {@code 307 Temporary Redirect} status codes will result in an automatic redirect of
 * HEAD and GET methods only. POST and PUT methods will not be automatically redirected
 * as requiring user confirmation.
 *
 * @since 4.1
 */
@Immutable
public class DefaultRedirectStrategy implements RedirectStrategy {

    private final Log log = LogFactory.getLog(getClass());

    public static final DefaultRedirectStrategy INSTANCE = new DefaultRedirectStrategy();

    public DefaultRedirectStrategy() {
        super();
    }

    @Override
    public boolean isRedirected(
            final HttpRequest request,
            final HttpResponse response,
            final HttpContext context) throws ProtocolException {
        Args.notNull(request, "HTTP request");
        Args.notNull(response, "HTTP response");

        if (!response.containsHeader(HttpHeaders.LOCATION)) {
            return false;
        }
        final int statusCode = response.getStatusLine().getStatusCode();
        switch (statusCode) {
            case HttpStatus.SC_MOVED_PERMANENTLY:
            case HttpStatus.SC_MOVED_TEMPORARILY:
            case HttpStatus.SC_SEE_OTHER:
            case HttpStatus.SC_TEMPORARY_REDIRECT:
                return true;
            default:
                return false;
        }
    }

    public URI getLocationURI(
            final HttpRequest request,
            final HttpResponse response,
            final HttpContext context) throws HttpException {
        Args.notNull(request, "HTTP request");
        Args.notNull(response, "HTTP response");
        Args.notNull(context, "HTTP context");

        final HttpClientContext clientContext = HttpClientContext.adapt(context);

        //get the location header to find out where to redirect to
        final Header locationHeader = response.getFirstHeader("location");
        if (locationHeader == null) {
            throw new HttpException("Redirect location is missing");
        }
        final String location = locationHeader.getValue();
        if (this.log.isDebugEnabled()) {
            this.log.debug("Redirect requested to location '" + location + "'");
        }

        final RequestConfig config = clientContext.getRequestConfig();

        URI uri = createLocationURI(location);
        try {
            if (!uri.isAbsolute()) {
                // Resolve location URI
                final HttpHost target = clientContext.getTargetHost();
                Asserts.notNull(target, "Target host");
                final URI requestURI = new URI(request.getRequestLine().getUri());
                final URI absoluteRequestURI = URIUtils.rewriteURI(requestURI, target, false);
                uri = URIUtils.resolve(absoluteRequestURI, uri);
            }
        } catch (final URISyntaxException ex) {
            throw new ProtocolException(ex.getMessage(), ex);
        }

        RedirectLocations redirectLocations = (RedirectLocations) clientContext.getAttribute(
                HttpClientContext.REDIRECT_LOCATIONS);
        if (redirectLocations == null) {
            redirectLocations = new RedirectLocations();
            context.setAttribute(HttpClientContext.REDIRECT_LOCATIONS, redirectLocations);
        }
        if (!config.isCircularRedirectsAllowed()) {
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
            final URIBuilder b = new URIBuilder(new URI(location).normalize());
            final String host = b.getHost();
            if (host != null) {
                b.setHost(host.toLowerCase(Locale.ROOT));
            }
            final String path = b.getPath();
            if (TextUtils.isEmpty(path)) {
                b.setPath("/");
            }
            return b.build();
        } catch (final URISyntaxException ex) {
            throw new ProtocolException("Invalid redirect URI: " + location, ex);
        }
    }

    @Override
    public HttpUriRequest getRedirect(
            final HttpRequest request,
            final HttpResponse response,
            final HttpContext context) throws HttpException {
        final URI uri = getLocationURI(request, response, context);
        final int statusCode = response.getStatusLine().getStatusCode();
        switch (statusCode) {
            case HttpStatus.SC_MOVED_PERMANENTLY:
            case HttpStatus.SC_MOVED_TEMPORARILY:
            case HttpStatus.SC_SEE_OTHER:
                final String method = request.getRequestLine().getMethod();
                if (method.equalsIgnoreCase("POST")) {
                    return new HttpGet(uri);
                }
            case HttpStatus.SC_TEMPORARY_REDIRECT:
            default:
                return RequestBuilder.copy(request).setUri(uri).build();
        }
    }

}
