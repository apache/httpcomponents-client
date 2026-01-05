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
package org.apache.hc.client5.http.websocket.core.extension;

import org.apache.hc.core5.annotation.Internal;

/**
 * Generic extension hook for payload transform (e.g., permessage-deflate).
 * Implementations may return RSV mask (usually RSV1) and indicate whether
 * the first frame of a message should set RSV.
 */
@Internal
public interface WebSocketExtensionChain {

    /**
     * RSV bits this extension uses on the first data frame (e.g. 0x40 for RSV1).
     */
    int rsvMask();

    /**
     * Create a thread-confined encoder instance (app thread).
     */
    Encoder newEncoder();

    /**
     * Create a thread-confined decoder instance (I/O thread).
     */
    Decoder newDecoder();

    /**
     * Encoded fragment result.
     */
    final class Encoded {
        public final byte[] payload;
        public final boolean setRsvOnFirst;

        public Encoded(final byte[] payload, final boolean setRsvOnFirst) {
            this.payload = payload;
            this.setRsvOnFirst = setRsvOnFirst;
        }
    }

    interface Encoder {
        /**
         * Encode one fragment; return transformed payload and whether to set RSV on FIRST frame.
         */
        Encoded encode(byte[] data, boolean first, boolean fin);
    }

    interface Decoder {
        /**
         * Decode a full message produced with this extension.
         */
        byte[] decode(byte[] payload) throws Exception;
    }
}
