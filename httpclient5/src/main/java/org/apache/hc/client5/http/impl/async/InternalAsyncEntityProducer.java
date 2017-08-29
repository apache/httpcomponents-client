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
import java.util.Set;

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.nio.AsyncDataProducer;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;

final class InternalAsyncEntityProducer implements AsyncEntityProducer {

    private final AsyncDataProducer dataProducer;
    private final EntityDetails entityDetails;

    InternalAsyncEntityProducer(final AsyncDataProducer dataProducer, final EntityDetails entityDetails) {
        this.dataProducer = dataProducer;
        this.entityDetails = entityDetails;
    }

    @Override
    public void releaseResources() {
        dataProducer.releaseResources();
    }

    @Override
    public void failed(final Exception cause) {
        dataProducer.releaseResources();
    }

    @Override
    public long getContentLength() {
        return entityDetails.getContentLength();
    }

    @Override
    public String getContentType() {
        return entityDetails.getContentType();
    }

    @Override
    public String getContentEncoding() {
        return entityDetails.getContentEncoding();
    }

    @Override
    public boolean isChunked() {
        return entityDetails.isChunked();
    }

    @Override
    public Set<String> getTrailerNames() {
        return entityDetails.getTrailerNames();
    }

    @Override
    public int available() {
        return dataProducer.available();
    }

    @Override
    public void produce(final DataStreamChannel channel) throws IOException {
        dataProducer.produce(channel);
    }

}