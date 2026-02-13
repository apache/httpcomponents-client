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

import org.apache.hc.core5.util.Args;

public final class WebSocketConfig {

    public static final WebSocketConfig DEFAULT = WebSocketConfig.custom().build();

    private final int maxFramePayloadSize;
    private final int maxMessageSize;

    private WebSocketConfig(final Builder builder) {
        this.maxFramePayloadSize = builder.maxFramePayloadSize;
        this.maxMessageSize = builder.maxMessageSize;
    }

    public int getMaxFramePayloadSize() {
        return maxFramePayloadSize;
    }

    public int getMaxMessageSize() {
        return maxMessageSize;
    }

    public static Builder custom() {
        return new Builder();
    }

    public static final class Builder {

        private int maxFramePayloadSize = 16 * 1024 * 1024;
        private int maxMessageSize = 64 * 1024 * 1024;

        public Builder setMaxFramePayloadSize(final int maxFramePayloadSize) {
            this.maxFramePayloadSize = Args.positive(maxFramePayloadSize, "Max frame payload size");
            return this;
        }

        public Builder setMaxMessageSize(final int maxMessageSize) {
            this.maxMessageSize = Args.positive(maxMessageSize, "Max message size");
            return this;
        }

        public WebSocketConfig build() {
            return new WebSocketConfig(this);
        }
    }
}
