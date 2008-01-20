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


/**
 * Parameter names for connection managers in HttpConn.
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public interface ConnManagerPNames {



    /** 
     * Defines the maximum number of connections to a host.
     * This limit is interpreted by client connection managers
     * and applies to individual manager instances.
     * <p>
     * This parameter expects a value of type {@link java.util.Map}.
     * The value should map instances of
     * {@link org.apache.http.conn.routing.HttpRoute}
     * to {@link Integer integers}.
     * The default value is mapped to a special, private key.
     * </p>
     */
    public static final String MAX_HOST_CONNECTIONS = "http.connection-manager.max-per-host";

    /** 
     * Defines the maximum number of connections in total.
     * This limit is interpreted by client connection managers
     * and applies to individual manager instances.
     * <p>
     * This parameter expects a value of type {@link Integer}.
     * </p>
     */
    public static final String MAX_TOTAL_CONNECTIONS = "http.connection-manager.max-total";

}
