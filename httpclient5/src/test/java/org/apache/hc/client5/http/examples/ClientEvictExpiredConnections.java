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
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.util.TimeValue;

/**
 * Example demonstrating how to configure connection keep-alive and
 * evict expired connections from the connection pool.
 */
public class ClientEvictExpiredConnections {

    public static void main(final String[] args) throws Exception {
        final PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(100);

        final RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionKeepAlive(TimeValue.ofSeconds(5))
                .build();
        try (final CloseableHttpClient httpclient = HttpClients.custom()
                .setConnectionManager(cm)
                .setDefaultRequestConfig(requestConfig)
                .evictExpiredConnections()
                .build()) {
            // create an array of URIs to perform GETs on
            final String[] urisToGet = {
                    "http://hc.apache.org/",
                    "http://hc.apache.org/httpcomponents-core-ga/",
                    "http://hc.apache.org/httpcomponents-client-ga/",
            };

            for (final String requestURI : urisToGet) {
                final HttpGet request = new HttpGet(requestURI);

                System.out.println("Executing request " + request.getMethod() + " " + request.getRequestUri());

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
