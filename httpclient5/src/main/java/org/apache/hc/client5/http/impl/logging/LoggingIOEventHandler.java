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

package org.apache.hc.client5.http.impl.logging;

import java.io.IOException;
import java.net.SocketAddress;

import org.apache.hc.core5.http.HttpConnectionMetrics;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.impl.nio.HttpConnectionEventHandler;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.logging.log4j.Logger;

public class LoggingIOEventHandler implements HttpConnectionEventHandler {

    private final HttpConnectionEventHandler handler;
    private final String id;
    private final Logger log;

    public LoggingIOEventHandler(
            final HttpConnectionEventHandler handler,
            final String id,
            final Logger log) {
        super();
        this.handler = handler;
        this.id = id;
        this.log = log;
    }

    @Override
    public void connected(final IOSession session) {
        if (log.isDebugEnabled()) {
            log.debug(id + " " + session + " connected");
        }
        handler.connected(session);
    }

    @Override
    public void inputReady(final IOSession session) {
        if (log.isDebugEnabled()) {
            log.debug(id + " " + session + " input ready");
        }
        handler.inputReady(session);
    }

    @Override
    public void outputReady(final IOSession session) {
        if (log.isDebugEnabled()) {
            log.debug(id + " " + session + " output ready");
        }
        handler.outputReady(session);
    }

    @Override
    public void timeout(final IOSession session) {
        if (log.isDebugEnabled()) {
            log.debug(id + " " + session + " timeout");
        }
        handler.timeout(session);
    }

    @Override
    public void exception(final IOSession session, final Exception cause) {
        handler.exception(session, cause);
    }

    @Override
    public void disconnected(final IOSession session) {
        if (log.isDebugEnabled()) {
            log.debug(id + " " + session + " disconnected");
        }
        handler.disconnected(session);
    }

    @Override
    public HttpConnectionMetrics getMetrics() {
        return handler.getMetrics();
    }

    @Override
    public void setSocketTimeout(final int timeout) {
        if (log.isDebugEnabled()) {
            log.debug(id + " set timeout " + timeout);
        }
        handler.setSocketTimeout(timeout);
    }

    @Override
    public int getSocketTimeout() {
        return handler.getSocketTimeout();
    }

    @Override
    public ProtocolVersion getProtocolVersion() {
        return handler.getProtocolVersion();
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return handler.getRemoteAddress();
    }

    @Override
    public SocketAddress getLocalAddress() {
        return handler.getLocalAddress();
    }

    @Override
    public boolean isOpen() {
        return handler.isOpen();
    }

    @Override
    public void close() throws IOException {
        if (log.isDebugEnabled()) {
            log.debug(id + " close");
        }
        handler.close();
    }

    @Override
    public void shutdown() throws IOException {
        if (log.isDebugEnabled()) {
            log.debug(id + " shutdown");
        }
        handler.shutdown();
    }

}
