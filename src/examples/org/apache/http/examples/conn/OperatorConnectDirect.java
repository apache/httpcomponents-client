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

package org.apache.http.examples.conn;


import org.apache.http.HttpHost;
import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.impl.params.DefaultHttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpExecutionContext;

import org.apache.http.conn.Scheme;
import org.apache.http.conn.SchemeRegistry;
import org.apache.http.conn.SocketFactory;
import org.apache.http.conn.PlainSocketFactory;
import org.apache.http.conn.OperatedClientConnection;
import org.apache.http.conn.ClientConnectionOperator;
import org.apache.http.impl.conn.DefaultClientConnection;
import org.apache.http.impl.conn.DefaultClientConnectionOperator;



/**
 * How to open a direct connection using
 * {@link ClientConnectionOperator ClientConnectionOperator}.
 * This exemplifies the <i>opening</i> of the connection only.
 * The subsequent message exchange in this example should not
 * be used as a template.
 *
 * @author <a href="mailto:rolandw at apache.org">Roland Weber</a>
 *
 *
 * <!-- empty lines above to avoid 'svn diff' context problems -->
 * @version $Revision$ $Date$
 *
 * @since 4.0
 */
public class OperatorConnectDirect {

    /**
     * The default parameters.
     * Instantiated in {@link #setup setup}.
     */
    private static HttpParams defaultParameters;

    /**
     * The scheme registry.
     * Instantiated in {@link #setup setup}.
     */
    private static SchemeRegistry supportedSchemes;


    /**
     * Main entry point to this example.
     *
     * @param args      ignored
     */
    public final static void main(String[] args)
        throws Exception {

        final HttpHost target = new HttpHost("jakarta.apache.org", 80, "http");

        setup(); // some general setup

        // one operator can be used for many connections
        ClientConnectionOperator  scop = createOperator();
        OperatedClientConnection conn = createConnection();

        HttpRequest req = createRequest(target);
        HttpContext ctx = createContext();

        System.out.println("opening connection to " + target);
        scop.openConnection(conn, target, ctx, getParams());

        System.out.println("sending request");
        conn.sendRequestHeader(req);
        // there is no request entity
        conn.flush();

        System.out.println("receiving response header");
        HttpResponse rsp = conn.receiveResponseHeader(getParams());

        System.out.println("----------------------------------------");
        System.out.println(rsp.getStatusLine());
        Header[] headers = rsp.getAllHeaders();
        for (int i=0; i<headers.length; i++) {
            System.out.println(headers[i]);
        }
        System.out.println("----------------------------------------");

        System.out.println("closing connection");
        conn.close();

    } // main


    private final static ClientConnectionOperator createOperator() {
        return new DefaultClientConnectionOperator(supportedSchemes);
    }

    private final static OperatedClientConnection createConnection() {
        return new DefaultClientConnection();
    }


    /**
     * Performs general setup.
     * This should be called only once.
     */
    private final static void setup() {

        // Register the "http" protocol scheme, it is required
        // by the default operator to look up socket factories.
        supportedSchemes = new SchemeRegistry();
        SocketFactory sf = PlainSocketFactory.getSocketFactory();
        supportedSchemes.register(new Scheme("http", sf, 80));

        // Prepare parameters.
        // Since this example doesn't use the full core framework,
        // only few parameters are actually required.
        HttpParams params = new DefaultHttpParams();
        HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
        HttpProtocolParams.setUseExpectContinue(params, false);
        defaultParameters = params;

    } // setup


    private final static HttpParams getParams() {
        return defaultParameters;
    }


    /**
     * Creates a request to execute in this example.
     * In a real application, request interceptors should be used
     * to add the required headers.
     *
     * @param target    the target server for the request
     *
     * @return  a request without an entity
     */
    private final static HttpRequest createRequest(HttpHost target) {

        HttpRequest req = new BasicHttpRequest
            ("OPTIONS", "*", HttpVersion.HTTP_1_1);

        req.addHeader("Host", target.getHostName());

        return req;
    }


    /**
     * Creates a context for executing a request.
     * Since this example doesn't really use the execution framework,
     * the context can be left empty.
     *
     * @return  a new, empty context
     */
    private final static HttpContext createContext() {
        return new HttpExecutionContext(null);
    }

} // class OperatorConnectDirect

