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
package org.apache.hc.core5.websocket.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.websocket.WebSocketCloseStatus;
import org.apache.hc.core5.websocket.WebSocketException;
import org.apache.hc.core5.websocket.WebSocketFrame;
import org.apache.hc.core5.websocket.WebSocketFrameType;
import org.apache.hc.core5.websocket.WebSocketHandler;
import org.apache.hc.core5.websocket.WebSocketSession;
import org.apache.hc.core5.websocket.exceptions.WebSocketProtocolException;
import org.apache.hc.core5.websocket.message.CloseCodec;

class WebSocketServerProcessor {

    private final WebSocketSession session;
    private final WebSocketHandler handler;
    private final int maxMessageSize;

    WebSocketServerProcessor(final WebSocketSession session, final WebSocketHandler handler, final int maxMessageSize) {
        this.session = Args.notNull(session, "WebSocket session");
        this.handler = Args.notNull(handler, "WebSocket handler");
        this.maxMessageSize = maxMessageSize;
    }

    void process() throws IOException {
        ByteArrayOutputStream continuationBuffer = null;
        WebSocketFrameType continuationType = null;
        while (true) {
            final WebSocketFrame frame = session.readFrame();
            if (frame == null) {
                break;
            }
            if (frame.isRsv2() || frame.isRsv3()) {
                throw new WebSocketException("Unsupported RSV bits");
            }
            final WebSocketFrameType type = frame.getType();
            final int payloadLen = frame.getPayload().remaining();
            if (type == WebSocketFrameType.CLOSE
                    || type == WebSocketFrameType.PING
                    || type == WebSocketFrameType.PONG) {
                if (!frame.isFin() || payloadLen > 125) {
                    throw new WebSocketException("Invalid control frame");
                }
            }
            switch (type) {
                case PING:
                    handler.onPing(session, frame.getPayload());
                    session.sendPong(frame.getPayload());
                    break;
                case PONG:
                    handler.onPong(session, frame.getPayload());
                    break;
                case CLOSE:
                    handleCloseFrame(frame);
                    return;
                case TEXT:
                case BINARY:
                    if (frame.isFin()) {
                        dispatchMessage(type, frame.getPayload());
                    } else {
                        continuationBuffer = startContinuation(type, frame.getPayload());
                        continuationType = type;
                    }
                    break;
                case CONTINUATION:
                    if (continuationBuffer == null || continuationType == null) {
                        throw new WebSocketException("Unexpected continuation frame");
                    }
                    appendContinuation(continuationBuffer, frame.getPayload());
                    if (frame.isFin()) {
                        final ByteBuffer payload = ByteBuffer.wrap(continuationBuffer.toByteArray());
                        dispatchMessage(continuationType, payload);
                        continuationBuffer = null;
                        continuationType = null;
                    }
                    break;
                default:
                    throw new WebSocketException("Unsupported frame type: " + type);
            }
        }
    }

    private void dispatchMessage(final WebSocketFrameType type, final ByteBuffer payload) throws IOException {
        if (payload.remaining() > maxMessageSize) {
            throw new WebSocketProtocolException(1009, "Message too large: " + payload.remaining());
        }
        if (type == WebSocketFrameType.TEXT) {
            handler.onText(session, WebSocketSession.decodeText(payload));
        } else {
            handler.onBinary(session, payload);
        }
    }

    private ByteArrayOutputStream startContinuation(final WebSocketFrameType type, final ByteBuffer payload) throws WebSocketException {
        if (payload.remaining() > maxMessageSize) {
            throw new WebSocketProtocolException(1009, "Message too large: " + payload.remaining());
        }
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream(payload.remaining());
        appendContinuation(buffer, payload);
        return buffer;
    }

    private void appendContinuation(final ByteArrayOutputStream buffer, final ByteBuffer payload) throws WebSocketException {
        if (buffer.size() + payload.remaining() > maxMessageSize) {
            throw new WebSocketProtocolException(1009, "Message too large: " + (buffer.size() + payload.remaining()));
        }
        final ByteBuffer copy = payload.asReadOnlyBuffer();
        final byte[] data = new byte[copy.remaining()];
        copy.get(data);
        buffer.write(data, 0, data.length);
    }

    private void handleCloseFrame(final WebSocketFrame frame) throws IOException {
        final ByteBuffer payload = frame.getPayload();
        final int remaining = payload.remaining();
        int statusCode = WebSocketCloseStatus.NORMAL.getCode();
        String reason = "";
        if (remaining == 1) {
            throw new WebSocketProtocolException(1002, "Invalid close payload length");
        } else if (remaining >= 2) {
            final int code = ((payload.get() & 0xFF) << 8) | (payload.get() & 0xFF);
            if (!CloseCodec.isValidToReceive(code)) {
                throw new WebSocketProtocolException(1002, "Invalid close code: " + code);
            }
            statusCode = code;
            if (payload.hasRemaining()) {
                reason = WebSocketSession.decodeText(payload);
            }
        }
        handler.onClose(session, statusCode, reason);
        session.close(statusCode, reason);
    }
}
