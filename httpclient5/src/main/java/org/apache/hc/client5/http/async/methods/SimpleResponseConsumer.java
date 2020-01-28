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

import java.io.IOException;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.http.nio.support.AbstractAsyncResponseConsumer;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * HTTP response consumer that generates a {@link SimpleHttpResponse} instance based on events
 * of an incoming data stream.
 *
 * @since 5.0
 *
 * @see SimpleBody
 */
public final class SimpleResponseConsumer extends AbstractAsyncResponseConsumer<SimpleHttpResponse, byte[]> {

    SimpleResponseConsumer(final AsyncEntityConsumer<byte[]> entityConsumer) {
        super(entityConsumer);
    }

    public static SimpleResponseConsumer create() {
        return new SimpleResponseConsumer(new SimpleAsyncEntityConsumer());
    }

    @Override
    public void informationResponse(final HttpResponse response, final HttpContext context) throws HttpException, IOException {
    }

    @Override
    protected SimpleHttpResponse buildResult(final HttpResponse response, final byte[] entity, final ContentType contentType) {
        final SimpleHttpResponse simpleResponse = SimpleHttpResponse.copy(response);
        if (entity != null) {
            simpleResponse.setBody(entity, contentType);
        }
        return simpleResponse;
    }

}