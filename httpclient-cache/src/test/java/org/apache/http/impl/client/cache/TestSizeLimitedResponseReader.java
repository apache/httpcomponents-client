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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.message.BasicRequestLine;
import org.apache.http.util.EntityUtils;
import org.easymock.classextension.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestSizeLimitedResponseReader {

    private static final long MAX_SIZE = 4;

    private SizeLimitedResponseReader impl;
    private HttpRequest mockRequest;
    private HttpResponse mockResponse;
    private HttpEntity mockEntity;

    private boolean mockedImpl;

    @Before
    public void setUp() {
        mockRequest = EasyMock.createMock(HttpRequest.class);
        mockResponse = EasyMock.createMock(HttpResponse.class);
        mockEntity = EasyMock.createMock(HttpEntity.class);
    }

    @Test
    public void testLargeResponseIsTooLarge() throws Exception {
        byte[] buf = new byte[] { 1, 2, 3, 4, 5};
        requestReturnsRequestLine();
        responseReturnsProtocolVersion();
        responseReturnsHeaders();
        responseReturnsContent(new ByteArrayInputStream(buf));
        initReader();
        replayMocks();

        impl.readResponse();
        boolean tooLarge = impl.isLimitReached();
        HttpResponse response = impl.getReconstructedResponse();
        byte[] result = EntityUtils.toByteArray(response.getEntity());

        verifyMocks();
        Assert.assertTrue(tooLarge);
        Assert.assertArrayEquals(buf, result);
    }

    @Test
    public void testExactSizeResponseIsNotTooLarge() throws Exception {
        byte[] buf = new byte[] { 1, 2, 3, 4 };
        requestReturnsRequestLine();
        responseReturnsProtocolVersion();
        responseReturnsHeaders();
        responseReturnsContent(new ByteArrayInputStream(buf));
        initReader();
        replayMocks();

        impl.readResponse();
        boolean tooLarge = impl.isLimitReached();
        HttpResponse response = impl.getReconstructedResponse();
        byte[] result = EntityUtils.toByteArray(response.getEntity());

        verifyMocks();
        Assert.assertFalse(tooLarge);
        Assert.assertArrayEquals(buf, result);
    }

    @Test
    public void testSmallResponseIsNotTooLarge() throws Exception {
        byte[] buf = new byte[] { 1, 2, 3 };
        requestReturnsRequestLine();
        responseReturnsProtocolVersion();
        responseReturnsHeaders();
        responseReturnsContent(new ByteArrayInputStream(buf));
        initReader();
        replayMocks();

        impl.readResponse();
        boolean tooLarge = impl.isLimitReached();
        HttpResponse response = impl.getReconstructedResponse();
        byte[] result = EntityUtils.toByteArray(response.getEntity());
        verifyMocks();

        Assert.assertFalse(tooLarge);
        Assert.assertArrayEquals(buf, result);
    }

    @Test
    public void testResponseWithNoEntityIsNotTooLarge() throws Exception {
        responseHasNullEntity();

        initReader();
        replayMocks();
        impl.readResponse();
        boolean tooLarge = impl.isLimitReached();
        verifyMocks();

        Assert.assertFalse(tooLarge);
    }

    private void responseReturnsContent(InputStream buffer) throws IOException {
        EasyMock.expect(mockResponse.getEntity()).andReturn(mockEntity);
        EasyMock.expect(mockEntity.getContent()).andReturn(buffer);
    }

    private void requestReturnsRequestLine() {
        EasyMock.expect(mockRequest.getRequestLine()).andReturn(
                new BasicRequestLine("GET", "/", HttpVersion.HTTP_1_1));
    }

    private void responseReturnsProtocolVersion() {
        EasyMock.expect(mockResponse.getProtocolVersion()).andReturn(HttpVersion.HTTP_1_1);
    }

    private void responseReturnsHeaders() {
        EasyMock.expect(mockResponse.getAllHeaders()).andReturn(new Header[] {});
    }

    private void responseHasNullEntity() {
        EasyMock.expect(mockResponse.getEntity()).andReturn(null);
    }

    private void verifyMocks() {
        EasyMock.verify(mockRequest, mockResponse, mockEntity);
        if (mockedImpl) {
            EasyMock.verify(impl);
        }
    }

    private void replayMocks() {
        EasyMock.replay(mockRequest, mockResponse, mockEntity);
        if (mockedImpl) {
            EasyMock.replay(impl);
        }
    }

    private void initReader() {
        impl = new SizeLimitedResponseReader(
                new HeapResourceFactory(), MAX_SIZE, mockRequest, mockResponse);
    }

}
