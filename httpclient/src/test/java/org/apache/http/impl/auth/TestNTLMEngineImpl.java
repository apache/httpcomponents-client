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

package org.apache.http.impl.auth;

import org.junit.Test;
import org.junit.Assert;

public class TestNTLMEngineImpl {

    @Test
    public void testMD4() throws Exception {
        checkMD4("", "31d6cfe0d16ae931b73c59d7e0c089c0");
        checkMD4("a", "bde52cb31de33e46245e05fbdbd6fb24");
        checkMD4("abc", "a448017aaf21d8525fc10ae87aa6729d");
        checkMD4("message digest", "d9130a8164549fe818874806e1c7014b");
        checkMD4("abcdefghijklmnopqrstuvwxyz", "d79e1c308aa5bbcdeea8ed63df412da9");
        checkMD4("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789",
                "043f8582f241db351ce627e153e7f0e4");
        checkMD4(
                "12345678901234567890123456789012345678901234567890123456789012345678901234567890",
                "e33b4ddc9c38f2199c3e7b164fcc0536");
    }

    /* Test suite helper */
    static byte toNibble(char c) {
        if (c >= 'a' && c <= 'f')
            return (byte) (c - 'a' + 0x0a);
        return (byte) (c - '0');
    }

    /* Test suite helper */
    static byte[] toBytes(String hex) {
        byte[] rval = new byte[hex.length() / 2];
        int i = 0;
        while (i < rval.length) {
            rval[i] = (byte) ((toNibble(hex.charAt(i * 2)) << 4) | (toNibble(hex
                    .charAt(i * 2 + 1))));
            i++;
        }
        return rval;
    }

    /* Test suite MD4 helper */
    static void checkMD4(String input, String hexOutput) throws Exception {
        NTLMEngineImpl.MD4 md4;
        md4 = new NTLMEngineImpl.MD4();
        md4.update(input.getBytes("ASCII"));
        byte[] answer = md4.getOutput();
        byte[] correctAnswer = toBytes(hexOutput);
        if (answer.length != correctAnswer.length)
            throw new Exception("Answer length disagrees for MD4('" + input + "')");
        int i = 0;
        while (i < answer.length) {
            if (answer[i] != correctAnswer[i])
                throw new Exception("Answer value for MD4('" + input + "') disagrees at position "
                        + Integer.toString(i));
            i++;
        }
    }

    @Test
    public void testLMResponse() throws Exception {
        NTLMEngineImpl.CipherGen gen = new NTLMEngineImpl.CipherGen(
            null,
            null,
            "SecREt01",
            toBytes("0123456789abcdef"),
            null,
            null,
            null,
            null,
            null,
            null);

        checkArraysMatch(toBytes("c337cd5cbd44fc9782a667af6d427c6de67c20c2d3e77c56"),
            gen.getLMResponse());
    }

    @Test
    public void testNTLMResponse() throws Exception {
        NTLMEngineImpl.CipherGen gen = new NTLMEngineImpl.CipherGen(
            null,
            null,
            "SecREt01",
            toBytes("0123456789abcdef"),
            null,
            null,
            null,
            null,
            null,
            null);

        checkArraysMatch(toBytes("25a98c1c31e81847466b29b2df4680f39958fb8c213a9cc6"),
            gen.getNTLMResponse());
    }

    @Test
    public void testLMv2Response() throws Exception {
        NTLMEngineImpl.CipherGen gen = new NTLMEngineImpl.CipherGen(
            "DOMAIN",
            "user",
            "SecREt01",
            toBytes("0123456789abcdef"),
            "DOMAIN",
            null,
            toBytes("ffffff0011223344"),
            toBytes("ffffff0011223344"),
            null,
            null);

        checkArraysMatch(toBytes("d6e6152ea25d03b7c6ba6629c2d6aaf0ffffff0011223344"),
            gen.getLMv2Response());
    }

