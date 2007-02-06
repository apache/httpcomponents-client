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

import java.io.IOException;
import java.net.SocketException;

import org.apache.http.HttpClientConnection;
import org.apache.http.HttpInetConnection;
import org.apache.http.HttpResponse;
import org.apache.http.params.HttpParams;

/**
 * "Old" connection interface, as ported from HttpClient 3.x.
 * @deprecated kept temporarily for reference. To be replaced by
 *      {@link OperatedClientConnection} and
 *      {@link ManagedClientConnection}.
 */
public interface HttpHostConnection extends HttpClientConnection, HttpInetConnection {
    
    void setHttpConnectionManager(HttpConnectionManager manager);
    
    HostConfiguration getHostConfiguration();

    void open(HttpParams params) throws IOException;
    
    void tunnelCreated(HttpParams params) throws IOException;

    void setSocketTimeout(int timeout) throws SocketException;
    
    HttpResponse getLastResponse();
    
    void setLastResponse(HttpResponse response);
    
    void setLocked(boolean locked);
    
    boolean isLocked();
    
    void releaseConnection();
    
}
