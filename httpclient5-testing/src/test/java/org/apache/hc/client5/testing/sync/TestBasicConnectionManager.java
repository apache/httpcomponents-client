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
package org.apache.hc.client5.testing.sync;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.io.BasicHttpClientConnectionManager;
import org.apache.hc.client5.testing.classic.RandomHandler;
import org.apache.hc.client5.testing.extension.sync.ClientProtocolLevel;
import org.apache.hc.client5.testing.extension.sync.TestClient;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestBasicConnectionManager extends AbstractIntegrationTestBase {

    public TestBasicConnectionManager() {
        super(URIScheme.HTTP, ClientProtocolLevel.STANDARD);
    }

    @Test
    void testBasics() throws Exception {
        configureServer(bootstrap -> bootstrap
                .register("/random/*", new RandomHandler()));
        final HttpHost target = startServer();

        configureClient(builder -> builder
                .setConnectionManager(new BasicHttpClientConnectionManager()));
        final TestClient client = client();

        final HttpGet get = new HttpGet("/random/1024");
        client.execute(target, get, response -> {
            Assertions.assertEquals(200, response.getCode());
            EntityUtils.consume(response.getEntity());
            return null;
        });
    }

    @Test
    void testConnectionStillInUse() throws Exception {
        configureServer(bootstrap -> bootstrap
                .register("/random/*", new RandomHandler()));
        final HttpHost target = startServer();

        configureClient(builder -> builder
                .setConnectionManager(new BasicHttpClientConnectionManager()));
        final TestClient client = client();

        final HttpGet get1 = new HttpGet("/random/1024");
        client.executeOpen(target, get1, null);
        final HttpGet get2 = new HttpGet("/random/1024");
        Assertions.assertThrows(IllegalStateException.class, () ->
                client.executeOpen(target, get2, null));
    }

}
