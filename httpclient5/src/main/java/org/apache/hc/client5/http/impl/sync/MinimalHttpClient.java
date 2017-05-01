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

import org.apache.hc.client5.http.CancellableAware;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.config.Configurable;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultConnectionKeepAliveStrategy;
import org.apache.hc.client5.http.impl.ExecSupport;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.protocol.ClientProtocolException;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.sync.ExecChain;
import org.apache.hc.client5.http.sync.ExecRuntime;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.util.Args;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Internal class.
 *
 * @since 4.3
 */
@Contract(threading = ThreadingBehavior.SAFE)
public class MinimalHttpClient extends CloseableHttpClient {

    private final Logger log = LogManager.getLogger(getClass());

    private final HttpClientConnectionManager connManager;
    private final MinimalClientExec execChain;
    private final HttpRequestExecutor requestExecutor;

    MinimalHttpClient(final HttpClientConnectionManager connManager) {
        super();
        this.connManager = Args.notNull(connManager, "HTTP connection manager");
        this.execChain = new MinimalClientExec(
                DefaultConnectionReuseStrategy.INSTANCE,
                DefaultConnectionKeepAliveStrategy.INSTANCE);
        this.requestExecutor = new HttpRequestExecutor(DefaultConnectionReuseStrategy.INSTANCE);
    }

    @Override
    protected CloseableHttpResponse doExecute(
            final HttpHost target,
            final ClassicHttpRequest request,
            final HttpContext context) throws IOException {
        Args.notNull(target, "Target host");
        Args.notNull(request, "HTTP request");
        try {
            if (request.getScheme() == null) {
                request.setScheme(target.getSchemeName());
            }
            if (request.getAuthority() == null) {
                request.setAuthority(new URIAuthority(target));
            }
            final HttpClientContext localcontext = HttpClientContext.adapt(
                    context != null ? context : new BasicHttpContext());
            RequestConfig config = null;
            if (request instanceof Configurable) {
                config = ((Configurable) request).getConfig();
            }
            if (config != null) {
                localcontext.setRequestConfig(config);
            }
            final ExecRuntime execRuntime = new ExecRuntimeImpl(log, connManager, requestExecutor,
                    request instanceof CancellableAware ? (CancellableAware) request : null);
            final ExecChain.Scope scope = new ExecChain.Scope(new HttpRoute(target), request, execRuntime, localcontext);
            final ClassicHttpResponse response = this.execChain.execute(ExecSupport.copy(request), scope, null);
            return CloseableHttpResponse.adapt(response);
        } catch (final HttpException httpException) {
            throw new ClientProtocolException(httpException);
        }
    }

    @Override
    public void close() throws IOException {
        this.connManager.close();
    }

}
