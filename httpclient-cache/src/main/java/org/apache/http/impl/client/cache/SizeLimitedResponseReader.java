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
package org.apache.http.impl.client.cache;

import java.io.IOException;
import java.io.InputStream;

import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.client.cache.InputLimit;
import org.apache.http.client.cache.Resource;
import org.apache.http.client.cache.ResourceFactory;
import org.apache.http.message.BasicHttpResponse;

/**
 * @since 4.1
 */
@NotThreadSafe
class SizeLimitedResponseReader {

    private final ResourceFactory resourceFactory;
    private final long maxResponseSizeBytes;
    private final HttpRequest request;
    private final HttpResponse response;

    private InputStream instream;
    private InputLimit limit;
    private Resource resource;
    private boolean consumed;

    /**
     * Create an {@link HttpResponse} that is limited in size, this allows for checking
     * the size of objects that will be stored in the cache.
     */
    public SizeLimitedResponseReader(
            ResourceFactory resourceFactory,
            long maxResponseSizeBytes,
            HttpRequest request,
            HttpResponse response) {
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

        HttpEntity entity = response.getEntity();
        if (entity == null) {
            return;
        }
        String uri = request.getRequestLine().getUri();
        instream = entity.getContent();
        resource = resourceFactory.generate(uri, instream, limit);
    }

    boolean isLimitReached() {
        ensureConsumed();
        return limit.isReached();
    }

    Resource getResource() {
        ensureConsumed();
        return resource;
    }

    HttpResponse getReconstructedResponse() throws IOException {
        ensureConsumed();
        HttpResponse reconstructed = new BasicHttpResponse(response.getStatusLine());
        reconstructed.setHeaders(response.getAllHeaders());

        CombinedEntity combinedEntity = new CombinedEntity(resource, instream);
        HttpEntity entity = response.getEntity();
        if (entity != null) {
            combinedEntity.setContentType(entity.getContentType());
            combinedEntity.setContentEncoding(entity.getContentEncoding());
            combinedEntity.setChunked(entity.isChunked());
        }
        reconstructed.setEntity(combinedEntity);
        return reconstructed;
    }

}
