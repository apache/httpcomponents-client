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

package org.apache.http.examples.conn;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.conn.ClientConnectionOperator;
import org.apache.http.conn.OperatedClientConnection;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.conn.DefaultClientConnectionOperator;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.params.SyncBasicHttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.BasicHttpContext;

/**
 * How to open a secure connection through a proxy using
 * {@link ClientConnectionOperator ClientConnectionOperator}.
 * This exemplifies the <i>opening</i> of the connection only.
 * The message exchange, both subsequently and for tunnelling,
 * should not be used as a template.
 *
 * @since 4.0
 */
public class OperatorConnectProxy {

    public static void main(String[] args) throws Exception {

        // make sure to use a proxy that supports CONNECT
        HttpHost target = new HttpHost("issues.apache.org", 443, "https");
        HttpHost proxy = new HttpHost("127.0.0.1", 8666, "http");

        // some general setup
        // Register the "http" and "https" protocol schemes, they are
        // required by the default operator to look up socket factories.
        SchemeRegistry supportedSchemes = new SchemeRegistry();
        supportedSchemes.register(new Scheme("http",
                80, PlainSocketFactory.getSocketFactory()));
        supportedSchemes.register(new Scheme("https",
                443, SSLSocketFactory.getSocketFactory()));

        // Prepare parameters.
        // Since this example doesn't use the full core framework,
        // only few parameters are actually required.
        HttpParams params = new SyncBasicHttpParams();
        HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
        HttpProtocolParams.setUseExpectContinue(params, false);

        // one operator can be used for many connections
        ClientConnectionOperator scop = new DefaultClientConnectionOperator(supportedSchemes);

        HttpRequest req = new BasicHttpRequest("OPTIONS", "*", HttpVersion.HTTP_1_1);
        // In a real application, request interceptors should be used
        // to add the required headers.
        req.addHeader("Host", target.getHostName());

        HttpContext ctx = new BasicHttpContext();

        OperatedClientConnection conn = scop.createConnection();
        try {
            System.out.println("opening connection to " + proxy);
            scop.openConnection(conn, proxy, null, ctx, params);

            // Creates a request to tunnel a connection.
            // For details see RFC 2817, section 5.2
            String authority = target.getHostName() + ":" + target.getPort();
            HttpRequest connect = new BasicHttpRequest("CONNECT", authority,
                    HttpVersion.HTTP_1_1);
            // In a real application, request interceptors should be used
            // to add the required headers.
            connect.addHeader("Host", authority);

            System.out.println("opening tunnel to " + target);
            conn.sendRequestHeader(connect);
            // there is no request entity
            conn.flush();

            System.out.println("receiving confirmation for tunnel");
            HttpResponse connected = conn.receiveResponseHeader();
            System.out.println("----------------------------------------");
            printResponseHeader(connected);
            System.out.println("----------------------------------------");
            int status = connected.getStatusLine().getStatusCode();
            if ((status < 200) || (status > 299)) {
                System.out.println("unexpected status code " + status);
                System.exit(1);
            }
            System.out.println("receiving response body (ignored)");
            conn.receiveResponseEntity(connected);

            // Now we have a tunnel to the target. As we will be creating a
            // layered TLS/SSL socket immediately afterwards, updating the
            // connection with the new target is optional - but good style.
            // The scheme part of the target is already "https", though the
            // connection is not yet switched to the TLS/SSL protocol.
            conn.update(null, target, false, params);

            System.out.println("layering secure connection");
            scop.updateSecureConnection(conn, target, ctx, params);

            // finally we have the secure connection and can send the request

            System.out.println("sending request");
            conn.sendRequestHeader(req);
            // there is no request entity
            conn.flush();

            System.out.println("receiving response header");
            HttpResponse rsp = conn.receiveResponseHeader();

            System.out.println("----------------------------------------");
            printResponseHeader(rsp);
            System.out.println("----------------------------------------");

        } finally {
            System.out.println("closing connection");
            conn.close();
        }
    }

    private final static void printResponseHeader(HttpResponse rsp) {
        System.out.println(rsp.getStatusLine());
        Header[] headers = rsp.getAllHeaders();
        for (int i=0; i<headers.length; i++) {
            System.out.println(headers[i]);
        }
    }

}

