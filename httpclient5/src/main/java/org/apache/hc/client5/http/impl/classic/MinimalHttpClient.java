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

package org.apache.hc.client5.http.impl.classic;

import java.io.IOException;
import java.io.InterruptedIOException;

import org.apache.hc.client5.http.CancellableAware;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.classic.ExecRuntime;
import org.apache.hc.client5.http.config.Configurable;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.ConnectionShutdownException;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.protocol.RequestClientConnControl;
import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.DefaultHttpProcessor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.RequestContent;
import org.apache.hc.core5.http.protocol.RequestTargetHost;
import org.apache.hc.core5.http.protocol.RequestUserAgent;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.VersionInfo;
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
    private final ConnectionReuseStrategy reuseStrategy;
    private final HttpRequestExecutor requestExecutor;
    private final HttpProcessor httpProcessor;

    MinimalHttpClient(final HttpClientConnectionManager connManager) {
        super();
        this.connManager = Args.notNull(connManager, "HTTP connection manager");
        this.reuseStrategy = DefaultConnectionReuseStrategy.INSTANCE;
        this.requestExecutor = new HttpRequestExecutor(this.reuseStrategy);
        this.httpProcessor = new DefaultHttpProcessor(
                new RequestContent(),
                new RequestTargetHost(),
                new RequestClientConnControl(),
                new RequestUserAgent(VersionInfo.getSoftwareInfo(
                        "Apache-HttpClient", "org.apache.hc.client5", getClass())));
    }

    @Override
    protected CloseableHttpResponse doExecute(
            final HttpHost target,
            final ClassicHttpRequest request,
            final HttpContext context) throws IOException {
        Args.notNull(target, "Target host");
        Args.notNull(request, "HTTP request");
        if (request.getScheme() == null) {
            request.setScheme(target.getSchemeName());
        }
        if (request.getAuthority() == null) {
            request.setAuthority(new URIAuthority(target));
        }
        final HttpClientContext clientContext = HttpClientContext.adapt(
                context != null ? context : new BasicHttpContext());
        RequestConfig config = null;
        if (request instanceof Configurable) {
            config = ((Configurable) request).getConfig();
        }
        if (config != null) {
            clientContext.setRequestConfig(config);
        }

        final HttpRoute route = new HttpRoute(target.getPort() > 0 ? target : new HttpHost(
                target.getHostName(),
                DefaultSchemePortResolver.INSTANCE.resolve(target),
                target.getSchemeName()));

        final ExecRuntime execRuntime = new ExecRuntimeImpl(log, connManager, requestExecutor,
                request instanceof CancellableAware ? (CancellableAware) request : null);
        try {
            if (!execRuntime.isConnectionAcquired()) {
                execRuntime.acquireConnection(route, null, clientContext);
            }
            if (!execRuntime.isConnected()) {
                execRuntime.connect(clientContext);
            }

            context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);
            context.setAttribute(HttpClientContext.HTTP_ROUTE, route);

            httpProcessor.process(request, request.getEntity(), context);
            final ClassicHttpResponse response = execRuntime.execute(request, clientContext);
            httpProcessor.process(response, response.getEntity(), context);

            if (reuseStrategy.keepAlive(request, response, context)) {
                execRuntime.markConnectionReusable();
            } else {
                execRuntime.markConnectionNonReusable();
            }

            // check for entity, release connection if possible
            final HttpEntity entity = response.getEntity();
            if (entity == null || !entity.isStreaming()) {
                // connection not needed and (assumed to be) in re-usable state
                execRuntime.releaseConnection();
                return new CloseableHttpResponse(response, null);
            } else {
                ResponseEntityProxy.enchance(response, execRuntime);
                return new CloseableHttpResponse(response, execRuntime);
            }
        } catch (final ConnectionShutdownException ex) {
            final InterruptedIOException ioex = new InterruptedIOException("Connection has been shut down");
            ioex.initCause(ex);
            execRuntime.discardConnection();
            throw ioex;
        } catch (final RuntimeException | IOException ex) {
            execRuntime.discardConnection();
            throw ex;
        } catch (final HttpException httpException) {
            execRuntime.discardConnection();
            throw new ClientProtocolException(httpException);
        }
    }

    @Override
    public void close() throws IOException {
        this.connManager.close();
    }

}
