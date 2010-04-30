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
package org.apache.http.client.cache.impl;

import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.ProtocolException;
import org.apache.http.impl.client.RequestWrapper;

/**
 * @since 4.1
 */
public class ConditionalRequestBuilder {

    public HttpRequest buildConditionalRequest(HttpRequest request, CacheEntry cacheEntry)
            throws ProtocolException {
        RequestWrapper wrapperRequest = new RequestWrapper(request);
        wrapperRequest.resetHeaders();
        Header eTag = cacheEntry.getFirstHeader(HeaderConstants.ETAG);
        if (eTag != null) {
            wrapperRequest.setHeader("If-None-Match", eTag.getValue());
        } else {
            Header lastModified = cacheEntry.getFirstHeader(HeaderConstants.LAST_MODIFIED);
            wrapperRequest.setHeader("If-Modified-Since", lastModified.getValue());
        }
        return wrapperRequest;

    }

}
