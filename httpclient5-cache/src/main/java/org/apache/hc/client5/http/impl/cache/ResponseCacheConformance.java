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
import java.time.Instant;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * This response interceptor removes common {@literal Content-} headers from the 304
 * response messages based on the provision of the HTTP specification that 304
 * status code represents a confirmation that the requested resource content has
 * not changed since its retrieval or the last update and adds {@literal Date}
 * header if it is not present in the response message..
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
class ResponseCacheConformance implements HttpResponseInterceptor {

    public static final ResponseCacheConformance INSTANCE = new ResponseCacheConformance();

    private final static String[] DISALLOWED_ENTITY_HEADERS = {
            HttpHeaders.CONTENT_ENCODING,
            HttpHeaders.CONTENT_LANGUAGE,
            HttpHeaders.CONTENT_LENGTH,
            HttpHeaders.CONTENT_MD5,
            HttpHeaders.CONTENT_RANGE,
            HttpHeaders.CONTENT_TYPE
    };

    @Override
    public void process(final HttpResponse response,
                        final EntityDetails entity,
                        final HttpContext context) throws HttpException, IOException {
        if (response.getCode() == HttpStatus.SC_NOT_MODIFIED) {
            for (final String headerName : DISALLOWED_ENTITY_HEADERS) {
                response.removeHeaders(headerName);
            }
        }
        if (!response.containsHeader(HttpHeaders.DATE)) {
            response.addHeader(new BasicHeader(HttpHeaders.DATE, Instant.now()));
        }
    }

}
