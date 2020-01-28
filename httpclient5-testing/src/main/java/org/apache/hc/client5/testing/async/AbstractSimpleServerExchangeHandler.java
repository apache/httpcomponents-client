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
package org.apache.hc.client5.testing.async;

import java.io.IOException;

import org.apache.hc.client5.http.async.methods.SimpleBody;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.AsyncRequestConsumer;
import org.apache.hc.core5.http.nio.AsyncServerRequestHandler;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityProducer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer;
import org.apache.hc.core5.http.nio.support.AbstractAsyncRequesterConsumer;
import org.apache.hc.core5.http.nio.support.AbstractServerExchangeHandler;
import org.apache.hc.core5.http.nio.support.BasicResponseProducer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;

public abstract class AbstractSimpleServerExchangeHandler extends AbstractServerExchangeHandler<SimpleHttpRequest> {

    protected abstract SimpleHttpResponse handle(SimpleHttpRequest request, HttpCoreContext context) throws HttpException;

    @Override
    protected final AsyncRequestConsumer<SimpleHttpRequest> supplyConsumer(
            final HttpRequest request,
            final EntityDetails entityDetails,
            final HttpContext context) throws HttpException {
        return new AbstractAsyncRequesterConsumer<SimpleHttpRequest, byte[]>(new BasicAsyncEntityConsumer()) {

            @Override
            protected SimpleHttpRequest buildResult(
                    final HttpRequest request,
                    final byte[] body,
                    final ContentType contentType) {
                final SimpleHttpRequest simpleRequest = SimpleHttpRequest.copy(request);
                if (body != null) {
                    simpleRequest.setBody(body, contentType);
                }
                return simpleRequest;
            }

        };
    }

    @Override
    protected final void handle(
            final SimpleHttpRequest request,
            final AsyncServerRequestHandler.ResponseTrigger responseTrigger,
            final HttpContext context) throws HttpException, IOException {
        final SimpleHttpResponse response = handle(request, HttpCoreContext.adapt(context));
        final SimpleBody body = response.getBody();
        final AsyncEntityProducer entityProducer;
        if (body != null) {
            if (body.isText()) {
                entityProducer = new StringAsyncEntityProducer(body.getBodyText(), body.getContentType());
            } else {
                entityProducer = new BasicAsyncEntityProducer(body.getBodyBytes(), body.getContentType());
            }
        } else {
            entityProducer = null;
        }
        responseTrigger.submitResponse(new BasicResponseProducer(response, entityProducer), context);

    }

}
