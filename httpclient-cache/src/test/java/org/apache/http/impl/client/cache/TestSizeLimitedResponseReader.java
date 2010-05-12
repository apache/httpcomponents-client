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
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.easymock.classextension.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestSizeLimitedResponseReader {

    private static final int MAX_SIZE = 4;

    private SizeLimitedResponseReader impl;
    private HttpResponse mockResponse;
    private HttpEntity mockEntity;
    private InputStream mockInputStream;
    private ProtocolVersion mockVersion;

    private boolean mockedImpl;

    @Before
    public void setUp() {
        mockResponse = EasyMock.createMock(HttpResponse.class);
        mockEntity = EasyMock.createMock(HttpEntity.class);
        mockInputStream = EasyMock.createMock(InputStream.class);
        mockVersion = EasyMock.createMock(ProtocolVersion.class);

    }

    @Test
    public void testLargeResponseIsTooLarge() throws Exception {

        responseHasValidEntity();
        entityHasValidContentStream();
        inputStreamReturnsValidBytes(5);

        getReader();

        replayMocks();
        boolean tooLarge = impl.isResponseTooLarge();
        byte[] result = impl.getResponseBytes();
        verifyMocks();

        Assert.assertTrue(tooLarge);

        Assert.assertArrayEquals(new byte[] { 1, 1, 1, 1, 1 }, result);
    }

    @Test
    public void testExactSizeResponseIsNotTooLarge() throws Exception {
        responseHasValidEntity();
        entityHasValidContentStream();
        inputStreamReturnsValidBytes(4);
        inputStreamReturnsEndOfStream();

        getReader();
        replayMocks();
        boolean tooLarge = impl.isResponseTooLarge();
        byte[] result = impl.getResponseBytes();
        verifyMocks();

        Assert.assertFalse(tooLarge);

        Assert.assertArrayEquals(new byte[] { 1, 1, 1, 1 }, result);
    }

    @Test
    public void testSmallResponseIsNotTooLarge() throws Exception {
        responseHasValidEntity();
        entityHasValidContentStream();

        org.easymock.EasyMock.expect(mockInputStream.read()).andReturn(1).times(3);

        org.easymock.EasyMock.expect(mockInputStream.read()).andReturn(-1).times(2);

        getReader();
        replayMocks();
        boolean tooLarge = impl.isResponseTooLarge();
        byte[] result = impl.getResponseBytes();
        verifyMocks();

        Assert.assertFalse(tooLarge);

        Assert.assertArrayEquals(new byte[] { 1, 1, 1 }, result);
    }

    @Test
    public void testResponseWithNoEntityIsNotTooLarge() throws Exception {
        responseHasNullEntity();

        getReader();
        replayMocks();
        boolean tooLarge = impl.isResponseTooLarge();
        verifyMocks();

        Assert.assertFalse(tooLarge);
    }

    @Test
    public void testReconstructedSmallResponseHasCorrectLength() throws Exception {

        byte[] expectedArray = new byte[] { 1, 1, 1, 1 };

        InputStream stream = new ByteArrayInputStream(new byte[] {});

        responseReturnsHeaders();
        responseReturnsProtocolVersion();

        getReader();
        mockImplMethods("getResponseBytes", "getContentInputStream");
        getContentInputStreamReturns(stream);
        getResponseBytesReturns(expectedArray);
        replayMocks();

        HttpResponse response = impl.getReconstructedResponse();

        verifyMocks();

        Assert.assertNotNull("Response should not be null", response);
        InputStream resultStream = response.getEntity().getContent();

        byte[] buffer = new byte[expectedArray.length];
        resultStream.read(buffer);

        Assert.assertArrayEquals(expectedArray, buffer);
    }

    private void getContentInputStreamReturns(InputStream inputStream) {
        org.easymock.EasyMock.expect(impl.getContentInputStream()).andReturn(inputStream);
    }

    @Test
    public void testReconstructedLargeResponseHasCorrectLength() throws Exception {

        byte[] expectedArray = new byte[] { 2, 2, 2, 2, 1, 1, 1, 1, 1, 1, 1 };
        byte[] arrayAfterConsumedBytes = new byte[] { 1, 1, 1, 1, 1, 1, 1 };
        byte[] smallArray = new byte[] { 2, 2, 2, 2, };
        InputStream is = new ByteArrayInputStream(arrayAfterConsumedBytes);

        responseReturnsHeaders();
        responseReturnsProtocolVersion();

        getReader();
        mockImplMethods("getResponseBytes", "getContentInputStream");
        getResponseBytesReturns(smallArray);
        getContentInputStreamReturns(is);

        replayMocks();

        HttpResponse response = impl.getReconstructedResponse();

        verifyMocks();

        InputStream resultStream = response.getEntity().getContent();

        byte[] buffer = new byte[expectedArray.length];
        resultStream.read(buffer);

        Assert.assertArrayEquals(expectedArray, buffer);
    }

    private void getResponseBytesReturns(byte[] expectedArray) {
        org.easymock.EasyMock.expect(impl.getResponseBytes()).andReturn(expectedArray);
    }

    private void responseReturnsHeaders() {
        org.easymock.EasyMock.expect(mockResponse.getAllHeaders()).andReturn(new Header[] {});
    }

    private void entityHasValidContentStream() throws IOException {
        org.easymock.EasyMock.expect(mockEntity.getContent()).andReturn(mockInputStream);
    }

    private void inputStreamReturnsEndOfStream() throws IOException {
        org.easymock.EasyMock.expect(mockInputStream.read()).andReturn(-1);
    }

    private void responseHasValidEntity() {
        org.easymock.EasyMock.expect(mockResponse.getEntity()).andReturn(mockEntity);
    }

    private void responseReturnsProtocolVersion() {
        org.easymock.EasyMock.expect(mockResponse.getProtocolVersion()).andReturn(mockVersion);
    }

    private void inputStreamReturnsValidBytes(int times) throws IOException {
        org.easymock.EasyMock.expect(mockInputStream.read()).andReturn(1).times(times);
    }

    private void responseHasNullEntity() {
        org.easymock.EasyMock.expect(mockResponse.getEntity()).andReturn(null);
    }

    private void verifyMocks() {
        EasyMock.verify(mockResponse, mockEntity, mockInputStream, mockVersion);
        if (mockedImpl) {
            EasyMock.verify(impl);
        }
    }

    private void replayMocks() {
        EasyMock.replay(mockResponse, mockEntity, mockInputStream, mockVersion);
        if (mockedImpl) {
            EasyMock.replay(impl);
        }
    }

    private void getReader() {
        impl = new SizeLimitedResponseReader(MAX_SIZE, mockResponse);
    }

    private void mockImplMethods(String... methods) {
        mockedImpl = true;
        impl = EasyMock.createMockBuilder(SizeLimitedResponseReader.class).withConstructor(
                MAX_SIZE, mockResponse).addMockedMethods(methods).createMock();
    }

}
