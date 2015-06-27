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
package org.apache.http.auth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.annotation.Immutable;
import org.apache.http.util.Args;

/**
 * This class represents an authentication challenge consisting of a auth scheme
 * and either a single parameter or a list of name / value pairs.
 *
 * @since 5.0
 */
@Immutable
public final class AuthChallenge {

    private final String scheme;
    private final String value;
    private final List<NameValuePair> params;

    public AuthChallenge(final String scheme, final String value, final List<? extends NameValuePair> params) {
        super();
        Args.notNull(scheme, "Auth scheme");
        this.scheme = scheme;
        this.value = value;
        this.params = params != null ? Collections.unmodifiableList(new ArrayList<>(params)) : null;
    }

    public String getScheme() {
        return scheme;
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
        buffer.append(scheme).append(" ");
        if (value != null) {
            buffer.append(value);
        } else if (params != null) {
            buffer.append(params);
        }
        return buffer.toString();
    }

}

