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
package org.apache.hc.client5.http.examples.fluent;

import java.io.File;

import org.apache.hc.client5.http.fluent.Form;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.util.Timeout;

/**
 * This example demonstrates basics of request execution with the HttpClient fluent API.
 */
public class FluentRequests {

    public static void main(final String... args)throws Exception {
        // Execute a GET with timeout settings and return response content as String.
        Request.get("http://somehost/")
                .connectTimeout(Timeout.ofSeconds(1))
                .responseTimeout(Timeout.ofSeconds(5))
                .execute().returnContent().asString();

        // Execute a POST with the 'expect-continue' handshake, using HTTP/1.1,
        // containing a request body as String and return response content as byte array.
        Request.post("http://somehost/do-stuff")
                .useExpectContinue()
                .version(HttpVersion.HTTP_1_1)
                .bodyString("Important stuff", ContentType.DEFAULT_TEXT)
                .execute().returnContent().asBytes();

        // Execute a POST with a custom header through the proxy containing a request body
        // as an HTML form and save the result to the file
        Request.post("http://somehost/some-form")
                .addHeader("X-Custom-header", "stuff")
                .viaProxy(new HttpHost("myproxy", 8080))
                .bodyForm(Form.form().add("username", "vip").add("password", "secret").build())
                .execute().saveContent(new File("result.dump"));
    }

}
