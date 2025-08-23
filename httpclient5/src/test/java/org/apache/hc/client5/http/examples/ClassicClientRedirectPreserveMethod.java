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

import java.nio.charset.StandardCharsets;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RedirectMethodPolicy;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.StatusLine;

/**
 * Demonstrates how to control 301/302 redirect method rewriting in the
 * <b>classic</b> client using {@link RedirectMethodPolicy}.
 * <p>
 * By default (browser-compatible), a 301/302 after a POST is followed with a GET and
 * the request body is dropped. When {@link RedirectMethodPolicy#PRESERVE_METHOD} is enabled,
 * the client preserves the original method and (repeatable) body on 301/302 as well.
 * </p>
 *
 * <h3>What this example does</h3>
 * <ul>
 *   <li>Sends a JSON POST to an endpoint that returns a 301 redirect to <code>/anything</code>.</li>
 *   <li>Runs twice:
 *     <ul>
 *       <li>Default policy: shows <code>"method":"GET"</code> and empty body.</li>
 *       <li>PRESERVE_METHOD: shows <code>"method":"POST"</code> and echoes the JSON body.</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h3>Notes</h3>
 * <ul>
 *   <li>Preservation requires a <em>repeatable</em> entity. Non-repeatable entities cannot be re-sent automatically.</li>
 *   <li>303 is always followed with GET; 307/308 always preserve method/body.</li>
 *   <li>Authorization headers are not forwarded across different authorities unless explicitly allowed by the redirect strategy.</li>
 * </ul>
 *
 * <h3>How to run</h3>
 * <pre>{@code
 * $ mvn -q -DskipTests exec:java -Dexec.mainClass=org.apache.hc.client5.http.examples.ClassicClientRedirectPreserveMethod
 * }</pre>
 *
 * @since 5.6
 */
public class ClassicClientRedirectPreserveMethod {

    private static String redirectUrl() {
        return "https://httpbin.org/redirect-to?url=/anything&status_code=301";
    }

    public static void main(final String[] args) throws Exception {
        final RequestConfig cfg = RequestConfig.custom()
                .setRedirectsEnabled(true)
                .setRedirectMethodPolicy(RedirectMethodPolicy.PRESERVE_METHOD)
                .build();

        try (CloseableHttpClient client = HttpClients.custom().build()) {
            final HttpPost post = new HttpPost(redirectUrl());
            post.setConfig(cfg);
            post.setEntity(new StringEntity("{\"hello\":\"world\"}", ContentType.APPLICATION_JSON));

            try (ClassicHttpResponse res = client.executeOpen(null, post, null)) {
                System.out.println(new StatusLine(res));
                System.out.println(EntityUtils.toString(res.getEntity(), StandardCharsets.UTF_8));
            }
        }
    }
}
