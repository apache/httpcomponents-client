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

import java.util.Arrays;
import java.util.Collection;

import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.async.MinimalH2AsyncClient;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.testing.SSLTestContexts;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@EnableRuleMigrationSupport
@RunWith(Parameterized.class)
public class TestH2AsyncMinimal extends AbstractHttpAsyncFundamentalsTest<MinimalH2AsyncClient> {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> protocols() {
        return Arrays.asList(new Object[][]{
                { URIScheme.HTTP },
                { URIScheme.HTTPS },
        });
    }

    public TestH2AsyncMinimal(final URIScheme scheme) {
        super(scheme);
    }

    @Override
    protected MinimalH2AsyncClient createClient() throws Exception {
        final IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
                .setSoTimeout(TIMEOUT)
                .build();
        return HttpAsyncClients.createHttp2Minimal(
                H2Config.DEFAULT, ioReactorConfig, new DefaultClientTlsStrategy(SSLTestContexts.createClientSSLContext()));
    }

    @Override
    public HttpHost start() throws Exception {
        return super.start(null, H2Config.DEFAULT);
    }

}