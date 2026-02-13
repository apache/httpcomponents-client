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

public enum WebSocketFrameType {

    CONTINUATION(0x0),
    TEXT(0x1),
    BINARY(0x2),
    CLOSE(0x8),
    PING(0x9),
    PONG(0xA);

    private final int opcode;

    WebSocketFrameType(final int opcode) {
        this.opcode = opcode;
    }

    public int getOpcode() {
        return opcode;
    }

    public boolean isControl() {
        return opcode >= 0x8;
    }

    public static WebSocketFrameType fromOpcode(final int opcode) {
        for (final WebSocketFrameType type : values()) {
            if (type.opcode == opcode) {
                return type;
            }
        }
        return null;
    }
}
