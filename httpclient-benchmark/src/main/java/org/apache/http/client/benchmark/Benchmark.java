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
package org.apache.http.client.benchmark;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URI;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.bio.SocketConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.ByteArrayOutputStream2;
import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

public class Benchmark {

   public static void main(String[] args) throws Exception {

       String ns = System.getProperty("hc.benchmark.n-requests", "200000");
       String nc = System.getProperty("hc.benchmark.concurrent", "100");
       String cls = System.getProperty("hc.benchmark.content-len", "2048");

       int n = Integer.parseInt(ns);
       int c = Integer.parseInt(nc);
       int contentLen = Integer.parseInt(cls);

       SocketConnector connector = new SocketConnector();
       connector.setPort(0);
       connector.setRequestBufferSize(12 * 1024);
       connector.setResponseBufferSize(12 * 1024);
       connector.setAcceptors(2);
       connector.setAcceptQueueSize(c);

       QueuedThreadPool threadpool = new QueuedThreadPool();
       threadpool.setMinThreads(c);
       threadpool.setMaxThreads(2000);

       Server server = new Server();
       server.addConnector(connector);
       server.setThreadPool(threadpool);
       server.setHandler(new RandomDataHandler());

       server.start();
       int port = connector.getLocalPort();

       // Sleep a little
       Thread.sleep(2000);

       TestHttpAgent[] agents = new TestHttpAgent[] {
               new TestHttpClient3(),
               new TestHttpJRE(),
               new TestHttpCore(),
               new TestHttpClient4(),
               new TestJettyHttpClient(),
               new TestNingHttpClient()
       };

       byte[] content = new byte[contentLen];
       int r = Math.abs(content.hashCode());
       for (int i = 0; i < content.length; i++) {
           content[i] = (byte) ((r + i) % 96 + 32);
       }

       URI target1 = new URI("http", null, "localhost", port, "/rnd", "c=" + contentLen, null);
       URI target2 = new URI("http", null, "localhost", port, "/echo", null, null);

       try {
           for (TestHttpAgent agent: agents) {
               agent.init();
               try {
                   System.out.println("=================================");
                   System.out.println("HTTP agent: " + agent.getClientName());
                   System.out.println("---------------------------------");
                   System.out.println(n + " GET requests");
                   System.out.println("---------------------------------");

                   long startTime1 = System.currentTimeMillis();
                   Stats stats1 = agent.get(target1, n, c);
                   long finishTime1 = System.currentTimeMillis();
                   Stats.printStats(target1, startTime1, finishTime1, stats1);
                   System.out.println("---------------------------------");
                   System.out.println(n + " POST requests");
                   System.out.println("---------------------------------");

                   long startTime2 = System.currentTimeMillis();
                   Stats stats2 = agent.post(target2, content, n, c);
                   long finishTime2 = System.currentTimeMillis();
                   Stats.printStats(target2, startTime2, finishTime2, stats2);
               } finally {
                   agent.shutdown();
               }
               agent.init();
               System.out.println("---------------------------------");
           }
       } finally {
           server.stop();
       }
       server.join();
    }

   static class RandomDataHandler extends AbstractHandler {

       public RandomDataHandler() {
           super();
       }

       public void handle(
               final String target,
               final Request baseRequest,
               final HttpServletRequest request,
               final HttpServletResponse response) throws IOException, ServletException {
           if (target.equals("/rnd")) {
               rnd(request, response);
           } else if (target.equals("/echo")) {
               echo(request, response);
           } else {
               response.setStatus(HttpStatus.NOT_FOUND_404);
               Writer writer = response.getWriter();
               writer.write("Target not found: " + target);
               writer.flush();
           }
       }

       private void rnd(
               final HttpServletRequest request,
               final HttpServletResponse response) throws IOException, ServletException {
           int count = 100;
           String s = request.getParameter("c");
           try {
               count = Integer.parseInt(s);
           } catch (NumberFormatException ex) {
               response.setStatus(500);
               Writer writer = response.getWriter();
               writer.write("Invalid query format: " + request.getQueryString());
               writer.flush();
               return;
           }

           response.setStatus(200);
           response.setContentLength(count);

           OutputStream outstream = response.getOutputStream();
           byte[] tmp = new byte[1024];
           int r = Math.abs(tmp.hashCode());
           int remaining = count;
           while (remaining > 0) {
               int chunk = Math.min(tmp.length, remaining);
               for (int i = 0; i < chunk; i++) {
                   tmp[i] = (byte) ((r + i) % 96 + 32);
               }
               outstream.write(tmp, 0, chunk);
               remaining -= chunk;
           }
           outstream.flush();
       }

       private void echo(
               final HttpServletRequest request,
               final HttpServletResponse response) throws IOException, ServletException {

           ByteArrayOutputStream2 buffer = new ByteArrayOutputStream2();
           InputStream instream = request.getInputStream();
           if (instream != null) {
               IO.copy(instream, buffer);
               buffer.flush();
           }
           byte[] content = buffer.getBuf();

           response.setStatus(200);
           response.setContentLength(content.length);

           OutputStream outstream = response.getOutputStream();
           outstream.write(content);
           outstream.flush();
       }

   }

}

