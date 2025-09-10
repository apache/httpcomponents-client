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
package org.apache.hc.client5.http.websocket.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Timeout;

/**
 * Immutable configuration for {@link WebSocket} clients.
 *
 * <p>Instances are normally created via the associated builder. The
 * configuration controls timeouts, maximum frame and message sizes,
 * fragmentation behaviour, buffer pooling and optional automatic
 * responses to PING frames.</p>
 *
 * @since 5.6
 */
public final class WebSocketClientConfig {

    private final Timeout connectTimeout;
    private final List<String> subprotocols;

    // PMCE offer
    private final boolean perMessageDeflateEnabled;
    private final boolean offerServerNoContextTakeover;
    private final boolean offerClientNoContextTakeover;
    private final Integer offerClientMaxWindowBits;
    private final Integer offerServerMaxWindowBits;

    // Framing / flow
    private final int maxFrameSize;
    private final int outgoingChunkSize;
    private final int maxFramesPerTick;

    // Buffers / pool
    private final int ioPoolCapacity;
    private final boolean directBuffers;

    // Behavior
    private final boolean autoPong;
    private final Timeout closeWaitTimeout;
    private final long maxMessageSize;

    // Outbound control queue
    private final int maxOutboundControlQueue;

    private WebSocketClientConfig(
            final Timeout connectTimeout,
            final List<String> subprotocols,
            final boolean perMessageDeflateEnabled,
            final boolean offerServerNoContextTakeover,
            final boolean offerClientNoContextTakeover,
            final Integer offerClientMaxWindowBits,
            final Integer offerServerMaxWindowBits,
            final int maxFrameSize,
            final int outgoingChunkSize,
            final int maxFramesPerTick,
            final int ioPoolCapacity,
            final boolean directBuffers,
            final boolean autoPong,
            final Timeout closeWaitTimeout,
            final long maxMessageSize,
            final int maxOutboundControlQueue) {

        this.connectTimeout = connectTimeout;
        this.subprotocols = subprotocols != null
                ? Collections.unmodifiableList(new ArrayList<>(subprotocols))
                : Collections.emptyList();
        this.perMessageDeflateEnabled = perMessageDeflateEnabled;
        this.offerServerNoContextTakeover = offerServerNoContextTakeover;
        this.offerClientNoContextTakeover = offerClientNoContextTakeover;
        this.offerClientMaxWindowBits = offerClientMaxWindowBits;
        this.offerServerMaxWindowBits = offerServerMaxWindowBits;
        this.maxFrameSize = maxFrameSize;
        this.outgoingChunkSize = outgoingChunkSize;
        this.maxFramesPerTick = maxFramesPerTick;
        this.ioPoolCapacity = ioPoolCapacity;
        this.directBuffers = directBuffers;
        this.autoPong = autoPong;
        this.closeWaitTimeout = Args.notNull(closeWaitTimeout, "closeWaitTimeout");
        this.maxMessageSize = maxMessageSize;
        this.maxOutboundControlQueue = maxOutboundControlQueue;
    }

    // ---- getters used across your code ----
    public Timeout getConnectTimeout() {
        return connectTimeout;
    }

    public List<String> getSubprotocols() {
        return subprotocols;
    }

    public boolean isPerMessageDeflateEnabled() {
        return perMessageDeflateEnabled;
    }

    public boolean isOfferServerNoContextTakeover() {
        return offerServerNoContextTakeover;
    }

    public boolean isOfferClientNoContextTakeover() {
        return offerClientNoContextTakeover;
    }

    public Integer getOfferClientMaxWindowBits() {
        return offerClientMaxWindowBits;
    }

    public Integer getOfferServerMaxWindowBits() {
        return offerServerMaxWindowBits;
    }

    public int getMaxFrameSize() {
        return maxFrameSize;
    }

    public int getOutgoingChunkSize() {
        return outgoingChunkSize;
    }

    public int getMaxFramesPerTick() {
        return maxFramesPerTick;
    }

    public int getIoPoolCapacity() {
        return ioPoolCapacity;
    }

    public boolean isDirectBuffers() {
        return directBuffers;
    }

    public boolean isAutoPong() {
        return autoPong;
    }

    public Timeout getCloseWaitTimeout() {
        return closeWaitTimeout;
    }

    public long getMaxMessageSize() {
        return maxMessageSize;
    }

