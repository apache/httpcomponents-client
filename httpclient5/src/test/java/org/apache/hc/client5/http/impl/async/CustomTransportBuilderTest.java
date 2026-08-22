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

package org.apache.hc.client5.http.impl.async;

import java.net.Socket;
import java.nio.channels.SocketChannel;

import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.DetachedSocketFactory;
import org.apache.hc.core5.reactor.SocketChannelFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CustomTransportBuilderTest {

    @Test
    void testClassicBuilderAcceptsDetachedSocketFactory() {
        final DetachedSocketFactory socketFactory = proxy -> new Socket();
        final PoolingHttpClientConnectionManager connectionManager =
                PoolingHttpClientConnectionManagerBuilder.create()
                        .setDetachedSocketFactory(socketFactory)
                        .build();
        try {
            Assertions.assertNotNull(connectionManager);
        } finally {
            connectionManager.close();
        }
    }

    @Test
    void testAsyncBuildersAcceptSocketChannelFactory() throws Exception {
        final SocketChannelFactory socketChannelFactory = remoteAddress -> SocketChannel.open();
        try (CloseableHttpAsyncClient client = HttpAsyncClientBuilder.create()
                .setSocketChannelFactory(socketChannelFactory)
                .build()) {
            Assertions.assertNotNull(client);
        }
        try (CloseableHttpAsyncClient client = H2AsyncClientBuilder.create()
                .setSocketChannelFactory(socketChannelFactory)
                .build()) {
            Assertions.assertNotNull(client);
        }
    }

}
