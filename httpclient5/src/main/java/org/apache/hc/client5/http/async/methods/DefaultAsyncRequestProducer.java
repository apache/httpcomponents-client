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

import org.apache.hc.client5.http.config.Configurable;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.util.Args;

public class DefaultAsyncRequestProducer implements AsyncRequestProducer, Configurable {

    private final HttpRequest request;
    private final AsyncEntityProducer entityProducer;
    private final RequestConfig config;

    public DefaultAsyncRequestProducer(final HttpRequest request, final AsyncEntityProducer entityProducer, final RequestConfig config) {
        Args.notNull(request, "Request");
        this.request = request;
        this.entityProducer = entityProducer;
        this.config = config;
    }

    public DefaultAsyncRequestProducer(final HttpRequest request, final AsyncEntityProducer entityProducer) {
        this(request, entityProducer, null);
    }

    @Override
    public RequestConfig getConfig() {
        return config;
    }

    @Override
    public final HttpRequest produceRequest() {
        return request;
    }

    @Override
    public final EntityDetails getEntityDetails() {
        return entityProducer;
    }

    @Override
    public final int available() {
        return entityProducer != null ? entityProducer.available() : 0;
    }

    @Override
    public final void produce(final DataStreamChannel channel) throws IOException {
        if (entityProducer != null) {
            entityProducer.produce(channel);
        }
    }

    @Override
    public final void failed(final Exception cause) {
        releaseResources();
    }

    @Override
    public final void releaseResources() {
        if (entityProducer != null) {
            entityProducer.releaseResources();
        }
    }

}
