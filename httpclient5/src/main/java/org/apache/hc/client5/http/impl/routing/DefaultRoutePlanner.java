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

package org.apache.hc.client5.http.impl.routing;

import java.net.InetAddress;
import java.util.Objects;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.client5.http.routing.RoutingSupport;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.net.NamedEndpoint;
import org.apache.hc.core5.net.URIAuthority;

/**
 * Default implementation of an {@link HttpRoutePlanner}. It will not make use of
 * any Java system properties, nor of system or browser proxy settings.
 *
 * @since 4.3
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public class DefaultRoutePlanner implements HttpRoutePlanner {

    private final SchemePortResolver schemePortResolver;

    public DefaultRoutePlanner(final SchemePortResolver schemePortResolver) {
        super();
        this.schemePortResolver = schemePortResolver != null ? schemePortResolver : DefaultSchemePortResolver.INSTANCE;
    }

    private static boolean sameNamedEndpoint(final NamedEndpoint n1, final NamedEndpoint n2) {
        if (n1 == null || n2 == null) {
            return false;
        }
        return Objects.equals(n1.getHostName(), n2.getHostName()) && n1.getPort() == n2.getPort();
    }

    @Override
    public final HttpRoute determineRoute(final HttpHost host, final HttpRequest request, final HttpContext context) throws HttpException {
        if (host == null) {
            throw new ProtocolException("Target host is not specified");
        }
        final HttpClientContext clientContext = HttpClientContext.cast(context);
        final RequestConfig config = clientContext.getRequestConfigOrDefault();
        @SuppressWarnings("deprecation")
        HttpHost proxy = config.getProxy();
        if (proxy == null) {
            proxy = determineProxy(host, context);
        }
        final HttpHost target = RoutingSupport.normalize(host, schemePortResolver);
        if (target.getPort() < 0) {
            throw new ProtocolException("Unroutable protocol scheme: " + target);
        }
        final boolean secure = URIScheme.HTTPS.same(target.getSchemeName());
        final URIAuthority authority;
        if (secure && request != null && !sameNamedEndpoint(request.getAuthority(), host)) {
            authority = request.getAuthority();
        } else {
            authority = null;
        }
        final InetAddress inetAddress = determineLocalAddress(target, context);

        if (proxy == null) {
            return new HttpRoute(target, authority, inetAddress, secure);
        }
        return new HttpRoute(target, authority, inetAddress, proxy, secure);
    }

    @Override
    public final HttpRoute determineRoute(final HttpHost host, final HttpContext context) throws HttpException {
        return determineRoute(host, null, context);
    }

    /**
     * This implementation returns null.
     *
     * @throws HttpException may be thrown if overridden
     */
    protected HttpHost determineProxy(
            final HttpHost target,
            final HttpContext context) throws HttpException {
        return null;
    }

    /**
     * This implementation returns null.
     *
     * @throws HttpException may be thrown if overridden
     */
    protected InetAddress determineLocalAddress(
            final HttpHost firstHop,
            final HttpContext context) throws HttpException {
        return null;
    }

}
