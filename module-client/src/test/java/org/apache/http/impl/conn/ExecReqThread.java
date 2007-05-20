/*
 * $HeadURL$
 * $Revision$
 * $Date$
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

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.protocol.HttpExecutionContext;
import org.apache.http.util.EntityUtils;

import org.apache.http.conn.HttpRoute;
import org.apache.http.conn.ManagedClientConnection;
import org.apache.http.conn.ClientConnectionManager;


/**
 * Executes a request from a new thread.
 * 
 * @author Michael Becke
 * @author Roland Weber (rolandw at apache.org)
 */
public class ExecReqThread extends GetConnThread {

    protected RequestSpec  request_spec;
    protected HttpResponse response;
    protected byte[]       response_data;


    /**
     * Executes a request.
     * This involves the following steps:
     * <ol>
     * <li>obtain a connection (see base class)</li>
     * <li>open the connection</li>
     * <li>prepare context and request</li>
     * <li>execute request to obtain the response</li>
     * <li>consume the response entity (if there is one)</li>
     * <li>release the connection</li>
     * </ol>
     */    
    public ExecReqThread(ClientConnectionManager mgr,
                         HttpRoute route, long timeout,
                         RequestSpec reqspec) {
        super(mgr, route, timeout);

        request_spec = reqspec;
    }

    
    public HttpResponse getResponse() {
        return response;
    }

    public byte[] getResponseData() {
        return response_data;
    }


    /**
     * This method is invoked when the thread is started.
     * It invokes the base class implementation.
     */
    public void run() {
        super.run();    // obtain connection
        if (connection == null)
            return;     // problem obtaining connection

        try {
            request_spec.context.setAttribute
                (HttpExecutionContext.HTTP_CONNECTION, connection);

            doOpenConnection();

            HttpRequest request = (HttpRequest) request_spec.context.
                getAttribute(HttpExecutionContext.HTTP_REQUEST);
            request_spec.executor.preProcess
                (request, request_spec.processor, request_spec.context);

            response = request_spec.executor.execute
                (request, connection, request_spec.context);

            request_spec.executor.postProcess
                (response, request_spec.processor, request_spec.context);

            doConsumeResponse();

        } catch (Throwable dart) {
            dart.printStackTrace(System.out);
            if (exception != null)
                exception = dart;

        } finally {
            conn_manager.releaseConnection(connection);
        }
    }


    /**
     * Opens the connection after it has been obtained.
     */
    protected void doOpenConnection() throws Exception {
        connection.open
            (conn_route, request_spec.context, request_spec.params);
    }

    /**
     * Reads the response entity, if there is one.
     */
    protected void doConsumeResponse() throws Exception {
        if (response.getEntity() != null)
            response_data = EntityUtils.toByteArray(response.getEntity());
    }


    /**
     * Helper class collecting request data.
     * The request and target are expected in the context.
     */
    public static class RequestSpec {
        public HttpRequestExecutor executor;
        public HttpProcessor processor;
        public HttpContext context;
        public HttpParams params;
    }

}
