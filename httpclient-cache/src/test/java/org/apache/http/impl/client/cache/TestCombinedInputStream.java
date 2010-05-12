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
import java.io.InputStream;

import org.easymock.classextension.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 */
public class TestCombinedInputStream {

    private InputStream mockInputStream1;
    private InputStream mockInputStream2;
    private CombinedInputStream impl;

    @Before
    public void setUp() {
        mockInputStream1 = EasyMock.createMock(InputStream.class);
        mockInputStream2 = EasyMock.createMock(InputStream.class);

        impl = new CombinedInputStream(mockInputStream1, mockInputStream2);
    }

    @Test
    public void testCreatingInputStreamWithNullInputFails() {

        boolean gotex1 = false;
        boolean gotex2 = false;

        try {
            impl = new CombinedInputStream(null, mockInputStream2);
        } catch (Exception ex) {
            gotex1 = true;
        }

        try {
            impl = new CombinedInputStream(mockInputStream1, null);
        } catch (Exception ex) {
            gotex2 = true;
        }

        Assert.assertTrue(gotex1);
        Assert.assertTrue(gotex2);

    }

    @Test
    public void testAvailableReturnsCorrectSize() throws Exception {
        ByteArrayInputStream s1 = new ByteArrayInputStream(new byte[] { 1, 1, 1, 1, 1 });
        ByteArrayInputStream s2 = new ByteArrayInputStream(new byte[] { 1, 1, 1, 1, 1 });

        impl = new CombinedInputStream(s1, s2);
        int avail = impl.available();

        Assert.assertEquals(10, avail);
    }

    @Test
    public void testFirstEmptyStreamReadsFromOtherStream() throws Exception {
        org.easymock.EasyMock.expect(mockInputStream1.read()).andReturn(-1);
        org.easymock.EasyMock.expect(mockInputStream2.read()).andReturn(500);

        replayMocks();
        int result = impl.read();
        verifyMocks();

        Assert.assertEquals(500, result);
    }

    @Test
    public void testThatWeReadTheFirstInputStream() throws Exception {
        org.easymock.EasyMock.expect(mockInputStream1.read()).andReturn(500);

        replayMocks();
        int result = impl.read();
        verifyMocks();

        Assert.assertEquals(500, result);
    }

    private void verifyMocks() {
        EasyMock.verify(mockInputStream1, mockInputStream2);
    }

    private void replayMocks() {
        EasyMock.replay(mockInputStream1, mockInputStream2);
    }

}
