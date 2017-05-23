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
package org.apache.hc.client5.http.impl.auth;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Random;

import org.junit.Assert;
import org.junit.Test;

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
    static byte toNibble(final char c) {
        if (c >= 'a' && c <= 'f') {
            return (byte) (c - 'a' + 0x0a);
        }
        if (c >= 'A' && c <= 'F') {
            return (byte) (c - 'A' + 0x0a);
        }
        return (byte) (c - '0');
    }

    /* Test suite helper */
    static byte[] toBytes(final String hex) {
        final byte[] rval = new byte[hex.length() / 2];
        int i = 0;
        while (i < rval.length) {
            rval[i] = (byte) ((toNibble(hex.charAt(i * 2)) << 4) | (toNibble(hex
                    .charAt(i * 2 + 1))));
            i++;
        }
        return rval;
    }

    /* Test suite MD4 helper */
    static void checkMD4(final String input, final String hexOutput) throws Exception {
        final NTLMEngineImpl.MD4 md4;
        md4 = new NTLMEngineImpl.MD4();
        md4.update(input.getBytes(StandardCharsets.US_ASCII));
        final byte[] answer = md4.getOutput();
        final byte[] correctAnswer = toBytes(hexOutput);
        if (answer.length != correctAnswer.length) {
            throw new Exception("Answer length disagrees for MD4('" + input + "')");
        }
        int i = 0;
        while (i < answer.length) {
            if (answer[i] != correctAnswer[i]) {
                throw new Exception("Answer value for MD4('" + input + "') disagrees at position "
                        + Integer.toString(i));
            }
            i++;
        }
    }

    @Test
    public void testLMResponse() throws Exception {
        final NTLMEngineImpl.CipherGen gen = new NTLMEngineImpl.CipherGen(
            new Random(1234),
            1234L,
            null,
            null,
            "SecREt01".toCharArray(),
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
        final NTLMEngineImpl.CipherGen gen = new NTLMEngineImpl.CipherGen(
            new Random(1234),
            1234L,
            null,
            null,
            "SecREt01".toCharArray(),
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
        final NTLMEngineImpl.CipherGen gen = new NTLMEngineImpl.CipherGen(
            new Random(1234),
            1234L,
            "DOMAIN",
            "user",
            "SecREt01".toCharArray(),
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
        final NTLMEngineImpl.CipherGen gen = new NTLMEngineImpl.CipherGen(
            new Random(1234),
            1234L,
            "DOMAIN",
            "user",
            "SecREt01".toCharArray(),
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
        final NTLMEngineImpl.CipherGen gen = new NTLMEngineImpl.CipherGen(
            new Random(1234),
            1234L,
            "DOMAIN",
            "user",
            "SecREt01".toCharArray(),
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
        final NTLMEngineImpl.CipherGen gen = new NTLMEngineImpl.CipherGen(
            new Random(1234),
            1234L,
            "DOMAIN",
            "user",
            "SecREt01".toCharArray(),
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
        final NTLMEngineImpl.CipherGen gen = new NTLMEngineImpl.CipherGen(
            new Random(1234),
            1234L,
            "DOMAIN",
            "user",
            "SecREt01".toCharArray(),
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
        final byte[] bytes = new NTLMEngineImpl.Type1Message("myhost", "mydomain").getBytes();
        final byte[] bytes2 = toBytes("4E544C4D5353500001000000018208A20C000C003800000010001000280000000501280A0000000F6D00790064006F006D00610069006E004D00590048004F0053005400");
        checkArraysMatch(bytes2, bytes);
    }

    @Test
    public void testType3Message() throws Exception {
        final byte[] bytes = new NTLMEngineImpl.Type3Message(
                new Random(1234),
                1234L,
                "me", "mypassword", "myhost", "mydomain".toCharArray(),
                toBytes("0001020304050607"),
                0xffffffff,
                null,null).getBytes();
        checkArraysMatch(toBytes("4E544C4D53535000030000001800180048000000180018006000000004000400780000000C000C007C0000001400140088000000100010009C000000FFFFFFFF0501280A0000000FA86886A5D297814200000000000000000000000000000000EEC7568E00798491244959B9C942F4F367C5CBABEEF546F74D0045006D00790068006F00730074006D007900700061007300730077006F007200640094DDAB1EBB82C9A1AB914CAE6F199644"),
            bytes);
        final byte[] bytes2 = new NTLMEngineImpl.Type3Message(
                new Random(1234),
                1234L,
                "me", "mypassword", "myhost", "mydomain".toCharArray(),
                toBytes("0001020304050607"),
                0xffffffff,
                "mytarget",
                toBytes("02000c0044004f004d00410049004e0001000c005300450052005600450052000400140064006f006d00610069006e002e0063006f006d00030022007300650072007600650072002e0064006f006d00610069006e002e0063006f006d0000000000")).getBytes();
        checkArraysMatch(toBytes("4E544C4D53535000030000001800180048000000920092006000000004000400F20000000C000C00F600000014001400020100001000100016010000FFFFFFFF0501280A0000000F3695F1EA7B164788A437892FA7504320DA2D8CF378EBC83CE856A8FB985BF7783545828A91A13AE8010100000000000020CBFAD5DEB19D01A86886A5D29781420000000002000C0044004F004D00410049004E0001000C005300450052005600450052000400140064006F006D00610069006E002E0063006F006D00030022007300650072007600650072002E0064006F006D00610069006E002E0063006F006D0000000000000000004D0045006D00790068006F00730074006D007900700061007300730077006F0072006400BB1AAD36F11631CC7CBC8800CEEE1C99"),
            bytes2);
    }

    private static final String cannedCert =
        "-----BEGIN CERTIFICATE-----\n"+
        "MIIDIDCCAgigAwIBAgIEOqKaWTANBgkqhkiG9w0BAQsFADBSMQswCQYDVQQGEwJVUzEQMA4GA1UEBxMH\n"+
        "TXkgQ2l0eTEYMBYGA1UEChMPTXkgT3JnYW5pemF0aW9uMRcwFQYDVQQDEw5NeSBBcHBsaWNhdGlvbjAe\n"+
        "Fw0xNzAzMTcxNDAyMzRaFw0yNzAzMTUxNDAyMzRaMFIxCzAJBgNVBAYTAlVTMRAwDgYDVQQHEwdNeSBD\n"+
        "aXR5MRgwFgYDVQQKEw9NeSBPcmdhbml6YXRpb24xFzAVBgNVBAMTDk15IEFwcGxpY2F0aW9uMIIBIjAN\n"+
        "BgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEArc+mbViBaHeRSt82KrJ5IF+62b/Qob95Lca4DJIislTY\n"+
        "vLPIo0R1faBV8BkEeUQwo01srkf3RaGLCHNZnFal4KEzbtiUy6W+n08G5E9w9YG+WSwW2dmjvEI7k2a2\n"+
        "xqlaM4NdMKL4ONPXcxfZsMDqxDgpdkaNPKpZ10NDq6rmBTkQw/OSG0z1KLtwLkF1ZQ/3mXdjVzvP83V2\n"+
        "g17AqBazb0Z1YHsVKmkGjPqnq3niJH/6Oke4N+5k/1cE5lSJcQNGP0nqeGdJfvqQZ+gk6gH/sOngZL9X\n"+
        "hPVkpseAwHa+xuPneDSjibLgLmMt3XGDK6jGfjdp5FWqFvAD5E3LHbW9gwIDAQABMA0GCSqGSIb3DQEB\n"+
        "CwUAA4IBAQCpUXUHhl5LyMSO5Q0OktEc9AaFjZtVfknpPde6Zeh35Pqd2354ErvJSBWgzFAphda0oh2s\n"+
        "OIAFkM6LJQEnVDTbXDXN+YY8e3gb9ryfh85hkhC0XI9qp17WPSkmw8XgDfvRd6YQgKm1AnLxjOCwG2jg\n"+
        "i09iZBIWkW3ZeRAMvWPHHjvq44iZB5ZrEl0apgumS6MxpUzKOr5Pcq0jxJDw2UCj5YloFMNl+UINv2vV\n"+
        "aL/DR6ivc61dOfN1E/VNBGkkCk/AogNyucGiFMCq9hd25Y9EbkBBqObYTH1XMX+ufsJh+6hG7KDQ1e/F\n"+
        "nRrlhKwM2uRe+aSH0D6/erjDBT7tXvwn\n"+
        "-----END CERTIFICATE-----";

    @Test
    public void testType3MessageWithCert() throws Exception {
        final ByteArrayInputStream fis = new ByteArrayInputStream(cannedCert.getBytes(StandardCharsets.US_ASCII));

        final CertificateFactory cf = CertificateFactory.getInstance("X.509");

        final Certificate cert = cf.generateCertificate(fis);

        final byte[] bytes = new NTLMEngineImpl.Type3Message(
                new Random(1234),
                1234L,
                "me", "mypassword", "myhost", "mydomain".toCharArray(),
                toBytes("0001020304050607"),
                0xffffffff,
                "mytarget",
                toBytes("02000c0044004f004d00410049004e0001000c005300450052005600450052000400140064006f006d00610069006e002e0063006f006d00030022007300650072007600650072002e0064006f006d00610069006e002e0063006f006d0000000000"),
                cert,
                toBytes("4E544C4D5353500001000000018208A20C000C003800000010001000280000000501280A0000000F6D00790064006F006D00610069006E004D00590048004F0053005400"),
                toBytes("4E544C4D5353500001000000018208A20C000C003800000010001000280000000501280A0000000F6D00790064006F006D00610069006E004D00590048004F0053005400FFFEFDFCFBFA")).getBytes();

        checkArraysMatch(toBytes("4E544C4D53535000030000001800180058000000AE00AE0070000000040004001E0100000C000C0022010000140014002E0100001000100042010000FFFFFFFF0501280A0000000FEEFCCE4187D6CDF1F91C686C4E571D943695F1EA7B164788A437892FA7504320DA2D8CF378EBC83C59D7A3B2951929079B66621D7CF4326B010100000000000020CBFAD5DEB19D01A86886A5D29781420000000002000C0044004F004D00410049004E0001000C005300450052005600450052000400140064006F006D00610069006E002E0063006F006D00030022007300650072007600650072002E0064006F006D00610069006E002E0063006F006D0006000400020000000A00100038EDC0B7EF8D8FE9E1E6A83F6DFEB8FF00000000000000004D0045006D00790068006F00730074006D007900700061007300730077006F0072006400BB1AAD36F11631CC7CBC8800CEEE1C99"),
            bytes);
    }

    @Test
    public void testRC4() throws Exception {
        checkArraysMatch(toBytes("e37f97f2544f4d7e"),
            NTLMEngineImpl.RC4(toBytes("0a003602317a759a"),
                toBytes("2785f595293f3e2813439d73a223810d")));
    }

    /* Byte array check helper */
    static void checkArraysMatch(final byte[] a1, final byte[] a2)
        throws Exception {
        Assert.assertEquals(a1.length,a2.length);
        for (int i = 0; i < a1.length; i++) {
            Assert.assertEquals(a1[i],a2[i]);
        }
    }

}
