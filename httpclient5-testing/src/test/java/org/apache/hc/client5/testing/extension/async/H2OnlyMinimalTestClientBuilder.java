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

package org.apache.hc.client5.testing.extension.async;

import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.testing.SSLTestContexts;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.Timeout;

final class H2OnlyMinimalTestClientBuilder implements TestAsyncClientBuilder {

    private Timeout timeout;
    private TlsStrategy tlsStrategy;
    private H2Config h2Config;

    public H2OnlyMinimalTestClientBuilder() {
    }

    @Override
    public TestAsyncClientBuilder setTimeout(final Timeout timeout) {
        this.timeout = timeout;
        return this;
    }

    @Override
    public TestAsyncClientBuilder setTlsStrategy(final TlsStrategy tlsStrategy) {
        this.tlsStrategy = tlsStrategy;
        return this;
    }

    @Override
    public ClientProtocolLevel getProtocolLevel() {
        return ClientProtocolLevel.MINIMAL_H2_ONLY;
    }

    @Override
    public TestAsyncClientBuilder setH2Config(final H2Config h2Config) {
        this.h2Config = h2Config;
        return this;
    }

    @Override
    public TestAsyncClientBuilder useMessageMultiplexing() {
        return this;
    }

    @Override
    public TestAsyncClient build() throws Exception {
        final CloseableHttpAsyncClient client = HttpAsyncClients.createHttp2Minimal(
                h2Config,
                IOReactorConfig.custom()
                        .setSoTimeout(timeout)
                        .build(),
                tlsStrategy != null ? tlsStrategy : new DefaultClientTlsStrategy(SSLTestContexts.createClientSSLContext()));
        return new TestAsyncClient(client, null);
    }

}
