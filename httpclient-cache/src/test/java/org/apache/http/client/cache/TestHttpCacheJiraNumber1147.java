package org.apache.http.client.cache;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.cache.CacheConfig;
import org.apache.http.impl.client.cache.CachingHttpClient;
import org.apache.http.impl.client.cache.FileResourceFactory;
import org.apache.http.impl.client.cache.ManagedHttpCacheStorage;

import org.junit.Test;

import java.io.File;

public class TestHttpCacheJiraNumber1147 {
    final String cacheDir = "/tmp/cachedir";
    HttpClient cachingHttpClient;
    HttpClient client = new DefaultHttpClient();

    @Test
    public void testIssue1147() throws Exception {
        final CacheConfig cacheConfig = new CacheConfig();
        cacheConfig.setSharedCache(true);
        cacheConfig.setMaxObjectSize(262144); //256kb

        new File(cacheDir).mkdir();

        if(! new File(cacheDir, "httpclient-cache").exists()){
            if(!new File(cacheDir, "httpclient-cache").mkdir()){
                throw new RuntimeException("failed to create httpclient cache directory: " +
                        new File(cacheDir, "httpclient-cache").getAbsolutePath());
            }
        }

        final ResourceFactory resourceFactory = new FileResourceFactory(new File(cacheDir, "httpclient-cache"));

        final HttpCacheStorage httpCacheStorage = new ManagedHttpCacheStorage(cacheConfig);

        cachingHttpClient = new CachingHttpClient(client, resourceFactory, httpCacheStorage, cacheConfig);

        final HttpGet get = new HttpGet("http://www.apache.org/js/jquery.js");

        System.out.println("Calling URL First time.");
        executeCall(get);

        removeDirectory(cacheDir);

        System.out.println("Calling URL Second time.");
        executeCall(get);
    }

    private void removeDirectory(String cacheDir) {
        File theDirectory = new File(cacheDir, "httpclient-cache");
        File[] files = theDirectory.listFiles();

        for (File cacheFile : files) {
            cacheFile.delete();
        }

        theDirectory.delete();

        new File(cacheDir).delete();
    }

    private void executeCall(HttpGet get) throws Exception {
        final HttpResponse response = cachingHttpClient.execute(get);
        final StatusLine statusLine = response.getStatusLine();
        System.out.println("Status Code: " + statusLine.getStatusCode());

        if (statusLine.getStatusCode() >= 300) {
            if(statusLine.getStatusCode() == 404)
                throw new NoResultException();

            throw new HttpResponseException(statusLine.getStatusCode(), statusLine.getReasonPhrase());
        }
        response.getEntity().getContent();
    }

    private class NoResultException extends Exception {

        private static final long serialVersionUID = 1277878788978491946L;

    }
}