    public int getMaxOutboundControlQueue() {
        return maxOutboundControlQueue;
    }

    // ---- builder ----
    public static Builder custom() {
        return new Builder();
    }

    public static final class Builder {
        private Timeout connectTimeout = Timeout.ofSeconds(10);
        private List<String> subprotocols = new ArrayList<>();

        private boolean perMessageDeflateEnabled = true;
        private boolean offerServerNoContextTakeover = true;
        private boolean offerClientNoContextTakeover = true;
        private Integer offerClientMaxWindowBits = 15;
        private Integer offerServerMaxWindowBits = null;

        private int maxFrameSize = 64 * 1024;
        private int outgoingChunkSize = 8 * 1024;
        private int maxFramesPerTick = 1024;

        private int ioPoolCapacity = 64;
        private boolean directBuffers = true;

        private boolean autoPong = true;
        private Timeout closeWaitTimeout = Timeout.ofSeconds(10);
        private long maxMessageSize = 8L * 1024 * 1024;

        private int maxOutboundControlQueue = 256;

        public Builder setConnectTimeout(final Timeout v) {
            this.connectTimeout = v;
            return this;
        }

        public Builder setSubprotocols(final List<String> v) {
            this.subprotocols = v;
            return this;
        }

        public Builder enablePerMessageDeflate(final boolean v) {
            this.perMessageDeflateEnabled = v;
            return this;
        }

        public Builder offerServerNoContextTakeover(final boolean v) {
            this.offerServerNoContextTakeover = v;
            return this;
        }

        public Builder offerClientNoContextTakeover(final boolean v) {
            this.offerClientNoContextTakeover = v;
            return this;
        }

        public Builder offerClientMaxWindowBits(final Integer v) {
            this.offerClientMaxWindowBits = v;
            return this;
        }

        public Builder offerServerMaxWindowBits(final Integer v) {
            this.offerServerMaxWindowBits = v;
            return this;
        }

        public Builder setMaxFrameSize(final int v) {
            this.maxFrameSize = v;
            return this;
        }

        public Builder setOutgoingChunkSize(final int v) {
            this.outgoingChunkSize = v;
            return this;
        }

        public Builder setMaxFramesPerTick(final int v) {
            this.maxFramesPerTick = v;
            return this;
        }

        public Builder setIoPoolCapacity(final int v) {
            this.ioPoolCapacity = v;
            return this;
        }

        public Builder setDirectBuffers(final boolean v) {
            this.directBuffers = v;
            return this;
        }

        public Builder setAutoPong(final boolean v) {
            this.autoPong = v;
            return this;
        }

        public Builder setCloseWaitTimeout(final Timeout v) {
            this.closeWaitTimeout = v;
            return this;
        }

        public Builder setMaxMessageSize(final long v) {
            this.maxMessageSize = v;
            return this;
        }

        public Builder setMaxOutboundControlQueue(final int v) {
            this.maxOutboundControlQueue = v;
            return this;
        }

        public WebSocketClientConfig build() {
            if (maxFrameSize <= 0) {
                throw new IllegalArgumentException("maxFrameSize > 0");
            }
            if (outgoingChunkSize <= 0) {
                throw new IllegalArgumentException("outgoingChunkSize > 0");
            }
            if (maxFramesPerTick <= 0) {
                throw new IllegalArgumentException("maxFramesPerTick > 0");
            }
            if (ioPoolCapacity <= 0) {
                throw new IllegalArgumentException("ioPoolCapacity > 0");
            }
            if (maxMessageSize <= 0) {
                throw new IllegalArgumentException("maxMessageSize > 0");
            }
            if (maxOutboundControlQueue <= 0) {
                throw new IllegalArgumentException("maxOutboundControlQueue > 0");
            }
            if (closeWaitTimeout == null) {
                throw new IllegalArgumentException("closeWaitTimeout != null");
            }
            return new WebSocketClientConfig(
                    connectTimeout, subprotocols,
                    perMessageDeflateEnabled, offerServerNoContextTakeover, offerClientNoContextTakeover,
                    offerClientMaxWindowBits, offerServerMaxWindowBits,
                    maxFrameSize, outgoingChunkSize, maxFramesPerTick,
                    ioPoolCapacity, directBuffers,
                    autoPong, closeWaitTimeout, maxMessageSize,
                    maxOutboundControlQueue
            );
        }
    }
}
