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

package org.apache.hc.client5.testing.auth;

import java.util.List;

import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.ProtocolException;

abstract class AbstractAuthenticationHandler implements AuthenticationHandler<String> {

    abstract String getSchemeName();

    @Override
    public final String challenge(final List<NameValuePair> params) {
        final StringBuilder buf = new StringBuilder();
        buf.append(getSchemeName());
        if (params != null && params.size() > 0) {
            buf.append(" ");
            for (int i = 0; i < params.size(); i++) {
                if (i > 0) {
                    buf.append(", ");
                }
                final NameValuePair param = params.get(i);
                buf.append(param.getName()).append("=\"").append(param.getValue()).append("\"");
            }
        }
        return buf.toString();
    }

    abstract String decodeChallenge(String challenge);

    public final String extractAuthToken(final String challengeResponse) throws HttpException {
        final int i = challengeResponse.indexOf(' ');
        if (i == -1) {
            throw new ProtocolException("Invalid " + getSchemeName() + " challenge response");
        }
        final String schemeName = challengeResponse.substring(0, i);
        if (schemeName.equalsIgnoreCase(getSchemeName())) {
            final String s = challengeResponse.substring(i + 1).trim();
            try {
                return decodeChallenge(s);
            } catch (final IllegalArgumentException ex) {
                throw new ProtocolException("Malformed " + getSchemeName() + " credentials");
            }
        } else {
            throw new ProtocolException("Unexpected challenge type");
        }
    }

}
