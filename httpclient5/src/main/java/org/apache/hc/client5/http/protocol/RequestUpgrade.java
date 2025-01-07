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
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @since 5.4
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public final class RequestUpgrade implements HttpRequestInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(RequestUpgrade.class);

    public RequestUpgrade() {
    }

    @Override
    public void process(
            final HttpRequest request,
            final EntityDetails entity,
            final HttpContext context) throws HttpException, IOException {
        Args.notNull(request, "HTTP request");
        Args.notNull(context, "HTTP context");

        final HttpClientContext clientContext = HttpClientContext.cast(context);
        final RequestConfig requestConfig = clientContext.getRequestConfigOrDefault();
        if (requestConfig.isProtocolUpgradeEnabled()) {
            final ProtocolVersion version = request.getVersion() != null ? request.getVersion() : clientContext.getProtocolVersion();
            if (!request.containsHeader(HttpHeaders.UPGRADE) &&
                    !request.containsHeader(HttpHeaders.CONNECTION) &&
                    version.getMajor() == 1 && version.getMinor() >= 1) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Connection is upgradable: protocol version = {}", version);
                }
                final String method = request.getMethod();
                if ((Method.OPTIONS.isSame(method) || Method.HEAD.isSame(method) || Method.GET.isSame(method)) &&
                        clientContext.getSSLSession() == null) {
                    LOG.debug("Connection is upgradable to TLS: method = {}", method);
                    request.addHeader(HttpHeaders.UPGRADE, "TLS/1.2");
                    request.addHeader(HttpHeaders.CONNECTION, HttpHeaders.UPGRADE);
                }
            }
        }
    }

}
