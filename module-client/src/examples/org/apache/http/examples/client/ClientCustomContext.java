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

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpState;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.BasicHttpContext;


/**
 * This example demonstrates the use of a local HTTP context populated with 
 * custom attributes.
 */
public class ClientCustomContext {

    public final static void main(String[] args) throws Exception {
        
        HttpClient httpclient = new DefaultHttpClient();

        // Create a local instance of HttpState
        HttpState localState = new HttpState();
        
        // Obtain default HTTP context
        HttpContext defaultContext = httpclient.getDefaultContext();
        // Create local HTTP context
        HttpContext localContext = new BasicHttpContext(defaultContext);
        // Bind custom HTTP state to the local context
        localContext.setAttribute(ClientContext.HTTP_STATE, localState);
        
        HttpGet httpget = new HttpGet("http://www.google.com/"); 

        System.out.println("executing request " + httpget.getURI());

        // Pass local context as a parameter
        HttpResponse response = httpclient.execute(httpget, localContext);
        HttpEntity entity = response.getEntity();

        System.out.println("----------------------------------------");
        System.out.println(response.getStatusLine());
        if (entity != null) {
            System.out.println("Response content length: " + entity.getContentLength());
            System.out.println("Chunked?: " + entity.isChunked());
        }
        Cookie[] cookies = localState.getCookies();
        for (int i = 0; i < cookies.length; i++) {
            System.out.println("Local cookie: " + cookies[i]);
        }
        
        // Consume response content
        entity.consumeContent();
        
        System.out.println("----------------------------------------");
    }
    
}

