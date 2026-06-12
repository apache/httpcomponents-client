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

package org.apache.hc.client5.testing.extension.async;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.function.Consumer;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.H2AsyncClientBuilder;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http2.ssl.H2ClientTlsStrategy;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.ssl.SSLContexts;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class H2AsyncClientResource implements AfterEachCallback {

    private final H2AsyncClientBuilder clientBuilder;
    private CloseableHttpAsyncClient client;

    public H2AsyncClientResource(final HttpHost proxy) throws IOException {
        try {
            this.clientBuilder = H2AsyncClientBuilder.create()
                    .setTlsStrategy(new H2ClientTlsStrategy(SSLContexts.custom()
                            .loadTrustMaterial(getClass().getResource("/test-ca.keystore"), "nopassword".toCharArray())
                            .build()));
        } catch (final CertificateException | NoSuchAlgorithmException | KeyStoreException | KeyManagementException ex) {
            throw new IllegalStateException(ex);
        }
        if (proxy != null) {
            final HttpRoutePlanner routePlanner = (target, context) ->
                    new HttpRoute(target, null, proxy, URIScheme.HTTPS.same(target.getSchemeName()));
            this.clientBuilder.setRoutePlanner(routePlanner);
        }
    }

    public void configure(final Consumer<H2AsyncClientBuilder> customizer) {
        customizer.accept(clientBuilder);
    }

    @Override
    public void afterEach(final ExtensionContext extensionContext) {
        if (client != null) {
            client.close(CloseMode.GRACEFUL);
        }
    }

    public CloseableHttpAsyncClient client() {
        if (client == null) {
            client = clientBuilder.build();
            client.start();
        }
        return client;
    }

}
