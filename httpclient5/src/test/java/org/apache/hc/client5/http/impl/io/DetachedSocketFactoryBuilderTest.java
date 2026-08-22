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

package org.apache.hc.client5.http.impl.io;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.io.DetachedSocketFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DetachedSocketFactoryBuilderTest {

    @Test
    void testPoolingBuilderUsesDetachedSocketFactory() throws Exception {
        final AtomicInteger invocationCount = new AtomicInteger();
        final IOException expected = new IOException("custom transport");
        final DetachedSocketFactory socketFactory = proxy -> {
            invocationCount.incrementAndGet();
            throw expected;
        };
        final PoolingHttpClientConnectionManager connectionManager =
                PoolingHttpClientConnectionManagerBuilder.create()
                        .setDetachedSocketFactory(socketFactory)
                        .build();
        try (CloseableHttpClient client = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .disableAutomaticRetries()
                .build()) {
            Assertions.assertThrows(IOException.class, () ->
                    client.executeOpen(null, new HttpGet("http://localhost:18080/"), null));
            Assertions.assertEquals(1, invocationCount.get());
        }
    }

}
