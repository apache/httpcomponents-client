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

package org.apache.hc.client5.http.impl.io;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.hc.client5.http.io.ManagedHttpClientConnection;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentLengthStrategy;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.DefaultContentLengthStrategy;
import org.apache.hc.core5.http.impl.io.DefaultHttpRequestWriterFactory;
import org.apache.hc.core5.http.impl.io.NoResponseOutOfOrderStrategy;
import org.apache.hc.core5.http.io.HttpConnectionFactory;
import org.apache.hc.core5.http.io.HttpMessageParserFactory;
import org.apache.hc.core5.http.io.HttpMessageWriterFactory;
import org.apache.hc.core5.http.io.ResponseOutOfOrderStrategy;

/**
 * Factory for {@link ManagedHttpClientConnection} instances.
 * @since 4.3
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public class ManagedHttpClientConnectionFactory implements HttpConnectionFactory<ManagedHttpClientConnection> {

    private static final AtomicLong COUNTER = new AtomicLong();

    public static final ManagedHttpClientConnectionFactory INSTANCE = new ManagedHttpClientConnectionFactory();

    private final Http1Config h1Config;
    private final CharCodingConfig charCodingConfig;
    private final HttpMessageWriterFactory<ClassicHttpRequest> requestWriterFactory;
    private final HttpMessageParserFactory<ClassicHttpResponse> responseParserFactory;
    private final ContentLengthStrategy incomingContentStrategy;
    private final ContentLengthStrategy outgoingContentStrategy;
    private final ResponseOutOfOrderStrategy responseOutOfOrderStrategy;

    private ManagedHttpClientConnectionFactory(
            final Http1Config h1Config,
            final CharCodingConfig charCodingConfig,
            final HttpMessageWriterFactory<ClassicHttpRequest> requestWriterFactory,
            final HttpMessageParserFactory<ClassicHttpResponse> responseParserFactory,
            final ContentLengthStrategy incomingContentStrategy,
            final ContentLengthStrategy outgoingContentStrategy,
            final ResponseOutOfOrderStrategy responseOutOfOrderStrategy) {
        this.h1Config = h1Config != null ? h1Config : Http1Config.DEFAULT;
        this.charCodingConfig = charCodingConfig != null ? charCodingConfig : CharCodingConfig.DEFAULT;
        this.requestWriterFactory = requestWriterFactory != null ? requestWriterFactory :
                DefaultHttpRequestWriterFactory.INSTANCE;
        this.responseParserFactory = responseParserFactory != null ? responseParserFactory :
                DefaultHttpResponseParserFactory.INSTANCE;
        this.incomingContentStrategy = incomingContentStrategy != null ? incomingContentStrategy :
                DefaultContentLengthStrategy.INSTANCE;
        this.outgoingContentStrategy = outgoingContentStrategy != null ? outgoingContentStrategy :
                DefaultContentLengthStrategy.INSTANCE;
        this.responseOutOfOrderStrategy = responseOutOfOrderStrategy != null ? responseOutOfOrderStrategy :
                NoResponseOutOfOrderStrategy.INSTANCE;
    }

    public ManagedHttpClientConnectionFactory(
            final Http1Config h1Config,
            final CharCodingConfig charCodingConfig,
            final HttpMessageWriterFactory<ClassicHttpRequest> requestWriterFactory,
            final HttpMessageParserFactory<ClassicHttpResponse> responseParserFactory,
            final ContentLengthStrategy incomingContentStrategy,
            final ContentLengthStrategy outgoingContentStrategy) {
        this(
                h1Config,
                charCodingConfig,
                requestWriterFactory,
                responseParserFactory,
                incomingContentStrategy,
                outgoingContentStrategy,
                null);
    }

    public ManagedHttpClientConnectionFactory(
            final Http1Config h1Config,
            final CharCodingConfig charCodingConfig,
            final HttpMessageWriterFactory<ClassicHttpRequest> requestWriterFactory,
            final HttpMessageParserFactory<ClassicHttpResponse> responseParserFactory) {
        this(h1Config, charCodingConfig, requestWriterFactory, responseParserFactory, null, null);
    }

    public ManagedHttpClientConnectionFactory(
            final Http1Config h1Config,
            final CharCodingConfig charCodingConfig,
            final HttpMessageParserFactory<ClassicHttpResponse> responseParserFactory) {
        this(h1Config, charCodingConfig, null, responseParserFactory);
    }

    public ManagedHttpClientConnectionFactory() {
        this(null, null, null);
    }

    @Override
    public ManagedHttpClientConnection createConnection(final Socket socket) throws IOException {
        CharsetDecoder charDecoder = null;
        CharsetEncoder charEncoder = null;
        final Charset charset = this.charCodingConfig.getCharset();
        final CodingErrorAction malformedInputAction = this.charCodingConfig.getMalformedInputAction() != null ?
                this.charCodingConfig.getMalformedInputAction() : CodingErrorAction.REPORT;
        final CodingErrorAction unmappableInputAction = this.charCodingConfig.getUnmappableInputAction() != null ?
                this.charCodingConfig.getUnmappableInputAction() : CodingErrorAction.REPORT;
        if (charset != null) {
            charDecoder = charset.newDecoder();
            charDecoder.onMalformedInput(malformedInputAction);
            charDecoder.onUnmappableCharacter(unmappableInputAction);
            charEncoder = charset.newEncoder();
            charEncoder.onMalformedInput(malformedInputAction);
            charEncoder.onUnmappableCharacter(unmappableInputAction);
        }
        final String id = "http-outgoing-" + COUNTER.getAndIncrement();
        final DefaultManagedHttpClientConnection conn = new DefaultManagedHttpClientConnection(
                id,
                charDecoder,
                charEncoder,
                h1Config,
                incomingContentStrategy,
                outgoingContentStrategy,
                responseOutOfOrderStrategy,
                requestWriterFactory,
                responseParserFactory);
        if (socket != null) {
            conn.bind(socket);
        }
        return conn;
    }

    /**
     * Create a new {@link Builder}.
     *
     * @since 5.1
     */
    public static Builder builder()  {
        return new Builder();
    }

    /**
     * Builder for {@link ManagedHttpClientConnectionFactory}.
     *
     * @since 5.1
     */
    public static final class Builder {

        private Http1Config http1Config;
        private CharCodingConfig charCodingConfig;
        private ContentLengthStrategy incomingContentLengthStrategy;
        private ContentLengthStrategy outgoingContentLengthStrategy;
        private ResponseOutOfOrderStrategy responseOutOfOrderStrategy;
        private HttpMessageWriterFactory<ClassicHttpRequest> requestWriterFactory;
        private HttpMessageParserFactory<ClassicHttpResponse> responseParserFactory;

        private Builder() {}

        public Builder http1Config(final Http1Config http1Config) {
            this.http1Config = http1Config;
            return this;
        }

        public Builder charCodingConfig(final CharCodingConfig charCodingConfig) {
            this.charCodingConfig = charCodingConfig;
            return this;
        }

        public Builder incomingContentLengthStrategy(final ContentLengthStrategy incomingContentLengthStrategy) {
            this.incomingContentLengthStrategy = incomingContentLengthStrategy;
            return this;
        }

        public Builder outgoingContentLengthStrategy(final ContentLengthStrategy outgoingContentLengthStrategy) {
            this.outgoingContentLengthStrategy = outgoingContentLengthStrategy;
            return this;
        }

        public Builder responseOutOfOrderStrategy(final ResponseOutOfOrderStrategy responseOutOfOrderStrategy) {
            this.responseOutOfOrderStrategy = responseOutOfOrderStrategy;
            return this;
        }

        public Builder requestWriterFactory(
                final HttpMessageWriterFactory<ClassicHttpRequest> requestWriterFactory) {
            this.requestWriterFactory = requestWriterFactory;
            return this;
        }

        public Builder responseParserFactory(
                final HttpMessageParserFactory<ClassicHttpResponse> responseParserFactory) {
            this.responseParserFactory = responseParserFactory;
            return this;
        }

        public ManagedHttpClientConnectionFactory build() {
            return new ManagedHttpClientConnectionFactory(
                    http1Config,
                    charCodingConfig,
                    requestWriterFactory,
                    responseParserFactory,
                    incomingContentLengthStrategy,
                    outgoingContentLengthStrategy,
                    responseOutOfOrderStrategy);
        }
    }
}
