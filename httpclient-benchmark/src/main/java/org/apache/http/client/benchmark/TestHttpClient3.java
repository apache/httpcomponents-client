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

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpVersion;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.params.HttpMethodParams;

public class TestHttpClient3 implements TestHttpAgent {

    private final HttpClient httpclient;
    
    public TestHttpClient3() {
        super();
        this.httpclient = new HttpClient();
        this.httpclient.getParams().setVersion(
                HttpVersion.HTTP_1_1);
        this.httpclient.getParams().setBooleanParameter(
                HttpMethodParams.USE_EXPECT_CONTINUE, false);
        this.httpclient.getHttpConnectionManager().getParams()
                .setStaleCheckingEnabled(false);
    }

    public void init() {
    }

    public Stats execute(final HttpMethod httpmethod, int n) throws Exception {

        Stats stats = new Stats();
        
        int successCount = 0;
        int failureCount = 0;
        long contentLen = 0;
        long totalContentLen = 0;
        
        byte[] buffer = new byte[4096];
        
        for (int i = 0; i < n; i++) {
            try {
                this.httpclient.executeMethod(httpmethod);
                InputStream instream = httpmethod.getResponseBodyAsStream();
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
                failureCount++;
            } finally {
                httpmethod.releaseConnection();
            }
        }
        Header header = httpmethod.getResponseHeader("Server");
        if (header != null) {
            stats.setServerName(header.getValue());
        }

        stats.setSuccessCount(successCount);
        stats.setFailureCount(failureCount);
        stats.setContentLen(contentLen);
        stats.setTotalContentLen(totalContentLen);
        return stats;
    }
    
    public Stats get(final URI target, int n) throws Exception {
        GetMethod httpget = new GetMethod(target.toASCIIString());
        return execute(httpget, n);
    }

    public Stats post(URI target, byte[] content, int n) throws Exception {
        PostMethod httppost = new PostMethod(target.toASCIIString());
        httppost.setRequestEntity(new ByteArrayRequestEntity(content));
        return execute(httppost, n);
    }

    public String getClientName() {
        return "Apache HttpClient 3.1";
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: <target URI> <no of requests>");
            System.exit(-1);
        }
        URI targetURI = new URI(args[0]);
        int n = Integer.parseInt(args[1]);
        
        TestHttpClient3 test = new TestHttpClient3(); 
        
        long startTime = System.currentTimeMillis();
        Stats stats = test.get(targetURI, n);
        long finishTime = System.currentTimeMillis();
       
        Stats.printStats(targetURI, startTime, finishTime, stats);
    }

}
