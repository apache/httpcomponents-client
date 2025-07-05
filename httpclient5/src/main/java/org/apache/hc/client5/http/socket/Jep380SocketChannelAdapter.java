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
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.SocketChannel;

final class Jep380SocketChannelAdapter extends Socket {
    private final SocketChannel channel;
    private final Jep380SocketChannelImplAdapter adapter;

    Jep380SocketChannelAdapter(final SocketChannel channel) throws IOException {
        this(channel, new Jep380SocketChannelImplAdapter(channel));
    }

    private Jep380SocketChannelAdapter(final SocketChannel channel, final Jep380SocketChannelImplAdapter adapter) {
        this.channel = channel;
        this.adapter = adapter;
    }

    @Override
    public void connect(final SocketAddress endpoint, final int timeout) throws IOException {
        channel.connect(endpoint);
        channel.configureBlocking(false);
    }

    @Override
    public void connect(final SocketAddress endpoint) throws IOException {
        channel.connect(endpoint);
        channel.configureBlocking(false);
    }

    @Override
    public boolean isConnected() {
        return channel.isConnected();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return adapter.getInputStream();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return adapter.getOutputStream();
    }

    @Override
    public boolean isClosed() {
        return !channel.isOpen();
    }

    @Override
    public void close() throws IOException {
        adapter.close();
    }

    @Override
    public int getSoTimeout() throws SocketException {
        return adapter.soTimeoutMs;
    }

    @Override
    public void setSoTimeout(final int timeout) throws SocketException {
        adapter.soTimeoutMs = timeout;
    }
}
