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
package org.apache.hc.client5.http.protocol;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.DecompressingEntity;
import org.apache.hc.client5.http.entity.GzipDecompressingEntity;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class TestResponseContentEncoding {

    @Test
    public void testContentEncodingNoEntity() throws Exception {
        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        final HttpContext context = new BasicHttpContext();

        final HttpResponseInterceptor interceptor = new ResponseContentEncoding();
        interceptor.process(response, response.getEntity(), context);
        final HttpEntity entity = response.getEntity();
        Assert.assertNull(entity);
    }

    @Test
    public void testNoContentEncoding() throws Exception {
        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        final StringEntity original = new StringEntity("plain stuff");
        response.setEntity(original);
        final HttpContext context = new BasicHttpContext();

        final HttpResponseInterceptor interceptor = new ResponseContentEncoding();
        interceptor.process(response, response.getEntity(), context);
        final HttpEntity entity = response.getEntity();
        Assert.assertNotNull(entity);
        Assert.assertTrue(entity instanceof StringEntity);
    }

    @Test
    public void testGzipContentEncoding() throws Exception {
        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        final StringEntity original = new StringEntity("encoded stuff");
        original.setContentEncoding("GZip");
        response.setEntity(original);
        final HttpContext context = new BasicHttpContext();

        final HttpResponseInterceptor interceptor = new ResponseContentEncoding();
        interceptor.process(response, response.getEntity(), context);
        final HttpEntity entity = response.getEntity();
        Assert.assertNotNull(entity);
        Assert.assertTrue(entity instanceof DecompressingEntity);
    }

    @Test
    public void testGzipContentEncodingZeroLength() throws Exception {
        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        final StringEntity original = new StringEntity("");
        original.setContentEncoding("GZip");
        response.setEntity(original);
        final HttpContext context = new BasicHttpContext();

        final HttpResponseInterceptor interceptor = new ResponseContentEncoding();
        interceptor.process(response, response.getEntity(), context);
        final HttpEntity entity = response.getEntity();
        Assert.assertNotNull(entity);
        Assert.assertTrue(entity instanceof StringEntity);
    }

    @Test
    public void testXGzipContentEncoding() throws Exception {
        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        final StringEntity original = new StringEntity("encoded stuff");
        original.setContentEncoding("x-gzip");
        response.setEntity(original);
        final HttpContext context = new BasicHttpContext();

        final HttpResponseInterceptor interceptor = new ResponseContentEncoding();
        interceptor.process(response, response.getEntity(), context);
        final HttpEntity entity = response.getEntity();
        Assert.assertNotNull(entity);
        Assert.assertTrue(entity instanceof DecompressingEntity);
    }

    @Test
    public void testDeflateContentEncoding() throws Exception {
        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        final StringEntity original = new StringEntity("encoded stuff");
        original.setContentEncoding("deFlaTe");
        response.setEntity(original);
        final HttpContext context = new BasicHttpContext();

        final HttpResponseInterceptor interceptor = new ResponseContentEncoding();
        interceptor.process(response, response.getEntity(), context);
        final HttpEntity entity = response.getEntity();
        Assert.assertNotNull(entity);
        Assert.assertTrue(entity instanceof DecompressingEntity);
    }

    @Test
    public void testIdentityContentEncoding() throws Exception {
        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        final StringEntity original = new StringEntity("encoded stuff");
        original.setContentEncoding("identity");
        response.setEntity(original);
        final HttpContext context = new BasicHttpContext();

        final HttpResponseInterceptor interceptor = new ResponseContentEncoding();
        interceptor.process(response, response.getEntity(), context);
        final HttpEntity entity = response.getEntity();
        Assert.assertNotNull(entity);
        Assert.assertTrue(entity instanceof StringEntity);
    }

    @Test(expected=HttpException.class)
    public void testUnknownContentEncoding() throws Exception {
        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        final StringEntity original = new StringEntity("encoded stuff");
        original.setContentEncoding("whatever");
        response.setEntity(original);
        final HttpContext context = new BasicHttpContext();

        final HttpResponseInterceptor interceptor = new ResponseContentEncoding(false);
        interceptor.process(response, response.getEntity(), context);
    }

    @Test
    public void testContentEncodingRequestParameter() throws Exception {
        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        final StringEntity original = new StringEntity("encoded stuff");
        original.setContentEncoding("GZip");
        response.setEntity(original);

        final RequestConfig config = RequestConfig.custom()
                .setContentCompressionEnabled(false)
                .build();

        final HttpContext context = new BasicHttpContext();
        context.setAttribute(HttpClientContext.REQUEST_CONFIG, config);

        final HttpResponseInterceptor interceptor = new ResponseContentEncoding();
        interceptor.process(response, response.getEntity(), context);
        final HttpEntity entity = response.getEntity();
        Assert.assertNotNull(entity);
        Assert.assertFalse(entity instanceof GzipDecompressingEntity);
    }

}
