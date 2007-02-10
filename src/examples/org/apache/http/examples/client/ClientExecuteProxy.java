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

package org.apache.http.examples.client;


import org.apache.http.HttpHost;
import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.impl.params.DefaultHttpParams;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestContent;
import org.apache.http.protocol.RequestExpectContinue;
import org.apache.http.protocol.RequestTargetHost;
import org.apache.http.protocol.RequestUserAgent;

import org.apache.http.conn.Scheme;
import org.apache.http.conn.SchemeRegistry;
import org.apache.http.conn.SocketFactory;
import org.apache.http.conn.PlainSocketFactory;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.HttpRoute;
import org.apache.http.impl.conn.ThreadSafeClientConnManager;
import org.apache.http.client.HttpClient;
import org.apache.http.client.RoutedRequest;
import org.apache.http.impl.client.DefaultHttpClient;



/**
 * How to send a request via proxy using {@link HttpClient HttpClient}.
 *
 * @author <a href="mailto:rolandw at apache.org">Roland Weber</a>
 *
 *
 * <!-- empty lines above to avoid 'svn diff' context problems -->
 * @version $Revision$
 *
 * @since 4.0
 */
public class ClientExecuteProxy {

    /**
     * The default parameters.
     * Instantiated in {@link #setup setup}.
     */
    private static HttpParams defaultParameters = null;

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

        // make sure to use a proxy that supports CONNECT
        final HttpHost target =
            new HttpHost("issues.apache.org", 443, "https");
        final HttpHost proxy =
            new HttpHost("127.0.0.1", 8666, "http");

        setup(); // some general setup

        HttpClient client = createHttpClient();

        HttpRequest req = createRequest(target);

        final HttpRoute route = new HttpRoute
            (target, null, proxy,
             supportedSchemes.getScheme(target).isLayered());
        final RoutedRequest roureq = new RoutedRequest.Impl(req, route);
        
        System.out.println("executing request to " + target + " via " + proxy);
        try {
            HttpResponse rsp = client.execute(roureq, null);

            System.out.println("----------------------------------------");
            System.out.println(rsp.getStatusLine());
            Header[] headers = rsp.getAllHeaders();
            for (int i=0; i<headers.length; i++) {
                System.out.println(headers[i]);
            }
            System.out.println("----------------------------------------");

            //@@@ there is no entity, so we can't call close() there
            //@@@ there is no need to call close() either, since the
            //@@@ connection will have been released immediately

        } finally {
            //@@@ any kind of cleanup that should be performed?
        }
    } // main


    private final static HttpClient createHttpClient() {

        ClientConnectionManager ccm =
            new ThreadSafeClientConnManager(getParams());

        DefaultHttpClient dhc =
            new DefaultHttpClient(getParams(), ccm, supportedSchemes);

        BasicHttpProcessor bhp = dhc.getProcessor();
        // Required protocol interceptors
        bhp.addInterceptor(new RequestContent());
        bhp.addInterceptor(new RequestTargetHost());
        // Recommended protocol interceptors
        bhp.addInterceptor(new RequestConnControl());
        bhp.addInterceptor(new RequestUserAgent());
        bhp.addInterceptor(new RequestExpectContinue());

        return dhc;
    }


    /**
     * Performs general setup.
     * This should be called only once.
     */
    private final static void setup() {

        //@@@ use dedicated SchemeRegistry instance
        //@@@ currently no way to pass it to TSCCM
        supportedSchemes = SchemeRegistry.DEFAULT; //new SchemeRegistry();

        // Register the "http" and "https" protocol schemes, they are
        // required by the default operator to look up socket factories.
        SocketFactory sf = PlainSocketFactory.getSocketFactory();
        supportedSchemes.register(new Scheme("http", sf, 80));
        sf = SSLSocketFactory.getSocketFactory();
        supportedSchemes.register(new Scheme("https", sf, 80));

        // prepare parameters
        HttpParams params = new DefaultHttpParams();
        HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
        HttpProtocolParams.setContentCharset(params, "UTF-8");
        HttpProtocolParams.setUserAgent(params, "Jakarta-HttpClient/4.0");
        HttpProtocolParams.setUseExpectContinue(params, true);
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


} // class ClientExecuteProxy

