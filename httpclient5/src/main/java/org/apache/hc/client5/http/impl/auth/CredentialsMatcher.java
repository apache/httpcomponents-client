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
package org.apache.hc.client5.http.impl.auth;

import java.util.Map;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;

final class CredentialsMatcher {

    /**
     * Find matching {@link Credentials credentials} for the given authentication scope.
     *
     * @param map the credentials hash map
     * @param authScope the {@link AuthScope authentication scope}
     * @return the credentials
     *
     */
    static Credentials matchCredentials(final Map<AuthScope, Credentials> map, final AuthScope authScope) {
        // see if we get a direct hit
        Credentials creds = map.get(authScope);
        if (creds == null) {
            // Nope.
            // Do a full scan
            int bestMatchFactor  = -1;
            AuthScope bestMatch  = null;
            for (final AuthScope current: map.keySet()) {
                final int factor = authScope.match(current);
                if (factor > bestMatchFactor) {
                    bestMatchFactor = factor;
                    bestMatch = current;
                }
            }
            if (bestMatch != null) {
                creds = map.get(bestMatch);
            }
        }
        return creds;
    }

}
