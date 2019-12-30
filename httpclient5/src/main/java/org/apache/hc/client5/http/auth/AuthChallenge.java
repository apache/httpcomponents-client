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
package org.apache.hc.client5.http.auth;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.util.Args;

/**
 * This class represents an authentication challenge consisting of a auth scheme
 * and either a single parameter or a list of name / value pairs.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public final class AuthChallenge {

    private final ChallengeType challengeType;
    private final String schemeName;
    private final String value;
    private final List<NameValuePair> params;

    public AuthChallenge(final ChallengeType challengeType, final String schemeName, final String value, final List<? extends NameValuePair> params) {
        super();
        this.challengeType = Args.notNull(challengeType, "Challenge type");
        this.schemeName = Args.notNull(schemeName, "schemeName");
        this.value = value;
        this.params = params != null ? Collections.unmodifiableList(new ArrayList<>(params)) : null;
    }

    public AuthChallenge(final ChallengeType challengeType, final String schemeName, final NameValuePair... params) {
        this(challengeType, schemeName, null, Arrays.asList(params));
    }

    public ChallengeType getChallengeType() {
        return challengeType;
    }

    public String getSchemeName() {
        return schemeName;
    }

    public String getValue() {
        return value;
    }

    public List<NameValuePair> getParams() {
        return params;
    }

    @Override
    public String toString() {
        final StringBuilder buffer = new StringBuilder();
        buffer.append(schemeName).append(" ");
        if (value != null) {
            buffer.append(value);
        } else if (params != null) {
            buffer.append(params);
        }
        return buffer.toString();
    }

}

