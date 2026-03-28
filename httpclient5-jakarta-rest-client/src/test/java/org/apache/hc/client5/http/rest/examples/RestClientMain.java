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
package org.apache.hc.client5.http.rest.examples;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.rest.RestClientBuilder;

/**
 * Example demonstrating the Jakarta REST client module backed by the
 * Apache HttpComponents async client.
 * <p>
 * The example uses httpbin.org as the target endpoint and shows query
 * parameter binding and JSON request / response body handling.
 *
 * @since 5.7
 */
public final class RestClientMain {

    private RestClientMain() {
    }

    public static void main(final String[] args) throws Exception {
        try (final CloseableHttpAsyncClient httpClient = HttpAsyncClients.createDefault()) {
            httpClient.start();

            final HttpBinApi api = RestClientBuilder.newBuilder()
                    .baseUri("https://httpbin.org")
                    .httpClient(httpClient)
                    .build(HttpBinApi.class);

            final GetResponse getResponse = api.get("en");
            System.out.println("GET:");
            System.out.println("  url       = " + getResponse.url);
            System.out.println("  args.lang = " + value(getResponse.args, "lang"));

            final Widget widget = new Widget(1, "test");
            final PostResponse postResponse = api.post(widget);

            System.out.println("POST:");
            System.out.println("  url         = " + postResponse.url);
            System.out.println("  echoed json = " + postResponse.json);
        }
    }

    private static String value(final Map<String, String> map, final String key) {
        return map != null ? map.get(key) : null;
    }

    @Path("/")
    interface HttpBinApi {

        @GET
        @Path("get")
        @Produces("application/json")
        GetResponse get(@QueryParam("lang") String lang);

        @POST
        @Path("post")
        @Consumes("application/json")
        @Produces("application/json")
        PostResponse post(Widget body);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class GetResponse {

        public Map<String, String> args;
        public Map<String, String> headers;
        public String origin;
        public String url;

        @Override
        public String toString() {
            return "GetResponse{" +
                    "args=" + args +
                    ", headers=" + headers +
                    ", origin='" + origin + '\'' +
                    ", url='" + url + '\'' +
                    '}';
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class PostResponse {

        public String data;
        public Map<String, String> headers;
        public Widget json;
        public String url;

        @Override
        public String toString() {
            return "PostResponse{" +
                    "data='" + data + '\'' +
                    ", headers=" + headers +
                    ", json=" + json +
                    ", url='" + url + '\'' +
                    '}';
        }
    }

    static final class Widget {

        public int id;
        public String name;

        public Widget() {
        }

        public Widget(final int id, final String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return "Widget{" +
                    "id=" + id +
                    ", name='" + name + '\'' +
                    '}';
        }
    }

}