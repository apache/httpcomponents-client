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
package org.apache.hc.client5.http.osgi.impl;

import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertThat;

import java.util.Hashtable;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.NTCredentials;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.osgi.services.ProxyConfiguration;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.Test;

public class OSGiCredentialsProviderTest {

    private static final String HOST = "proxy.example.org";

    private static final int PORT = 8080;

    private static final HttpContext HTTP_CONTEXT = new BasicHttpContext();

    @Test
    public void basicAuthentication() {
        final CredentialsProvider provider = credentialsProvider(proxy("user", "secret"));
        final Credentials credentials = provider.getCredentials(new AuthScope(HOST, PORT, null, "BASIC"), HTTP_CONTEXT);
        assertThat(credentials, instanceOf(UsernamePasswordCredentials.class));
        assertCredentials((UsernamePasswordCredentials) credentials, "user", "secret");
    }

    @Test
    public void ntlmAuthenticationWithoutDomain() {
        final CredentialsProvider provider = credentialsProvider(proxy("user", "secret"));
        final Credentials credentials = provider.getCredentials(new AuthScope(HOST, PORT, null, "NTLM"), HTTP_CONTEXT);
        assertThat(credentials, instanceOf(NTCredentials.class));
        assertCredentials((NTCredentials) credentials, "user", "secret", null);
    }

    @Test
    public void ntlmAuthenticationWithDomain() {
        final CredentialsProvider provider = credentialsProvider(proxy("DOMAIN\\user", "secret"));
        final Credentials credentials = provider.getCredentials(new AuthScope(HOST, PORT, null, "NTLM"), HTTP_CONTEXT);
        assertThat(credentials, instanceOf(NTCredentials.class));
        assertCredentials((NTCredentials) credentials, "user", "secret", "DOMAIN");
    }

    private CredentialsProvider credentialsProvider(final ProxyConfiguration... proxies) {
        return new OSGiCredentialsProvider(asList(proxies));
    }

    private void assertCredentials(final UsernamePasswordCredentials credentials, final String user, final String password) {
        assertThat("Username mismatch", credentials.getUserName(), equalTo(user));
        assertThat("Password mismatch", credentials.getPassword(), equalTo(password.toCharArray()));
    }

    private void assertCredentials(final NTCredentials credentials, final String user, final String password, final String domain) {
        assertThat("Username mismatch", credentials.getUserName(), equalTo(user));
        assertThat("Password mismatch", credentials.getPassword(), equalTo(password.toCharArray()));
        assertThat("Domain mismatch", credentials.getDomain(), equalTo(domain));
    }

    private ProxyConfiguration proxy(final String username, final String password) {
        final OSGiProxyConfiguration proxyConfiguration = new OSGiProxyConfiguration();
        final Hashtable<String, Object> config = new Hashtable<>();
        config.put("proxy.enabled", true);
        config.put("proxy.host", HOST);
        config.put("proxy.port", PORT);
        config.put("proxy.user", username);
        config.put("proxy.password", password);
        config.put("proxy.exceptions", new String[0]);
        proxyConfiguration.update(config);
        return proxyConfiguration;
    }
}
