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

package org.apache.hc.client5.http.impl.protocol;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.protocol.CircularRedirectException;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.protocol.RedirectLocations;
import org.apache.hc.client5.http.protocol.RedirectStrategy;
import org.apache.hc.client5.http.sync.methods.HttpGet;
import org.apache.hc.client5.http.sync.methods.HttpUriRequest;
import org.apache.hc.client5.http.sync.methods.RequestBuilder;
import org.apache.hc.client5.http.utils.URIUtils;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TextUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Default implementation of {@link RedirectStrategy}. This strategy honors the restrictions
 * on automatic redirection of unsafe methods such as POST, PUT and DELETE imposed by
 * the HTTP specification. Non safe methods will be redirected as GET in response to
 * status code {@link HttpStatus#SC_MOVED_PERMANENTLY}, {@link HttpStatus#SC_MOVED_TEMPORARILY}
 * and {@link HttpStatus#SC_SEE_OTHER}.
 *
 * @since 4.1
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class DefaultRedirectStrategy<T extends HttpRequest> implements RedirectStrategy {

    private final Logger log = LogManager.getLogger(getClass());

    public static final DefaultRedirectStrategy INSTANCE = new DefaultRedirectStrategy();

    private final ConcurrentMap<String, Boolean> safeMethods;

    public DefaultRedirectStrategy(final String... safeMethods) {
        super();
        this.safeMethods = new ConcurrentHashMap<>();
        for (String safeMethod: safeMethods) {
            this.safeMethods.put(safeMethod.toUpperCase(Locale.ROOT), Boolean.TRUE);
        }
    }

    public DefaultRedirectStrategy() {
        this("GET", "HEAD", "OPTIONS", "TRACE");
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
        final int statusCode = response.getCode();
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
                uri = URIUtils.resolve(request.getUri(), uri);
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
            final ClassicHttpRequest request,
            final HttpResponse response,
            final HttpContext context) throws HttpException {
        final URI uri = getLocationURI(request, response, context);
        final int statusCode = response.getCode();
        switch (statusCode) {
            case HttpStatus.SC_MOVED_PERMANENTLY:
            case HttpStatus.SC_MOVED_TEMPORARILY:
            case HttpStatus.SC_SEE_OTHER:
                final String method = request.getMethod().toUpperCase(Locale.ROOT);
                if (!this.safeMethods.containsKey(method)) {
                    final HttpGet httpGet = new HttpGet(uri);
                    httpGet.setHeaders(request.getAllHeaders());
                    return httpGet;
                }
            case HttpStatus.SC_TEMPORARY_REDIRECT:
            default:
                return RequestBuilder.copy(request).setUri(uri).build();
        }
    }

}
