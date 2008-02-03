/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.conn.params;


import java.net.InetAddress;

import org.apache.http.HttpHost;
import org.apache.http.params.HttpParams;
import org.apache.http.conn.routing.HttpRoute;



/**
 * An adaptor for accessing route related parameters in {@link HttpParams}.
 * See {@link ConnRoutePNames} for parameter name definitions.
 * 
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 * @author <a href="mailto:rolandw at apache.org">Roland Weber</a>
 * 
 * @version $Revision$
 * 
 * @since 4.0
 */
public class HttpRouteParams {

    /**
     * A special value indicating "no host".
     * This relies on a nonsense scheme name to avoid conflicts
     * with actual hosts.
     */
    public static final HttpHost NO_HOST =
        new HttpHost("127.0.0.255", -32768, "$_");

    /**
     * A special value indicating "no route".
     * This is a route with {@link #NO_HOST} as the target.
     */
    public static final HttpRoute NO_ROUTE = new HttpRoute(NO_HOST);



    /** Disabled default constructor. */
    private HttpRouteParams() {
        // no body
    }


    /**
     * Obtains the {@link ConnRoutePNames#DEFAULT_PROXY DEFAULT_PROXY}
     * parameter value.
     * {@link #NO_HOST} will be mapped to <code>null</code>,
     * to allow unsetting in a hierarchy.
     *
     * @param params    the parameters in which to look up
     *
     * @return  the default proxy set in the argument parameters, or
     *          <code>null</code> if not set
     */
    public final static HttpHost getDefaultProxy(HttpParams params) {
        if (params == null) {
            throw new IllegalArgumentException("Parameters must not be null.");
        }
        HttpHost proxy = (HttpHost)
            params.getParameter(ConnRoutePNames.DEFAULT_PROXY);
        if ((proxy != null) && NO_HOST.equals(proxy)) {
            // value is explicitly unset
            proxy = null;
        }
        return proxy;
    }


    /**
     * Sets the {@link ConnRoutePNames#DEFAULT_PROXY DEFAULT_PROXY}
     * parameter value.
     *
     * @param params    the parameters in which to set the value
     * @param proxy     the value to set, may be <code>null</code>.
     *                  Note that {@link #NO_HOST} will be mapped to
     *                  <code>null</code> by {@link #getDefaultProxy},
     *                  to allow for explicit unsetting in hierarchies.
     */
    public final static void setDefaultProxy(HttpParams params,
                                             HttpHost proxy) {
        if (params == null) {
            throw new IllegalArgumentException("Parameters must not be null.");
        }
        params.setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
    }


    /**
     * Obtains the {@link ConnRoutePNames#FORCED_ROUTE FORCED_ROUTE}
     * parameter value.
     * {@link #NO_ROUTE} will be mapped to <code>null</code>,
     * to allow unsetting in a hierarchy.
     *
     * @param params    the parameters in which to look up
     *
     * @return  the forced route set in the argument parameters, or
     *          <code>null</code> if not set
     */
    public final static HttpRoute getForcedRoute(HttpParams params) {
        if (params == null) {
            throw new IllegalArgumentException("Parameters must not be null.");
        }
        HttpRoute route = (HttpRoute)
            params.getParameter(ConnRoutePNames.FORCED_ROUTE);
        if ((route != null) && NO_ROUTE.equals(route)) {
            // value is explicitly unset
            route = null;
        }
        return route;
    }


    /**
     * Sets the {@link ConnRoutePNames#FORCED_ROUTE FORCED_ROUTE}
     * parameter value.
     *
     * @param params    the parameters in which to set the value
     * @param route     the value to set, may be <code>null</code>.
     *                  Note that {@link #NO_ROUTE} will be mapped to
     *                  <code>null</code> by {@link #getForcedRoute},
     *                  to allow for explicit unsetting in hierarchies.
     */
    public final static void setForcedRoute(HttpParams params,
                                            HttpRoute route) {
        if (params == null) {
            throw new IllegalArgumentException("Parameters must not be null.");
        }
        params.setParameter(ConnRoutePNames.FORCED_ROUTE, route);
    }


    /**
     * Obtains the {@link ConnRoutePNames#LOCAL_ADDRESS LOCAL_ADDRESS}
     * parameter value.
     * {@link #NO_HOST} will be mapped to <code>null</code>,
     * to allow unsetting in a hierarchy.
     *
     * @param params    the parameters in which to look up
     *
     * @return  the default proxy set in the argument parameters, or
     *          <code>null</code> if not set
     */
    public final static InetAddress getLocalAddress(HttpParams params) {
        if (params == null) {
            throw new IllegalArgumentException("Parameters must not be null.");
        }
        InetAddress local = (InetAddress)
            params.getParameter(ConnRoutePNames.LOCAL_ADDRESS);
        // currently no explicit unsetting
        return local;
    }


    /**
     * Sets the {@link ConnRoutePNames#LOCAL_ADDRESS LOCAL_ADDRESS}
     * parameter value.
     *
     * @param params    the parameters in which to set the value
     * @param local     the value to set, may be <code>null</code>.
     */
    public final static void setLocalAddress(HttpParams params,
                                             InetAddress local) {
        if (params == null) {
            throw new IllegalArgumentException("Parameters must not be null.");
        }
        params.setParameter(ConnRoutePNames.LOCAL_ADDRESS, local);
    }
}

