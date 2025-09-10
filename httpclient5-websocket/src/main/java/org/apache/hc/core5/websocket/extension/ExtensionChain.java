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

import java.util.ArrayList;
import java.util.List;

import org.apache.hc.core5.annotation.Internal;

/**
 * Simple single-step chain; if multiple extensions are added they are applied in order.
 * Only the FIRST extension can contribute the RSV bit (RSV1 in practice).
 */
@Internal
public final class ExtensionChain {
    private final List<WebSocketExtensionChain> exts = new ArrayList<>();

    public void add(final WebSocketExtensionChain e) {
        if (e != null) {
            exts.add(e);
        }
    }

    public boolean isEmpty() {
        return exts.isEmpty();
    }

    /**
     * RSV bits used by the first extension in the chain (if any).
     * Only the first extension may contribute RSV bits.
     */
    public int rsvMask() {
        if (exts.isEmpty()) {
            return 0;
        }
        return exts.get(0).rsvMask();
    }

    /**
     * App-thread encoder chain.
     */
    public EncodeChain newEncodeChain() {
        final List<WebSocketExtensionChain.Encoder> encs = new ArrayList<>(exts.size());
        for (final WebSocketExtensionChain e : exts) {
            encs.add(e.newEncoder());
        }
        return new EncodeChain(encs);
    }

    /**
     * I/O-thread decoder chain.
     */
    public DecodeChain newDecodeChain() {
        final List<WebSocketExtensionChain.Decoder> decs = new ArrayList<>(exts.size());
        for (final WebSocketExtensionChain e : exts) {
            decs.add(e.newDecoder());
        }
        return new DecodeChain(decs);
    }

    // ----------------------

    public static final class EncodeChain {
        private final List<WebSocketExtensionChain.Encoder> encs;

        public EncodeChain(final List<WebSocketExtensionChain.Encoder> encs) {
            this.encs = encs;
        }

        /**
         * Encode one fragment through the chain; note RSV flag for the first extension.
         * Returns {@link WebSocketExtensionChain.Encoded}.
         */
        public WebSocketExtensionChain.Encoded encode(final byte[] data, final boolean first, final boolean fin) {
            if (encs.isEmpty()) {
                return new WebSocketExtensionChain.Encoded(data, false);
            }
            byte[] out = data;
            boolean setRsv1 = false;
            boolean firstExt = true;
            for (final WebSocketExtensionChain.Encoder e : encs) {
                final WebSocketExtensionChain.Encoded res = e.encode(out, first, fin);
                out = res.payload;
                if (first && firstExt && res.setRsvOnFirst) {
                    setRsv1 = true;
                }
                firstExt = false;
            }
            return new WebSocketExtensionChain.Encoded(out, setRsv1);
        }
    }

    public static final class DecodeChain {
        private final List<WebSocketExtensionChain.Decoder> decs;

        public DecodeChain(final List<WebSocketExtensionChain.Decoder> decs) {
            this.decs = decs;
        }

        /**
         * Decode a full message (reverse order if stacking).
         */
        public byte[] decode(final byte[] data) throws Exception {
            byte[] out = data;
            for (int i = decs.size() - 1; i >= 0; i--) {
                out = decs.get(i).decode(out);
            }
            return out;
        }
    }
}