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
package org.apache.hc.client5.http.impl.cache;

import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Date;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.cache.HttpCacheStorage;
import org.apache.hc.client5.http.cache.ResourceFactory;
import org.apache.hc.client5.http.impl.sync.ClientExecChain;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.sync.methods.HttpExecutionAware;
import org.apache.hc.client5.http.sync.methods.HttpGet;
import org.apache.hc.client5.http.impl.sync.RoutedHttpRequest;
import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
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
        final HttpHost target = new HttpHost("somehost", 80);
        final HttpRoute route = new HttpRoute(target);
        final RoutedHttpRequest get = RoutedHttpRequest.adapt(new HttpGet("http://somehost/"), route);
        final HttpClientContext context = HttpClientContext.create();

        final Date now = new Date();
        final Date tenSecondsAgo = new Date(now.getTime() - 10 * 1000L);

        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        response.setEntity(HttpTestUtils.makeBody(128));
        response.setHeader("Content-Length", "128");
        response.setHeader("ETag", "\"etag\"");
        response.setHeader("Cache-Control", "public, max-age=3600");
        response.setHeader("Last-Modified", DateUtils.formatDate(tenSecondsAgo));

        when(backend.execute(
                isA(RoutedHttpRequest.class),
                isA(HttpClientContext.class),
                (HttpExecutionAware) Matchers.isNull())).thenReturn(response);

        final BasicHttpCache cache = new BasicHttpCache(resourceFactory, httpCacheStorage, cacheConfig);
        final ClientExecChain t = createCachingExecChain(backend, cache, cacheConfig);

        final ClassicHttpResponse response1 = t.execute(get, context, null);
        Assert.assertEquals(200, response1.getCode());
        IOUtils.consume(response1.getEntity());

        verify(backend).execute(
                isA(RoutedHttpRequest.class),
                isA(HttpClientContext.class),
                (HttpExecutionAware) Matchers.isNull());

        removeCache();

        reset(backend);
        when(backend.execute(
                isA(RoutedHttpRequest.class),
                isA(HttpClientContext.class),
                (HttpExecutionAware) Matchers.isNull())).thenReturn(response);

        final ClassicHttpResponse response2 = t.execute(get, context, null);
        Assert.assertEquals(200, response2.getCode());
        IOUtils.consume(response2.getEntity());

        verify(backend).execute(
                isA(RoutedHttpRequest.class),
                isA(HttpClientContext.class),
                (HttpExecutionAware) Matchers.isNull());
    }

    protected ClientExecChain createCachingExecChain(final ClientExecChain backend,
            final BasicHttpCache cache, final CacheConfig config) {
        return new CachingExec(backend, cache, config);
    }

}
