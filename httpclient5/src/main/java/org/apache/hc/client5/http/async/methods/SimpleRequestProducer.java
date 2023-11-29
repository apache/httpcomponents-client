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
package org.apache.hc.client5.http.async.methods;

import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityProducer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.util.Args;

/**
 * HTTP request producer that generates message data stream events based
 * on content of a {@link SimpleHttpRequest} instance.
 * <p>
 * IMPORTANT: {@link SimpleHttpRequest}s are intended for simple scenarios where entities inclosed
 * in requests are known to be small. It is generally recommended to use
 * {@link org.apache.hc.core5.http.nio.support.AsyncRequestBuilder} and streaming
 * {@link org.apache.hc.core5.http.nio.AsyncEntityProducer}s.
 *
 * @since 5.0
 *
 * @see SimpleBody
 * @see SimpleHttpRequest
 * @see org.apache.hc.core5.http.nio.support.AsyncRequestBuilder
 * @see org.apache.hc.core5.http.nio.AsyncEntityProducer
 */
public final class SimpleRequestProducer extends BasicRequestProducer {

    SimpleRequestProducer(final SimpleHttpRequest request, final AsyncEntityProducer entityProducer) {
        super(request, entityProducer);
    }

    public static SimpleRequestProducer create(final SimpleHttpRequest request) {
        Args.notNull(request, "Request");
        final SimpleBody body = request.getBody();
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
        return new SimpleRequestProducer(request, entityProducer);
    }

}
