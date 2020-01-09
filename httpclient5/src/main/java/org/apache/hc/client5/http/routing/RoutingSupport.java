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
package org.apache.hc.client5.http.routing;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.utils.URIUtils;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.net.URIAuthority;

public final class RoutingSupport {

    public static HttpHost determineHost(final HttpRequest request) throws HttpException {
        if (request == null) {
            return null;
        }
        final URIAuthority authority = request.getAuthority();
        if (authority != null) {
            final String scheme = request.getScheme();
            if (scheme == null) {
                throw new ProtocolException("Protocol scheme is not specified");
            }
            return new HttpHost(scheme, authority);
        }
        try {
            final URI requestURI = request.getUri();
            if (requestURI.isAbsolute()) {
                final HttpHost httpHost = URIUtils.extractHost(requestURI);
                if (httpHost == null) {
                    throw new ProtocolException("URI does not specify a valid host name: " + requestURI);
                }
                return httpHost;
            }
        } catch (final URISyntaxException ignore) {
        }
        return null;
    }

    public static HttpHost normalize(final HttpHost host, final SchemePortResolver schemePortResolver) {
        if (host == null) {
            return null;
        }
        if (host.getPort() < 0) {
            final int port = (schemePortResolver != null ? schemePortResolver: DefaultSchemePortResolver.INSTANCE).resolve(host);
            if (port > 0) {
                return new HttpHost(host.getSchemeName(), host.getHostName(), port);
            }
        }
        return host;
    }

}
