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
package org.apache.hc.client5.testing.compatibility.async;

import java.util.concurrent.Future;

import javax.security.auth.Subject;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public abstract class HttpAsyncClientHttp1CompatibilityTest extends HttpAsyncClientCompatibilityTest {


    public HttpAsyncClientHttp1CompatibilityTest(
            final HttpHost target,
            final Credentials targetCreds,
            final HttpHost proxy,
            final Credentials proxyCreds) throws Exception {
        super(HttpVersionPolicy.FORCE_HTTP_1, target, targetCreds, proxy, proxyCreds);
    }

    public HttpAsyncClientHttp1CompatibilityTest(
            final HttpHost target,
            final Credentials targetCreds,
            final HttpHost proxy,
            final Credentials proxyCreds,
            final Subject doAs) throws Exception {
        super(HttpVersionPolicy.FORCE_HTTP_1, target, targetCreds, proxy, proxyCreds, doAs);
    }
    
    @Test
    void test_auth_success_no_keep_alive() throws Exception {
        setCredentials(
                new AuthScope(target),
                targetCreds);
        final CloseableHttpAsyncClient client = client();
        final HttpClientContext context = context();

        final SimpleHttpRequest httpGetSecret = SimpleRequestBuilder.get()
                .setHttpHost(target)
                .setPath(secretPath)
                .addHeader(HttpHeaders.CONNECTION, HeaderElements.CLOSE)
                .build();
        final Future<SimpleHttpResponse> future = client.execute(httpGetSecret, context, null);
        final SimpleHttpResponse response = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
        assertProtocolVersion(context);
    }

}
