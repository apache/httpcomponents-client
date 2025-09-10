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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.websocket.exceptions.WebSocketProtocolException;

public final class WebSocketSession {

    private final WebSocketConfig config;
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private final SocketAddress remoteAddress;
    private final SocketAddress localAddress;
    private final WebSocketFrameReader reader;
    private final WebSocketFrameWriter writer;
    private final ReentrantLock writeLock = new ReentrantLock();
    private volatile boolean closeSent;

    public WebSocketSession(
            final WebSocketConfig config,
            final InputStream inputStream,
            final OutputStream outputStream,
            final SocketAddress remoteAddress,
            final SocketAddress localAddress,
            final List<WebSocketExtension> extensions) {
        this.config = config != null ? config : WebSocketConfig.DEFAULT;
        this.inputStream = Args.notNull(inputStream, "Input stream");
        this.outputStream = Args.notNull(outputStream, "Output stream");
        this.remoteAddress = remoteAddress;
        this.localAddress = localAddress;
        final List<WebSocketExtension> negotiated = extensions != null ? extensions : Collections.emptyList();
        this.reader = new WebSocketFrameReader(this.config, this.inputStream, negotiated);
        this.writer = new WebSocketFrameWriter(this.outputStream, negotiated);
        this.closeSent = false;
    }

    public SocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    public SocketAddress getLocalAddress() {
        return localAddress;
    }

    public WebSocketFrame readFrame() throws IOException {
        return reader.readFrame();
    }

    public void sendText(final String text) throws IOException, WebSocketException {
        Args.notNull(text, "Text");
        writeLock.lock();
        try {
            writer.writeText(text);
        } finally {
            writeLock.unlock();
        }
    }

    public void sendBinary(final ByteBuffer data) throws IOException, WebSocketException {
        Args.notNull(data, "Binary payload");
        writeLock.lock();
        try {
            writer.writeBinary(data);
        } finally {
            writeLock.unlock();
        }
    }

    public void sendPing(final ByteBuffer data) throws IOException {
        writeLock.lock();
        try {
            writer.writePing(data);
        } finally {
            writeLock.unlock();
        }
    }

    public void sendPong(final ByteBuffer data) throws IOException {
        writeLock.lock();
        try {
            writer.writePong(data);
        } finally {
            writeLock.unlock();
        }
    }

    public void close(final int statusCode, final String reason) throws IOException {
        writeLock.lock();
        try {
            if (!closeSent) {
                writer.writeClose(statusCode, reason);
                closeSent = true;
            }
        } finally {
            writeLock.unlock();
        }
    }

    public void close(final WebSocketCloseStatus status) throws IOException {
        final int code = status != null ? status.getCode() : WebSocketCloseStatus.NORMAL.getCode();
        close(code, "");
    }

    public void closeQuietly() {
        try {
            close(WebSocketCloseStatus.NORMAL.getCode(), "");
        } catch (final IOException ignore) {
            // ignore
        }
    }

    public static String decodeText(final ByteBuffer payload) throws WebSocketException {
        try {
            final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            final CharBuffer chars = decoder.decode(payload.asReadOnlyBuffer());
            return chars.toString();
        } catch (final CharacterCodingException ex) {
            throw new WebSocketProtocolException(1007, "Invalid UTF-8 payload");
        }
    }
}
