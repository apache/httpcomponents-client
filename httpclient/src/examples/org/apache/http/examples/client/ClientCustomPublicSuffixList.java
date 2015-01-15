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
package org.apache.http.examples.client;

import java.net.URL;

import org.apache.http.HttpEntity;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.config.Lookup;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.util.PublicSuffixMatcher;
import org.apache.http.conn.util.PublicSuffixMatcherLoader;
import org.apache.http.cookie.CookieSpecProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.cookie.RFC6265CookieSpecProvider;
import org.apache.http.util.EntityUtils;

/**
 * This example demonstrates how to use a custom public suffix list.
 */
public class ClientCustomPublicSuffixList {

    public final static void main(String[] args) throws Exception {

        // Use PublicSuffixMatcherLoader to load public suffix list from a file,
        // resource or from an arbitrary URL
        PublicSuffixMatcher publicSuffixMatcher = PublicSuffixMatcherLoader.load(
                new URL("https://publicsuffix.org/list/effective_tld_names.dat"));

        // Please use the publicsuffix.org URL to download the list no more than once per day !!!
        // Please consider making a local copy !!!

        DefaultHostnameVerifier hostnameVerifier = new DefaultHostnameVerifier(publicSuffixMatcher);

        RFC6265CookieSpecProvider cookieSpecProvider = new RFC6265CookieSpecProvider(publicSuffixMatcher);
        Lookup<CookieSpecProvider> cookieSpecRegistry = RegistryBuilder.<CookieSpecProvider>create()
                .register(CookieSpecs.DEFAULT, cookieSpecProvider)
                .register(CookieSpecs.STANDARD, cookieSpecProvider)
                .register(CookieSpecs.STANDARD_STRICT, cookieSpecProvider)
                .build();

        CloseableHttpClient httpclient = HttpClients.custom()
                .setSSLHostnameVerifier(hostnameVerifier)
                .setDefaultCookieSpecRegistry(cookieSpecRegistry)
                .build();
        try {

            HttpGet httpget = new HttpGet("https://remotehost/");

            System.out.println("executing request " + httpget.getRequestLine());

            CloseableHttpResponse response = httpclient.execute(httpget);
            try {
                HttpEntity entity = response.getEntity();

                System.out.println("----------------------------------------");
                System.out.println(response.getStatusLine());
                EntityUtils.consume(entity);
            } finally {
                response.close();
            }
        } finally {
            httpclient.close();
        }
    }

}
