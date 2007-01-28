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

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpException;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.conn.HostConfiguration;
import org.apache.http.conn.ClientConnectionManager;

import org.apache.http.client.HttpClient;
import org.apache.http.client.RoutedRequest;
import org.apache.http.client.ClientRequestDirector;



/**
 * Default implementation of an HTTP client.
 * <br/>
 * This class replaces <code>HttpClient</code> in HttpClient 3.
 *
 * @author <a href="mailto:rolandw at apache.org">Roland Weber</a>
 *
 *
 * <!-- empty lines to avoid svn diff problems -->
 * @version   $Revision$
 *
 * @since 4.0
 */
public class DefaultHttpClient extends AbstractHttpClient {


    /**
     * Creates a new HTTP client from parameters and a connection manager.
     *
     * @param params    the parameters
     * @param conman    the connection manager
     */
    public DefaultHttpClient(HttpParams params,
                             ClientConnectionManager conman) {
        super(null, params, conman, null);
        httpProcessor = createProcessor();
    }


    /**
     * Creates and initializes an HTTP processor.
     * This method is typically called by the constructor,
     * after the base class has been initialized.
     *
     * @return  a new, initialized HTTP processor
     */
    protected BasicHttpProcessor createProcessor() {

        BasicHttpProcessor bhp = new BasicHttpProcessor();
        //@@@ evaluate defaultParams to initialize interceptors

        return bhp;
    }


    // non-javadoc, see interface HttpClient
    public HttpResponse execute(RoutedRequest roureq, HttpContext context)
        throws HttpException, IOException {

        if (roureq == null) {
            throw new IllegalArgumentException
                ("Routed request must not be null.");
        }
        if (roureq.getRequest() == null) {
            throw new IllegalArgumentException
                ("Request must not be null.");
        }
        if (roureq.getRoute() == null) {
            throw new IllegalArgumentException
                ("Route must not be null.");
        }

        if (context == null)
            context = defaultContext;

        ClientRequestDirector director = createDirector(context);
        HttpResponse          response = director.execute(roureq, context);

        //@@@ "finalize" response, to allow for buffering of entities?
        //@@@ here or in director?

        System.out.println("@@@ what about "+director.getConnection()+"?");
        //@@@ return a "connected response" with a release() callback?
        //@@@ use a special response entity to implement the release()?

        return response;

    } // execute


    // non-javadoc, see base class AbstractHttpClient
    protected RoutedRequest determineRoute(HttpHost target,
                                           HttpRequest request,
                                           HttpContext context)
        throws HttpException {

        //@@@ allow null target if there is a default route with a target?
        if (target == null) {
            throw new IllegalArgumentException
                ("Target host must not be null.");
        }

        //@@@ refer to a default HostConfiguration?
        HostConfiguration route = new HostConfiguration(target, null, null);

        return new RoutedRequest.Impl(request, route);
    }


    /**
     * Creates a new director for a request execution.
     *
     * @param context   the context to use for the execution,
     *                  never <code>null</code>
     *
     * @return  the new director for executing a method in the given context
     */
    protected ClientRequestDirector createDirector(HttpContext context) {

        //@@@ can we use a single reqexec without sacrificing thread safety?
        //@@@ it seems wasteful to throw away both director and reqexec
        HttpRequestExecutor reqexec = new HttpRequestExecutor(httpProcessor);
        reqexec.setParams(defaultParams);

        return new DefaultClientRequestDirector
            (connManager, reqexec, defaultParams);

    }

} // class DefaultHttpClient
