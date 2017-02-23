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

package org.apache.hc.client5.http.impl.async;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

import org.apache.hc.client5.http.impl.ConnPoolSupport;
import org.apache.hc.client5.http.impl.logging.LogAppendable;
import org.apache.hc.client5.http.impl.logging.LoggingIOEventHandler;
import org.apache.hc.client5.http.impl.logging.LoggingIOSession;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.impl.ConnectionListener;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.frame.FramePrinter;
import org.apache.hc.core5.http2.frame.RawFrame;
import org.apache.hc.core5.http2.impl.nio.ClientHttpProtocolNegotiator;
import org.apache.hc.core5.http2.impl.nio.Http2StreamListener;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.Args;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class DefaultAsyncHttp2ClientEventHandlerFactory implements IOEventHandlerFactory {

    private final Logger streamLog = LogManager.getLogger(ClientHttpProtocolNegotiator.class);
    private final Logger wireLog = LogManager.getLogger("org.apache.hc.client5.http.wire");
    private final Logger headerLog = LogManager.getLogger("org.apache.hc.client5.http.headers");
    private final Logger frameLog = LogManager.getLogger("org.apache.hc.client5.http2.frame");
    private final Logger framePayloadLog = LogManager.getLogger("org.apache.hc.client5.http2.frame.payload");
    private final Logger flowCtrlLog = LogManager.getLogger("org.apache.hc.client5.http2.flow");

    private final HttpProcessor httpProcessor;
    private final HandlerFactory<AsyncPushConsumer> exchangeHandlerFactory;
    private final Charset charset;
    private final H2Config h2Config;

    DefaultAsyncHttp2ClientEventHandlerFactory(
            final HttpProcessor httpProcessor,
            final HandlerFactory<AsyncPushConsumer> exchangeHandlerFactory,
            final Charset charset,
            final H2Config h2Config) {
        this.httpProcessor = Args.notNull(httpProcessor, "HTTP processor");
        this.exchangeHandlerFactory = exchangeHandlerFactory;
        this.charset = charset;
        this.h2Config = h2Config;
    }

    @Override
    public IOEventHandler createHandler(final IOSession ioSession) {
        final Logger sessionLog = LogManager.getLogger(ioSession.getClass());
        if (sessionLog.isDebugEnabled()
                || streamLog.isDebugEnabled()
                || wireLog.isDebugEnabled()
                || headerLog.isDebugEnabled()
                || frameLog.isDebugEnabled()
                || framePayloadLog.isDebugEnabled()
                || flowCtrlLog.isDebugEnabled()) {
            final String id = ConnPoolSupport.getId(ioSession);
            return new LoggingIOEventHandler(new DefaultAsyncHttpClientProtocolNegotiator(
                    new LoggingIOSession(ioSession, id, sessionLog, wireLog),
                    httpProcessor, exchangeHandlerFactory, charset, h2Config,
                    new ConnectionListener() {

                        @Override
                        public void onConnect(final HttpConnection connection) {
                            if (streamLog.isDebugEnabled()) {
                                streamLog.debug(id + ": " + connection + " connected");
                            }
                        }

                        @Override
                        public void onDisconnect(final HttpConnection connection) {
                            if (streamLog.isDebugEnabled()) {
                                streamLog.debug(id + ": " + connection + " disconnected");
                            }
                        }

                        @Override
                        public void onError(final HttpConnection connection, final Exception ex) {
                            if (ex instanceof ConnectionClosedException) {
                                return;
                            }
                            streamLog.error(id + ": " + ex.getMessage(), ex);
                        }

                    },
                    new Http2StreamListener() {

                        final FramePrinter framePrinter = new FramePrinter();

                        private void logFrameInfo(final String prefix, final RawFrame frame) {
                            try {
                                final LogAppendable logAppendable = new LogAppendable(frameLog, prefix);
                                framePrinter.printFrameInfo(frame, logAppendable);
                                logAppendable.flush();
                            } catch (IOException ignore) {
                            }
                        }

                        private void logFramePayload(final String prefix, final RawFrame frame) {
                            try {
                                final LogAppendable logAppendable = new LogAppendable(framePayloadLog, prefix);
                                framePrinter.printPayload(frame, logAppendable);
                                logAppendable.flush();
                            } catch (IOException ignore) {
                            }
                        }

                        private void logFlowControl(final String prefix, final int streamId, final int delta, final int actualSize) {
                            final StringBuilder buffer = new StringBuilder();
                            buffer.append(prefix).append(" stream ").append(streamId).append(" flow control " )
                                    .append(delta).append(" -> ")
                                    .append(actualSize);
                            flowCtrlLog.debug(buffer.toString());
                        }

                        @Override
                        public void onHeaderInput(final HttpConnection connection, final int streamId, final List<? extends Header> headers) {
                            if (headerLog.isDebugEnabled()) {
                                for (int i = 0; i < headers.size(); i++) {
                                    headerLog.debug(id + " << " + headers.get(i));
                                }
                            }
                        }

                        @Override
                        public void onHeaderOutput(final HttpConnection connection, final int streamId, final List<? extends Header> headers) {
                            if (headerLog.isDebugEnabled()) {
                                for (int i = 0; i < headers.size(); i++) {
                                    headerLog.debug(id + " >> " + headers.get(i));
                                }
                            }
                        }

                        @Override
                        public void onFrameInput(final HttpConnection connection, final int streamId, final RawFrame frame) {
                            if (frameLog.isDebugEnabled()) {
                                logFrameInfo(id + " <<", frame);
                            }
                            if (framePayloadLog.isDebugEnabled()) {
                                logFramePayload(id + " <<", frame);
                            }
                        }

                        @Override
                        public void onFrameOutput(final HttpConnection connection, final int streamId, final RawFrame frame) {
                            if (frameLog.isDebugEnabled()) {
                                logFrameInfo(id + " >>", frame);
                            }
                            if (framePayloadLog.isDebugEnabled()) {
                                logFramePayload(id + " >>", frame);
                            }
                        }

                        @Override
                        public void onInputFlowControl(final HttpConnection connection, final int streamId, final int delta, final int actualSize) {
                            if (flowCtrlLog.isDebugEnabled()) {
                                logFlowControl(id + " <<", streamId, delta, actualSize);
                            }
                        }

                        @Override
                        public void onOutputFlowControl(final HttpConnection connection, final int streamId, final int delta, final int actualSize) {
                            if (flowCtrlLog.isDebugEnabled()) {
                                logFlowControl(id + " >>", streamId, delta, actualSize);
                            }
                        }

            }), id, streamLog);
        } else {
            return new DefaultAsyncHttpClientProtocolNegotiator(ioSession,
                    httpProcessor, exchangeHandlerFactory, charset, h2Config, null, null);
        }

   }

}
