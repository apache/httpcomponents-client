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

package org.apache.hc.client5.http.ssl;

import java.io.IOException;
import java.net.Socket;

import javax.net.ssl.SSLSocket;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * TLS protocol upgrade strategy for blocking {@link Socket}s.
 *
 * @since 5.4
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public interface TlsSocketStrategy {

    /**
     * Upgrades the given plain socket and executes the TLS handshake over it.
     *
     * @param socket     the existing plain socket
     * @param target     the name of the target host.
     * @param port       the port to connect to on the target host.
     * @param context    the actual HTTP context.
     * @param attachment connect request attachment.
     * @return socket upgraded to TLS.
     */
    SSLSocket upgrade(
            Socket socket,
            String target,
            int port,
            Object attachment,
            HttpContext context) throws IOException;

}
