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

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;

/**
 * HTTP cache support utilities.
 *
 * @since 5.0
 *
 * @deprecated Do not use. This functionality is internal.
 */
@Deprecated
public final class HttpCacheSupport {

    public static String getRequestUri(final HttpRequest request, final HttpHost target) {
        return CacheKeyGenerator.getRequestUri(target, request);
    }

    public static URI normalize(final URI requestUri) throws URISyntaxException {
        return CacheKeyGenerator.normalize(requestUri);
    }

    /**
     * Lenient URI parser that normalizes valid {@link URI}s and returns {@code null} for malformed URIs.
     * @deprecated Use {@link #normalizeQuietly(String)}
     */
    @Deprecated
    public static URI normalizeQuetly(final String requestUri) {
        if (requestUri == null) {
            return null;
        }
        try {
            return normalize(new URI(requestUri));
        } catch (final URISyntaxException ex) {
            return null;
        }
    }

    /**
     * Lenient URI parser that normalizes valid {@link URI}s and returns {@code null} for malformed URIs.
     * @since 5.2
     */
    public static URI normalizeQuietly(final String requestUri) {
        return CacheKeyGenerator.normalize(requestUri);
    }

}
