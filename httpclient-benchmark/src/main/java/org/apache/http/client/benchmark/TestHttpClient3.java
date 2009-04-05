package org.apache.http.client.benchmark;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpVersion;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.params.HttpMethodParams;

public class TestHttpClient3 {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: <target URI> <no of requests>");
            System.exit(-1);
        }
        String targetURI = args[0];
        int n = Integer.parseInt(args[1]);
        
        HttpClient httpclient = new HttpClient();
        httpclient.getParams().setVersion(
                HttpVersion.HTTP_1_1);
        httpclient.getParams().setBooleanParameter(
                HttpMethodParams.USE_EXPECT_CONTINUE, false);
        httpclient.getHttpConnectionManager().getParams()
                .setStaleCheckingEnabled(false);
        
        GetMethod httpget = new GetMethod(targetURI);

        byte[] buffer = new byte[4096];
        
        long startTime;
        long finishTime;
        int successCount = 0;
        int failureCount = 0;
        String serverName = "unknown";
        long total = 0;
        long contentLen = 0;
        long totalContentLen = 0;
        
        startTime = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            try {
                httpclient.executeMethod(httpget);
                InputStream instream = httpget.getResponseBodyAsStream();
                contentLen = 0;
                if (instream != null) {
                    int l = 0;
                    while ((l = instream.read(buffer)) != -1) {
                        total += l;
                        contentLen += l;
                    }
                }
                successCount++;
                totalContentLen += contentLen;
            } catch (IOException ex) {
                failureCount++;
            } finally {
                httpget.releaseConnection();
            }
        }
        finishTime = System.currentTimeMillis();
        
        Header header = httpget.getResponseHeader("Server");
        if (header != null) {
            serverName = header.getValue();
        }
        
        float totalTimeSec = (float) (finishTime - startTime) / 1000;
        float reqsPerSec = (float) successCount / totalTimeSec; 
        float timePerReqMs = (float) (finishTime - startTime) / (float) successCount; 
        
        System.out.print("Server Software:\t");
        System.out.println(serverName);
        System.out.println();
        System.out.print("Document URI:\t\t");
        System.out.println(targetURI);
        System.out.print("Document Length:\t");
        System.out.print(contentLen);
        System.out.println(" bytes");
        System.out.println();
        System.out.print("Time taken for tests:\t");
        System.out.print(totalTimeSec);
        System.out.println(" seconds");
        System.out.print("Complete requests:\t");
        System.out.println(successCount);
        System.out.print("Failed requests:\t");
        System.out.println(failureCount);
        System.out.print("Content transferred:\t");
        System.out.print(total);
        System.out.println(" bytes");
        System.out.print("Requests per second:\t");
        System.out.print(reqsPerSec);
        System.out.println(" [#/sec] (mean)");
        System.out.print("Time per request:\t");
        System.out.print(timePerReqMs);
        System.out.println(" [ms] (mean)");
    }

}
