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

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.annotation.Immutable;
import org.apache.http.client.cache.HeaderConstants;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.protocol.HTTP;

/**
 * Rebuilds an {@link HttpResponse} from a {@link CacheEntry}
 *
 * @since 4.1
 */
@Immutable
class CachedHttpResponseGenerator {

    private final CacheValidityPolicy validityStrategy;

    CachedHttpResponseGenerator(final CacheValidityPolicy validityStrategy) {
        super();
        this.validityStrategy = validityStrategy;
    }

    CachedHttpResponseGenerator() {
        this(new CacheValidityPolicy());
    }

    /**
     * If I was able to use a {@link CacheEntry} to response to the {@link org.apache.http.HttpRequest} then
     * generate an {@link HttpResponse} based on the cache entry.
     * @param entry
     *            {@link CacheEntry} to transform into an {@link HttpResponse}
     * @return {@link HttpResponse} that was constructed
     */
    HttpResponse generateResponse(HttpCacheEntry entry) {

        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, entry
                .getStatusCode(), entry.getReasonPhrase());

        if (entry.getStatusCode() != HttpStatus.SC_NOT_MODIFIED) {
            HttpEntity entity = entry.getBody();
            response.setEntity(entity);
            response.setHeaders(entry.getAllHeaders());
            addMissingContentLengthHeader(response, entity);
        }

        long age = this.validityStrategy.getCurrentAgeSecs(entry);
        if (age > 0) {
            if (age >= Integer.MAX_VALUE) {
                response.setHeader(HeaderConstants.AGE, "2147483648");
            } else {
                response.setHeader(HeaderConstants.AGE, "" + ((int) age));
            }
        }

        return response;
    }

    private void addMissingContentLengthHeader(HttpResponse response, HttpEntity entity) {
        if (transferEncodingIsPresent(response))
            return;

        Header contentLength = response.getFirstHeader(HTTP.CONTENT_LEN);
        if (contentLength == null) {
            contentLength = new BasicHeader(HTTP.CONTENT_LEN, Long.toString(entity
                    .getContentLength()));
            response.setHeader(contentLength);
        }
    }

    private boolean transferEncodingIsPresent(HttpResponse response) {
        Header hdr = response.getFirstHeader(HTTP.TRANSFER_ENCODING);
        return hdr != null;
    }
}
