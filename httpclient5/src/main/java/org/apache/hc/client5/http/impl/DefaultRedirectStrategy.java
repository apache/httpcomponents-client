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

package org.apache.hc.client5.http.impl;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.Locale;

import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.protocol.RedirectStrategy;
import org.apache.hc.client5.http.utils.URIUtils;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.util.Args;

/**
 * Default implementation of {@link RedirectStrategy}.
 *
 * @since 4.1
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public class DefaultRedirectStrategy implements RedirectStrategy {

    private final SchemePortResolver schemePortResolver;

    /**
     * Default instance of {@link DefaultRedirectStrategy}.
     */
    public static final DefaultRedirectStrategy INSTANCE = new DefaultRedirectStrategy();

    /**
     * Creates a new {@code DefaultRedirectStrategy} using the given {@link SchemePortResolver}.
     * If {@code schemePortResolver} is {@code null}, this will default to
     * {@link DefaultSchemePortResolver#INSTANCE}.
     *
     * @param schemePortResolver the resolver to use for determining default ports
     * @since 5.6
     */
    public DefaultRedirectStrategy(final SchemePortResolver schemePortResolver) {
        this.schemePortResolver = schemePortResolver != null ? schemePortResolver : DefaultSchemePortResolver.INSTANCE;
    }

    /**
     * Creates a new {@code DefaultRedirectStrategy} with the default
     * {@link DefaultSchemePortResolver#INSTANCE}.
     *
     * @since 5.6
     */
    public DefaultRedirectStrategy() {
        this(null);
    }

    @Override
    public boolean isRedirectAllowed(
            final HttpHost currentTarget,
            final HttpHost newTarget,
            final HttpRequest redirect,
            final HttpContext context) {

        // If authority (host + effective port) differs, strip sensitive headers
        if (!isSameAuthority(currentTarget, newTarget)) {
            for (final Iterator<Header> it = redirect.headerIterator(); it.hasNext(); ) {
                final Header header = it.next();
                if (header.isSensitive()
                        || header.getName().equalsIgnoreCase(HttpHeaders.AUTHORIZATION)
                        || header.getName().equalsIgnoreCase(HttpHeaders.COOKIE)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isSameAuthority(final HttpHost h1, final HttpHost h2) {
        if (h1 == null || h2 == null) {
            return false;
        }
        final String host1 = h1.getHostName();
        final String host2 = h2.getHostName();
        if (!host1.equalsIgnoreCase(host2)) {
            return false;
        }
        final int port1 = schemePortResolver.resolve(h1);
        final int port2 = schemePortResolver.resolve(h2);
        return port1 == port2;
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
            case HttpStatus.SC_PERMANENT_REDIRECT:
                return true;
            default:
                return false;
        }
    }

    @Override
    public URI getLocationURI(
            final HttpRequest request,
            final HttpResponse response,
            final HttpContext context) throws HttpException {
        Args.notNull(request, "HTTP request");
        Args.notNull(response, "HTTP response");
        Args.notNull(context, "HTTP context");

        //get the location header to find out where to redirect to
        final Header locationHeader = response.getFirstHeader(HttpHeaders.LOCATION);
        if (locationHeader == null) {
            throw new HttpException("Redirect location is missing");
        }
        final String location = locationHeader.getValue();
        URI uri = createLocationURI(location);
        try {
            if (!uri.isAbsolute()) {
                // Resolve location URI
                uri = URIUtils.resolve(request.getUri(), uri);
            }
        } catch (final URISyntaxException ex) {
            throw new ProtocolException(ex.getMessage(), ex);
        }

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
            if (b.isPathEmpty()) {
                b.setPathSegments("");
            }
            return b.build();
        } catch (final URISyntaxException ex) {
            throw new ProtocolException("Invalid redirect URI: " + location, ex);
        }
    }

}
