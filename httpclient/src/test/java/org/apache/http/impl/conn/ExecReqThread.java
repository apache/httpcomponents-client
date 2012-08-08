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

package org.apache.http.impl.conn;

import java.util.concurrent.TimeUnit;

import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ClientConnectionRequest;
import org.apache.http.conn.ManagedClientConnection;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.protocol.ImmutableHttpProcessor;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestContent;
import org.apache.http.util.EntityUtils;

class ExecReqThread extends Thread {

    private final HttpRequest request;
    private final HttpRoute route;
    private final ClientConnectionManager connman;
    private final long timeout;
    
    private volatile Exception exception;
    private volatile HttpResponse response;
    private volatile byte[] response_data;

    public ExecReqThread(
            HttpRequest request,
            HttpRoute route, 
            ClientConnectionManager mgr,
            long timeout) {
        super();
        this.request = request;
        this.route = route;
        this.connman = mgr;
        this.timeout = timeout;
    }

    public Exception getException() {
        return this.exception;
    }

    public HttpResponse getResponse() {
        return this.response;
    }

    public byte[] getResponseData() {
        return this.response_data;
    }

    @Override
    public void run() {
        try {
            
            HttpProcessor processor = new ImmutableHttpProcessor(
                    new HttpRequestInterceptor[] { new RequestContent(), new RequestConnControl() });
            HttpRequestExecutor executor = new HttpRequestExecutor();
            HttpContext context = new BasicHttpContext();
            HttpParams params = new BasicHttpParams();
            
            ClientConnectionRequest connRequest = this.connman.requestConnection(this.route, null);
            ManagedClientConnection conn = connRequest.getConnection(this.timeout, TimeUnit.MILLISECONDS);
            try {
                context.setAttribute(ExecutionContext.HTTP_TARGET_HOST, this.route.getTargetHost());
                context.setAttribute(ClientContext.ROUTE, this.route);
                context.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);

                conn.open(this.route, context, params);

                executor.preProcess(this.request, processor, context);

                this.response = executor.execute(this.request, conn, context);
                if (this.response.getEntity() != null) {
                    this.response_data = EntityUtils.toByteArray(this.response.getEntity());
                }
            } finally {
                this.connman.releaseConnection(conn, -1, null);
            }
        } catch (Exception ex) {
            this.exception = ex;
        }
    }

}
