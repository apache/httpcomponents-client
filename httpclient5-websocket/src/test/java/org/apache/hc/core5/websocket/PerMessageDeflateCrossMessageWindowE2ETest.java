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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;

import org.apache.hc.core5.http.ExceptionListener;
import org.apache.hc.core5.websocket.server.WebSocketServer;
import org.apache.hc.core5.websocket.server.WebSocketServerBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end test against the project's actual {@link WebSocketServer}: two consecutive
 * TEXT messages, using the two literal wire vectors RFC 7692 gives as worked examples.
 *
 * <p>Message 1 is the RFC 7692 section 7.2.3.4 example: "Hello" compressed as a single
 * DEFLATE block with BFINAL set to 1 (the "flush via BFINAL" technique for platforms
 * without an empty-block flush primitive): {@code f3 48 cd c9 c9 07 00 00}.</p>
 *
 * <p>Message 2 is the RFC 7692 section 7.2.3.2 example: "Hello" again, compressed as a
 * short LZ77 back-reference into message 1's output, which context takeover (the
 * default; neither side negotiates {@code *_no_context_takeover}) entitles a peer to
 * do: {@code f2 00 11 00 00}.</p>
 *
 * <p>Both messages are sent by a minimal hand-rolled RFC 6455 client: no encoder in
 * this project produces the BFINAL-flush technique from 7.2.3.4, but the bytes sent
 * here are the RFC's own examples.</p>
 */
class PerMessageDeflateCrossMessageWindowE2ETest {

    private static final byte[] MSG1_BFINAL_FLUSH_HELLO =
            {(byte) 0xf3, 0x48, (byte) 0xcd, (byte) 0xc9, (byte) 0xc9, 0x07, 0x00, 0x00};
    private static final byte[] MSG2_CONTEXT_TAKEOVER_BACKREF_HELLO =
            {(byte) 0xf2, 0x00, 0x11, 0x00, 0x00};

    private WebSocketServer server;
    private int port;

