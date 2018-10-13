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

package org.apache.hc.client5.http.io;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.TimeValue;

/**
 * Connection operator that performs connection connect and upgrade operations.
 *
 * @since 4.4
 */
@Contract(threading = ThreadingBehavior.STATELESS)
@Internal
public interface HttpClientConnectionOperator {

    /**
     * Connect the given managed connection to the remote endpoint.
     *
     * @param conn the managed connection.
     * @param host the address of the opposite endpoint.
     * @param localAddress the address of the local endpoint.
     * @param connectTimeout the timeout of the connect operation.
     * @param socketConfig the socket configuration.
     * @param context the execution context.
     */
    void connect(
            ManagedHttpClientConnection conn,
            HttpHost host,
            InetSocketAddress localAddress,
            TimeValue connectTimeout,
            SocketConfig socketConfig,
            HttpContext context) throws IOException;

    /**
     * Upgrades transport security of the given managed connection
     * by using the TLS security protocol.
     *
     * @param conn the managed connection.
     * @param host the address of the opposite endpoint with TLS security.
     * @param context the execution context.
     */
    void upgrade(
            ManagedHttpClientConnection conn,
            HttpHost host,
            HttpContext context) throws IOException;

}
