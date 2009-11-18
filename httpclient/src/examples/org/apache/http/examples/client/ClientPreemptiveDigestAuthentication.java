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
 */

package org.apache.http.examples.client;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.impl.auth.DigestScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;

/**
 * An example of HttpClient can be customized to authenticate 
 * preemptively using DIGEST scheme.
 * <b/>
 * Generally, preemptive authentication can be considered less
 * secure than a response to an authentication challenge
 * and therefore discouraged.
 */
public class ClientPreemptiveDigestAuthentication {

    public static void main(String[] args) throws Exception {

        HttpHost targetHost = new HttpHost("localhost", 80, "http"); 

        DefaultHttpClient httpclient = new DefaultHttpClient();

        httpclient.getCredentialsProvider().setCredentials(
                new AuthScope(targetHost.getHostName(), targetHost.getPort()), 
                new UsernamePasswordCredentials("username", "password"));

        // Create AuthCache instance
        AuthCache authCache = new BasicAuthCache();
        // Generate DIGEST scheme object, initialize it and add it to the local 
        // auth cache
        DigestScheme digestAuth = new DigestScheme();
        // Suppose we already know the realm name
        digestAuth.overrideParamter("realm", "some realm");        
        // Suppose we already know the expected nonce value 
        digestAuth.overrideParamter("nonce", "whatever");        
        authCache.put(targetHost, digestAuth);
        
        // Add AuthCache to the execution context
        BasicHttpContext localcontext = new BasicHttpContext();
        localcontext.setAttribute(ClientContext.AUTH_CACHE, authCache);        
        
        HttpGet httpget = new HttpGet("/");

        System.out.println("executing request: " + httpget.getRequestLine());
        System.out.println("to target: " + targetHost);
        
        for (int i = 0; i < 3; i++) {
            HttpResponse response = httpclient.execute(targetHost, httpget, localcontext);
            HttpEntity entity = response.getEntity();

            System.out.println("----------------------------------------");
            System.out.println(response.getStatusLine());
            if (entity != null) {
                System.out.println("Response content length: " + entity.getContentLength());
                entity.consumeContent();
            }
        }
        
        // When HttpClient instance is no longer needed, 
        // shut down the connection manager to ensure
        // immediate deallocation of all system resources
        httpclient.getConnectionManager().shutdown();        
    }
    
}
