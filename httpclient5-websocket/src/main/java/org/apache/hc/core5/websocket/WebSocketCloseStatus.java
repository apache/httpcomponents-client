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

public enum WebSocketCloseStatus {

    NORMAL(1000),
    GOING_AWAY(1001),
    PROTOCOL_ERROR(1002),
    UNSUPPORTED_DATA(1003),
    NO_STATUS_RECEIVED(1005),
    ABNORMAL_CLOSURE(1006),
    INVALID_PAYLOAD(1007),
    POLICY_VIOLATION(1008),
    MESSAGE_TOO_BIG(1009),
    MANDATORY_EXTENSION(1010),
    INTERNAL_ERROR(1011),
    SERVICE_RESTART(1012),
    TRY_AGAIN_LATER(1013),
    BAD_GATEWAY(1014);

    private final int code;

    WebSocketCloseStatus(final int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
