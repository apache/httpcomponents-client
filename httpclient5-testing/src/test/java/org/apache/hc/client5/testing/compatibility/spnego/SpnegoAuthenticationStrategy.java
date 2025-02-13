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
package org.apache.hc.client5.testing.compatibility.spnego;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.impl.DefaultAuthenticationStrategy;

public class SpnegoAuthenticationStrategy extends DefaultAuthenticationStrategy {

    private static final List<String> SPNEGO_SCHEME_PRIORITY =
            Collections.unmodifiableList(
                Arrays.asList(StandardAuthScheme.SPNEGO,
                    StandardAuthScheme.BEARER,
                    StandardAuthScheme.DIGEST,
                    StandardAuthScheme.BASIC));

    @Override
    protected final List<String> getSchemePriority() {
        return SPNEGO_SCHEME_PRIORITY;
    }
}