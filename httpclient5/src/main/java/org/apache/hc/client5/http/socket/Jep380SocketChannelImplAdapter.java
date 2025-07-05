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

package org.apache.hc.client5.http.socket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketImpl;
import java.net.SocketOptions;
import java.net.SocketTimeoutException;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

final class Jep380SocketChannelImplAdapter extends SocketImpl {
    private final SocketChannel channel;
    volatile int soTimeoutMs = 0;

    public Jep380SocketChannelImplAdapter(final SocketChannel channel) throws IOException {
        this.channel = channel;
    }

    @Override
    protected void close() throws IOException {
        channel.close();
    }

    @Override
    protected InputStream getInputStream() throws IOException {
        return new InputStreamAdapter();
    }

    @Override
    protected OutputStream getOutputStream() throws IOException {
        return new OutputStreamAdapter();
    }

    @Override
    public Object getOption(final int optID) throws SocketException {
        try {
            switch (optID) {
                case SocketOptions.SO_TIMEOUT:
                    return soTimeoutMs;
                case SocketOptions.SO_RCVBUF:
                    return channel.getOption(StandardSocketOptions.SO_RCVBUF);
                case SocketOptions.SO_SNDBUF:
                    return channel.getOption(StandardSocketOptions.SO_SNDBUF);
            }
        } catch (final IOException ex) {
            throw new UncheckedIOException(ex);
        }
        throw new UnsupportedOperationException("getOption: " + optID);
    }

    @Override
    public void setOption(final int optID, final Object value) throws SocketException {
        try {
            switch (optID) {
                case SocketOptions.SO_TIMEOUT:
                    soTimeoutMs = (Integer) value;
                    return;
                case SocketOptions.SO_RCVBUF:
                    channel.setOption(StandardSocketOptions.SO_RCVBUF, (Integer) value);
                    return;
                case SocketOptions.SO_SNDBUF:
                    channel.setOption(StandardSocketOptions.SO_SNDBUF, (Integer) value);
                    return;
            }
        } catch (final IOException ex) {
            throw new RuntimeException(ex);
        }
        throw new UnsupportedOperationException();
    }

    @Override
    protected void accept(final SocketImpl s) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected int available() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void bind(final InetAddress host, final int port) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void connect(final String host, final int port) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void connect(final InetAddress address, final int port) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void connect(final SocketAddress address, final int timeout) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void create(final boolean stream) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void listen(final int backlog) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void sendUrgentData(final int data) throws IOException {
        throw new UnsupportedOperationException();
    }

    private class InputStreamAdapter extends InputStream {
        private final Selector sel;
        private final SelectionKey key;

        private InputStreamAdapter() throws IOException {
            this.sel = Selector.open();
            this.key = channel.register(sel, SelectionKey.OP_READ);
        }

        @Override
        public int read() throws IOException {
            final byte[] b = new byte[1];
            final int n = read(b, 0, 1);
            return (n == -1) ? -1 : (b[0] & 0xFF);
        }

        @Override
        public int read(final byte[] b) throws IOException {
            return read(b, 0, b.length);
        }

        @Override
        public int read(final byte[] b, final int off, final int len) throws IOException {
            final ByteBuffer buf = ByteBuffer.wrap(b, off, len);
            if (sel.select(soTimeoutMs) == 0) {
                throw new SocketTimeoutException();
            }
            final int read = channel.read(buf);
            sel.selectedKeys().clear();
            return read;
        }

        @Override
        public void close() throws IOException {
            key.cancel();
            sel.close();
            channel.close();
        }
    }

    private class OutputStreamAdapter extends OutputStream {
        private final Selector sel;
        private final SelectionKey key;

        private OutputStreamAdapter() throws IOException {
            this.sel = Selector.open();
            this.key = channel.register(sel, SelectionKey.OP_WRITE);
        }

        @Override
        public void write(final int b) throws IOException {
            write(new byte[]{ (byte) b});
        }

        @Override
        public void write(final byte[] b) throws IOException {
            write(b, 0, b.length);
        }

        @Override
        public void write(final byte[] b, final int off, final int len) throws IOException {
            final ByteBuffer buf = ByteBuffer.wrap(b, off, len);
            while (buf.hasRemaining()) {
                final int n = channel.write(buf);
                if (n == 0) {
                    if (sel.select(60_000) == 0) {
                        throw new SocketTimeoutException("write timed out");
                    }
                    sel.selectedKeys().clear();
                }
            }
        }

        @Override
        public void close() throws IOException {
            key.cancel();
            sel.close();
            channel.close();
        }
    }
}
