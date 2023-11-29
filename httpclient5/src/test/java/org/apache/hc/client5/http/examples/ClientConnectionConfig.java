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
package org.apache.hc.client5.http.examples;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.http.ssl.TLS;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

/**
 * This example demonstrates how to use connection configuration on a per-route or a per-host
 * basis.
 */
public class ClientConnectionConfig {

    public final static void main(final String[] args) throws Exception {
        final PoolingHttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create()
                .setConnectionConfigResolver(route -> {
                    // Use different settings for all secure (TLS) connections
                    final HttpHost targetHost = route.getTargetHost();
                    if (route.isSecure()) {
                        return ConnectionConfig.custom()
                                .setConnectTimeout(Timeout.ofMinutes(2))
                                .setSocketTimeout(Timeout.ofMinutes(2))
                                .setValidateAfterInactivity(TimeValue.ofMinutes(1))
                                .setTimeToLive(TimeValue.ofHours(1))
                                .build();
                    } else {
                        return ConnectionConfig.custom()
                                .setConnectTimeout(Timeout.ofMinutes(1))
                                .setSocketTimeout(Timeout.ofMinutes(1))
                                .setValidateAfterInactivity(TimeValue.ofSeconds(15))
                                .setTimeToLive(TimeValue.ofMinutes(15))
                                .build();
                    }
                })
                .setTlsConfigResolver(host -> {
                    // Use different settings for specific hosts
                    if (host.getSchemeName().equalsIgnoreCase("httpbin.org")) {
                        return TlsConfig.custom()
                                .setSupportedProtocols(TLS.V_1_3)
                                .setHandshakeTimeout(Timeout.ofSeconds(10))
                                .build();
                    } else {
                        return TlsConfig.DEFAULT;
                    }
                })
                .build();
        try (CloseableHttpClient httpclient = HttpClients.custom()
                .setConnectionManager(cm)
                .build()) {

            for (final URIScheme uriScheme : URIScheme.values()) {
                final ClassicHttpRequest request = ClassicRequestBuilder.get()
                        .setHttpHost(new HttpHost(uriScheme.id, "httpbin.org"))
                        .setPath("/headers")
                        .build();
                System.out.println("Executing request " + request);
                httpclient.execute(request, response -> {
                    System.out.println("----------------------------------------");
                    System.out.println(request + "->" + new StatusLine(response));
                    EntityUtils.consume(response.getEntity());
                    return null;
                });
            }
        }
    }

}
