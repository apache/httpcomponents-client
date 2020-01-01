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

import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.testing.auth.Authenticator;
import org.apache.hc.client5.testing.auth.BasicAuthTokenExtractor;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpServerRequestHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.util.Args;

public class AuthenticatingDecorator implements HttpServerRequestHandler {

    private final HttpServerRequestHandler requestHandler;
    private final Authenticator authenticator;
    private final BasicAuthTokenExtractor authTokenExtractor;

    public AuthenticatingDecorator(final HttpServerRequestHandler requestHandler, final Authenticator authenticator) {
        this.requestHandler = Args.notNull(requestHandler, "Request handler");
        this.authenticator = Args.notNull(authenticator, "Authenticator");
        this.authTokenExtractor = new BasicAuthTokenExtractor();
    }

    protected void customizeUnauthorizedResponse(final ClassicHttpResponse unauthorized) {
    }

    @Override
    public void handle(
            final ClassicHttpRequest request,
            final ResponseTrigger responseTrigger,
            final HttpContext context) throws HttpException, IOException {
        final Header h = request.getFirstHeader(HttpHeaders.AUTHORIZATION);
        final String challengeResponse = h != null ? authTokenExtractor.extract(h.getValue()) : null;

        final URIAuthority authority = request.getAuthority();
        final String requestUri = request.getRequestUri();

        final boolean authenticated = authenticator.authenticate(authority, requestUri, challengeResponse);
        final Header expect = request.getFirstHeader(HttpHeaders.EXPECT);
        final boolean expectContinue = expect != null && "100-continue".equalsIgnoreCase(expect.getValue());

        if (authenticated) {
            if (expectContinue) {
                responseTrigger.sendInformation(new BasicClassicHttpResponse(HttpStatus.SC_CONTINUE));
            }
            requestHandler.handle(request, responseTrigger, context);
        } else {
            final ClassicHttpResponse unauthorized = new BasicClassicHttpResponse(HttpStatus.SC_UNAUTHORIZED);
            final String realm = authenticator.getRealm(authority, requestUri);
            unauthorized.addHeader(HttpHeaders.WWW_AUTHENTICATE, StandardAuthScheme.BASIC + " realm=\"" + realm + "\"");
            customizeUnauthorizedResponse(unauthorized);
            if (unauthorized.getEntity() == null) {
                unauthorized.setEntity(new StringEntity("Unauthorized"));
            }
            if (expectContinue || request.getEntity() == null) {
                // Respond immediately
                responseTrigger.submitResponse(unauthorized);
                // Consume request body later
                EntityUtils.consume(request.getEntity());
            } else {
                // Consume request body first
                EntityUtils.consume(request.getEntity());
                // Respond later
                responseTrigger.submitResponse(unauthorized);
            }
        }
    }

}
