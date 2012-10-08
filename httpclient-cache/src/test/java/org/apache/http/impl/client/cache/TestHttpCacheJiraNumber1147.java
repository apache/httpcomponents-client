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
package org.apache.http.impl.client.cache;

import java.io.File;
import java.util.Date;

import junit.framework.Assert;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.cache.HttpCacheStorage;
import org.apache.http.client.cache.ResourceFactory;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.cache.CacheConfig;
import org.apache.http.impl.client.cache.CachingHttpClient;
import org.apache.http.impl.client.cache.FileResourceFactory;
import org.apache.http.impl.client.cache.ManagedHttpCacheStorage;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestHttpCacheJiraNumber1147 {

    private File cacheDir;

    private void removeCache() {
        if (this.cacheDir != null) {
            File[] files = this.cacheDir.listFiles();
            for (File cacheFile : files) {
                cacheFile.delete();
            }
            this.cacheDir.delete();
            this.cacheDir = null;
        }
    }

    @Before
    public void setUp() throws Exception {
        cacheDir = File.createTempFile("cachedir", "");
        if (cacheDir.exists()) {
            cacheDir.delete();
        }
        cacheDir.mkdir();
    }
    
    @After
    public void cleanUp() {
        removeCache();
    }
    
    @Test
    public void testIssue1147() throws Exception {
        CacheConfig cacheConfig = new CacheConfig();
        cacheConfig.setSharedCache(true);
        cacheConfig.setMaxObjectSize(262144); //256kb

        ResourceFactory resourceFactory = new FileResourceFactory(cacheDir);
        HttpCacheStorage httpCacheStorage = new ManagedHttpCacheStorage(cacheConfig);

        HttpClient client = EasyMock.createNiceMock(HttpClient.class);
        HttpGet get = new HttpGet("http://somehost/");
        HttpContext context = new BasicHttpContext();
        HttpHost target = new HttpHost("somehost");

        Date now = new Date();
        Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);
        
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.setEntity(HttpTestUtils.makeBody(128));
        response.setHeader("Content-Length", "128");
        response.setHeader("ETag", "\"etag\"");
        response.setHeader("Cache-Control", "public, max-age=3600");
        response.setHeader("Last-Modified", DateUtils.formatDate(tenSecondsAgo));
        
        EasyMock.expect(client.execute(
                EasyMock.eq(target),
                EasyMock.isA(HttpRequest.class),
                EasyMock.same(context))).andReturn(response);
        EasyMock.replay(client);
        
        CachingHttpClient t = new CachingHttpClient(client, resourceFactory, httpCacheStorage, cacheConfig);

        HttpResponse response1 = t.execute(get, context);
        Assert.assertEquals(200, response1.getStatusLine().getStatusCode());
        EntityUtils.consume(response1.getEntity());
        
        EasyMock.verify(client);
        
        removeCache();

        EasyMock.reset(client);
        EasyMock.expect(client.execute(
                EasyMock.eq(target),
                EasyMock.isA(HttpRequest.class),
                EasyMock.same(context))).andReturn(response);
        EasyMock.replay(client);
        
        HttpResponse response2 = t.execute(get, context);
        Assert.assertEquals(200, response2.getStatusLine().getStatusCode());
        EntityUtils.consume(response2.getEntity());

        EasyMock.verify(client);
    }

}
