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
package org.apache.http.client.async;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.client.HttpClients;

public class ClientAsyncWithFuture {
    public static void main(String[] args) {
        // the simplest way to create a HttpAsyncClientWithFuture
        HttpAsyncClientWithFuture client = HttpClients.createAsync(3);

        // Because things are asynchronous, you must provide a ResponseHandler
        ResponseHandler<Boolean> handler = new ResponseHandler<Boolean>() {
            public Boolean handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
                // simply return true if the status was OK
                return response.getStatusLine().getStatusCode() == 200;
            }
        };

        // Simple request ...
        try {
            HttpGet request = new HttpGet("http://google.com");
            HttpAsyncClientFutureTask<Boolean> futureTask = client.execute(request, handler);
            Boolean wasItOk = futureTask.get();
            System.out.println("It was ok? "  + wasItOk);
        } catch (InterruptedException e) {
            // Threads may be interrupted
            e.printStackTrace();
        } catch (ExecutionException e) {
            // if something went wrong, there will be an exception wrapped by an ExecutionException
            e.printStackTrace();
        }

        // Cancel a request
        try {
            HttpGet request = new HttpGet("http://google.com");
            HttpAsyncClientFutureTask<Boolean> futureTask = client.execute(request, handler);
            futureTask.cancel(true);
            Boolean wasItOk = futureTask.get();
            System.out.println("It was cancelled so it should never print this: " + wasItOk);
        } catch (InterruptedException e) {
            // Threads may be interrupted
            e.printStackTrace();
        } catch (CancellationException e) {
            System.out.println("We cancelled it, so this is expected");
        } catch (ExecutionException e) {
           // if something went wrong, there will be an exception wrapped by an ExecutionException
            e.printStackTrace();
        }

        // Request with a timeout
        try {
            HttpGet request = new HttpGet("http://google.com");
            HttpAsyncClientFutureTask<Boolean> futureTask = client.execute(request, handler);
            Boolean wasItOk = futureTask.get(10, TimeUnit.SECONDS);
            System.out.println("It was ok? "  + wasItOk);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (TimeoutException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        FutureCallback<Boolean> callback = new FutureCallback<Boolean>() {
            public void completed(Boolean result) {
                System.out.println("completed with " + result);
            }

            public void failed(Exception ex) {
                System.out.println("failed with " + ex.getMessage());
            }

            public void cancelled() {
                System.out.println("cancelled");
            }
        };

        // Simple request with a callback
        try {
            HttpGet request = new HttpGet("http://google.com");
            // using a null HttpContext here since it is optional
            // the callback will be called when the task completes, fails, or is cancelled
            HttpAsyncClientFutureTask<Boolean> futureTask = client.execute(request, null, handler, callback);
            Boolean wasItOk = futureTask.get(10, TimeUnit.SECONDS);
            System.out.println("It was ok? "  + wasItOk);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (TimeoutException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        // Multiple requests, with a callback
        try {
            HttpGet request1 = new HttpGet("http://google.com");
            HttpGet request2 = new HttpGet("http://bing.com");
            HttpGet request3 = new HttpGet("http://yahoo.com");
            // using a null HttpContext here since it is optional
            // the callback will be called for each request as their responses come back.
            List<Future<Boolean>> futureTask = client.executeMultiple(null, handler, callback,20, TimeUnit.SECONDS, request1,request2,request3);
            // you can still access the futures directly, if you want. The futures are in the same order as the requests.
            for (Future<Boolean> future : futureTask) {
                System.out.println("another result " + future.get());
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }
}