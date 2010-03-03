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
import java.net.Socket;
import java.net.URI;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpClientConnection;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.params.SyncBasicHttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.protocol.ImmutableHttpProcessor;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestContent;
import org.apache.http.protocol.RequestTargetHost;
import org.apache.http.protocol.RequestUserAgent;
import org.apache.http.util.VersionInfo;

public class TestHttpCore implements TestHttpAgent {

    private final HttpParams params;
    private final HttpProcessor httpproc;
    private final HttpRequestExecutor httpexecutor;
    private final ConnectionReuseStrategy connStrategy;
    
    public TestHttpCore() {
        super();
        this.params = new SyncBasicHttpParams();
        this.params.setParameter(HttpProtocolParams.PROTOCOL_VERSION,
                HttpVersion.HTTP_1_1);
        this.params.setBooleanParameter(HttpProtocolParams.USE_EXPECT_CONTINUE,
                false);
        this.params.setBooleanParameter(HttpConnectionParams.STALE_CONNECTION_CHECK,
                false);
        this.params.setIntParameter(HttpConnectionParams.SOCKET_BUFFER_SIZE,
                8 * 1024);
        
        this.httpproc = new ImmutableHttpProcessor(new HttpRequestInterceptor[] {
                new RequestContent(),
                new RequestTargetHost(),
                new RequestConnControl(),
                new RequestUserAgent()
                
        }, null);

        this.httpexecutor = new HttpRequestExecutor();
        this.connStrategy = new DefaultConnectionReuseStrategy();
    }
    
    public Stats execute(
            final HttpHost targetHost, final HttpRequest request, int n) throws Exception {
        
        Stats stats = new Stats();
        
        int successCount = 0;
        int failureCount = 0;
        long contentLen = 0;
        long totalContentLen = 0;
        
        byte[] buffer = new byte[4096];
        HttpContext context = new BasicHttpContext();

        DefaultHttpClientConnection conn = new DefaultHttpClientConnection();
        for (int i = 0; i < n; i++) {
            if (!conn.isOpen()) {
                Socket socket = new Socket(
                        targetHost.getHostName(),
                        targetHost.getPort() > 0 ? targetHost.getPort() : 80);
                conn.bind(socket, this.params);
            }

            context.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);
            context.setAttribute(ExecutionContext.HTTP_TARGET_HOST, targetHost);

            this.httpexecutor.preProcess(request, this.httpproc, context);
            HttpResponse response = this.httpexecutor.execute(request, conn, context);
            this.httpexecutor.postProcess(response, this.httpproc, context);
           
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                InputStream instream = entity.getContent();
                try {
                    contentLen = 0;
                    if (instream != null) {
                        int l = 0;
                        while ((l = instream.read(buffer)) != -1) {
                            contentLen += l;
                        }
                    }
                    successCount++;
                    totalContentLen += contentLen;
                } catch (IOException ex) {
                    conn.shutdown();
                    failureCount++;
                } finally {
                    instream.close();
                }
            }
            if (!this.connStrategy.keepAlive(response, context)) {
                conn.close();
            }
            Header header = response.getFirstHeader("Server");
            if (header != null) {
                stats.setServerName(header.getValue());
            }
            for (HeaderIterator it = request.headerIterator(); it.hasNext();) {
                it.next();
                it.remove();
            }
        }
        stats.setSuccessCount(successCount);
        stats.setFailureCount(failureCount);
        stats.setContentLen(contentLen);
        stats.setTotalContentLen(totalContentLen);
        return stats;
    }
    
    public Stats get(final URI target, int n) throws Exception {
        HttpHost targetHost = new HttpHost(target.getHost(), target.getPort());
        StringBuilder buffer = new StringBuilder();
        buffer.append(target.getPath());
        if (target.getQuery() != null) {
            buffer.append("?");
            buffer.append(target.getQuery());
        }
        BasicHttpRequest httpget = new BasicHttpRequest("GET", buffer.toString());
        return execute(targetHost, httpget, n);
    }
    
    public Stats post(final URI target, byte[] content, int n) throws Exception {
        HttpHost targetHost = new HttpHost(target.getHost(), target.getPort());
        StringBuilder buffer = new StringBuilder();
        buffer.append(target.getPath());
        if (target.getQuery() != null) {
            buffer.append("?");
            buffer.append(target.getQuery());
        }
        BasicHttpEntityEnclosingRequest httppost = new BasicHttpEntityEnclosingRequest("POST", 
                buffer.toString());
        httppost.setEntity(new ByteArrayEntity(content));
        return execute(targetHost, httppost, n);
    }
    
    public String getClientName() {
        VersionInfo vinfo = VersionInfo.loadVersionInfo("org.apache.http", 
                Thread.currentThread().getContextClassLoader());
        return "Apache HttpCore 4 (ver: " + 
            ((vinfo != null) ? vinfo.getRelease() : VersionInfo.UNAVAILABLE) + ")";
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: <target URI> <no of requests>");
            System.exit(-1);
        }
        URI targetURI = new URI(args[0]);
        int n = Integer.parseInt(args[1]);

        TestHttpCore test = new TestHttpCore(); 
        
        long startTime = System.currentTimeMillis();
        Stats stats = test.get(targetURI, n);
        long finishTime = System.currentTimeMillis();
       
        Stats.printStats(targetURI, startTime, finishTime, stats);
    }
   
}