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

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Experimental;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http2.priority.PriorityFormatter;
import org.apache.hc.core5.http2.priority.PriorityValue;
import org.apache.hc.core5.util.Args;

/**
 * Adds the {@code Priority} request header to HTTP/2+ requests when a per-request
 * priority is configured.
 * <p>
 * The priority is taken from {@link RequestConfig#getH2Priority()}. If a {@code Priority}
 * header is already present on the request, it is left unchanged. If formatting the
 * configured value yields an empty string (e.g., because it encodes protocol defaults),
 * the header is not added.
 *
 * @since 5.6
 */
@Experimental
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public final class H2RequestPriority implements HttpRequestInterceptor {

    /**
     * Singleton instance.
     */
    public static final H2RequestPriority INSTANCE = new H2RequestPriority();

    @Override
    public void process(
            final HttpRequest request,
            final EntityDetails entity,
            final HttpContext context) throws HttpException, IOException {

        Args.notNull(request, "HTTP request");
        Args.notNull(context, "HTTP context");

        final HttpClientContext httpClientContext = HttpClientContext.cast(context);

        final ProtocolVersion pv = httpClientContext.getProtocolVersion();
        if (pv.compareToVersion(HttpVersion.HTTP_2) < 0) {
            return; // only for HTTP/2+
        }

        final Header existing = request.getFirstHeader(HttpHeaders.PRIORITY);
        if (existing != null) {
            return;
        }

        final RequestConfig requestConfig = httpClientContext.getRequestConfigOrDefault();
        final PriorityValue pri = requestConfig.getH2Priority();
        if (pri == null || PriorityValue.defaults().equals(pri)) {
            return;
        }

        request.addHeader(PriorityFormatter.formatHeader(pri));
    }
}
