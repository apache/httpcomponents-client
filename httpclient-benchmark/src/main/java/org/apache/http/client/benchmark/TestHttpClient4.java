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
import java.net.URI;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.params.SyncBasicHttpParams;
import org.apache.http.util.VersionInfo;

public class TestHttpClient4 implements TestHttpAgent {

    private final HttpClient httpclient;
    
    public TestHttpClient4() {
        super();
        HttpParams params = new SyncBasicHttpParams();
        params.setParameter(HttpProtocolParams.PROTOCOL_VERSION,
                HttpVersion.HTTP_1_1);
        params.setBooleanParameter(HttpProtocolParams.USE_EXPECT_CONTINUE,
                false);
        params.setBooleanParameter(HttpConnectionParams.STALE_CONNECTION_CHECK,
                false);
        params.setIntParameter(HttpConnectionParams.SOCKET_BUFFER_SIZE,
                8 * 1024);
       
        this.httpclient = new DefaultHttpClient(params);
    }

    public Stats execute(final HttpUriRequest request, int n) throws Exception {
        Stats stats = new Stats();
        
        int successCount = 0;
        int failureCount = 0;
        long contentLen = 0;
        long totalContentLen = 0;
        
        byte[] buffer = new byte[4096];
        
        for (int i = 0; i < n; i++) {
            HttpResponse response = this.httpclient.execute(request);
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
                    request.abort();
                    failureCount++;
                } finally {
                    instream.close();
                }
            }
            Header header = response.getFirstHeader("Server");
            if (header != null) {
                stats.setServerName(header.getValue());
            }
        }
        stats.setSuccessCount(successCount);
        stats.setFailureCount(failureCount);
        stats.setContentLen(contentLen);
        stats.setTotalContentLen(totalContentLen);
        return stats;
    }    
    
    public Stats get(final URI target, int n) throws Exception {
        HttpGet httpget = new HttpGet(target);
        return execute(httpget, n);
    }
    
    public Stats post(final URI target, byte[] content, int n) throws Exception {
        HttpPost httppost = new HttpPost(target);
        httppost.setEntity(new ByteArrayEntity(content));
        return execute(httppost, n);
    }
    
    public String getClientName() {
        VersionInfo vinfo = VersionInfo.loadVersionInfo("org.apache.http.client", 
                Thread.currentThread().getContextClassLoader());
        return "Apache HttpClient 4 (ver: " + 
            ((vinfo != null) ? vinfo.getRelease() : VersionInfo.UNAVAILABLE) + ")";
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: <target URI> <no of requests>");
            System.exit(-1);
        }
        URI targetURI = new URI(args[0]);
        int n = Integer.parseInt(args[1]);
        
        TestHttpClient4 test = new TestHttpClient4(); 
        
        long startTime = System.currentTimeMillis();
        Stats stats = test.get(targetURI, n);
        long finishTime = System.currentTimeMillis();
       
        Stats.printStats(targetURI, startTime, finishTime, stats);
    }
   
} 