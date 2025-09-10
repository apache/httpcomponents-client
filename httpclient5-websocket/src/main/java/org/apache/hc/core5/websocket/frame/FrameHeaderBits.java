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
package org.apache.hc.core5.websocket.frame;

import org.apache.hc.core5.annotation.Internal;

/**
 * WebSocket frame header bit masks (RFC 6455 §5.2).
 */
@Internal
public final class FrameHeaderBits {
    private FrameHeaderBits() {
    }

    // First header byte
    public static final int FIN = 0x80;
    public static final int RSV1 = 0x40;
    public static final int RSV2 = 0x20;
    public static final int RSV3 = 0x10;
    // low 4 bits (0x0F) are opcode

    // Second header byte
    public static final int MASK_BIT = 0x80;  // client->server payload mask bit
    // low 7 bits (0x7F) are payload len indicator
}
