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
package org.apache.http.client.fluent;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.http.concurrent.FutureCallback;

/**
 * This example demonstrates how the he HttpClient fluent API can be used to execute multiple
 * requests asynchronously using background threads.
 */
public class FluentAsync {

    public static void main(String[] args)throws Exception {
        // Use pool of two threads
        ExecutorService threadpool = Executors.newFixedThreadPool(2);
        Async async = Async.newInstance().use(threadpool);

        Request[] requests = new Request[] {
                Request.Get("http://www.google.com/"),
                Request.Get("http://www.yahoo.com/"),
                Request.Get("http://www.apache.com/"),
                Request.Get("http://www.apple.com/")
        };


        Queue<Future<Content>> queue = new LinkedList<Future<Content>>();
        // Execute requests asynchronously
        for (final Request request: requests) {
            Future<Content> future = async.execute(request, new FutureCallback<Content>() {

                public void failed(final Exception ex) {
                    System.out.println(ex.getMessage() + ": " + request);
                }

                public void completed(final Content content) {
                    System.out.println("Request completed: " + request);
                }

                public void cancelled() {
                }

            });
            queue.add(future);
        }

        while(!queue.isEmpty()) {
            Future<Content> future = queue.remove();
            try {
                future.get();
            } catch (ExecutionException ex) {
            }
        }
        System.out.println("Done");
        threadpool.shutdown();
    }

}
