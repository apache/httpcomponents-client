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

import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.testing.nio.H2TestServer;

public abstract class TestH2Reactive extends AbstractHttpReactiveFundamentalsTest<CloseableHttpAsyncClient> {

    public TestH2Reactive(final URIScheme scheme) {
        super(scheme);
    }

    @Override
    protected H2TestServer startServer() throws Exception {
        return startServer(H2Config.DEFAULT, null, null);
    }

    @Override
    protected CloseableHttpAsyncClient startClient() throws Exception {
        return startH2Client(b -> {});
    }

}
