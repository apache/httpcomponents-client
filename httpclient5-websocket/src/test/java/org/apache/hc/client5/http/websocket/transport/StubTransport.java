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
package org.apache.hc.client5.http.websocket.transport;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.hc.core5.util.Timeout;

/**
 * No-op transport for unit tests.
 */
final class StubTransport implements WebSocketTransport {

    volatile Timeout lastTimeout;
    volatile boolean outputRequested;
    volatile boolean closedGracefully;
    volatile boolean aborted;
    volatile boolean streamEnded;

    @Override
    public int write(final ByteBuffer src) throws IOException {
        // swallow all bytes
        final int n = src.remaining();
        src.position(src.limit());
        return n;
    }

    @Override
    public void requestOutput() {
        outputRequested = true;
    }

    @Override
    public void setTimeout(final Timeout timeout) {
        lastTimeout = timeout;
    }

    @Override
    public void closeGracefully() {
        closedGracefully = true;
    }

    @Override
    public void abort() {
        aborted = true;
    }

    @Override
    public void endStream() throws IOException {
        streamEnded = true;
    }
}
