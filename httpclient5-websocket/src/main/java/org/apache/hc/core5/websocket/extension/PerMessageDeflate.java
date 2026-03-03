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
package org.apache.hc.core5.websocket.extension;

import java.io.ByteArrayOutputStream;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.websocket.frame.FrameHeaderBits;

/**
 * permessage-deflate (RFC 7692).
 *
 * <p>Window bit parameters are negotiated during the handshake:
 * {@code client_max_window_bits} limits the client's compression window (client->server),
 * while {@code server_max_window_bits} limits the server's compression window (server->client).
 * The decoder can accept any server window size (8..15). The encoder currently requires
 * {@code client_max_window_bits} to be 15, due to JDK Deflater limitations.</p>
 */
@Internal
public final class PerMessageDeflate implements WebSocketExtensionChain {
    private static final byte[] TAIL = new byte[]{0x00, 0x00, (byte) 0xFF, (byte) 0xFF};

    private final boolean enabled;
    private final boolean serverNoContextTakeover;
    private final boolean clientNoContextTakeover;
    private final Integer clientMaxWindowBits; // negotiated or null
    private final Integer serverMaxWindowBits; // negotiated or null

    public PerMessageDeflate(final boolean enabled,
                             final boolean serverNoContextTakeover,
                             final boolean clientNoContextTakeover,
                             final Integer clientMaxWindowBits,
                             final Integer serverMaxWindowBits) {
        this.enabled = enabled;
        this.serverNoContextTakeover = serverNoContextTakeover;
        this.clientNoContextTakeover = clientNoContextTakeover;
        this.clientMaxWindowBits = clientMaxWindowBits;
        this.serverMaxWindowBits = serverMaxWindowBits;
    }

    @Override
    public int rsvMask() {
        return FrameHeaderBits.RSV1;
    }

    @Override
    public Encoder newEncoder() {
        if (!enabled) {
            return (data, first, fin) -> new Encoded(data, false);
        }
        return new Encoder() {
            private final Deflater def = new Deflater(Deflater.DEFAULT_COMPRESSION, true); // raw DEFLATE

            @Override
            public Encoded encode(final byte[] data, final boolean first, final boolean fin) {
                final byte[] out = first && fin
                        ? compressMessage(data)
                        : compressFragment(data, fin);
                // RSV1 on first compressed data frame only
                return new Encoded(out, first);
            }

            private byte[] compressMessage(final byte[] data) {
                return doDeflate(data, true, true, clientNoContextTakeover);
            }

            private byte[] compressFragment(final byte[] data, final boolean fin) {
                return doDeflate(data, fin, true, fin && clientNoContextTakeover);
            }

            private byte[] doDeflate(final byte[] data,
                                     final boolean fin,
                                     final boolean stripTail,
                                     final boolean maybeReset) {
                if (data == null || data.length == 0) {
                    if (fin && maybeReset) {
                        def.reset();
                    }
                    return new byte[0];
                }
                def.setInput(data);
                final ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(128, data.length / 2));
                final byte[] buf = new byte[8192];
                while (!def.needsInput()) {
                    final int n = def.deflate(buf, 0, buf.length, Deflater.SYNC_FLUSH);
                    if (n > 0) {
                        out.write(buf, 0, n);
                    } else {
                        break;
                    }
                }
                byte[] all = out.toByteArray();
                if (stripTail && all.length >= 4) {
                    final int newLen = all.length - 4; // strip 00 00 FF FF
                    if (newLen <= 0) {
                        all = new byte[0];
                    } else {
                        final byte[] trimmed = new byte[newLen];
                        System.arraycopy(all, 0, trimmed, 0, newLen);
                        all = trimmed;
                    }
                }
                if (fin && maybeReset) {
                    def.reset();
                }
                return all;
            }
        };
    }

    @Override
    public Decoder newDecoder() {
        if (!enabled) {
            return payload -> payload;
        }
        return new Decoder() {
            private final Inflater inf = new Inflater(true);

            @Override
            public byte[] decode(final byte[] compressedMessage) throws Exception {
                final byte[] withTail;
                if (compressedMessage == null || compressedMessage.length == 0) {
                    withTail = TAIL.clone();
                } else {
                    withTail = new byte[compressedMessage.length + 4];
                    System.arraycopy(compressedMessage, 0, withTail, 0, compressedMessage.length);
                    System.arraycopy(TAIL, 0, withTail, compressedMessage.length, 4);
                }

                inf.setInput(withTail);
                final ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(128, withTail.length * 2));
                final byte[] buf = new byte[8192];
                while (!inf.needsInput()) {
                    final int n = inf.inflate(buf);
                    if (n > 0) {
                        out.write(buf, 0, n);
                    } else {
                        break;
                    }
                }
                if (serverNoContextTakeover) {
                    inf.reset();
                }
                return out.toByteArray();
            }
        };
    }

    // optional getters for logging/tests
    public boolean isEnabled() {
        return enabled;
    }

    public boolean isServerNoContextTakeover() {
        return serverNoContextTakeover;
    }

    public boolean isClientNoContextTakeover() {
        return clientNoContextTakeover;
    }

    public Integer getClientMaxWindowBits() {
        return clientMaxWindowBits;
    }

    public Integer getServerMaxWindowBits() {
        return serverMaxWindowBits;
    }
}