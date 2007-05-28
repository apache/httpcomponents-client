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

package org.apache.http.client;

import java.io.IOException;

import org.apache.http.HttpResponse;
import org.apache.http.HttpException;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ClientConnectionManager;

/**
 * Interface for an HTTP client.
 * HTTP clients encapsulate a smorgasbord of objects required to
 * execute HTTP requests while handling cookies, authentication,
 * connection management, and other features.
 * Thread safety of HTTP clients depends on the implementation
 * and configuration of the specific client.
 *
 * @author <a href="mailto:rolandw at apache.org">Roland Weber</a>
 *
 *
 * <!-- empty lines to avoid svn diff problems -->
 * @version   $Revision$
 *
 * @since 4.0
 */
public interface HttpClient {


    /**
     * Obtains the default context used by this client.
     * This context will be used by default when executing requests
     * with this client.
     *
     * @return  the default context
     */
    HttpContext getContext()
        ;


    /**
     * Obtains the parameters for this client.
     * These parameters will become defaults for all requests being
     * executed with this client, and for the parameters of
     * dependent objects in this client.
     *
     * @return  the default parameters
     */
    HttpParams getParams()
        ;


    /**
     * Obtains the connection manager used by this client.
     *
     * @return  the connection manager
     */
    ClientConnectionManager getConnectionManager()
        ;

    /**
     * Executes a request using the {@link #getContext default context}.
     * Same as {@link #execute(HttpUriRequest,HttpContext)
     *          client.execute(request, client.getContext())},
     * see there for details.
     *
     * @param request   the request to execute
     *
     * @return  the response to the request
     *
     * @throws HttpException    in case of a problem
     * @throws IOException      in case of an IO problem
     * <br/><i @@@>timeout exceptions?</i>
     */
    HttpResponse execute(HttpUriRequest request)
        throws HttpException, IOException
        ;


    /**
     * Executes a request using the given context.
     * The route to the target will be determined by the HTTP client.
     *
     * @param request   the request to execute
     * @param context   the context to use for the execution, or
     *                  <code>null</code> to use the
     *                  {@link #getContext default context}
     *
     * @return  the response to the request. This is always a final response,
     *          never an intermediate response with an 1xx status code.
     *          Whether redirects or authentication challenges will be returned
     *          or handled automatically depends on the implementation and
     *          configuration of this client.
     *
     * @throws HttpException    in case of a problem
     * @throws IOException      in case of an IO problem
     * <br/><i @@@>timeout exceptions?</i>
     */
    HttpResponse execute(HttpUriRequest request, HttpContext context)
        throws HttpException, IOException
        ;


    /**
     * Executes a request along the given route.
     *
     * @param roureq    the request to execute along with the route
     * @param context   the context to use for the execution, or
     *                  <code>null</code> to use the
     *                  {@link #getContext default context}
     *
     * @return  the response to the request. See
     *          {@link #execute(HttpUriRequest,HttpContext)
     *                  execute(target,request,context)}
     *          for details.
     *
     * @throws HttpException    in case of a problem
     * @throws IOException      in case of an IO problem
     * <br/><i @@@>timeout exceptions?</i>
     */
    HttpResponse execute(RoutedRequest roureq, HttpContext context)
        throws HttpException, IOException
        ;


} // interface HttpClient
