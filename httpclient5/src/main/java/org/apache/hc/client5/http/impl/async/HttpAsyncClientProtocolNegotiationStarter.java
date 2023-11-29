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
import java.util.Iterator;
import java.util.List;

import org.apache.hc.client5.http.impl.DefaultClientConnectionReuseStrategy;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.Http1StreamListener;
import org.apache.hc.core5.http.impl.nio.ClientHttp1IOEventHandler;
import org.apache.hc.core5.http.impl.nio.ClientHttp1StreamDuplexerFactory;
import org.apache.hc.core5.http.impl.nio.DefaultHttpRequestWriterFactory;
import org.apache.hc.core5.http.impl.nio.DefaultHttpResponseParserFactory;
import org.apache.hc.core5.http.message.RequestLine;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.nio.NHttpMessageParserFactory;
import org.apache.hc.core5.http.nio.NHttpMessageWriterFactory;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.frame.FramePrinter;
import org.apache.hc.core5.http2.frame.RawFrame;
import org.apache.hc.core5.http2.impl.nio.ClientH2PrefaceHandler;
import org.apache.hc.core5.http2.impl.nio.ClientH2StreamMultiplexerFactory;
import org.apache.hc.core5.http2.impl.nio.ClientH2UpgradeHandler;
import org.apache.hc.core5.http2.impl.nio.ClientHttp1UpgradeHandler;
import org.apache.hc.core5.http2.impl.nio.H2StreamListener;
import org.apache.hc.core5.http2.impl.nio.HttpProtocolNegotiator;
import org.apache.hc.core5.http2.ssl.ApplicationProtocol;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.ProtocolIOSession;
import org.apache.hc.core5.util.Args;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class HttpAsyncClientProtocolNegotiationStarter implements IOEventHandlerFactory {

    private static final Logger STREAM_LOG = LoggerFactory.getLogger(InternalHttpAsyncClient.class);
    private static final Logger HEADER_LOG = LoggerFactory.getLogger("org.apache.hc.client5.http.headers");
    private static final Logger FRAME_LOG = LoggerFactory.getLogger("org.apache.hc.client5.http2.frame");
    private static final Logger FRAME_PAYLOAD_LOG = LoggerFactory.getLogger("org.apache.hc.client5.http2.frame.payload");
    private static final Logger FLOW_CTRL_LOG = LoggerFactory.getLogger("org.apache.hc.client5.http2.flow");

    private final HttpProcessor httpProcessor;
    private final HandlerFactory<AsyncPushConsumer> exchangeHandlerFactory;
    private final H2Config h2Config;
    private final Http1Config h1Config;
    private final CharCodingConfig charCodingConfig;
    private final ConnectionReuseStrategy http1ConnectionReuseStrategy;
    private final NHttpMessageParserFactory<HttpResponse> http1ResponseParserFactory;
    private final NHttpMessageWriterFactory<HttpRequest> http1RequestWriterFactory;

    HttpAsyncClientProtocolNegotiationStarter(
            final HttpProcessor httpProcessor,
            final HandlerFactory<AsyncPushConsumer> exchangeHandlerFactory,
            final H2Config h2Config,
            final Http1Config h1Config,
            final CharCodingConfig charCodingConfig,
            final ConnectionReuseStrategy connectionReuseStrategy) {
        this.httpProcessor = Args.notNull(httpProcessor, "HTTP processor");
        this.exchangeHandlerFactory = exchangeHandlerFactory;
        this.h2Config = h2Config != null ? h2Config : H2Config.DEFAULT;
        this.h1Config = h1Config != null ? h1Config : Http1Config.DEFAULT;
        this.charCodingConfig = charCodingConfig != null ? charCodingConfig : CharCodingConfig.DEFAULT;
        this.http1ConnectionReuseStrategy = connectionReuseStrategy != null ? connectionReuseStrategy : DefaultClientConnectionReuseStrategy.INSTANCE;
        this.http1ResponseParserFactory = new DefaultHttpResponseParserFactory(h1Config);
        this.http1RequestWriterFactory = DefaultHttpRequestWriterFactory.INSTANCE;
    }

    @Override
    public IOEventHandler createHandler(final ProtocolIOSession ioSession, final Object attachment) {
        final ClientHttp1StreamDuplexerFactory http1StreamHandlerFactory;
        final ClientH2StreamMultiplexerFactory http2StreamHandlerFactory;

        if (STREAM_LOG.isDebugEnabled()
                || HEADER_LOG.isDebugEnabled()
                || FRAME_LOG.isDebugEnabled()
                || FRAME_PAYLOAD_LOG.isDebugEnabled()
                || FLOW_CTRL_LOG.isDebugEnabled()) {
            final String id = ioSession.getId();
            http1StreamHandlerFactory = new ClientHttp1StreamDuplexerFactory(
                    httpProcessor,
                    h1Config,
                    charCodingConfig,
                    http1ConnectionReuseStrategy,
                    http1ResponseParserFactory,
                    http1RequestWriterFactory,
                    new Http1StreamListener() {

                        @Override
                        public void onRequestHead(final HttpConnection connection, final HttpRequest request) {
                            if (HEADER_LOG.isDebugEnabled()) {
                                HEADER_LOG.debug("{} >> {}", id, new RequestLine(request));
                                for (final Iterator<Header> it = request.headerIterator(); it.hasNext(); ) {
                                    HEADER_LOG.debug("{} >> {}", id, it.next());
                                }
                            }
                        }

                        @Override
                        public void onResponseHead(final HttpConnection connection, final HttpResponse response) {
                            if (HEADER_LOG.isDebugEnabled()) {
                                HEADER_LOG.debug("{} << {}", id, new StatusLine(response));
                                for (final Iterator<Header> it = response.headerIterator(); it.hasNext(); ) {
                                    HEADER_LOG.debug("{} << {}", id, it.next());
                                }
                            }
                        }

                        @Override
                        public void onExchangeComplete(final HttpConnection connection, final boolean keepAlive) {
                            if (STREAM_LOG.isDebugEnabled()) {
                                if (keepAlive) {
                                    STREAM_LOG.debug("{} Connection is kept alive", id);
                                } else {
                                    STREAM_LOG.debug("{} Connection is not kept alive", id);
                                }
                            }
                        }

                    });
            http2StreamHandlerFactory = new ClientH2StreamMultiplexerFactory(
                    httpProcessor,
                    exchangeHandlerFactory,
                    h2Config,
                    charCodingConfig,
                    new H2StreamListener() {

                        final FramePrinter framePrinter = new FramePrinter();

                        private void logFrameInfo(final String prefix, final RawFrame frame) {
                            try {
                                final LogAppendable logAppendable = new LogAppendable(FRAME_LOG, prefix);
                                framePrinter.printFrameInfo(frame, logAppendable);
                                logAppendable.flush();
                            } catch (final IOException ignore) {
                            }
                        }

                        private void logFramePayload(final String prefix, final RawFrame frame) {
                            try {
                                final LogAppendable logAppendable = new LogAppendable(FRAME_PAYLOAD_LOG, prefix);
                                framePrinter.printPayload(frame, logAppendable);
                                logAppendable.flush();
                            } catch (final IOException ignore) {
                            }
                        }

                        private void logFlowControl(final String prefix, final int streamId, final int delta, final int actualSize) {
                            FLOW_CTRL_LOG.debug("{} stream {} flow control {} -> {}", prefix, streamId, delta, actualSize);
                        }

                        @Override
                        public void onHeaderInput(final HttpConnection connection, final int streamId, final List<? extends Header> headers) {
                            if (HEADER_LOG.isDebugEnabled()) {
                                for (int i = 0; i < headers.size(); i++) {
                                    HEADER_LOG.debug("{} << {}", id, headers.get(i));
                                }
                            }
                        }

                        @Override
                        public void onHeaderOutput(final HttpConnection connection, final int streamId, final List<? extends Header> headers) {
                            if (HEADER_LOG.isDebugEnabled()) {
                                for (int i = 0; i < headers.size(); i++) {
                                    HEADER_LOG.debug("{} >> {}", id, headers.get(i));
                                }
                            }
                        }

                        @Override
                        public void onFrameInput(final HttpConnection connection, final int streamId, final RawFrame frame) {
                            if (FRAME_LOG.isDebugEnabled()) {
                                logFrameInfo(id + " <<", frame);
                            }
                            if (FRAME_PAYLOAD_LOG.isDebugEnabled()) {
                                logFramePayload(id + " <<", frame);
                            }
                        }

                        @Override
                        public void onFrameOutput(final HttpConnection connection, final int streamId, final RawFrame frame) {
                            if (FRAME_LOG.isDebugEnabled()) {
                                logFrameInfo(id + " >>", frame);
                            }
                            if (FRAME_PAYLOAD_LOG.isDebugEnabled()) {
                                logFramePayload(id + " >>", frame);
                            }
                        }

                        @Override
                        public void onInputFlowControl(final HttpConnection connection, final int streamId, final int delta, final int actualSize) {
                            if (FLOW_CTRL_LOG.isDebugEnabled()) {
                                logFlowControl(id + " <<", streamId, delta, actualSize);
                            }
                        }

                        @Override
                        public void onOutputFlowControl(final HttpConnection connection, final int streamId, final int delta, final int actualSize) {
                            if (FLOW_CTRL_LOG.isDebugEnabled()) {
                                logFlowControl(id + " >>", streamId, delta, actualSize);
                            }
                        }

                    });
        } else {
            http1StreamHandlerFactory = new ClientHttp1StreamDuplexerFactory(
                    httpProcessor,
                    h1Config,
                    charCodingConfig,
                    http1ConnectionReuseStrategy,
                    http1ResponseParserFactory,
                    http1RequestWriterFactory,
                    null);
            http2StreamHandlerFactory = new ClientH2StreamMultiplexerFactory(
                    httpProcessor,
                    exchangeHandlerFactory,
                    h2Config,
                    charCodingConfig,
                    null);
        }

        ioSession.registerProtocol(ApplicationProtocol.HTTP_1_1.id, new ClientHttp1UpgradeHandler(http1StreamHandlerFactory));
        ioSession.registerProtocol(ApplicationProtocol.HTTP_2.id, new ClientH2UpgradeHandler(http2StreamHandlerFactory));

        final HttpVersionPolicy versionPolicy = attachment instanceof HttpVersionPolicy ? (HttpVersionPolicy) attachment : HttpVersionPolicy.NEGOTIATE;
        switch (versionPolicy) {
            case FORCE_HTTP_2:
                return new ClientH2PrefaceHandler(ioSession, http2StreamHandlerFactory, false);
            case FORCE_HTTP_1:
                return new ClientHttp1IOEventHandler(http1StreamHandlerFactory.create(ioSession));
            default:
                return new HttpProtocolNegotiator(ioSession, null);
        }
   }

}
