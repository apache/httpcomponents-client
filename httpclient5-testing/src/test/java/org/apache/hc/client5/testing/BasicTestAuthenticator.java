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

package org.apache.hc.client5.testing;

import java.util.Objects;

import org.apache.hc.client5.testing.auth.AuthResult;
import org.apache.hc.client5.testing.auth.Authenticator;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.util.TextUtils;

public class BasicTestAuthenticator implements Authenticator {

    private final String userToken;
    private final String realm;

    public BasicTestAuthenticator(final String userToken, final String realm) {
        this.userToken = userToken;
        this.realm = realm;
    }

    @Override
    public boolean authenticate(final URIAuthority authority, final String requestUri, final String credentials) {
        return Objects.equals(userToken, credentials);
    }

    @Override
    public AuthResult perform(final URIAuthority authority,
                              final String requestUri,
                              final String credentials) {
        final boolean result = authenticate(authority, requestUri, credentials);
        if (result) {
            return new AuthResult(true);
        } else {
            if (TextUtils.isBlank(credentials)) {
                return new AuthResult(false);
            } else {
                final String error = credentials.endsWith("-expired") ? "token expired"  : "invalid token";
                return new AuthResult(false, new BasicNameValuePair("error", error));
            }
        }
    }

    @Override
    public String getRealm(final URIAuthority authority, final String requestUri) {
        return realm;
    }

}
