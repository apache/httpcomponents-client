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

import org.apache.http.annotation.ThreadSafe;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.util.Args;

/**
 * {@link org.apache.http.client.CredentialsProvider} implementation that always returns
 * {@link org.apache.http.impl.auth.win.CurrentWindowsCredentials} instance to NTLM
 * and SPNego authentication challenges.
 * <p>
 * EXPERIMENTAL
 * </p>
 *
 * @since 4.4
 */
@ThreadSafe
public class WindowsCredentialsProvider implements CredentialsProvider {

    private final CredentialsProvider provider;

    public WindowsCredentialsProvider(final CredentialsProvider provider) {
        this.provider = Args.notNull(provider, "Credentials provider");
    }

    @Override
    public Credentials getCredentials(final AuthScope authscope) {
        final String scheme = authscope.getScheme();
        if (AuthSchemes.NTLM.equalsIgnoreCase(scheme) || AuthSchemes.SPNEGO.equalsIgnoreCase(scheme)) {
            return CurrentWindowsCredentials.INSTANCE;
        } else {
            return provider.getCredentials(authscope);
        }
    }

    @Override
    public void setCredentials(final AuthScope authscope, final Credentials credentials) {
        provider.setCredentials(authscope, credentials);
    }

    @Override
    public void clear() {
        provider.clear();
    }
}


