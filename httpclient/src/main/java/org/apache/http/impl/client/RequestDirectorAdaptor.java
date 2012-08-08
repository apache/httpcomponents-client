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

package org.apache.http.impl.client;

import java.io.IOException;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.annotation.ThreadSafe;
import org.apache.http.auth.AuthState;
import org.apache.http.client.AuthenticationStrategy;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.RequestDirector;
import org.apache.http.client.UserTokenHandler;
import org.apache.http.client.methods.HttpExecutionAware;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.impl.client.exec.ClientExecChain;
import org.apache.http.impl.client.exec.HttpRequestWrapper;
import org.apache.http.impl.client.exec.MainClientExec;
import org.apache.http.impl.client.exec.ProtocolExec;
import org.apache.http.impl.client.exec.RedirectExec;
import org.apache.http.impl.client.exec.RetryExec;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestExecutor;

@ThreadSafe
class RequestDirectorAdaptor implements RequestDirector {

    private final HttpRoutePlanner routePlanner;
    private final HttpParams params;
    private final ClientExecChain execChain;

    public RequestDirectorAdaptor(
            final HttpRequestExecutor requestExecutor,
            final ClientConnectionManager connman,
            final ConnectionReuseStrategy reustrat,
            final ConnectionKeepAliveStrategy kastrat,
            final HttpRoutePlanner rouplan,
            final HttpProcessor httpProcessor,
            final HttpRequestRetryHandler retryHandler,
            final RedirectStrategy redirectStrategy,
            final AuthenticationStrategy targetAuthStrategy,
            final AuthenticationStrategy proxyAuthStrategy,
            final UserTokenHandler userTokenHandler,
            final HttpParams params) {
        this.routePlanner = rouplan;
        this.params = params;
        MainClientExec mainExecutor = new MainClientExec(
                requestExecutor, connman, reustrat, kastrat,
                targetAuthStrategy, proxyAuthStrategy, userTokenHandler);
        ProtocolExec protocolFacade = new ProtocolExec(mainExecutor, httpProcessor);
        RetryExec retryFacade = new RetryExec(protocolFacade, retryHandler);
        RedirectExec redirectFacade = new RedirectExec(retryFacade, rouplan, redirectStrategy);
        this.execChain = redirectFacade;
    }

    public HttpResponse execute(
            HttpHost target,
            final HttpRequest request,
            final HttpContext context) throws HttpException, IOException {
        if (target == null) {
            target = (HttpHost) this.params.getParameter(ClientPNames.DEFAULT_HOST);
        }
        if (target == null) {
            throw new IllegalStateException("Target host must not be null, or set in parameters");
        }
        HttpRequestWrapper wrapper = HttpRequestWrapper.wrap(request);
        wrapper.setParams(this.params);
        HttpHost virtualHost = (HttpHost) this.params.getParameter(ClientPNames.VIRTUAL_HOST);
        wrapper.setVirtualHost(virtualHost);
        HttpExecutionAware execListner = null;
        if (request instanceof HttpExecutionAware) {
            execListner = (HttpExecutionAware) request;
            if (execListner.isAborted()) {
                throw new RequestAbortedException("Request aborted");
            }
        }
        HttpRoute route = this.routePlanner.determineRoute(target, request, context);

        if (context.getAttribute(ClientContext.TARGET_AUTH_STATE) == null) {
            context.setAttribute(ClientContext.TARGET_AUTH_STATE, new AuthState());
        }
        if (context.getAttribute(ClientContext.PROXY_AUTH_STATE) == null) {
            context.setAttribute(ClientContext.PROXY_AUTH_STATE, new AuthState());
        }

        return this.execChain.execute(route, wrapper, context, execListner);
    }

}
