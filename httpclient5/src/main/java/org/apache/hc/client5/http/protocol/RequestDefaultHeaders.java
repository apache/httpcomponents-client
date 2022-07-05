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

package org.apache.hc.client5.http.protocol;

import java.io.IOException;
import java.util.Collection;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;

/**
 * Request interceptor that adds default request headers.
 *
 * @since 4.0
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public class RequestDefaultHeaders implements HttpRequestInterceptor {

    /**
     * Singleton instance.
     *
     * @since 5.2
     */
    public static final RequestDefaultHeaders INSTANCE = new RequestDefaultHeaders();

    private final Collection<? extends Header> defaultHeaders;

    /**
     * @since 4.3
     */
    public RequestDefaultHeaders(final Collection<? extends Header> defaultHeaders) {
        super();
        this.defaultHeaders = defaultHeaders;
    }

    public RequestDefaultHeaders() {
        this(null);
    }

    @Override
    public void process(final HttpRequest request, final EntityDetails entity, final HttpContext context)
            throws HttpException, IOException {
        Args.notNull(request, "HTTP request");

        final String method = request.getMethod();
        if (Method.CONNECT.isSame(method)) {
            return;
        }

        if (this.defaultHeaders != null) {
            for (final Header defHeader : this.defaultHeaders) {
                if(!request.containsHeader(defHeader.getName())) {
                    request.addHeader(defHeader);
                }
            }
        }
    }

}
