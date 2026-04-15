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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.CredentialsProviderBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.StatusLine;

/**
 * An example of how to explicitly enable {@code SCRAM-SHA-256} authentication.
 * SCRAM-SHA-256 is not part of the default auth scheme preference list and
 * must be opted into by placing it on the preferred auth schemes list of
 * the {@link RequestConfig}.
 *
 * @since 5.7
 */
public class ClientScramAuthentication {

    public static void main(final String[] args) throws Exception {
        final List<String> priority = Collections.unmodifiableList(Arrays.asList(
                StandardAuthScheme.SCRAM_SHA_256,
                StandardAuthScheme.BEARER));

        final HttpHost target = new HttpHost("http", "httpbin.org", 80);

        try (final CloseableHttpClient httpclient = HttpClients.custom()
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setTargetPreferredAuthSchemes(priority)
                        .setProxyPreferredAuthSchemes(priority)
                        .build())
                .setDefaultCredentialsProvider(CredentialsProviderBuilder.create()
                        .add(target, "user", "passwd".toCharArray())
                        .build())
                .build()) {

            final HttpGet httpget = new HttpGet("http://httpbin.org/basic-auth/user/passwd");

            System.out.println("Executing request " + httpget.getMethod() + " " + httpget.getUri());
            httpclient.execute(httpget, response -> {
                System.out.println("----------------------------------------");
                System.out.println(httpget + "->" + new StatusLine(response));
                EntityUtils.consume(response.getEntity());
                return null;
            });
        }
    }
}
