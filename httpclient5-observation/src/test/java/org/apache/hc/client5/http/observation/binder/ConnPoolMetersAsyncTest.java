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
package org.apache.hc.client5.http.observation.binder;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.nio.AsyncClientConnectionManager;
import org.junit.jupiter.api.Test;

class ConnPoolMetersAsyncTest {

    @Test
    void registersGaugesWhenAsyncPoolPresent() throws Exception {
        final MeterRegistry reg = new SimpleMeterRegistry();

        final AsyncClientConnectionManager cm = PoolingAsyncClientConnectionManagerBuilder.create().build();
        final HttpAsyncClientBuilder b = HttpAsyncClients.custom().setConnectionManager(cm);

        ConnPoolMetersAsync.bindTo(b, reg);

        // build to finalize builder configuration
        b.build().close();

        assertNotNull(reg.find("http.client.pool.leased").gauge());
        assertNotNull(reg.find("http.client.pool.available").gauge());
        assertNotNull(reg.find("http.client.pool.pending").gauge());
    }

    @Test
    void noExceptionIfNoAsyncPool() {
        final MeterRegistry reg = new SimpleMeterRegistry();
        final HttpAsyncClientBuilder b = HttpAsyncClients.custom(); // no CM set
        ConnPoolMetersAsync.bindTo(b, reg);
        assertNull(reg.find("http.client.pool.leased").gauge());
    }
}
