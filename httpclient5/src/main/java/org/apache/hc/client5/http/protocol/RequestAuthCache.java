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

package org.apache.hc.client5.http.protocol;

import java.io.IOException;

import org.apache.hc.client5.http.RouteInfo;
import org.apache.hc.client5.http.auth.AuthCache;
import org.apache.hc.client5.http.auth.AuthExchange;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.impl.RequestSupport;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Request interceptor that can preemptively authenticate against known hosts,
 * if there is a cached {@link AuthScheme} instance in the local
 * {@link AuthCache} associated with the given target or proxy host.
 *
 * @since 4.1
 *
 * @deprecated Do not use.
 */
@Deprecated
@Contract(threading = ThreadingBehavior.STATELESS)
public class RequestAuthCache implements HttpRequestInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(RequestAuthCache.class);

    public RequestAuthCache() {
        super();
    }

    @Override
    public void process(final HttpRequest request, final EntityDetails entity, final HttpContext context)
            throws HttpException, IOException {
        Args.notNull(request, "HTTP request");
        Args.notNull(context, "HTTP context");

        final HttpClientContext clientContext = HttpClientContext.adapt(context);
        final String exchangeId = clientContext.getExchangeId();

        final AuthCache authCache = clientContext.getAuthCache();
        if (authCache == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} Auth cache not set in the context", exchangeId);
            }
            return;
        }

        final CredentialsProvider credsProvider = clientContext.getCredentialsProvider();
        if (credsProvider == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} Credentials provider not set in the context", exchangeId);
            }
            return;
        }

        final RouteInfo route = clientContext.getHttpRoute();
        if (route == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} Route info not set in the context", exchangeId);
            }
            return;
        }

        final HttpHost target = new HttpHost(request.getScheme(), request.getAuthority());
        final AuthExchange targetAuthExchange = clientContext.getAuthExchange(target);
        if (targetAuthExchange.getState() == AuthExchange.State.UNCHALLENGED) {
            final String pathPrefix = RequestSupport.extractPathPrefix(request);
            final AuthScheme authScheme = authCache.get(target, pathPrefix);
            if (authScheme != null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("{} Re-using cached '{}' auth scheme for {}", exchangeId, authScheme.getName(), target);
                }
                targetAuthExchange.select(authScheme);
            }
        }

        final HttpHost proxy = route.getProxyHost();
        if (proxy != null) {
            final AuthExchange proxyAuthExchange = clientContext.getAuthExchange(proxy);
            if (proxyAuthExchange.getState() == AuthExchange.State.UNCHALLENGED) {
                final AuthScheme authScheme = authCache.get(proxy, null);
                if (authScheme != null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("{} Re-using cached '{}' auth scheme for {}", exchangeId, authScheme.getName(), proxy);
                    }
                    proxyAuthExchange.select(authScheme);
                }
            }
        }
    }

}
