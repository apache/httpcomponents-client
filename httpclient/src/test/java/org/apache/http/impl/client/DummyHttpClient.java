/*
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
package org.apache.http.impl.client;

import java.io.IOException;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;

@SuppressWarnings("deprecation")
public class DummyHttpClient implements HttpClient {

    private HttpParams params = new BasicHttpParams();
    private ClientConnectionManager connManager = new SingleClientConnManager();
    private HttpRequest request;
    private HttpResponse response = new BasicHttpResponse(new ProtocolVersion("HTTP",1,1), HttpStatus.SC_OK, "OK");
    
    public void setParams(HttpParams params) {
        this.params = params;
    }
    
    public HttpParams getParams() {
        return params;
    }

    public ClientConnectionManager getConnectionManager() {
        return connManager;
    }
    
    public void setConnectionManager(ClientConnectionManager ccm) {
        connManager = ccm;
    }
    
    public void setResponse(HttpResponse resp) {
        response = resp;
    }
    
    public HttpRequest getCapturedRequest() {
        return request;
    }

    public HttpResponse execute(HttpUriRequest request) throws IOException,
            ClientProtocolException {
        this.request = request;
        return response;
    }

    public HttpResponse execute(HttpUriRequest request, HttpContext context)
            throws IOException, ClientProtocolException {
        this.request = request;
        return response;
    }

    public HttpResponse execute(HttpHost target, HttpRequest request)
            throws IOException, ClientProtocolException {
        this.request = request;
        return response;
    }

    public HttpResponse execute(HttpHost target, HttpRequest request,
            HttpContext context) throws IOException, ClientProtocolException {
        this.request = request;
        return response;
    }

    public <T> T execute(HttpUriRequest request,
            ResponseHandler<? extends T> responseHandler) throws IOException,
            ClientProtocolException {
        this.request = request;
        return responseHandler.handleResponse(response);
    }

    public <T> T execute(HttpUriRequest request,
            ResponseHandler<? extends T> responseHandler, HttpContext context)
            throws IOException, ClientProtocolException {
        this.request = request;
        return responseHandler.handleResponse(response);
    }

    public <T> T execute(HttpHost target, HttpRequest request,
            ResponseHandler<? extends T> responseHandler) throws IOException,
            ClientProtocolException {
        this.request = request;
        return responseHandler.handleResponse(response);
    }

    public <T> T execute(HttpHost target, HttpRequest request,
            ResponseHandler<? extends T> responseHandler, HttpContext context)
            throws IOException, ClientProtocolException {
        this.request = request;
        return responseHandler.handleResponse(response);
    }

}
