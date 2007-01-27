/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
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

package org.apache.http.client.impl;

import java.io.IOException;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpException;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.SyncHttpExecutionContext;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.client.HttpClient;



/**
 * Convenience base class for HTTP client implementations.
 *
 * @author <a href="mailto:rolandw@apache.org">Roland Weber</a>
 *
 *
 * <!-- empty lines to avoid svn diff problems -->
 * @version   $Revision$
 *
 * @since 4.0
 */
public abstract class AbstractHttpClient
    implements HttpClient {


    /** The default context. */
    protected HttpContext defaultContext;

    /** The parameters. */
    protected HttpParams defaultParams;

    /** The connection manager. */
    protected ClientConnectionManager connManager;


    /**
     * Creates a new HTTP client.
     *
     * @param context   the context, or <code>null</code> to use an instance of
     *        {@link SyncHttpExecutionContext SyncHttpExecutionContext}
     * @param params    the parameters
     * @param conman    the connection manager
     */
    protected AbstractHttpClient(HttpContext context, HttpParams params,
                                 ClientConnectionManager conman) {
        if (params == null)
            throw new IllegalArgumentException
                ("Parameters must not be null.");
        if (conman == null)
            throw new IllegalArgumentException
                ("Connection manager must not be null.");

        defaultParams  = params;
        connManager    = conman;
        defaultContext = (context != null) ?
            context : new SyncHttpExecutionContext(null);

    } // constructor


    // non-javadoc, see interface HttpClient
    public final HttpContext getContext() {
        return defaultContext;
    }


    /**
     * Replaces the default context.
     *
     * @param context   the new default context
     */
    public void setContext(HttpContext context) {
        if (context == null)
            throw new IllegalArgumentException
                ("Context must not be null.");
        defaultContext = context;
    }


    // non-javadoc, see interface HttpClient
    public final HttpParams getParams() {
        return defaultParams;
    }


    /**
     * Replaces the parameters.
     * The implementation here does not update parameters of dependent objects.
     *
     * @param params    the new default parameters
     */
    public void setParams(HttpParams params) {
        if (params == null)
            throw new IllegalArgumentException
                ("Parameters must not be null.");
        defaultParams = params;
    }


    // non-javadoc, see interface HttpClient
    public final ClientConnectionManager getConnectionManager() {
        return connManager;
    }


    // no setConnectionManager(), too dangerous to replace while in use
    // derived classes may offer that method at their own risk


    // non-javadoc, see interface HttpClient
    public final HttpResponse execute(HttpRequest request)
        throws HttpException, IOException {

        return execute(request, defaultContext);
    }


} // class AbstractHttpClient
