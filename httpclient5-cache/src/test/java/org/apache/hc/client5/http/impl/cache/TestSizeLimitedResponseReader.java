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

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.client5.http.sync.methods.HttpGet;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
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
        final byte[] buf = new byte[] { 1, 2, 3, 4, 5 };
        final ClassicHttpResponse response = make200Response(buf);

        impl = new SizeLimitedResponseReader(new HeapResourceFactory(), MAX_SIZE, request, response);

        impl.readResponse();
        final boolean tooLarge = impl.isLimitReached();
        final ClassicHttpResponse result = impl.getReconstructedResponse();
        final byte[] body = EntityUtils.toByteArray(result.getEntity());

        Assert.assertTrue(tooLarge);
        Assert.assertArrayEquals(buf, body);
    }

    @Test
    public void testExactSizeResponseIsNotTooLarge() throws Exception {
        final byte[] buf = new byte[] { 1, 2, 3, 4 };
        final ClassicHttpResponse response = make200Response(buf);

        impl = new SizeLimitedResponseReader(new HeapResourceFactory(), MAX_SIZE, request, response);

        impl.readResponse();
        final boolean tooLarge = impl.isLimitReached();
        final ClassicHttpResponse reconstructed = impl.getReconstructedResponse();
        final byte[] result = EntityUtils.toByteArray(reconstructed.getEntity());

        Assert.assertFalse(tooLarge);
        Assert.assertArrayEquals(buf, result);
    }

    @Test
    public void testSmallResponseIsNotTooLarge() throws Exception {
        final byte[] buf = new byte[] { 1, 2, 3 };
        final ClassicHttpResponse response = make200Response(buf);

        impl = new SizeLimitedResponseReader(new HeapResourceFactory(), MAX_SIZE, request, response);

        impl.readResponse();
        final boolean tooLarge = impl.isLimitReached();
        final ClassicHttpResponse reconstructed = impl.getReconstructedResponse();
        final byte[] result = EntityUtils.toByteArray(reconstructed.getEntity());

        Assert.assertFalse(tooLarge);
        Assert.assertArrayEquals(buf, result);
    }

    @Test
    public void testResponseWithNoEntityIsNotTooLarge() throws Exception {
        final ClassicHttpResponse response = make200Response();

        impl = new SizeLimitedResponseReader(new HeapResourceFactory(), MAX_SIZE, request, response);

        impl.readResponse();
        final boolean tooLarge = impl.isLimitReached();

        Assert.assertFalse(tooLarge);
    }

    @Test
    public void testTooLargeEntityHasOriginalContentTypes() throws Exception {
        final ClassicHttpResponse response = make200Response();
        final StringEntity entity = new StringEntity("large entity content");
        response.setEntity(entity);

        impl = new SizeLimitedResponseReader(new HeapResourceFactory(), MAX_SIZE, request, response);

        impl.readResponse();
        final boolean tooLarge = impl.isLimitReached();
        final ClassicHttpResponse result = impl.getReconstructedResponse();
        final HttpEntity reconstructedEntity = result.getEntity();
        Assert.assertEquals(entity.getContentEncoding(), reconstructedEntity.getContentEncoding());
        Assert.assertEquals(entity.getContentType(), reconstructedEntity.getContentType());

        final String content = EntityUtils.toString(reconstructedEntity);

        Assert.assertTrue(tooLarge);
        Assert.assertEquals("large entity content", content);
    }

    @Test
    public void testTooLargeResponseCombinedClosed() throws Exception {
        final AtomicBoolean closed = new AtomicBoolean(false);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK") {
            @Override
            public void close() throws IOException {
                closed.set(true);
            }
        };
        final StringEntity entity = new StringEntity("large entity content");
        response.setEntity(entity);

        impl = new SizeLimitedResponseReader(new HeapResourceFactory(), MAX_SIZE, request, response);

        impl.readResponse();
        final boolean tooLarge = impl.isLimitReached();
        try (ClassicHttpResponse result = impl.getReconstructedResponse()) {
            final HttpEntity reconstructedEntity = result.getEntity();
            Assert.assertEquals(entity.getContentEncoding(), reconstructedEntity.getContentEncoding());
            Assert.assertEquals(entity.getContentType(), reconstructedEntity.getContentType());

            Assert.assertFalse(closed.get());
            final String content = EntityUtils.toString(reconstructedEntity);

            Assert.assertTrue(tooLarge);
            Assert.assertEquals("large entity content", content);
        }
        Assert.assertTrue(closed.get());
    }

    @Test
    public void testResponseCopiesAllOriginalHeaders() throws Exception {
        final byte[] buf = new byte[] { 1, 2, 3 };
        final ClassicHttpResponse response = make200Response(buf);
        response.setHeader("Content-Encoding", "gzip");

        impl = new SizeLimitedResponseReader(new HeapResourceFactory(), MAX_SIZE, request, response);

        impl.readResponse();
        final boolean tooLarge = impl.isLimitReached();
        final ClassicHttpResponse reconstructed = impl.getReconstructedResponse();
        final byte[] result = EntityUtils.toByteArray(reconstructed.getEntity());

        Assert.assertFalse(tooLarge);
        Assert.assertArrayEquals(buf, result);
        Assert.assertEquals("gzip", reconstructed.getFirstHeader("Content-Encoding").getValue());
    }

    private ClassicHttpResponse make200Response() {
        return new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
    }

    private ClassicHttpResponse make200Response(final byte[] buf) {
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_OK, "OK");
        response.setEntity(new ByteArrayEntity(buf));
        return response;
    }

}
