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

import java.security.Principal;

import javax.net.ssl.SSLSession;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.UserTokenHandler;
import org.apache.hc.client5.http.auth.AuthExchange;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * Default implementation of {@link UserTokenHandler}. This class will use
 * an instance of {@link Principal} as a state object for HTTP connections,
 * if it can be obtained from the given execution context. This helps ensure
 * persistent connections created with a particular user identity within
 * a particular security context can be reused by the same user only.
 * <p>
 * DefaultUserTokenHandler will use the user principal of connection
 * based authentication schemes such as NTLM or that of the SSL session
 * with the client authentication turned on. If both are unavailable,
 * {@code null} token will be returned.
 * </p>
 *
 * @since 4.0
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public class DefaultUserTokenHandler implements UserTokenHandler {

    public static final DefaultUserTokenHandler INSTANCE = new DefaultUserTokenHandler();

    @Override
    public Object getUserToken(final HttpRoute route, final HttpContext context) {
        return getUserToken(route, null, context);
    }

    @Override
    public Object getUserToken(final HttpRoute route, final HttpRequest request, final HttpContext context) {

        final HttpClientContext clientContext = HttpClientContext.adapt(context);

        final HttpHost target = request != null ? new HttpHost(request.getScheme(), request.getAuthority()) : route.getTargetHost();

        final AuthExchange targetAuthExchange = clientContext.getAuthExchange(target);
        if (targetAuthExchange != null) {
            final Principal authPrincipal = getAuthPrincipal(targetAuthExchange);
            if (authPrincipal != null) {
                return authPrincipal;
            }
        }
        final HttpHost proxy = route.getProxyHost();
        if (proxy != null) {
            final AuthExchange proxyAuthExchange = clientContext.getAuthExchange(proxy);
            if (proxyAuthExchange != null) {
                final Principal authPrincipal = getAuthPrincipal(proxyAuthExchange);
                if (authPrincipal != null) {
                    return authPrincipal;
                }
            }
        }
        final SSLSession sslSession = clientContext.getSSLSession();
        if (sslSession != null) {
            return sslSession.getLocalPrincipal();
        }
        return null;
    }

    private static Principal getAuthPrincipal(final AuthExchange authExchange) {
        final AuthScheme scheme = authExchange.getAuthScheme();
        if (scheme != null && scheme.isConnectionBased()) {
            return scheme.getPrincipal();
        }
        return null;
    }

}
