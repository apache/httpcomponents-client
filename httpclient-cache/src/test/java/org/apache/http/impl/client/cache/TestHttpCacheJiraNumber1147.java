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

import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Date;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.cache.HttpCacheStorage;
import org.apache.http.client.cache.ResourceFactory;
import org.apache.http.client.methods.HttpExecutionAware;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.DateUtils;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.impl.execchain.ClientExecChain;
import org.apache.http.message.BasicHttpResponse;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;

public class TestHttpCacheJiraNumber1147 {

    private File cacheDir;

    private void removeCache() {
        if (this.cacheDir != null) {
            final File[] files = this.cacheDir.listFiles();
            for (final File cacheFile : files) {
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
        final CacheConfig cacheConfig = CacheConfig.custom()
            .setSharedCache(true)
            .setMaxObjectSize(262144) //256kb
            .build();

        final ResourceFactory resourceFactory = new FileResourceFactory(cacheDir);
        final HttpCacheStorage httpCacheStorage = new ManagedHttpCacheStorage(cacheConfig);

        final ClientExecChain backend = mock(ClientExecChain.class);
        final HttpRequestWrapper get = HttpRequestWrapper.wrap(new HttpGet("http://somehost/"));
        final HttpClientContext context = HttpClientContext.create();
        final HttpHost target = new HttpHost("somehost", 80);
        final HttpRoute route = new HttpRoute(target);

        context.setTargetHost(target);

        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.setEntity(HttpTestUtils.makeBody(128));
        response.setHeader("Content-Length", "128");
        response.setHeader("ETag", "\"etag\"");
        response.setHeader("Cache-Control", "public, max-age=3600");
        response.setHeader("Last-Modified", DateUtils.formatDate(tenSecondsAgo));

        when(backend.execute(
                eq(route),
                isA(HttpRequestWrapper.class),
                isA(HttpClientContext.class),
                (HttpExecutionAware) Matchers.isNull())).thenReturn(Proxies.enhanceResponse(response));

        final BasicHttpCache cache = new BasicHttpCache(resourceFactory, httpCacheStorage, cacheConfig);
        final ClientExecChain t = createCachingExecChain(backend, cache, cacheConfig);

        final HttpResponse response1 = t.execute(route, get, context, null);
        Assert.assertEquals(200, response1.getStatusLine().getStatusCode());
        IOUtils.consume(response1.getEntity());

        verify(backend).execute(
                eq(route),
                isA(HttpRequestWrapper.class),
                isA(HttpClientContext.class),
                (HttpExecutionAware) Matchers.isNull());

        removeCache();

        reset(backend);
        when(backend.execute(
                eq(route),
                isA(HttpRequestWrapper.class),
                isA(HttpClientContext.class),
                (HttpExecutionAware) Matchers.isNull())).thenReturn(Proxies.enhanceResponse(response));

        final HttpResponse response2 = t.execute(route, get, context, null);
        Assert.assertEquals(200, response2.getStatusLine().getStatusCode());
        IOUtils.consume(response2.getEntity());

        verify(backend).execute(
                eq(route),
                isA(HttpRequestWrapper.class),
                isA(HttpClientContext.class),
                (HttpExecutionAware) Matchers.isNull());
    }

    protected ClientExecChain createCachingExecChain(final ClientExecChain backend,
            final BasicHttpCache cache, final CacheConfig config) {
        return new CachingExec(backend, cache, config);
    }

}
