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

package org.apache.hc.client5.http.nio;

import java.net.SocketAddress;
import java.util.concurrent.Future;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.reactor.ConnectionInitiator;
import org.apache.hc.core5.util.Timeout;

/**
 * Connection operator that performs connection connect and upgrade operations.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.STATELESS)
@Internal
public interface AsyncClientConnectionOperator {

    /**
     * Initiates operation to create a connection to the remote endpoint using
     * the provided {@link ConnectionInitiator}.
     *
     * @param connectionInitiator the connection initiator.
     * @param host the address of the opposite endpoint.
     * @param localAddress the address of the local endpoint.
     * @param connectTimeout the timeout of the connect operation.
     * @param attachment the attachment, which can be any object representing custom parameter
     *                    of the operation.
     * @param callback the future result callback.
     */
    Future<ManagedAsyncClientConnection> connect(
            ConnectionInitiator connectionInitiator,
            HttpHost host,
            SocketAddress localAddress,
            Timeout connectTimeout,
            Object attachment,
            FutureCallback<ManagedAsyncClientConnection> callback);


    /**
     * Upgrades transport security of the given managed connection
     * by using the TLS security protocol.
     *
     * @param conn the managed connection.
     * @param host the address of the opposite endpoint with TLS security.
     * @param attachment the attachment, which can be any object representing custom parameter
     *                    of the operation.
     */
    void upgrade(ManagedAsyncClientConnection conn, HttpHost host, Object attachment);

}
