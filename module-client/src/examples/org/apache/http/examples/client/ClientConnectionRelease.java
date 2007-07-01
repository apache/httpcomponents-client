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

import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

/**
 * This examples demonstrates the recommended way of using API to make sure 
 * the underlying connection gets released back to the connection manager.
 *
 * <!-- empty lines above to avoid 'svn diff' context problems -->
 * @version $Revision$
 *
 * @since 4.0
 */
public class ClientConnectionRelease {

    public final static void main(String[] args) throws Exception {
        HttpClient httpclient = new DefaultHttpClient();

        HttpGet httpget = new HttpGet("http://www.apache.org/"); 

        // Execute HTTP request
        System.out.println("executing request " + httpget.getURI());
        HttpResponse response = httpclient.execute(httpget);

        System.out.println("----------------------------------------");
        System.out.println(response.getStatusLine());
        System.out.println("----------------------------------------");

        // Get hold of the response entity
        HttpEntity entity = response.getEntity();
        
        // If the response does not enclose an entity, there is no need
        // to bother about connection release
        if (entity != null) {
            // do something useful with the response
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(entity.getContent()));
            System.out.println(reader.readLine());
            // In case of an IOException the connection will be released
            // back to the connection manager automatically
            // No need for ugly try-finally clause
            
            // If a runtime exception can be thrown in the process
            // of response processing make sure the HTTP request 
            // gets aborted to ensure connection release.
            try {
                // Do something that can throw a non-fatal 
                // unchecked exception
            } catch (RuntimeException ex) {
                // Abort HTTP request to ensure connection release
                httpget.abort();
            }
            
            // When done with the response either close the stream
            reader.close();
            // Or call
            entity.consumeContent();
            // Either of these methods will cause connection release 
        }
    }

}

