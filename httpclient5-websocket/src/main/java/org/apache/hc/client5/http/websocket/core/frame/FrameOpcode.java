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
package org.apache.hc.client5.http.websocket.core.frame;

import org.apache.hc.core5.annotation.Internal;

/**
 * RFC 6455 opcode constants + helpers.
 */
@Internal
public final class FrameOpcode {
    public static final int CONT = 0x0;
    public static final int TEXT = 0x1;
    public static final int BINARY = 0x2;
    public static final int CLOSE = 0x8;
    public static final int PING = 0x9;
    public static final int PONG = 0xA;

    private FrameOpcode() {
    }

    /**
     * Control frames have the high bit set in the low nibble (0x8–0xF).
     */
    public static boolean isControl(final int opcode) {
        return (opcode & 0x08) != 0;
    }

    /**
     * Data opcodes (not continuation).
     */
    public static boolean isData(final int opcode) {
        return opcode == TEXT || opcode == BINARY;
    }

    /**
     * Continuation opcode.
     */
    public static boolean isContinuation(final int opcode) {
        return opcode == CONT;
    }

    /**
     * Optional: human-readable name for debugging.
     */
    public static String name(final int opcode) {
        switch (opcode) {
            case CONT:
                return "CONT";
            case TEXT:
                return "TEXT";
            case BINARY:
                return "BINARY";
            case CLOSE:
                return "CLOSE";
            case PING:
                return "PING";
            case PONG:
                return "PONG";
            default:
                return "0x" + Integer.toHexString(opcode);
        }
    }
}
