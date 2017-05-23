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
package org.apache.hc.client5.http.impl.cache;

import java.io.IOException;
import java.io.InputStream;

import org.apache.hc.client5.http.cache.InputLimit;
import org.apache.hc.client5.http.cache.Resource;
import org.apache.hc.client5.http.cache.ResourceFactory;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;

/**
 * @since 4.1
 */
class SizeLimitedResponseReader {

    private final ResourceFactory resourceFactory;
    private final long maxResponseSizeBytes;
    private final HttpRequest request;
    private final ClassicHttpResponse response;

    private InputStream instream;
    private InputLimit limit;
    private Resource resource;
    private boolean consumed;

    /**
     * Create an {@link ClassicHttpResponse} that is limited in size, this allows for checking
     * the size of objects that will be stored in the cache.
     */
    public SizeLimitedResponseReader(
            final ResourceFactory resourceFactory,
            final long maxResponseSizeBytes,
            final HttpRequest request,
            final ClassicHttpResponse response) {
        super();
        this.resourceFactory = resourceFactory;
        this.maxResponseSizeBytes = maxResponseSizeBytes;
        this.request = request;
        this.response = response;
    }

    protected void readResponse() throws IOException {
        if (!consumed) {
            doConsume();
        }
    }

    private void ensureNotConsumed() {
        if (consumed) {
            throw new IllegalStateException("Response has already been consumed");
        }
    }

    private void ensureConsumed() {
        if (!consumed) {
            throw new IllegalStateException("Response has not been consumed");
        }
    }

    private void doConsume() throws IOException {
        ensureNotConsumed();
        consumed = true;

        limit = new InputLimit(maxResponseSizeBytes);

        final HttpEntity entity = response.getEntity();
        if (entity == null) {
            return;
        }
        final String uri = request.getRequestUri();
        instream = entity.getContent();
        try {
            resource = resourceFactory.generate(uri, instream, limit);
        } finally {
            if (!limit.isReached()) {
                instream.close();
            }
        }
    }

    boolean isLimitReached() {
        ensureConsumed();
        return limit.isReached();
    }

    Resource getResource() {
        ensureConsumed();
        return resource;
    }

    ClassicHttpResponse getReconstructedResponse() throws IOException {
        ensureConsumed();
        final ClassicHttpResponse reconstructed = new BasicClassicHttpResponse(response.getCode()) {

            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    response.close();
                }
            }
        };
        reconstructed.setHeaders(response.getAllHeaders());

        final CombinedEntity combinedEntity = new CombinedEntity(resource, instream);
        final HttpEntity entity = response.getEntity();
        if (entity != null) {
            combinedEntity.setContentType(entity.getContentType());
            combinedEntity.setContentEncoding(entity.getContentEncoding());
            combinedEntity.setChunked(entity.isChunked());
        }
        reconstructed.setEntity(combinedEntity);
        return reconstructed;
    }

}
