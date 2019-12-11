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
package org.apache.hc.client5.http.impl.classic;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecRuntime;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.DecompressingEntity;
import org.apache.hc.client5.http.entity.EntityBuilder;
import org.apache.hc.client5.http.entity.GzipDecompressingEntity;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TestContentCompressionExec {

    @Mock
    private ExecRuntime execRuntime;
    @Mock
    private ExecChain execChain;
    @Mock
    private ClassicHttpRequest originaRequest;

    private HttpClientContext context;
    private HttpHost host;
    private ExecChain.Scope scope;
    private ContentCompressionExec impl;

    @Before
    public void setup() {
        host = new HttpHost("somehost", 80);
        context = HttpClientContext.create();
        scope = new ExecChain.Scope("test", new HttpRoute(host), originaRequest, execRuntime, context);
        impl = new ContentCompressionExec();
    }


    @Test
    public void testContentEncodingNoEntity() throws Exception {
        final ClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, host, "/");
        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");

        Mockito.when(execChain.proceed(request, scope)).thenReturn(response);

        impl.execute(request, scope, execChain);

        final HttpEntity entity = response.getEntity();
        Assert.assertNull(entity);
    }

    @Test
    public void testNoContentEncoding() throws Exception {
        final ClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, host, "/");
        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        final StringEntity original = new StringEntity("plain stuff");
        response.setEntity(original);

        Mockito.when(execChain.proceed(request, scope)).thenReturn(response);

        impl.execute(request, scope, execChain);

        final HttpEntity entity = response.getEntity();
        Assert.assertNotNull(entity);
        Assert.assertTrue(entity instanceof StringEntity);
    }

    @Test
    public void testGzipContentEncoding() throws Exception {
        final ClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, host, "/");
        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        final HttpEntity original = EntityBuilder.create().setText("encoded stuff").setContentEncoding("GZip").build();
        response.setEntity(original);

        Mockito.when(execChain.proceed(request, scope)).thenReturn(response);

        impl.execute(request, scope, execChain);

        final HttpEntity entity = response.getEntity();
        Assert.assertNotNull(entity);
        Assert.assertTrue(entity instanceof DecompressingEntity);
    }

    @Test
    public void testGzipContentEncodingZeroLength() throws Exception {
        final ClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, host, "/");
        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        final HttpEntity original = EntityBuilder.create().setText("").setContentEncoding("GZip").build();
        response.setEntity(original);

        Mockito.when(execChain.proceed(request, scope)).thenReturn(response);

        impl.execute(request, scope, execChain);

        final HttpEntity entity = response.getEntity();
        Assert.assertNotNull(entity);
        Assert.assertTrue(entity instanceof StringEntity);
    }

    @Test
    public void testXGzipContentEncoding() throws Exception {
        final ClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, host, "/");
        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        final HttpEntity original = EntityBuilder.create().setText("encoded stuff").setContentEncoding("x-gzip").build();
        response.setEntity(original);

        Mockito.when(execChain.proceed(request, scope)).thenReturn(response);

        impl.execute(request, scope, execChain);

        final HttpEntity entity = response.getEntity();
        Assert.assertNotNull(entity);
        Assert.assertTrue(entity instanceof DecompressingEntity);
    }

    @Test
    public void testDeflateContentEncoding() throws Exception {
        final ClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, host, "/");
        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        final HttpEntity original = EntityBuilder.create().setText("encoded stuff").setContentEncoding("deFlaTe").build();
        response.setEntity(original);

        Mockito.when(execChain.proceed(request, scope)).thenReturn(response);

        impl.execute(request, scope, execChain);

        final HttpEntity entity = response.getEntity();
        Assert.assertNotNull(entity);
        Assert.assertTrue(entity instanceof DecompressingEntity);
    }

    @Test
    public void testIdentityContentEncoding() throws Exception {
        final ClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, host, "/");
        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        final HttpEntity original = EntityBuilder.create().setText("encoded stuff").setContentEncoding("identity").build();
        response.setEntity(original);

        Mockito.when(execChain.proceed(request, scope)).thenReturn(response);

        impl.execute(request, scope, execChain);

        final HttpEntity entity = response.getEntity();
        Assert.assertNotNull(entity);
        Assert.assertTrue(entity instanceof StringEntity);
    }

    @Test(expected=HttpException.class)
    public void testUnknownContentEncoding() throws Exception {
        final ClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, host, "/");
        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        final HttpEntity original = EntityBuilder.create().setText("encoded stuff").setContentEncoding("whatever").build();
        response.setEntity(original);

        impl = new ContentCompressionExec(false);

        Mockito.when(execChain.proceed(request, scope)).thenReturn(response);

        impl.execute(request, scope, execChain);
    }

    @Test
    public void testContentEncodingRequestParameter() throws Exception {
        final ClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, host, "/");
        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        final HttpEntity original = EntityBuilder.create().setText("encoded stuff").setContentEncoding("GZip").build();
        response.setEntity(original);

        final RequestConfig config = RequestConfig.custom()
                .setContentCompressionEnabled(false)
                .build();

        context.setAttribute(HttpClientContext.REQUEST_CONFIG, config);

        Mockito.when(execChain.proceed(request, scope)).thenReturn(response);

        impl.execute(request, scope, execChain);

        final HttpEntity entity = response.getEntity();
        Assert.assertNotNull(entity);
        Assert.assertFalse(entity instanceof GzipDecompressingEntity);
    }

}
