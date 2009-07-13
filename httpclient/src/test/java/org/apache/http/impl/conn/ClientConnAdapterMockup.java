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

package org.apache.http.impl.conn;

import java.io.IOException;

import org.apache.http.HttpHost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;


/**
 * Mockup connection adapter.
 */
public class ClientConnAdapterMockup extends AbstractClientConnAdapter {

    public ClientConnAdapterMockup(ClientConnectionManager mgr) {
        super(mgr, null);
    }

    public void close() {
    }

    public HttpRoute getRoute() {
        throw new UnsupportedOperationException("just a mockup");
    }

    public void layerProtocol(HttpContext context, HttpParams params) {
        throw new UnsupportedOperationException("just a mockup");
    }

    public void open(HttpRoute route, HttpContext context, HttpParams params) throws IOException {
        throw new UnsupportedOperationException("just a mockup");
    }

    public void shutdown() {
    }

    public void tunnelTarget(boolean secure, HttpParams params) {
        throw new UnsupportedOperationException("just a mockup");
    }

    public void tunnelProxy(HttpHost next, boolean secure, HttpParams params) {
        throw new UnsupportedOperationException("just a mockup");
    }

    public Object getState() {
        throw new UnsupportedOperationException("just a mockup");
    }

    public void setState(Object state) {
        throw new UnsupportedOperationException("just a mockup");
    }
}
