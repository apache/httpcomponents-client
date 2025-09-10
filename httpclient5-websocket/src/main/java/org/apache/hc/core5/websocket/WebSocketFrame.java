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
package org.apache.hc.core5.websocket;

import java.nio.ByteBuffer;

import org.apache.hc.core5.util.Args;

public final class WebSocketFrame {

    private final boolean fin;
    private final boolean rsv1;
    private final boolean rsv2;
    private final boolean rsv3;
    private final WebSocketFrameType type;
    private final ByteBuffer payload;

    public WebSocketFrame(
            final boolean fin,
            final boolean rsv1,
            final boolean rsv2,
            final boolean rsv3,
            final WebSocketFrameType type,
            final ByteBuffer payload) {
        this.fin = fin;
        this.rsv1 = rsv1;
        this.rsv2 = rsv2;
        this.rsv3 = rsv3;
        this.type = Args.notNull(type, "Frame type");
        this.payload = payload != null ? payload.asReadOnlyBuffer() : ByteBuffer.allocate(0).asReadOnlyBuffer();
    }

    public boolean isFin() {
        return fin;
    }

    public boolean isRsv1() {
        return rsv1;
    }

    public boolean isRsv2() {
        return rsv2;
    }

    public boolean isRsv3() {
        return rsv3;
    }

    public WebSocketFrameType getType() {
        return type;
    }

    public ByteBuffer getPayload() {
        return payload.asReadOnlyBuffer();
    }
}
