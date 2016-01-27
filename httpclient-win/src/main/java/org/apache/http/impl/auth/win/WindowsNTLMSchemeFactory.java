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

package org.apache.http.impl.auth.win;

import org.apache.http.annotation.Immutable;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.protocol.HttpContext;

/**
 * {@link AuthSchemeProvider} implementation that creates and initializes
 * {@link WindowsNegotiateScheme} using JNA to implement NTLM
 * <p>
 * EXPERIMENTAL
 * </p>
 *
 * @since 4.4
 */
@Immutable
public class WindowsNTLMSchemeFactory implements AuthSchemeProvider {

    private final String servicePrincipalName;

    public WindowsNTLMSchemeFactory(final String servicePrincipalName) {
        super();
        this.servicePrincipalName = servicePrincipalName;
    }

    @Override
    public AuthScheme create(final HttpContext context) {
        return new WindowsNegotiateScheme(AuthSchemes.NTLM, servicePrincipalName);
    }

}



