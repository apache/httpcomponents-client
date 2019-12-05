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
import java.util.List;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.frame.FramePrinter;
import org.apache.hc.core5.http2.frame.RawFrame;
import org.apache.hc.core5.http2.impl.nio.ClientH2StreamMultiplexerFactory;
import org.apache.hc.core5.http2.impl.nio.H2OnlyClientProtocolNegotiator;
import org.apache.hc.core5.http2.impl.nio.H2StreamListener;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.util.Args;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class H2AsyncClientEventHandlerFactory implements IOEventHandlerFactory {

    private final Logger headerLog = LoggerFactory.getLogger("org.apache.hc.client5.http.headers");
    private final Logger frameLog = LoggerFactory.getLogger("org.apache.hc.client5.http2.frame");
    private final Logger framePayloadLog = LoggerFactory.getLogger("org.apache.hc.client5.http2.frame.payload");
    private final Logger flowCtrlLog = LoggerFactory.getLogger("org.apache.hc.client5.http2.flow");

    private final HttpProcessor httpProcessor;
    private final HandlerFactory<AsyncPushConsumer> exchangeHandlerFactory;
    private final H2Config h2Config;
    private final CharCodingConfig charCodingConfig;

    H2AsyncClientEventHandlerFactory(
            final HttpProcessor httpProcessor,
            final HandlerFactory<AsyncPushConsumer> exchangeHandlerFactory,
            final H2Config h2Config,
            final CharCodingConfig charCodingConfig) {
        this.httpProcessor = Args.notNull(httpProcessor, "HTTP processor");
        this.exchangeHandlerFactory = exchangeHandlerFactory;
        this.h2Config = h2Config != null ? h2Config : H2Config.DEFAULT;
        this.charCodingConfig = charCodingConfig != null ? charCodingConfig : CharCodingConfig.DEFAULT;
    }

    @Override
    public IOEventHandler createHandler(final ProtocolIOSession ioSession, final Object attachment) {
        if (headerLog.isDebugEnabled()
                || frameLog.isDebugEnabled()
                || framePayloadLog.isDebugEnabled()
                || flowCtrlLog.isDebugEnabled()) {
            final String id = ioSession.getId();
            final ClientH2StreamMultiplexerFactory http2StreamHandlerFactory = new ClientH2StreamMultiplexerFactory(
                    httpProcessor,
                    exchangeHandlerFactory,
                    h2Config,
                    charCodingConfig,
                    new H2StreamListener() {

                        final FramePrinter framePrinter = new FramePrinter();

                        private void logFrameInfo(final String prefix, final RawFrame frame) {
                            try {
                                final LogAppendable logAppendable = new LogAppendable(frameLog, prefix);
                                framePrinter.printFrameInfo(frame, logAppendable);
                                logAppendable.flush();
                            } catch (final IOException ignore) {
                            }
                        }

                        private void logFramePayload(final String prefix, final RawFrame frame) {
                            try {
                                final LogAppendable logAppendable = new LogAppendable(framePayloadLog, prefix);
                                framePrinter.printPayload(frame, logAppendable);
                                logAppendable.flush();
                            } catch (final IOException ignore) {
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

                    });
            return new H2OnlyClientProtocolNegotiator(ioSession, http2StreamHandlerFactory, false);
        }
        final ClientH2StreamMultiplexerFactory http2StreamHandlerFactory = new ClientH2StreamMultiplexerFactory(
                httpProcessor,
                exchangeHandlerFactory,
                h2Config,
                charCodingConfig,
                null);
        return new H2OnlyClientProtocolNegotiator(ioSession, http2StreamHandlerFactory, false);
   }

}
