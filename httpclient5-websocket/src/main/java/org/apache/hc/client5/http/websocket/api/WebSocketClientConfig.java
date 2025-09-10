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
 * fragmentation behaviour and optional automatic responses to PING frames.</p>
 *
 * <p>Unless explicitly overridden, reasonable defaults are selected for
 * desktop and server environments. For mobile or memory-constrained
 * deployments, consider adjusting buffer sizes and queue limits.</p>
 *
 * @since 5.7
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

    // Buffers
    private final boolean directBuffers;

    // Behavior
    private final boolean autoPong;
    private final Timeout closeWaitTimeout;
    private final long maxMessageSize;
    private final boolean http2Enabled;

    // Outbound queue limits
    private final int maxOutboundControlQueue;
    private final long maxOutboundDataBytes;

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
            final boolean directBuffers,
            final boolean autoPong,
            final Timeout closeWaitTimeout,
            final long maxMessageSize,
            final int maxOutboundControlQueue,
            final long maxOutboundDataBytes,
            final boolean http2Enabled) {

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
        this.directBuffers = directBuffers;
        this.autoPong = autoPong;
        this.closeWaitTimeout = Args.notNull(closeWaitTimeout, "closeWaitTimeout");
        this.maxMessageSize = maxMessageSize;
        this.maxOutboundControlQueue = maxOutboundControlQueue;
        this.maxOutboundDataBytes = maxOutboundDataBytes;
        this.http2Enabled = http2Enabled;
    }

    /**
     * Timeout used for establishing the initial TCP/TLS connection.
     *
     * @return connection timeout, may be {@code null} if the caller wants to rely on defaults
     * @since 5.7
     */
    public Timeout getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * Ordered list of WebSocket subprotocols offered to the server via {@code Sec-WebSocket-Protocol}.
     *
     * <p>The server may select at most one. The client should treat a server-selected protocol that
     * was not offered as a handshake failure.</p>
     *
     * @return immutable list of offered subprotocols (never {@code null})
     * @since 5.7
     */
    public List<String> getSubprotocols() {
        return subprotocols;
    }

    /**
     * Whether the client offers the {@code permessage-deflate} extension during the handshake.
     *
     * @return {@code true} if PMCE is offered, {@code false} otherwise
     * @since 5.7
     */
    public boolean isPerMessageDeflateEnabled() {
        return perMessageDeflateEnabled;
    }

    /**
     * Whether the client offers the {@code server_no_context_takeover} PMCE parameter.
     *
     * @return {@code true} if the parameter is included in the offer
     * @since 5.7
     */
    public boolean isOfferServerNoContextTakeover() {
        return offerServerNoContextTakeover;
    }

    /**
     * Whether the client offers the {@code client_no_context_takeover} PMCE parameter.
     *
     * @return {@code true} if the parameter is included in the offer
     * @since 5.7
     */
    public boolean isOfferClientNoContextTakeover() {
        return offerClientNoContextTakeover;
    }

    /**
     * Optional value for {@code client_max_window_bits} in the PMCE offer.
     *
     * <p>Valid values are in range 8..15 when non-null. The client encoder
     * currently supports only {@code 15} due to JDK Deflater limitations.</p>
     *
     * @return offered {@code client_max_window_bits}, or {@code null} if not offered
     * @since 5.7
     */
    public Integer getOfferClientMaxWindowBits() {
        return offerClientMaxWindowBits;
    }

    /**
     * Optional value for {@code server_max_window_bits} in the PMCE offer.
     *
     * <p>Valid values are in range 8..15 when non-null. This value limits the
     * server's compressor; the client decoder can accept any 8..15 value.</p>
     *
     * @return offered {@code server_max_window_bits}, or {@code null} if not offered
     * @since 5.7
     */
    public Integer getOfferServerMaxWindowBits() {
        return offerServerMaxWindowBits;
    }

    /**
     * Maximum accepted WebSocket frame payload size.
     *
     * <p>If an incoming frame exceeds this limit, the implementation should treat it as a protocol
     * violation and initiate a close with an appropriate close code.</p>
     *
     * @return maximum frame payload size in bytes (must be &gt; 0)
     * @since 5.7
     */
    public int getMaxFrameSize() {
        return maxFrameSize;
    }

    /**
     * Preferred outgoing fragmentation chunk size.
     *
     * <p>Outgoing messages larger than this value may be fragmented into multiple frames.</p>
     *
     * @return outgoing chunk size in bytes (must be &gt; 0)
     * @since 5.7
     */
    public int getOutgoingChunkSize() {
        return outgoingChunkSize;
    }

    /**
     * Limit of frames written per reactor "tick".
     *
     * <p>This is a fairness control to reduce the risk of starving the reactor thread when
     * a large backlog exists.</p>
     *
     * @return maximum frames per tick (must be &gt; 0)
     * @since 5.7
     */
    public int getMaxFramesPerTick() {
        return maxFramesPerTick;
    }

    /**
     * Whether direct byte buffers are preferred for internal I/O.
     *
     * @return {@code true} for direct buffers, {@code false} for heap buffers
     * @since 5.7
     */
    public boolean isDirectBuffers() {
        return directBuffers;
    }

    /**
     * Whether the client automatically responds to PING frames with a PONG frame.
     *
     * @return {@code true} if auto-PONG is enabled
     * @since 5.7
     */
    public boolean isAutoPong() {
        return autoPong;
    }

    /**
     * Socket timeout used while waiting for the peer to complete the close handshake.
     *
     * @return close wait timeout (never {@code null})
     * @since 5.7
     */
    public Timeout getCloseWaitTimeout() {
        return closeWaitTimeout;
    }

    /**
     * Maximum accepted message size after fragment reassembly (and after decompression if enabled).
     *
     * @return maximum message size in bytes (must be &gt; 0)
     * @since 5.7
     */
    public long getMaxMessageSize() {
        return maxMessageSize;
    }

    /**
     * Maximum number of queued outbound control frames.
     *
     * <p>This bounds memory usage and prevents unbounded growth of control traffic under backpressure.</p>
     *
     * @return maximum outbound control queue size (must be &gt; 0)
     * @since 5.7
     */
    public int getMaxOutboundControlQueue() {
        return maxOutboundControlQueue;
    }

    /**
     * Upper bound for the total number of bytes queued for outbound data frames.
     *
     * <p>When this limit is exceeded, {@code sendText/sendBinary} return {@code false}
     * and the data frame is rejected. A value of {@code 0} disables the limit.</p>
     *
     * @return max queued bytes for outbound data frames
     * @since 5.7
     */
    public long getMaxOutboundDataBytes() {
        return maxOutboundDataBytes;
    }

    /**
     * Returns {@code true} if HTTP/2 Extended CONNECT (RFC 8441) is enabled.
     *
     * @since 5.7
     */
    public boolean isHttp2Enabled() {
        return http2Enabled;
    }

    /**
     * Creates a new builder instance with default settings.
     *
     * @return builder
     * @since 5.7
     */
    public static Builder custom() {
        return new Builder();
    }

    /**
     * Builder for {@link WebSocketClientConfig}.
     *
     * <p>The builder is mutable and not thread-safe.</p>
     *
     * @since 5.7
     */
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

        private boolean directBuffers = true;

        private boolean autoPong = true;
        private Timeout closeWaitTimeout = Timeout.ofSeconds(10);
        private long maxMessageSize = 8L * 1024 * 1024;

        private int maxOutboundControlQueue = 256;
        private long maxOutboundDataBytes = 16L * 1024 * 1024;
        private boolean http2Enabled;

        /**
         * Sets the timeout used to establish the initial TCP/TLS connection.
         *
         * @param v timeout, may be {@code null} to rely on defaults
         * @return this builder
         * @since 5.7
         */
        public Builder setConnectTimeout(final Timeout v) {
            this.connectTimeout = v;
            return this;
        }

        /**
         * Sets the ordered list of subprotocols offered to the server.
         *
         * @param v list of subprotocol names, may be {@code null} to offer none
         * @return this builder
         * @since 5.7
         */
        public Builder setSubprotocols(final List<String> v) {
            this.subprotocols = v;
            return this;
        }

        /**
         * Enables or disables offering {@code permessage-deflate} during the handshake.
         *
         * @param v {@code true} to offer PMCE, {@code false} otherwise
         * @return this builder
         * @since 5.7
         */
        public Builder enablePerMessageDeflate(final boolean v) {
            this.perMessageDeflateEnabled = v;
            return this;
        }

        /**
         * Offers {@code server_no_context_takeover} in the PMCE offer.
         *
         * @param v whether to include the parameter in the offer
         * @return this builder
         * @since 5.7
         */
        public Builder offerServerNoContextTakeover(final boolean v) {
            this.offerServerNoContextTakeover = v;
            return this;
        }

        /**
         * Offers {@code client_no_context_takeover} in the PMCE offer.
         *
         * @param v whether to include the parameter in the offer
         * @return this builder
         * @since 5.7
         */
        public Builder offerClientNoContextTakeover(final boolean v) {
            this.offerClientNoContextTakeover = v;
            return this;
        }

        /**
         * Offers {@code client_max_window_bits} in the PMCE offer.
         *
         * <p>Valid values are in range 8..15 when non-null. The client encoder
         * currently supports only {@code 15} due to JDK Deflater limitations.</p>
         *
         * @param v window bits, or {@code null} to omit the parameter
         * @return this builder
         * @since 5.7
         */
        public Builder offerClientMaxWindowBits(final Integer v) {
            this.offerClientMaxWindowBits = v;
            return this;
        }

        /**
         * Offers {@code server_max_window_bits} in the PMCE offer.
         *
         * <p>Valid values are in range 8..15 when non-null. This value limits the
         * server's compressor; the client decoder can accept any 8..15 value.</p>
         *
         * @param v window bits, or {@code null} to omit the parameter
         * @return this builder
         * @since 5.7
         */
        public Builder offerServerMaxWindowBits(final Integer v) {
            this.offerServerMaxWindowBits = v;
            return this;
        }

        /**
         * Sets the maximum accepted frame payload size.
         *
         * @param v maximum frame payload size in bytes (must be &gt; 0)
         * @return this builder
         * @since 5.7
         */
        public Builder setMaxFrameSize(final int v) {
            this.maxFrameSize = v;
            return this;
        }

        /**
         * Sets the preferred outgoing fragmentation chunk size.
         *
         * @param v chunk size in bytes (must be &gt; 0)
         * @return this builder
         * @since 5.7
         */
        public Builder setOutgoingChunkSize(final int v) {
            this.outgoingChunkSize = v;
            return this;
        }

        /**
         * Sets the limit of frames written per reactor tick.
         *
         * @param v max frames per tick (must be &gt; 0)
         * @return this builder
         * @since 5.7
         */
        public Builder setMaxFramesPerTick(final int v) {
            this.maxFramesPerTick = v;
            return this;
        }

        /**
         * Enables or disables the use of direct buffers for internal I/O.
         *
         * @param v {@code true} for direct buffers, {@code false} for heap buffers
         * @return this builder
         * @since 5.7
         */
        public Builder setDirectBuffers(final boolean v) {
            this.directBuffers = v;
            return this;
        }

        /**
         * Enables or disables automatic PONG replies for received PING frames.
         *
         * @param v {@code true} to auto-reply with PONG
         * @return this builder
         * @since 5.7
         */
        public Builder setAutoPong(final boolean v) {
            this.autoPong = v;
            return this;
        }

        /**
         * Sets the close handshake wait timeout.
         *
         * @param v close wait timeout, must not be {@code null}
         * @return this builder
         * @since 5.7
         */
        public Builder setCloseWaitTimeout(final Timeout v) {
            this.closeWaitTimeout = v;
            return this;
        }

        /**
         * Sets the maximum accepted message size.
         *
         * @param v max message size in bytes (must be &gt; 0)
         * @return this builder
         * @since 5.7
         */
        public Builder setMaxMessageSize(final long v) {
            this.maxMessageSize = v;
            return this;
        }

        /**
         * Sets the maximum number of queued outbound control frames.
         *
         * @param v max control queue size (must be &gt; 0)
         * @return this builder
         * @since 5.7
         */
        public Builder setMaxOutboundControlQueue(final int v) {
            this.maxOutboundControlQueue = v;
            return this;
        }

        /**
         * Sets the maximum number of bytes that can be queued for outbound data frames.
         *
         * <p>When the limit is exceeded, {@code sendText/sendBinary} return {@code false}.
         * A value of {@code 0} disables the limit.</p>
         *
         * @param v max queued bytes for outbound data frames
         * @return this builder
         * @since 5.7
         */
        public Builder setMaxOutboundDataBytes(final long v) {
            this.maxOutboundDataBytes = v;
            return this;
        }

        /**
         * Enables HTTP/2 Extended CONNECT (RFC 8441) for supported endpoints.
         *
         * @param enabled true to enable HTTP/2 WebSocket connections
         * @return this builder
         * @since 5.7
         */
        public Builder enableHttp2(final boolean enabled) {
            this.http2Enabled = enabled;
            return this;
        }

        /**
         * Builds an immutable {@link WebSocketClientConfig}.
         *
         * @return configuration instance
         * @throws IllegalArgumentException if any parameter is invalid
         * @since 5.7
         */
        public WebSocketClientConfig build() {
            if (offerClientMaxWindowBits != null && (offerClientMaxWindowBits < 8 || offerClientMaxWindowBits > 15)) {
                throw new IllegalArgumentException("offerClientMaxWindowBits must be in range [8..15]");
            }
            if (offerServerMaxWindowBits != null && (offerServerMaxWindowBits < 8 || offerServerMaxWindowBits > 15)) {
                throw new IllegalArgumentException("offerServerMaxWindowBits must be in range [8..15]");
            }
            if (closeWaitTimeout == null) {
                throw new IllegalArgumentException("closeWaitTimeout != null");
            }
            if (maxFrameSize <= 0) {
                throw new IllegalArgumentException("maxFrameSize > 0");
            }
            if (outgoingChunkSize <= 0) {
                throw new IllegalArgumentException("outgoingChunkSize > 0");
            }
            if (maxFramesPerTick <= 0) {
                throw new IllegalArgumentException("maxFramesPerTick > 0");
            }
            if (maxMessageSize <= 0) {
                throw new IllegalArgumentException("maxMessageSize > 0");
            }
            if (maxOutboundControlQueue <= 0) {
                throw new IllegalArgumentException("maxOutboundControlQueue > 0");
            }
            if (maxOutboundDataBytes < 0) {
                throw new IllegalArgumentException("maxOutboundDataBytes >= 0");
            }
            return new WebSocketClientConfig(
                    connectTimeout, subprotocols,
                    perMessageDeflateEnabled, offerServerNoContextTakeover, offerClientNoContextTakeover,
                    offerClientMaxWindowBits, offerServerMaxWindowBits,
                    maxFrameSize, outgoingChunkSize, maxFramesPerTick,
                    directBuffers,
                    autoPong, closeWaitTimeout, maxMessageSize,
                    maxOutboundControlQueue,
                    maxOutboundDataBytes,
                    http2Enabled
            );
        }
    }
}