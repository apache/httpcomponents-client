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

package org.apache.http.client.protocol;

import junit.framework.Assert;

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.HttpVersion;
import org.apache.http.client.entity.DeflateDecompressingEntity;
import org.apache.http.client.entity.GzipDecompressingEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.junit.Test;

public class TestResponseContentEncoding {

    @Test
    public void testContentEncodingNoEntity() throws Exception {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        HttpContext context = new BasicHttpContext();

        HttpResponseInterceptor interceptor = new ResponseContentEncoding();
        interceptor.process(response, context);
        HttpEntity entity = response.getEntity();
        Assert.assertNull(entity);
    }

    @Test
    public void testNoContentEncoding() throws Exception {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        StringEntity original = new StringEntity("plain stuff");
        response.setEntity(original);
        HttpContext context = new BasicHttpContext();

        HttpResponseInterceptor interceptor = new ResponseContentEncoding();
        interceptor.process(response, context);
        HttpEntity entity = response.getEntity();
        Assert.assertNotNull(entity);
        Assert.assertTrue(entity instanceof StringEntity);
    }

    @Test
    public void testGzipContentEncoding() throws Exception {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        StringEntity original = new StringEntity("encoded stuff");
        original.setContentEncoding("GZip");
        response.setEntity(original);
        HttpContext context = new BasicHttpContext();

        HttpResponseInterceptor interceptor = new ResponseContentEncoding();
        interceptor.process(response, context);
        HttpEntity entity = response.getEntity();
        Assert.assertNotNull(entity);
        Assert.assertTrue(entity instanceof GzipDecompressingEntity);
    }

    @Test
    public void testXGzipContentEncoding() throws Exception {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        StringEntity original = new StringEntity("encoded stuff");
        original.setContentEncoding("x-gzip");
        response.setEntity(original);
        HttpContext context = new BasicHttpContext();

        HttpResponseInterceptor interceptor = new ResponseContentEncoding();
        interceptor.process(response, context);
        HttpEntity entity = response.getEntity();
        Assert.assertNotNull(entity);
        Assert.assertTrue(entity instanceof GzipDecompressingEntity);
    }

    @Test
    public void testDeflateContentEncoding() throws Exception {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        StringEntity original = new StringEntity("encoded stuff");
        original.setContentEncoding("deFlaTe");
        response.setEntity(original);
        HttpContext context = new BasicHttpContext();

        HttpResponseInterceptor interceptor = new ResponseContentEncoding();
        interceptor.process(response, context);
        HttpEntity entity = response.getEntity();
        Assert.assertNotNull(entity);
        Assert.assertTrue(entity instanceof DeflateDecompressingEntity);
    }

    @Test
    public void testIdentityContentEncoding() throws Exception {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        StringEntity original = new StringEntity("encoded stuff");
        original.setContentEncoding("identity");
        response.setEntity(original);
        HttpContext context = new BasicHttpContext();

        HttpResponseInterceptor interceptor = new ResponseContentEncoding();
        interceptor.process(response, context);
        HttpEntity entity = response.getEntity();
        Assert.assertNotNull(entity);
        Assert.assertTrue(entity instanceof StringEntity);
    }

    @Test(expected=HttpException.class)
    public void testUnknownContentEncoding() throws Exception {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        StringEntity original = new StringEntity("encoded stuff");
        original.setContentEncoding("whatever");
        response.setEntity(original);
        HttpContext context = new BasicHttpContext();

        HttpResponseInterceptor interceptor = new ResponseContentEncoding();
        interceptor.process(response, context);
    }

}
