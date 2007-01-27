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

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpConnection;
import org.apache.http.HttpResponse;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.protocol.HttpContext;

/**
 * <p>
 * </p>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public class DefaultResponseConsumedWatcher 
        implements ResponseConsumedWatcher {

    private final HttpConnection conn;
    private final HttpResponse response;
    private final HttpContext context;
    
    public DefaultResponseConsumedWatcher(
            final HttpConnection conn, 
            final HttpResponse response, 
            final HttpContext context) {
        super();
        if (conn == null) {
            throw new IllegalArgumentException("HTTP connection may not be null");
        }
        if (response == null) {
            throw new IllegalArgumentException("HTTP response may not be null");
        }
        this.conn = conn;
        this.response = response;
        this.context = context;
    }
    
    public void responseConsumed() {
        ConnectionReuseStrategy s = new DefaultConnectionReuseStrategy();
        if (!s.keepAlive(this.response, this.context)) {
            try {
                this.conn.close();
            } catch (IOException ex) {
                // log error
            }
        }
    }
        
}
