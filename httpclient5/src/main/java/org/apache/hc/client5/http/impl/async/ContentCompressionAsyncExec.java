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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.function.UnaryOperator;

import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.apache.hc.client5.http.async.methods.InflatingAsyncDataConsumer;
import org.apache.hc.client5.http.entity.compress.ContentCoding;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.message.BasicHeaderValueParser;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.http.message.ParserCursor;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.util.Args;

@Contract(threading = ThreadingBehavior.STATELESS)
@Internal
public final class ContentCompressionAsyncExec implements AsyncExecChainHandler {

    private final Lookup<UnaryOperator<AsyncDataConsumer>> decoders;
    private final boolean ignoreUnknown;

    public ContentCompressionAsyncExec(
            final LinkedHashMap<String, UnaryOperator<AsyncDataConsumer>> decoderMap,
            final boolean ignoreUnknown) {

        Args.notEmpty(decoderMap, "Decoder map");

        final RegistryBuilder<UnaryOperator<AsyncDataConsumer>> rb = RegistryBuilder.create();
        decoderMap.forEach(rb::register);
        this.decoders = rb.build();
        this.ignoreUnknown = ignoreUnknown;
    }

    /**
     * default = DEFLATE only
     */
    public ContentCompressionAsyncExec() {
        final LinkedHashMap<String, UnaryOperator<AsyncDataConsumer>> map = new LinkedHashMap<>();
        map.put(ContentCoding.DEFLATE.token(),
                d -> new InflatingAsyncDataConsumer(d, null));
        this.decoders = RegistryBuilder.<UnaryOperator<AsyncDataConsumer>>create()
                .register(ContentCoding.DEFLATE.token(), map.get(ContentCoding.DEFLATE.token()))
                .build();
        this.ignoreUnknown = true;
    }


    @Override
    public void execute(
            final HttpRequest request,
            final AsyncEntityProducer producer,
            final AsyncExecChain.Scope scope,
            final AsyncExecChain chain,
            final AsyncExecCallback cb) throws IOException, HttpException {

        final HttpClientContext ctx = scope != null ? scope.clientContext : HttpClientContext.create();
        final boolean enabled = ctx.getRequestConfigOrDefault().isContentCompressionEnabled();

        if (enabled && !request.containsHeader(HttpHeaders.ACCEPT_ENCODING)) {
            request.addHeader(MessageSupport.headerOfTokens(
                    HttpHeaders.ACCEPT_ENCODING, Collections.singletonList("deflate")));
        }

        chain.proceed(request, producer, scope, new AsyncExecCallback() {

            @Override
            public AsyncDataConsumer handleResponse(final HttpResponse rsp,
                                                    final EntityDetails details)
                    throws HttpException, IOException {

                if (!enabled) {
                    return cb.handleResponse(rsp, details);
                }

                final String coding = details != null ? details.getContentEncoding() : null;

                if (coding != null) {
                    AsyncDataConsumer downstream = cb.handleResponse(rsp, wrapEntityDetails(details));

                    final HeaderElement[] el = BasicHeaderValueParser.INSTANCE
                            .parseElements(coding, new ParserCursor(0, coding.length()));
                    for (int i = el.length - 1; i >= 0; i--) {
                        final String token = el[i].getName().toLowerCase(Locale.ROOT);
                        if ("identity".equals(token) || token.isEmpty()) {
                            continue;
                        }
                        final UnaryOperator<AsyncDataConsumer> op = decoders.lookup(token);
                        if (op != null) {
                            downstream = op.apply(downstream);
                        } else if (!ignoreUnknown) {
                            throw new HttpException("Unsupported Content-Encoding: " + token);
                        }
                    }
                    rsp.removeHeaders(HttpHeaders.CONTENT_ENCODING);
                    rsp.removeHeaders(HttpHeaders.CONTENT_LENGTH);
                    rsp.removeHeaders(HttpHeaders.CONTENT_MD5);
                    return downstream;
                }
                return cb.handleResponse(rsp, details);
            }

            @Override
            public void handleInformationResponse(final HttpResponse r)
                    throws HttpException, IOException {
                cb.handleInformationResponse(r);
            }

            @Override
            public void completed() {
                cb.completed();
            }

            @Override
            public void failed(final Exception ex) {
                cb.failed(ex);
            }
        });
    }

    private static EntityDetails wrapEntityDetails(final EntityDetails original) {
        return new EntityDetails() {
            @Override
            public long getContentLength() {
                return -1;
            }

            @Override
            public String getContentType() {
                return original.getContentType();
            }

            @Override
            public String getContentEncoding() {
                return null;
            }

            @Override
            public boolean isChunked() {
                return true;
            }

            @Override
            public Set<String> getTrailerNames() {
                return original.getTrailerNames();
            }
        };
    }
}
