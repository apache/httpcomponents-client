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
package org.apache.http.client.fluent;

import java.io.File;

import org.apache.http.HttpHost;
import org.apache.http.HttpVersion;
import org.apache.http.entity.ContentType;

/**
 * This example demonstrates how the he HttpClient fluent API can be used to execute multiple
 * requests within the same security context. The Executor class maintains a common context shared
 * by all requests executed with it. The Executor is thread-safe and can be used to execute
 * requests concurrently from multiple threads of execution.
 */
public class FluentExecutor {

    public static void main(String[] args)throws Exception {
        Executor executor = Executor.newInstance()
                .auth(new HttpHost("somehost"), "username", "password")
                .auth(new HttpHost("myproxy", 8080), "username", "password")
                .authPreemptive(new HttpHost("myproxy", 8080));

        // Execute a GET with timeout settings and return response content as String.
        executor.execute(Request.Get("http://somehost/")
                .connectTimeout(1000)
                .socketTimeout(1000)
                ).returnContent().asString();

        // Execute a POST with the 'expect-continue' handshake, using HTTP/1.1,
        // containing a request body as String and return response content as byte array.
        executor.execute(Request.Post("http://somehost/do-stuff")
                .useExpectContinue()
                .version(HttpVersion.HTTP_1_1)
                .bodyString("Important stuff", ContentType.DEFAULT_TEXT)
                ).returnContent().asBytes();

        // Execute a POST with a custom header through the proxy containing a request body
        // as an HTML form and save the result to the file
        executor.execute(Request.Post("http://somehost/some-form")
                .addHeader("X-Custom-header", "stuff")
                .viaProxy(new HttpHost("myproxy", 8080))
                .bodyForm(Form.form().add("username", "vip").add("password", "secret").build())
                ).saveContent(new File("result.dump"));
    }

}
