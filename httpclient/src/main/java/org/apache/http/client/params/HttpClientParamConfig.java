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

package org.apache.http.client.params;

import java.util.Collection;

import org.apache.http.auth.params.AuthPNames;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.params.ConnRouteParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;

/**
 * @deprecated (4.3) provided for compatibility with {@link HttpParams}. Do not use.
 *
 * @since 4.3
 */
@Deprecated
public final class HttpClientParamConfig {

    private HttpClientParamConfig() {
    }

    @SuppressWarnings("unchecked")
    public static RequestConfig getRequestConfig(final HttpParams params) {
        return RequestConfig.custom()
                .setAuthenticationEnabled(HttpClientParams.isAuthenticating(params))
                .setCircularRedirectsAllowed(params.isParameterFalse(ClientPNames.REJECT_RELATIVE_REDIRECT))
                .setConnectionRequestTimeout((int) HttpClientParams.getConnectionManagerTimeout(params))
                .setConnectTimeout(HttpConnectionParams.getConnectionTimeout(params))
                .setCookieSpec(HttpClientParams.getCookiePolicy(params))
                .setDefaultProxy(ConnRouteParams.getDefaultProxy(params))
                .setExpectContinueEnabled(HttpProtocolParams.useExpectContinue(params))
                .setLocalAddress(ConnRouteParams.getLocalAddress(params))
                .setMaxRedirects(params.getIntParameter(ClientPNames.MAX_REDIRECTS, 50))
                .setProxyPreferredAuthSchemes((Collection<String>) params.getParameter(
                        AuthPNames.PROXY_AUTH_PREF))
                .setTargetPreferredAuthSchemes((Collection<String>) params.getParameter(
                        AuthPNames.TARGET_AUTH_PREF))
                .setRedirectsEnabled(HttpClientParams.isRedirecting(params))
                .setRelativeRedirectsAllowed(params.isParameterTrue(ClientPNames.ALLOW_CIRCULAR_REDIRECTS))
                .setSocketTimeout(HttpConnectionParams.getSoTimeout(params))
                .setStaleConnectionCheckEnabled(HttpConnectionParams.isStaleCheckingEnabled(params))
                .build();
    }

}
