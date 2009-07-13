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

package org.apache.http.examples.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

/**
 * This example demonstrates the recommended way of using API to make sure 
 * the underlying connection gets released back to the connection manager.
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
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(entity.getContent()));
            try {
                
                // do something useful with the response
                System.out.println(reader.readLine());
                
            } catch (IOException ex) {

                // In case of an IOException the connection will be released
                // back to the connection manager automatically
                throw ex;
                
            } catch (RuntimeException ex) {

                // In case of an unexpected exception you may want to abort
                // the HTTP request in order to shut down the underlying 
                // connection and release it back to the connection manager.
                httpget.abort();
                throw ex;
                
            } finally {

                // Closing the input stream will trigger connection release
                reader.close();
                
            }
        }

        // When HttpClient instance is no longer needed, 
        // shut down the connection manager to ensure
        // immediate deallocation of all system resources
        httpclient.getConnectionManager().shutdown();        
    }

}

