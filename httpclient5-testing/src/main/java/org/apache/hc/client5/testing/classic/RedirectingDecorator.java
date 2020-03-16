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
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.hc.client5.testing.redirect.Redirect;
import org.apache.hc.client5.testing.redirect.RedirectResolver;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.io.HttpServerRequestHandler;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;

public class RedirectingDecorator implements HttpServerRequestHandler {

    private final HttpServerRequestHandler requestHandler;
    private final RedirectResolver redirectResolver;

    public RedirectingDecorator(final HttpServerRequestHandler requestHandler,
                                final RedirectResolver redirectResolver) {
        this.requestHandler = Args.notNull(requestHandler, "Request handler");
        this.redirectResolver = redirectResolver;
    }

    @Override
    public void handle(final ClassicHttpRequest request,
                       final ResponseTrigger responseTrigger,
                       final HttpContext context) throws HttpException, IOException {
        try {
            final URI requestURI = request.getUri();
            final Redirect redirect = redirectResolver != null ? redirectResolver.resolve(requestURI) : null;
            if (redirect != null) {
                final ClassicHttpResponse response = new BasicClassicHttpResponse(redirect.status);
                if (redirect.location != null) {
                    response.addHeader(new BasicHeader(HttpHeaders.LOCATION, redirect.location));
                }
                switch (redirect.connControl) {
                    case KEEP_ALIVE:
                        response.addHeader(new BasicHeader(HttpHeaders.CONNECTION, HeaderElements.KEEP_ALIVE));
                        break;
                    case CLOSE:
                        response.addHeader(new BasicHeader(HttpHeaders.CONNECTION, HeaderElements.CLOSE));
                }
                responseTrigger.submitResponse(response);
            } else {
                requestHandler.handle(request, responseTrigger, context);
            }
        } catch (final URISyntaxException ex) {
            throw new ProtocolException(ex.getMessage(), ex);
        }
    }
}
