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

import org.apache.hc.client5.http.RouteInfo;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This protocol interceptor is responsible for adding the {@code Connection}
 * header to the outgoing requests, which is essential for managing persistence
 * of {@code HTTP/1.0} connections.
 *
 * @since 4.0
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public class RequestClientConnControl implements HttpRequestInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(RequestClientConnControl.class);

    public RequestClientConnControl() {
        super();
    }

    @Override
    public void process(final HttpRequest request, final EntityDetails entity, final HttpContext context)
            throws HttpException, IOException {
        Args.notNull(request, "HTTP request");

        final String method = request.getMethod();
        if (Method.CONNECT.isSame(method)) {
            return;
        }

        final HttpClientContext clientContext = HttpClientContext.adapt(context);
        final String exchangeId = clientContext.getExchangeId();

        // Obtain the client connection (required)
        final RouteInfo route = clientContext.getHttpRoute();
        if (route == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} Connection route not set in the context", exchangeId);
            }
            return;
        }

        if (route.getHopCount() == 1 || route.isTunnelled()) {
            if (!request.containsHeader(HttpHeaders.CONNECTION)) {
                request.addHeader(HttpHeaders.CONNECTION, HeaderElements.KEEP_ALIVE);
            }
        }
    }

}
