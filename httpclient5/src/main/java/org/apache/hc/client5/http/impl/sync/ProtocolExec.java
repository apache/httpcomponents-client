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

package org.apache.hc.client5.http.impl.sync;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.CredentialsStore;
import org.apache.hc.client5.http.auth.util.CredentialSupport;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.sync.ExecChain;
import org.apache.hc.client5.http.sync.ExecChainHandler;
import org.apache.hc.client5.http.utils.URIUtils;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.util.Args;

/**
 * Request executor in the request execution chain that is responsible
 * for implementation of HTTP specification requirements.
 * Internally this executor relies on a {@link HttpProcessor} to populate
 * requisite HTTP request headers, process HTTP response headers and processChallenge
 * session state in {@link HttpClientContext}.
 * <p>
 * Further responsibilities such as communication with the opposite
 * endpoint is delegated to the next executor in the request execution
 * chain.
 * </p>
 *
 * @since 4.3
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
final class ProtocolExec implements ExecChainHandler {

    private final HttpProcessor httpProcessor;

    public ProtocolExec(final HttpProcessor httpProcessor) {
        Args.notNull(httpProcessor, "HTTP protocol processor");
        this.httpProcessor = httpProcessor;
    }

    @Override
    public ClassicHttpResponse execute(
            final ClassicHttpRequest request,
            final ExecChain.Scope scope,
            final ExecChain chain) throws IOException, HttpException {
        Args.notNull(request, "HTTP request");
        Args.notNull(scope, "Scope");

        final HttpRoute route = scope.route;
        final HttpClientContext context = scope.clientContext;

        if (route.getProxyHost() != null && !route.isTunnelled()) {
            try {
                URI uri = request.getUri();
                if (!uri.isAbsolute()) {
                    final HttpHost target = route.getTargetHost();
                    uri = URIUtils.rewriteURI(uri, target, true);
                } else {
                    uri = URIUtils.rewriteURI(uri);
                }
                request.setPath(uri.toASCIIString());
            } catch (final URISyntaxException ex) {
                throw new ProtocolException("Invalid URI: " + request.getRequestUri(), ex);
            }
        }

        final URIAuthority authority = request.getAuthority();
        if (authority != null) {
            final CredentialsProvider credsProvider = context.getCredentialsProvider();
            if (credsProvider instanceof CredentialsStore) {
                CredentialSupport.extractFromAuthority(authority, (CredentialsStore) credsProvider);
            }
        }

        // Run request protocol interceptors
        context.setAttribute(HttpClientContext.HTTP_ROUTE, route);
        context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);

        this.httpProcessor.process(request, request.getEntity(), context);

        final ClassicHttpResponse response = chain.proceed(request, scope);
        try {
            // Run response protocol interceptors
            context.setAttribute(HttpCoreContext.HTTP_RESPONSE, response);
            this.httpProcessor.process(response, response.getEntity(), context);
            return response;
        } catch (final RuntimeException | HttpException | IOException ex) {
            response.close();
            throw ex;
        }
    }

}