    @Test
    public void testNTLMv2Response() throws Exception {
        NTLMEngineImpl.CipherGen gen = new NTLMEngineImpl.CipherGen(
            "DOMAIN",
            "user",
            "SecREt01",
            toBytes("0123456789abcdef"),
            "DOMAIN",
            toBytes("02000c0044004f004d00410049004e0001000c005300450052005600450052000400140064006f006d00610069006e002e0063006f006d00030022007300650072007600650072002e0064006f006d00610069006e002e0063006f006d0000000000"),
            toBytes("ffffff0011223344"),
            toBytes("ffffff0011223344"),
            null,
            toBytes("0090d336b734c301"));

        checkArraysMatch(toBytes("01010000000000000090d336b734c301ffffff00112233440000000002000c0044004f004d00410049004e0001000c005300450052005600450052000400140064006f006d00610069006e002e0063006f006d00030022007300650072007600650072002e0064006f006d00610069006e002e0063006f006d000000000000000000"),
            gen.getNTLMv2Blob());
        checkArraysMatch(toBytes("cbabbca713eb795d04c97abc01ee498301010000000000000090d336b734c301ffffff00112233440000000002000c0044004f004d00410049004e0001000c005300450052005600450052000400140064006f006d00610069006e002e0063006f006d00030022007300650072007600650072002e0064006f006d00610069006e002e0063006f006d000000000000000000"),
            gen.getNTLMv2Response());
    }

    @Test
    public void testLM2SessionResponse() throws Exception {
        NTLMEngineImpl.CipherGen gen = new NTLMEngineImpl.CipherGen(
            "DOMAIN",
            "user",
            "SecREt01",
            toBytes("0123456789abcdef"),
            "DOMAIN",
            toBytes("02000c0044004f004d00410049004e0001000c005300450052005600450052000400140064006f006d00610069006e002e0063006f006d00030022007300650072007600650072002e0064006f006d00610069006e002e0063006f006d0000000000"),
            toBytes("ffffff0011223344"),
            toBytes("ffffff0011223344"),
            null,
            toBytes("0090d336b734c301"));

        checkArraysMatch(toBytes("ffffff001122334400000000000000000000000000000000"),
            gen.getLM2SessionResponse());
    }

    @Test
    public void testNTLM2SessionResponse() throws Exception {
        NTLMEngineImpl.CipherGen gen = new NTLMEngineImpl.CipherGen(
            "DOMAIN",
            "user",
            "SecREt01",
            toBytes("0123456789abcdef"),
            "DOMAIN",
            toBytes("02000c0044004f004d00410049004e0001000c005300450052005600450052000400140064006f006d00610069006e002e0063006f006d00030022007300650072007600650072002e0064006f006d00610069006e002e0063006f006d0000000000"),
            toBytes("ffffff0011223344"),
            toBytes("ffffff0011223344"),
            null,
            toBytes("0090d336b734c301"));

        checkArraysMatch(toBytes("10d550832d12b2ccb79d5ad1f4eed3df82aca4c3681dd455"),
            gen.getNTLM2SessionResponse());
    }

    @Test
    public void testNTLMUserSessionKey() throws Exception {
        NTLMEngineImpl.CipherGen gen = new NTLMEngineImpl.CipherGen(
            "DOMAIN",
            "user",
            "SecREt01",
            toBytes("0123456789abcdef"),
            "DOMAIN",
            toBytes("02000c0044004f004d00410049004e0001000c005300450052005600450052000400140064006f006d00610069006e002e0063006f006d00030022007300650072007600650072002e0064006f006d00610069006e002e0063006f006d0000000000"),
            toBytes("ffffff0011223344"),
            toBytes("ffffff0011223344"),
            null,
            toBytes("0090d336b734c301"));

        checkArraysMatch(toBytes("3f373ea8e4af954f14faa506f8eebdc4"),
            gen.getNTLMUserSessionKey());
    }

    @Test
    public void testType1Message() throws Exception {
        new NTLMEngineImpl().getType1Message("myhost", "mydomain");
    }
    
    @Test
    public void testType3Message() throws Exception {
        new NTLMEngineImpl().getType3Message("me", "mypassword", "myhost", "mydomain",
            toBytes("0001020304050607"),
            0xffffffff,
            null,null);
        new NTLMEngineImpl().getType3Message("me", "mypassword", "myhost", "mydomain",
            toBytes("0001020304050607"),
            0xffffffff,
            "mytarget",
            toBytes("02000c0044004f004d00410049004e0001000c005300450052005600450052000400140064006f006d00610069006e002e0063006f006d00030022007300650072007600650072002e0064006f006d00610069006e002e0063006f006d0000000000"));
    }
    
    @Test
    public void testRC4() throws Exception {
        checkArraysMatch(toBytes("e37f97f2544f4d7e"),
            NTLMEngineImpl.RC4(toBytes("0a003602317a759a"),
                toBytes("2785f595293f3e2813439d73a223810d")));
    }

    /* Byte array check helper */
    static void checkArraysMatch(byte[] a1, byte[] a2)
        throws Exception {
        Assert.assertEquals(a1.length,a2.length);
        for (int i = 0; i < a1.length; i++) {
            Assert.assertEquals(a1[i],a2[i]);
        }
    }
}
