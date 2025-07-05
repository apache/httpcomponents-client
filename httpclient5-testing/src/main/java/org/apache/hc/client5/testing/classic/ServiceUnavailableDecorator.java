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

package org.apache.hc.client5.testing.classic;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.core5.function.Resolver;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.io.HttpServerRequestHandler;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;

public class ServiceUnavailableDecorator implements HttpServerRequestHandler {

    private final HttpServerRequestHandler requestHandler;
    private final Resolver<HttpRequest, TimeValue> serviceAvailabilityResolver;
    private final AtomicBoolean serviceUnavailable;

    public ServiceUnavailableDecorator(final HttpServerRequestHandler requestHandler,
                                       final Resolver<HttpRequest, TimeValue> serviceAvailabilityResolver) {
        this.requestHandler = Args.notNull(requestHandler, "Request handler");
        this.serviceAvailabilityResolver = Args.notNull(serviceAvailabilityResolver, "Service availability resolver");
        this.serviceUnavailable = new AtomicBoolean();
    }

    @Override
    public void handle(final ClassicHttpRequest request,
                       final ResponseTrigger responseTrigger,
                       final HttpContext context) throws HttpException, IOException {
        final TimeValue retryAfter = serviceAvailabilityResolver.resolve(request);
        serviceUnavailable.set(TimeValue.isPositive(retryAfter));
        if (serviceUnavailable.get()) {
            final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_SERVICE_UNAVAILABLE);
            response.addHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter.toSeconds()));
            final ProtocolVersion version = request.getVersion();
            if (version != null && version.compareToVersion(HttpVersion.HTTP_2) < 0) {
                response.addHeader(HttpHeaders.CONNECTION, "Close");
            }
            responseTrigger.submitResponse(response);
        } else {
            requestHandler.handle(request, responseTrigger, context);
        }
    }

}
