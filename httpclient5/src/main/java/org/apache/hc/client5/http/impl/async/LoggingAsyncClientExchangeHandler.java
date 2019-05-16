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
import java.nio.ByteBuffer;
import java.util.List;

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.message.RequestLine;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.RequestChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Identifiable;
import org.slf4j.Logger;

final class LoggingAsyncClientExchangeHandler implements AsyncClientExchangeHandler, Identifiable {

    private final Logger log;
    private final String exchangeId;
    private final AsyncClientExchangeHandler handler;

    LoggingAsyncClientExchangeHandler(final Logger log, final String exchangeId, final AsyncClientExchangeHandler handler) {
        this.log = log;
        this.exchangeId = exchangeId;
        this.handler = handler;
    }

    @Override
    public String getId() {
        return exchangeId;
    }

    @Override
    public void releaseResources() {
        handler.releaseResources();
    }

    @Override
    public void produceRequest(final RequestChannel channel, final HttpContext context) throws HttpException, IOException {
        handler.produceRequest(new RequestChannel() {

            @Override
            public void sendRequest(
                    final HttpRequest request,
                    final EntityDetails entityDetails,
                    final HttpContext context) throws HttpException, IOException {
                if (log.isDebugEnabled()) {
                    log.debug(exchangeId + ": send request " + new RequestLine(request) + ", " +
                            (entityDetails != null ? "entity len " + entityDetails.getContentLength() : "null entity"));
                }
                channel.sendRequest(request, entityDetails, context);
            }

        }, context);
    }

    @Override
    public int available() {
        return handler.available();
    }

    @Override
    public void produce(final DataStreamChannel channel) throws IOException {
        if (log.isDebugEnabled()) {
            log.debug(exchangeId + ": produce request data");
        }
        handler.produce(new DataStreamChannel() {

            @Override
            public void requestOutput() {
                channel.requestOutput();
            }

            @Override
            public int write(final ByteBuffer src) throws IOException {
                if (log.isDebugEnabled()) {
                    log.debug(exchangeId + ": produce request data, len " + src.remaining() + " bytes");
                }
                return channel.write(src);
            }

            @Override
            public void endStream() throws IOException {
                if (log.isDebugEnabled()) {
                    log.debug(exchangeId + ": end of request data");
                }
                channel.endStream();
            }

            @Override
            public void endStream(final List<? extends Header> trailers) throws IOException {
                if (log.isDebugEnabled()) {
                    log.debug(exchangeId + ": end of request data");
                }
                channel.endStream(trailers);
            }

        });
    }

    @Override
    public void consumeInformation(
            final HttpResponse response,
            final HttpContext context) throws HttpException, IOException {
        if (log.isDebugEnabled()) {
            log.debug(exchangeId + ": information response " + new StatusLine(response));
        }
        handler.consumeInformation(response, context);
    }

    @Override
    public void consumeResponse(
            final HttpResponse response,
            final EntityDetails entityDetails,
            final HttpContext context) throws HttpException, IOException {
        if (log.isDebugEnabled()) {
            log.debug(exchangeId + ": consume response " + new StatusLine(response) + ", " +
                    (entityDetails != null ? "entity len " + entityDetails.getContentLength() : " null entity"));
        }
        handler.consumeResponse(response, entityDetails, context);
    }


    @Override
    public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        handler.updateCapacity(new CapacityChannel() {

            @Override
            public void update(final int increment) throws IOException {
                if (log.isDebugEnabled()) {
                    log.debug(exchangeId + ": capacity update " + increment);
                }
                capacityChannel.update(increment);
            }

        });
    }

    @Override
    public void consume(final ByteBuffer src) throws IOException {
        if (log.isDebugEnabled()) {
            log.debug(exchangeId + ": consume response data, len " + src.remaining() + " bytes");
        }
        handler.consume(src);
    }

    @Override
    public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        if (log.isDebugEnabled()) {
            log.debug(exchangeId + ": end of response data");
        }
        handler.streamEnd(trailers);
    }

    @Override
    public void failed(final Exception cause) {
        if (log.isDebugEnabled()) {
            log.debug(exchangeId + ": execution failed: " + cause.getMessage());
        }
        handler.failed(cause);
    }

    @Override
    public void cancel() {
        if (log.isDebugEnabled()) {
            log.debug(exchangeId + ": execution cancelled");
        }
        handler.cancel();
    }

}
