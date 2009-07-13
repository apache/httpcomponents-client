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

import org.apache.http.HttpClientConnection;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.params.HttpParams;
import org.apache.http.params.DefaultedHttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestExecutor;


/**
 * Static helper methods.
 */
public final class Helper {

    /** Disabled default constructor. */
    private Helper() {
        // no body
    }


    /**
     * Executes a request.
     */
    public static HttpResponse execute(HttpRequest req,
                                       HttpClientConnection conn,
                                       HttpHost target,
                                       HttpRequestExecutor exec,
                                       HttpProcessor proc,
                                       HttpParams params,
                                       HttpContext ctxt)
        throws Exception {

        ctxt.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);
        ctxt.setAttribute(ExecutionContext.HTTP_TARGET_HOST, target);
        ctxt.setAttribute(ExecutionContext.HTTP_REQUEST, req);

        req.setParams(new DefaultedHttpParams(req.getParams(), params));
        exec.preProcess(req, proc, ctxt);
        HttpResponse rsp = exec.execute(req, conn, ctxt);
        rsp.setParams(new DefaultedHttpParams(rsp.getParams(), params));
        exec.postProcess(rsp, proc, ctxt);

        return rsp;
    }

}
