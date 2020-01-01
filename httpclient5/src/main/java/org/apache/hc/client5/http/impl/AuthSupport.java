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

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.CredentialsStore;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.util.Args;

/**
 * Authentication support methods.
 *
 * @since 5.0
 */
@Internal
public class AuthSupport {

    public static void extractFromAuthority(
            final String scheme,
            final URIAuthority authority,
            final CredentialsStore credentialsStore) {
        Args.notNull(credentialsStore, "Credentials store");
        if (authority == null) {
            return;
        }
        final String userInfo = authority.getUserInfo();
        if (userInfo == null) {
            return;
        }
        final int atColon = userInfo.indexOf(':');
        final String userName = atColon >= 0 ? userInfo.substring(0, atColon) : userInfo;
        final char[] password = atColon >= 0 ? userInfo.substring(atColon + 1).toCharArray() : null;

        credentialsStore.setCredentials(
                new AuthScope(scheme, authority.getHostName(), authority.getPort(), null, StandardAuthScheme.BASIC),
                new UsernamePasswordCredentials(userName, password));
    }

    public static HttpHost resolveAuthTarget(final HttpRequest request, final HttpRoute route) {
        Args.notNull(request, "Request");
        Args.notNull(route, "Route");
        final URIAuthority authority = request.getAuthority();
        final String scheme = request.getScheme();
        final HttpHost target = authority != null ? new HttpHost(scheme, authority) : route.getTargetHost();
        if (target.getPort() < 0) {
            return new HttpHost(
                    target.getSchemeName(),
                    target.getHostName(),
                    route.getTargetHost().getPort());
        }
        return target;
    }

}
