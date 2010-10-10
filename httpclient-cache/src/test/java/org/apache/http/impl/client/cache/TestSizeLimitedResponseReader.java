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

import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestSizeLimitedResponseReader {

    private static final long MAX_SIZE = 4;

    private HttpRequest request;
    private SizeLimitedResponseReader impl;

    @Before
    public void setUp() {
        request = new HttpGet("http://foo.example.com/bar");
    }

    @Test
    public void testLargeResponseIsTooLarge() throws Exception {
        byte[] buf = new byte[] { 1, 2, 3, 4, 5 };
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        response.setEntity(new ByteArrayEntity(buf));

        impl = new SizeLimitedResponseReader(new HeapResourceFactory(), MAX_SIZE, request, response);

        impl.readResponse();
        boolean tooLarge = impl.isLimitReached();
        HttpResponse result = impl.getReconstructedResponse();
        byte[] body = EntityUtils.toByteArray(result.getEntity());

        Assert.assertTrue(tooLarge);
        Assert.assertArrayEquals(buf, body);
    }

    @Test
    public void testExactSizeResponseIsNotTooLarge() throws Exception {
        byte[] buf = new byte[] { 1, 2, 3, 4 };
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        response.setEntity(new ByteArrayEntity(buf));

        impl = new SizeLimitedResponseReader(new HeapResourceFactory(), MAX_SIZE, request, response);

        impl.readResponse();
        boolean tooLarge = impl.isLimitReached();
        HttpResponse reconstructed = impl.getReconstructedResponse();
        byte[] result = EntityUtils.toByteArray(reconstructed.getEntity());

        Assert.assertFalse(tooLarge);
        Assert.assertArrayEquals(buf, result);
    }

    @Test
    public void testSmallResponseIsNotTooLarge() throws Exception {
        byte[] buf = new byte[] { 1, 2, 3 };
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        response.setEntity(new ByteArrayEntity(buf));

        impl = new SizeLimitedResponseReader(new HeapResourceFactory(), MAX_SIZE, request, response);

        impl.readResponse();
        boolean tooLarge = impl.isLimitReached();
        HttpResponse reconstructed = impl.getReconstructedResponse();
        byte[] result = EntityUtils.toByteArray(reconstructed.getEntity());

        Assert.assertFalse(tooLarge);
        Assert.assertArrayEquals(buf, result);
    }

    @Test
    public void testResponseWithNoEntityIsNotTooLarge() throws Exception {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");

        impl = new SizeLimitedResponseReader(new HeapResourceFactory(), MAX_SIZE, request, response);

        impl.readResponse();
        boolean tooLarge = impl.isLimitReached();

        Assert.assertFalse(tooLarge);
    }

    @Test
    public void testTooLargeEntityHasOriginalContentTypes() throws Exception {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        StringEntity entity = new StringEntity("large entity content", "text/plain", "utf-8");
        response.setEntity(entity);

        impl = new SizeLimitedResponseReader(new HeapResourceFactory(), MAX_SIZE, request, response);

        impl.readResponse();
        boolean tooLarge = impl.isLimitReached();
        HttpResponse result = impl.getReconstructedResponse();
        HttpEntity reconstructedEntity = result.getEntity();
        Assert.assertEquals(entity.getContentEncoding(), reconstructedEntity.getContentEncoding());
        Assert.assertEquals(entity.getContentType(), reconstructedEntity.getContentType());

        String content = EntityUtils.toString(reconstructedEntity);

        Assert.assertTrue(tooLarge);
        Assert.assertEquals("large entity content", content);
    }

    @Test
    public void testResponseCopiesAllOriginalHeaders() throws Exception {
        byte[] buf = new byte[] { 1, 2, 3 };
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        response.setEntity(new ByteArrayEntity(buf));
        response.setHeader("Content-Encoding", "gzip");

        impl = new SizeLimitedResponseReader(new HeapResourceFactory(), MAX_SIZE, request, response);

        impl.readResponse();
        boolean tooLarge = impl.isLimitReached();
        HttpResponse reconstructed = impl.getReconstructedResponse();
        byte[] result = EntityUtils.toByteArray(reconstructed.getEntity());

        Assert.assertFalse(tooLarge);
        Assert.assertArrayEquals(buf, result);
        Assert.assertEquals("gzip", reconstructed.getFirstHeader("Content-Encoding").getValue());
    }

}
