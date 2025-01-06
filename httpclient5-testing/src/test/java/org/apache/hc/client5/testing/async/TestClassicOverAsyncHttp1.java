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
package org.apache.hc.client5.testing.async;

import java.util.Random;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.testing.extension.async.ClientProtocolLevel;
import org.apache.hc.client5.testing.extension.async.ServerProtocolLevel;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public abstract class TestClassicOverAsyncHttp1 extends TestClassicOverAsync {

    public TestClassicOverAsyncHttp1(final URIScheme scheme,
                                     final ClientProtocolLevel clientProtocolLevel,
                                     final ServerProtocolLevel serverProtocolLevel) {
        super(scheme, clientProtocolLevel, serverProtocolLevel);
    }

    @ValueSource(ints = {0, 2048, 10240})
    @ParameterizedTest(name = "{displayName}; content length: {0}")
    void testSequentialPostRequestsNoKeepAlive(final int contentSize) throws Exception {
        configureServer(bootstrap ->
                bootstrap.register("/echo/*", AsyncEchoHandler::new));
        final HttpHost target = startServer();

        final CloseableHttpClient client = startClient();

        final int n = 10;
        for (int i = 0; i < n; i++) {
            final byte[] temp = new byte[contentSize];
            new Random(System.currentTimeMillis()).nextBytes(temp);
            client.execute(
                    ClassicRequestBuilder.post()
                            .setHttpHost(target)
                            .setPath("/echo/")
                            .addHeader(HttpHeaders.CONNECTION, "Close")
                            .setEntity(new ByteArrayEntity(temp, ContentType.DEFAULT_BINARY))
                            .build(),
                    response -> {
                        Assertions.assertEquals(200, response.getCode());
                        final byte[] bytes = EntityUtils.toByteArray(response.getEntity());
                        Assertions.assertNotNull(bytes);
                        Assertions.assertArrayEquals(temp, bytes);
                        return null;
                    });
        }
    }

}