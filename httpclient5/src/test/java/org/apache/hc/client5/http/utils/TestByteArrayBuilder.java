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
package org.apache.hc.client5.http.utils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

/**
 * {@link ByteArrayBuilder} test cases.
 */
public class TestByteArrayBuilder {

    @Test
    public void testEmptyBuffer() throws Exception {
        final ByteArrayBuilder buffer = new ByteArrayBuilder();
        final ByteBuffer byteBuffer = buffer.toByteBuffer();
        Assert.assertNotNull(byteBuffer);
        Assert.assertEquals(0, byteBuffer.capacity());

        final byte[] bytes = buffer.toByteArray();
        Assert.assertNotNull(bytes);
        Assert.assertEquals(0, bytes.length);
    }

    @Test
    public void testAppendBytes() throws Exception {
        final ByteArrayBuilder buffer = new ByteArrayBuilder();
        buffer.append(new byte[]{1, 2, 3, 4, 5});
        buffer.append(new byte[]{3, 4, 5, 6, 7, 8, 9, 10, 11}, 3, 5);
        buffer.append((byte[]) null);

        final byte[] bytes = buffer.toByteArray();
        Assert.assertNotNull(bytes);
        Assert.assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, bytes);
    }

    @Test
    public void testInvalidAppendBytes() throws Exception {
        final ByteArrayBuilder buffer = new ByteArrayBuilder();
        buffer.append((byte[])null, 0, 0);

        final byte[] tmp = new byte[] { 1, 2, 3, 4};
        try {
            buffer.append(tmp, -1, 0);
            Assert.fail("IndexOutOfBoundsException should have been thrown");
        } catch (final IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            buffer.append(tmp, 0, -1);
            Assert.fail("IndexOutOfBoundsException should have been thrown");
        } catch (final IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            buffer.append(tmp, 0, 8);
            Assert.fail("IndexOutOfBoundsException should have been thrown");
        } catch (final IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            buffer.append(tmp, 10, Integer.MAX_VALUE);
            Assert.fail("IndexOutOfBoundsException should have been thrown");
        } catch (final IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            buffer.append(tmp, 2, 4);
            Assert.fail("IndexOutOfBoundsException should have been thrown");
        } catch (final IndexOutOfBoundsException ex) {
            // expected
        }
    }

    @Test
    public void testEnsureCapacity() throws Exception {
        final ByteArrayBuilder buffer = new ByteArrayBuilder();
        buffer.ensureFreeCapacity(10);
        Assert.assertEquals(10, buffer.capacity());
        buffer.ensureFreeCapacity(5);
        Assert.assertEquals(10, buffer.capacity());
        buffer.append(new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
        buffer.ensureFreeCapacity(5);
        Assert.assertEquals(13, buffer.capacity());
        buffer.ensureFreeCapacity(15);
        Assert.assertEquals(23, buffer.capacity());
    }

    @Test
    public void testAppendText() throws Exception {
        final ByteArrayBuilder buffer = new ByteArrayBuilder();
        buffer.append(new char[]{'1', '2', '3', '4', '5'});
        buffer.append(new char[]{'3', '4', '5', '6', '7', '8', '9', 'a', 'b'}, 3, 5);
        buffer.append("bcd");
        buffer.append("e");
        buffer.append("f");
        buffer.append((String) null);
        buffer.append((char[]) null);

        final byte[] bytes = buffer.toByteArray();
        Assert.assertNotNull(bytes);
        Assert.assertEquals("123456789abcdef", new String(bytes, StandardCharsets.US_ASCII));
    }

    @Test
    public void testInvalidAppendChars() throws Exception {
        final ByteArrayBuilder buffer = new ByteArrayBuilder();
        buffer.append((char[])null, 0, 0);

        final char[] tmp = new char[] { 1, 2, 3, 4};
        try {
            buffer.append(tmp, -1, 0);
            Assert.fail("IndexOutOfBoundsException should have been thrown");
        } catch (final IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            buffer.append(tmp, 0, -1);
            Assert.fail("IndexOutOfBoundsException should have been thrown");
        } catch (final IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            buffer.append(tmp, 0, 8);
            Assert.fail("IndexOutOfBoundsException should have been thrown");
        } catch (final IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            buffer.append(tmp, 10, Integer.MAX_VALUE);
            Assert.fail("IndexOutOfBoundsException should have been thrown");
        } catch (final IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            buffer.append(tmp, 2, 4);
            Assert.fail("IndexOutOfBoundsException should have been thrown");
        } catch (final IndexOutOfBoundsException ex) {
            // expected
        }
    }

    @Test
    public void testReset() throws Exception {
        final ByteArrayBuilder buffer = new ByteArrayBuilder();
        buffer.append("abcd");
        buffer.append("e");
        buffer.append("f");

        final byte[] bytes1 = buffer.toByteArray();
        Assert.assertNotNull(bytes1);
        Assert.assertEquals("abcdef", new String(bytes1, StandardCharsets.US_ASCII));

        buffer.reset();

        final byte[] bytes2 = buffer.toByteArray();
        Assert.assertNotNull(bytes2);
        Assert.assertEquals("", new String(bytes2, StandardCharsets.US_ASCII));
    }

    @Test
    public void testNonAsciiCharset() throws Exception {
        final int[] germanChars = { 0xE4, 0x2D, 0xF6, 0x2D, 0xFc };
        final StringBuilder tmp = new StringBuilder();
        for (final int germanChar : germanChars) {
            tmp.append((char) germanChar);
        }
        final String umlauts = tmp.toString();


        final ByteArrayBuilder buffer = new ByteArrayBuilder();
        buffer.append(umlauts);

        final byte[] bytes1 = buffer.toByteArray();
        Assert.assertNotNull(bytes1);
        Assert.assertEquals("?-?-?", new String(bytes1, StandardCharsets.US_ASCII));

        buffer.reset();
        buffer.charset(StandardCharsets.UTF_8);
        buffer.append(umlauts);

        final byte[] bytes2 = buffer.toByteArray();
        Assert.assertNotNull(bytes2);
        Assert.assertEquals(umlauts, new String(bytes2, StandardCharsets.UTF_8));
    }

}
