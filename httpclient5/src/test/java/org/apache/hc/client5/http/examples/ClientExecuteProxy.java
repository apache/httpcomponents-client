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

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.entity.EntityUtils;

/**
 * How to send a request via proxy.
 *
 * @since 4.0
 */
public class ClientExecuteProxy {

    public static void main(final String[] args)throws Exception {
        try (final CloseableHttpClient httpclient = HttpClients.createDefault()) {
            final HttpHost target = new HttpHost("https", "httpbin.org", 443);
            final HttpHost proxy = new HttpHost("http", "127.0.0.1", 8080);

            final RequestConfig config = RequestConfig.custom()
                    .setProxy(proxy)
                    .build();
            final HttpGet request = new HttpGet("/get");
            request.setConfig(config);

            System.out.println("Executing request " + request.getMethod() + " " + request.getUri() +
                    " via " + proxy);

            try (final CloseableHttpResponse response = httpclient.execute(target, request)) {
                System.out.println("----------------------------------------");
                System.out.println(response.getCode() + " " + response.getReasonPhrase());
                System.out.println(EntityUtils.toString(response.getEntity()));
            }
        }
    }

}
