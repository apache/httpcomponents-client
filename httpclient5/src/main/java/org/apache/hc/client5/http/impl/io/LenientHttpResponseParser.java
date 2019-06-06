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

import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponseFactory;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.io.DefaultHttpResponseParser;
import org.apache.hc.core5.http.message.LineParser;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lenient HTTP response parser implementation that can skip malformed data until
 * a valid HTTP response message head is encountered.
 *
 * @since 4.2
 */
public class LenientHttpResponseParser extends DefaultHttpResponseParser {

    private final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Creates new instance of DefaultHttpResponseParser.
     *
     * @param lineParser      the line parser. If {@code null}
     *                        {@link org.apache.hc.core5.http.message.BasicLineParser#INSTANCE} will be used.
     * @param responseFactory HTTP response factory. If {@code null}
     *                        {@link org.apache.hc.core5.http.impl.io.DefaultClassicHttpResponseFactory#INSTANCE}
     *                        will be used.
     * @param h1Config        HTTP/1.1 parameters. If {@code null}. {@link Http1Config#DEFAULT} will be used.
     *
     * @since 4.3
     */
    public LenientHttpResponseParser(
            final LineParser lineParser,
            final HttpResponseFactory<ClassicHttpResponse> responseFactory,
            final Http1Config h1Config) {
        super(lineParser, responseFactory, h1Config);
    }

    /**
     * Creates new instance of DefaultHttpResponseParser.
     *
     * @param h1Config        HTTP/1.1 parameters. If {@code null}. {@link Http1Config#DEFAULT} will be used.
     *
     * @since 4.3
     */
    public LenientHttpResponseParser(final Http1Config h1Config) {
        this(null, null, h1Config);
    }

    @Override
    protected ClassicHttpResponse createMessage(final CharArrayBuffer buffer) throws IOException {
        try {
            return super.createMessage(buffer);
        } catch (final HttpException ex) {
            if (this.log.isDebugEnabled()) {
                this.log.debug("Garbage in response: " + buffer.toString());
            }
            return null;
        }
    }

}