    @BeforeEach
    void startServer() throws Exception {
        server = WebSocketServerBootstrap.bootstrap()
                .setCanonicalHostName("127.0.0.1")
                .register("/echo", EchoHandler::new)
                .setExceptionListener(ExceptionListener.NO_OP)
                .create();
        server.start();
        port = server.getLocalPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.initiateShutdown();
            server.stop();
        }
    }

    /**
     * Message 1 alone must decode to "Hello": this is just the 7.2.3.4 example
     * working on its own, unrelated to context takeover.
     */
    @Test
    void realServerDecodesBfinalFlushedMessageAlone() throws Exception {
        try (RawWsClient client = RawWsClient.connect("127.0.0.1", port, "/echo")) {
            client.sendFrame(0x1, true, true, MSG1_BFINAL_FLUSH_HELLO);
            final Frame response = client.readFrame(Duration.ofSeconds(5));
            assertEquals(0x1, response.opcode,
                    "expected TEXT back, got opcode " + response.opcode
                            + (response.opcode == 0x8 ? " (CLOSE, payload=" + hex(response.payload) + ")" : ""));
            assertEquals("Hello", decodeServerEcho(response.payload));
        }
    }

    /**
     * After message 1 uses the BFINAL-flush technique, the server's decoder must still
     * honor context takeover for message 2's back-reference into message 1's window.
     */
    @Test
    void realServerHonorsContextTakeoverAcrossBfinalFlushedMessage() throws Exception {
        // One persistent decoder across both messages, matching how a real peer keeps
        // one per-connection decoder instance: the server's own echo encoder also
        // carries context takeover across these two responses, so decoding them
        // correctly here requires the same shared-window assumption being tested.
        final PerMessageDeflateExtension echoDecoder = new PerMessageDeflateExtension();
        try (RawWsClient client = RawWsClient.connect("127.0.0.1", port, "/echo")) {
            final Frame r1 = roundTrip(client, MSG1_BFINAL_FLUSH_HELLO);
            assertEquals("Hello", decodeWith(echoDecoder, r1.payload));

            final Frame r2 = roundTrip(client, MSG2_CONTEXT_TAKEOVER_BACKREF_HELLO);
            assertEquals("Hello", decodeWith(echoDecoder, r2.payload));
        } finally {
            echoDecoder.close();
        }
    }

    // The client-side PerMessageDeflate decoder carries the same fix and is covered directly
    // against the same two RFC vectors in MessageDeflateTest
    // (decode_messageAfterBfinalFlushedMessage_withContextTakeover): the server here always
    // re-encodes with SYNC_FLUSH, never BFINAL, so routing its echo through the client decoder
    // would not exercise the client's own BFINAL-reset path.

    private static Frame roundTrip(final RawWsClient client, final byte[] vector) throws IOException {
        client.sendFrame(0x1, true, true, vector);
        final Frame response = client.readFrame(Duration.ofSeconds(5));
        assertEquals(0x1, response.opcode,
                "expected TEXT back, got opcode " + response.opcode
                        + (response.opcode == 0x8 ? " (CLOSE, payload=" + hex(response.payload) + ")" : ""));
        return response;
    }

    private static String decodeServerEcho(final byte[] compressedPayload) throws Exception {
        final PerMessageDeflateExtension dec = new PerMessageDeflateExtension();
        try {
            return decodeWith(dec, compressedPayload);
        } finally {
            dec.close();
        }
    }

    private static String decodeWith(final PerMessageDeflateExtension dec, final byte[] compressedPayload) throws Exception {
        final ByteBuffer out = dec.decode(WebSocketFrameType.TEXT, true, ByteBuffer.wrap(compressedPayload));
        final byte[] b = new byte[out.remaining()];
        out.get(b);
        return new String(b, StandardCharsets.UTF_8);
    }

    private static String hex(final byte[] b) {
        final StringBuilder sb = new StringBuilder();
        for (final byte x : b) {
            sb.append(String.format("%02x ", x));
        }
        return sb.toString().trim();
    }

    private static final class EchoHandler implements WebSocketHandler {
        @Override
        public void onText(final WebSocketSession session, final String text) {
            try {
                session.sendText(text);
            } catch (final IOException ex) {
                throw new RuntimeException(ex);
            }
        }

        @Override
        public void onBinary(final WebSocketSession session, final ByteBuffer data) {
        }
    }

    private static final class Frame {
        final int opcode;
        final byte[] payload;

        Frame(final int opcode, final byte[] payload) {
            this.opcode = opcode;
            this.payload = payload;
        }
    }

    /** Minimal hand-rolled RFC 6455 client: just enough handshake + framing to drive the real server. */
    private static final class RawWsClient implements AutoCloseable {
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;
        private final SecureRandom random = new SecureRandom();

        private RawWsClient(final Socket socket, final InputStream in, final OutputStream out) {
            this.socket = socket;
            this.in = in;
            this.out = out;
        }

        static RawWsClient connect(final String host, final int port, final String path) throws Exception {
            final Socket socket = new Socket(host, port);
            socket.setSoTimeout(5000);
            final OutputStream out = socket.getOutputStream();
            final InputStream in = socket.getInputStream();

            final byte[] keyBytes = new byte[16];
            new SecureRandom().nextBytes(keyBytes);
            final String key = Base64.getEncoder().encodeToString(keyBytes);

            final StringBuilder req = new StringBuilder();
            req.append("GET ").append(path).append(" HTTP/1.1\r\n");
            req.append("Host: ").append(host).append(":").append(port).append("\r\n");
            req.append("Upgrade: websocket\r\n");
            req.append("Connection: Upgrade\r\n");
            req.append("Sec-WebSocket-Key: ").append(key).append("\r\n");
            req.append("Sec-WebSocket-Version: 13\r\n");
            req.append("Sec-WebSocket-Extensions: permessage-deflate\r\n");
            req.append("\r\n");
            out.write(req.toString().getBytes(StandardCharsets.US_ASCII));
            out.flush();

            final String response = readHttpHeaders(in);
            Assertions.assertTrue(response.startsWith("HTTP/1.1 101"), "handshake failed:\n" + response);
            final String expectedAccept = Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-1").digest(
                            (key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)));
            Assertions.assertTrue(response.contains(expectedAccept), "Sec-WebSocket-Accept mismatch:\n" + response);
            final String lowerResponse = response.toLowerCase(Locale.ROOT);
            Assertions.assertTrue(lowerResponse.contains("permessage-deflate"),
                    "server did not accept permessage-deflate:\n" + response);
            // This test is only meaningful under real context takeover: fail loudly if the
            // negotiation ever starts sending *_no_context_takeover, rather than silently
            // passing on a path the fix does not need to touch.
            assertFalse(lowerResponse.contains("no_context_takeover"),
                    "test requires real context takeover, but server negotiated *_no_context_takeover:\n" + response);
            return new RawWsClient(socket, in, out);
        }

        private static String readHttpHeaders(final InputStream in) throws IOException {
            final ByteArrayOutputStream buf = new ByteArrayOutputStream();
            int state = 0;
            int b;
            while ((b = in.read()) != -1) {
                buf.write(b);
                if (state == 0 && b == '\r' || state == 2 && b == '\r') {
                    state++;
                } else if (state == 1 && b == '\n' || state == 3 && b == '\n') {
                    state++;
                    if (state == 4) {
                        break;
                    }
                } else {
                    state = 0;
                }
            }
            return buf.toString("US-ASCII");
        }

        void sendFrame(final int opcode, final boolean fin, final boolean rsv1, final byte[] payload) throws IOException {
            int b0 = opcode & 0x0F;
            if (fin) {
                b0 |= 0x80;
            }
            if (rsv1) {
                b0 |= 0x40;
            }
            out.write(b0);
            final int len = payload.length;
            final byte[] maskKey = new byte[4];
            random.nextBytes(maskKey);
            if (len < 126) {
                out.write(0x80 | len);
            } else if (len < 65536) {
                out.write(0x80 | 126);
                out.write((len >> 8) & 0xFF);
                out.write(len & 0xFF);
            } else {
                out.write(0x80 | 127);
                for (int i = 7; i >= 0; i--) {
                    out.write((int) ((((long) len) >> (8 * i)) & 0xFF));
                }
            }
            out.write(maskKey);
            final byte[] masked = new byte[len];
            for (int i = 0; i < len; i++) {
                masked[i] = (byte) (payload[i] ^ maskKey[i % 4]);
            }
            out.write(masked);
            out.flush();
        }

        Frame readFrame(final Duration timeout) throws IOException {
            socket.setSoTimeout((int) timeout.toMillis());
            final int b0 = requireByte();
            final int opcode = b0 & 0x0F;
            final int b1 = requireByte();
            final boolean masked = (b1 & 0x80) != 0;
            long len = b1 & 0x7F;
            if (len == 126) {
                len = (requireByte() << 8) | requireByte();
            } else if (len == 127) {
                len = 0;
                for (int i = 0; i < 8; i++) {
                    len = (len << 8) | requireByte();
                }
            }
            final byte[] maskKey = masked ? readExactly(4) : null;
            final byte[] payload = readExactly((int) len);
            if (masked) {
                for (int i = 0; i < payload.length; i++) {
                    payload[i] ^= maskKey[i % 4];
                }
            }
            return new Frame(opcode, payload);
        }

        private int requireByte() throws IOException {
            final int b = in.read();
            if (b == -1) {
                throw new IOException("unexpected EOF reading WebSocket frame");
            }
            return b;
        }

        private byte[] readExactly(final int n) throws IOException {
            final byte[] buf = new byte[n];
            int off = 0;
            while (off < n) {
                final int r = in.read(buf, off, n - off);
                if (r == -1) {
                    throw new IOException("unexpected EOF reading " + n + " bytes, got " + off);
                }
                off += r;
            }
            return buf;
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
