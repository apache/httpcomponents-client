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

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpResponseFactory;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.io.DefaultClassicHttpResponseFactory;
import org.apache.hc.core5.http.io.HttpMessageParser;
import org.apache.hc.core5.http.io.HttpMessageParserFactory;
import org.apache.hc.core5.http.message.BasicLineParser;
import org.apache.hc.core5.http.message.LineParser;

/**
 * Default factory for response message parsers.
 *
 * @since 4.3
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public class DefaultHttpResponseParserFactory implements HttpMessageParserFactory<ClassicHttpResponse> {

    public static final DefaultHttpResponseParserFactory INSTANCE = new DefaultHttpResponseParserFactory();

    private final LineParser lineParser;
    private final HttpResponseFactory<ClassicHttpResponse> responseFactory;

    public DefaultHttpResponseParserFactory(
            final LineParser lineParser,
            final HttpResponseFactory<ClassicHttpResponse> responseFactory) {
        super();
        this.lineParser = lineParser != null ? lineParser : BasicLineParser.INSTANCE;
        this.responseFactory = responseFactory != null ? responseFactory : DefaultClassicHttpResponseFactory.INSTANCE;
    }

    public DefaultHttpResponseParserFactory(final HttpResponseFactory<ClassicHttpResponse> responseFactory) {
        this(null, responseFactory);
    }

    public DefaultHttpResponseParserFactory() {
        this(null, null);
    }

    @Override
    public HttpMessageParser<ClassicHttpResponse> create(final Http1Config h1Config) {
        return new LenientHttpResponseParser(this.lineParser, this.responseFactory, h1Config);
    }

}
