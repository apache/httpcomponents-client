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

package org.apache.http.conn;



/**
 * Provides directions on establishing a route.
 * Instances of this class compare a planned route with a tracked route
 * and indicate the next step required.
 * 
 * @author <a href="mailto:rolandw at apache.org">Roland Weber</a>
 *
 *
 * <!-- empty lines to avoid svn diff problems -->
 * @version $Revision$
 *
 * @since 4.0
 */
public class RouteDirector {

    /** Indicates that the route can not be established at all. */
    public final static int UNREACHABLE = -1;

    /** Indicates that the route is complete. */
    public final static int COMPLETE = 0;

    /** Step: open connection to target. */
    public final static int CONNECT_TARGET = 1;

    /** Step: open connection to proxy. */
    public final static int CONNECT_PROXY = 2;

    /** Step: tunnel through proxy. */
    public final static int CREATE_TUNNEL = 3;

    /** Step: layer protocol (over tunnel). */
    public final static int LAYER_PROTOCOL = 4;


    // public default constructor


    /**
     * Provides the next step.
     *
     * @param plan      the planned route
     * @param fact      the currently established route, or
     *                  <code>null</code> if nothing is established
     *
     * @return  one of the constants defined in this class, indicating
     *          either the next step to perform, or success, or failure.
     *          0 is for success, a negative value for failure.
     */
    public int nextStep(HttpRoute plan, HttpRoute fact) {
        if (plan == null) {
            throw new IllegalArgumentException
                ("Planned route may not be null.");
        }

        int step = UNREACHABLE;

        if (fact == null)
            step = firstStep(plan);
        else if (plan.getProxyHost() == null)
            step = directStep(plan, fact);
        else
            step = proxiedStep(plan, fact);

        return step;

    } // nextStep


    /**
     * Determines the first step to establish a route.
     *
     * @param plan      the planned route
     *
     * @return  the first step
     */
    protected int firstStep(HttpRoute plan) {

        return (plan.getProxyHost() == null) ?
            CONNECT_TARGET : CONNECT_PROXY;
    }


    /**
     * Determines the next step to establish a direct connection.
     *
     * @param plan      the planned route
     * @param fact      the currently established route
     *
     * @return  one of the constants defined in this class, indicating
     *          either the next step to perform, or success, or failure
     */
    protected int directStep(HttpRoute plan, HttpRoute fact) {

        if (fact.getProxyHost() != null)
            return UNREACHABLE;
        if (!plan.getTargetHost().equals(fact.getTargetHost()))
            return UNREACHABLE;
        // If the security is too low, we could now suggest to layer
        // a secure protocol on the direct connection. Layering on direct
        // connections has not been supported in HttpClient 3.x, we don't
        // consider it here until there is a real-life use case for it.

        // Should we tolerate if security is better than planned?

        // yes, this would cover the two checks above as well...
        if (!plan.equals(fact))
            return UNREACHABLE;

        return COMPLETE;
    }


    /**
     * Determines the next step to establish a connection via proxy.
     *
     * @param plan      the planned route
     * @param fact      the currently established route
     *
     * @return  one of the constants defined in this class, indicating
     *          either the next step to perform, or success, or failure
     */
    protected int proxiedStep(HttpRoute plan, HttpRoute fact) {

        if (fact.getProxyHost() == null)
            return UNREACHABLE;
        if (!plan.getProxyHost().equals(fact.getProxyHost()) ||
            !plan.getTargetHost().equals(fact.getTargetHost()))
            return UNREACHABLE;

        // proxy and target are the same, check tunnelling and layering
        if ((fact.isTunnelled() && !plan.isTunnelled()) ||
            (fact.isLayered()   && !plan.isLayered()))
            return UNREACHABLE;

        if (plan.isTunnelled() && !fact.isTunnelled())
            return CREATE_TUNNEL;
        if (plan.isLayered() && !fact.isLayered())
            return LAYER_PROTOCOL;

        // tunnel and layering are the same, remains to check the security
        // Should we tolerate if security is better than planned?
        if (plan.isSecure() != fact.isSecure())
            return UNREACHABLE;

        return COMPLETE;
    }


} // class RouteDirector
