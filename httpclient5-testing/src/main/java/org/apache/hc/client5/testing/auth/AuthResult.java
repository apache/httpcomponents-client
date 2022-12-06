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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.util.LangUtils;

public final class AuthResult {

    private final boolean success;
    private final List<NameValuePair> params;

    public AuthResult(final boolean success, final List<NameValuePair> params) {
        this.success = success;
        this.params = params != null ? Collections.unmodifiableList(params) : Collections.emptyList();
    }

    public AuthResult(final boolean success, final NameValuePair... params) {
        this(success, Arrays.asList(params));
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean hasParams() {
        return !params.isEmpty();
    }

    public List<NameValuePair> getParams() {
        return params;
    }

    @Override
    public int hashCode() {
        int hash = LangUtils.HASH_SEED;
        hash = LangUtils.hashCode(hash, this.success);
        return hash;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof AuthResult) {
            final AuthResult that = (AuthResult) o;
            return this.success == that.success;
        }
        return false;
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append(success);
        if (!params.isEmpty()) {
            buf.append(" ").append(params);
        }
        return buf.toString();
    }

}
