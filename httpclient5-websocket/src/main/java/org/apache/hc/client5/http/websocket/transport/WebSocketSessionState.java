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

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.hc.client5.http.websocket.api.WebSocketClientConfig;
import org.apache.hc.client5.http.websocket.api.WebSocketListener;
import org.apache.hc.core5.websocket.extension.ExtensionChain;
import org.apache.hc.core5.websocket.frame.WebSocketFrameWriter;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.reactor.ProtocolIOSession;

/**
 * Shared state & resources.
 */
@Internal
final class WebSocketSessionState {

    // External
    final ProtocolIOSession session;
    final WebSocketListener listener;
    final WebSocketClientConfig cfg;

    // Extensions
    final ExtensionChain.EncodeChain encChain;
    final ExtensionChain.DecodeChain decChain;
    final int rsvMask;

    // Buffers & codec
    final WebSocketFrameWriter writer = new WebSocketFrameWriter();
    final WebSocketFrameDecoder decoder;

    // Read side
    ByteBuffer readBuf;
    ByteBuffer inbuf = ByteBuffer.allocate(4096);

    // Outbound queues
    final ConcurrentLinkedQueue<WebSocketOutbound.OutFrame> ctrlOutbound = new ConcurrentLinkedQueue<>();
    final ConcurrentLinkedQueue<WebSocketOutbound.OutFrame> dataOutbound = new ConcurrentLinkedQueue<>();
    WebSocketOutbound.OutFrame activeWrite = null;
    // Queue accounting invariant: bytes accepted for data frames but not fully written yet.
    final AtomicLong dataQueuedBytes = new AtomicLong();

    // Flags / locks
    final AtomicBoolean open = new AtomicBoolean(true);
    final AtomicBoolean closeSent = new AtomicBoolean(false);
    final AtomicBoolean closeReceived = new AtomicBoolean(false);
    volatile boolean closeAfterFlush = false;
    final ReentrantLock writeLock = new ReentrantLock();

    // Message assembly
    int assemblingOpcode = -1;
    boolean assemblingCompressed = false;
    ByteArrayOutputStream assemblingBytes = null;
    long assemblingSize = 0L;

    // Outbound fragmentation
    int outOpcode = -1;
    final int outChunk;
    final int maxFramesPerTick;

    WebSocketSessionState(final ProtocolIOSession session,
                          final WebSocketListener listener,
                          final WebSocketClientConfig cfg,
                          final ExtensionChain chain) {
        this.session = session;
        this.listener = listener;
        this.cfg = cfg;

        this.decoder = new WebSocketFrameDecoder(cfg.getMaxFrameSize(), false);

        this.outChunk = Math.max(256, cfg.getOutgoingChunkSize());
        this.maxFramesPerTick = Math.max(1, cfg.getMaxFramesPerTick());

        if (chain != null && !chain.isEmpty()) {
            this.encChain = chain.newEncodeChain();
            this.decChain = chain.newDecodeChain();
            this.rsvMask = chain.rsvMask();
        } else {
            this.encChain = null;
            this.decChain = null;
            this.rsvMask = 0;
        }

        final int poolBufSize = Math.max(8192, this.outChunk);
        this.readBuf = cfg.isDirectBuffers() ? ByteBuffer.allocateDirect(poolBufSize) : ByteBuffer.allocate(poolBufSize);
    }
}
