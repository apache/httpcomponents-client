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

package org.apache.http.impl.conn;


import java.net.InetAddress;

import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.protocol.HttpContext;

import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.HttpRoute;
import org.apache.http.conn.HttpRoutePlanner;
import org.apache.http.conn.Scheme;

import org.apache.http.conn.params.ConnRoutePNames;


/**
 * Default implementation of an {@link HttpRoutePlanner}.
 * This implementation is based on {@link ConnRoutePNames parameters}.
 * It will not make use of any Java system properties,
 * nor of system or browser proxy settings.
 */
public class DefaultHttpRoutePlanner implements HttpRoutePlanner {
    
    private ClientConnectionManager connectionManager;
    
    public DefaultHttpRoutePlanner(ClientConnectionManager aConnManager) {
        setConnectionManager(aConnManager);
    }


    // default constructor

    
    public void setConnectionManager(ClientConnectionManager aConnManager) {
        this.connectionManager = aConnManager;
    }


    // non-javadoc, see interface HttpRoutePlanner
    public HttpRoute determineRoute(HttpHost target,
                                    HttpRequest request,
                                    HttpContext context)
        throws HttpException {

        if (request == null) {
            throw new IllegalStateException
                ("Request must not be null.");
        }

        // If we have a forced route, we can do without a target.
        HttpRoute route = (HttpRoute)
            request.getParams().getParameter(ConnRoutePNames.FORCED_ROUTE);
        if (route != null)
            return route;

        // If we get here, there is no forced route.
        // So we need a target to compute a route.

        if (target == null) {
            throw new IllegalStateException
                ("Target host must not be null.");
        }

        final InetAddress local = (InetAddress)
            request.getParams().getParameter(ConnRoutePNames.LOCAL_ADDRESS);
        final HttpHost proxy = (HttpHost)
            request.getParams().getParameter(ConnRoutePNames.DEFAULT_PROXY);

        final Scheme schm = this.connectionManager.getSchemeRegistry().
            getScheme(target.getSchemeName());
        // as it is typically used for TLS/SSL, we assume that
        // a layered scheme implies a secure connection
        final boolean secure = schm.isLayered();

        if (proxy == null) {
            route = new HttpRoute(target, local, secure);
        } else {
            route = new HttpRoute(target, local, proxy, secure);
        }
        return route;
    }
    
    
}
