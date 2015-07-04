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
package org.apache.http.impl.auth;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.http.NameValuePair;
import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.auth.AuthChallenge;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.ChallengeType;
import org.apache.http.auth.MalformedChallengeException;

/**
 * Abstract authentication scheme class that lays foundation for standard HTTP authentication schemes and
 * provides capabilities common to all authentication schemes defined in the HTTP specification.
 *
 * @since 4.0
 */
@NotThreadSafe
public abstract class StandardAuthScheme implements AuthScheme, Serializable {

    private static final long serialVersionUID = -2845454858205884623L;

    private final Map<String, String> paramMap;
    private ChallengeType challengeType;

    /**
     * @since 4.3
     */
    public StandardAuthScheme() {
        super();
        this.paramMap = new LinkedHashMap<>();
    }

    protected void update(final ChallengeType challengeType, final AuthChallenge authChallenge) throws MalformedChallengeException {
        final List<NameValuePair> params = authChallenge.getParams();
        this.challengeType = challengeType;
        if (params != null) {
            for (NameValuePair param: params) {
                this.paramMap.put(param.getName().toLowerCase(Locale.ROOT), param.getValue());
            }
        }
    }

    @Override
    public String getRealm() {
        return getParameter("realm");
    }

    protected Map<String, String> getParameters() {
        return this.paramMap;
    }

    /**
     * Returns authentication parameter with the given name, if available.
     *
     * @param name The name of the parameter to be returned
     *
     * @return the parameter with the given name
     */
    @Override
    public String getParameter(final String name) {
        if (name == null) {
            return null;
        }
        return this.paramMap.get(name.toLowerCase(Locale.ROOT));
    }

    /**
     * Returns {@code true} if authenticating against a proxy, {@code false}
     * otherwise.
     */
    public boolean isProxy() {
        return this.challengeType != null && this.challengeType == ChallengeType.PROXY;
    }

    @Override
    public String toString() {
        return getSchemeName() + "(" + this.challengeType + ") " + this.paramMap;
    }

}
