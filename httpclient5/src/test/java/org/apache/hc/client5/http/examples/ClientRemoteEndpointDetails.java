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
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.StatusLine;

/**
 * This example demonstrates how to get details of the underlying connection endpoint.
 */
public class ClientRemoteEndpointDetails {

    public static void main(final String[] args) throws Exception {
        try (final CloseableHttpClient httpclient = HttpClients.createDefault()) {
            // Create local HTTP context
            final HttpClientContext localContext = new HttpClientContext();

            final HttpGet httpget = new HttpGet("http://httpbin.org/get");
            System.out.println("Executing request " + httpget.getMethod() + " " + httpget.getUri());

            // Pass local context as a parameter
            httpclient.execute(httpget, localContext, response -> {
                System.out.println("----------------------------------------");
                System.out.println(httpget + "->" + new StatusLine(response));
                EntityUtils.consume(response.getEntity());

                final EndpointDetails endpointDetails = localContext.getEndpointDetails();
                System.out.println("Remote address: " + endpointDetails.getRemoteAddress());
                System.out.println("Request counts: " + endpointDetails.getRequestCount());
                System.out.println("Response counts: " + endpointDetails.getResponseCount());
                System.out.println("Bytes sent: " + endpointDetails.getSentBytesCount());
                System.out.println("Bytes received: " + endpointDetails.getReceivedBytesCount());

                return null;
            });

            // EndpointDetails is present in the context even if the underlying connection
            // gets immediately released back to the connection pool

            final HttpHead httphead = new HttpHead("http://httpbin.org/get");
            System.out.println("Executing request " + httphead.getMethod() + " " + httphead.getUri());

            // Pass local context as a parameter
            httpclient.execute(httphead, localContext, response -> {
                System.out.println("----------------------------------------");
                System.out.println(httphead + "->" + new StatusLine(response));
                EntityUtils.consume(response.getEntity());

                final EndpointDetails endpointDetails = localContext.getEndpointDetails();
                System.out.println("Remote address: " + endpointDetails.getRemoteAddress());
                System.out.println("Request counts: " + endpointDetails.getRequestCount());
                System.out.println("Response counts: " + endpointDetails.getResponseCount());
                System.out.println("Bytes sent: " + endpointDetails.getSentBytesCount());
                System.out.println("Bytes received: " + endpointDetails.getReceivedBytesCount());

                return null;
            });
        }
    }

}

