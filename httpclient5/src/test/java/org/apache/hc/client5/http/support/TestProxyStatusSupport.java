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

package org.apache.hc.client5.http.support;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.hc.core5.http.ParseException;
import org.apache.hc.client5.http.ProxyStatus;
import org.apache.hc.client5.http.ProxyStatusError;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestProxyStatusSupport {

    @Test
    void testValidParameters() throws Exception {
        final List<ProxyStatus> members = ProxyStatusSupport.parse(
                "ExampleProxy; error=connection_timeout; next-hop=\"backend.example.net\"; "
                        + "next-protocol=h2; received-status=504; details=\"timed out\"");
        Assertions.assertEquals(1, members.size());
        final ProxyStatus member = members.get(0);
        Assertions.assertEquals("ExampleProxy", member.getName());
        Assertions.assertEquals(ProxyStatusError.CONNECTION_TIMEOUT, member.getError());
        Assertions.assertEquals("connection_timeout", member.getErrorToken());
        Assertions.assertEquals("backend.example.net", member.getNextHop());
        Assertions.assertEquals("h2", member.getNextProtocol());
        Assertions.assertEquals(Integer.valueOf(504), member.getReceivedStatus());
        Assertions.assertEquals("timed out", member.getDetails());
    }

    @Test
    void testMultipleListMembers() throws Exception {
        final List<ProxyStatus> members = ProxyStatusSupport.parse(
                "cdn.example.org; error=http_request_denied, \"proxy.example.net\"; received-status=503");
        Assertions.assertEquals(2, members.size());
        Assertions.assertEquals("cdn.example.org", members.get(0).getName());
        Assertions.assertEquals(ProxyStatusError.HTTP_REQUEST_DENIED, members.get(0).getError());
        Assertions.assertEquals("proxy.example.net", members.get(1).getName());
        Assertions.assertEquals(Integer.valueOf(503), members.get(1).getReceivedStatus());
    }

    @Test
    void testUnknownExtensionParameters() throws Exception {
        final ProxyStatus member = ProxyStatusSupport.parse(
                "ExampleProxy; x-token=custom; x-str=\"custom\"; x-count=3; x-ratio=1.5; x-flag; x-off=?0").get(0);
        Assertions.assertEquals(new ProxyStatus.Token("custom"), member.getParameter("x-token"));
        Assertions.assertEquals("custom", member.getParameter("x-str"));
        Assertions.assertEquals(Long.valueOf(3), member.getParameter("x-count"));
        Assertions.assertEquals(new BigDecimal("1.5"), member.getParameter("x-ratio"));
        Assertions.assertEquals(Boolean.TRUE, member.getParameter("x-flag"));
        Assertions.assertEquals(Boolean.FALSE, member.getParameter("x-off"));
        Assertions.assertNull(member.getError());
        Assertions.assertNull(member.getReceivedStatus());
    }

    @Test
    void testStandardizedErrors() throws Exception {
        Assertions.assertEquals(ProxyStatusError.DNS_TIMEOUT,
                ProxyStatusSupport.parse("p; error=dns_timeout").get(0).getError());
        Assertions.assertEquals(ProxyStatusError.TLS_CERTIFICATE_ERROR,
                ProxyStatusSupport.parse("p; error=tls_certificate_error").get(0).getError());
        Assertions.assertEquals(ProxyStatusError.PROXY_LOOP_DETECTED,
                ProxyStatusSupport.parse("p; error=proxy_loop_detected").get(0).getError());
    }

    @Test
    void testUnregisteredErrorTokenPreserved() throws Exception {
        final ProxyStatus member = ProxyStatusSupport.parse("p; error=some_new_error").get(0);
        Assertions.assertNull(member.getError());
        Assertions.assertEquals("some_new_error", member.getErrorToken());
    }

    @Test
    void testProxyStatusErrorFromToken() {
        Assertions.assertEquals(ProxyStatusError.CONNECTION_REFUSED, ProxyStatusError.fromToken("connection_refused"));
        Assertions.assertEquals("connection_refused", ProxyStatusError.CONNECTION_REFUSED.getToken());
        Assertions.assertNull(ProxyStatusError.fromToken("not_a_registered_error"));
        Assertions.assertNull(ProxyStatusError.fromToken(null));
    }

    // Fix 1: the 8 error values added from RFC 9209.

    @Test
    void testNewlyAddedErrorValues() throws Exception {
        Assertions.assertEquals(32, ProxyStatusError.values().length);
        Assertions.assertEquals(ProxyStatusError.HTTP_RESPONSE_HEADER_SECTION_SIZE,
                ProxyStatusSupport.parse("p; error=http_response_header_section_size").get(0).getError());
        Assertions.assertEquals(ProxyStatusError.HTTP_RESPONSE_BODY_SIZE,
                ProxyStatusSupport.parse("p; error=http_response_body_size").get(0).getError());
        Assertions.assertEquals(ProxyStatusError.HTTP_RESPONSE_CONTENT_CODING,
                ProxyStatusSupport.parse("p; error=http_response_content_coding").get(0).getError());
        Assertions.assertEquals(ProxyStatusError.HTTP_RESPONSE_TIMEOUT,
                ProxyStatusSupport.parse("p; error=http_response_timeout").get(0).getError());
    }

    // Fix 2: Token and String kept as distinct Java types, standardized parameter types enforced.

    @Test
    void testTokenAndStringPreservedAsDistinctTypes() throws Exception {
        final ProxyStatus member = ProxyStatusSupport.parse("p; x-a=token; x-b=\"token\"").get(0);
        Assertions.assertTrue(member.getParameter("x-a") instanceof ProxyStatus.Token);
        Assertions.assertTrue(member.getParameter("x-b") instanceof String);
        Assertions.assertNotEquals(member.getParameter("x-a"), member.getParameter("x-b"));
    }

    @Test
    void testErrorMustBeToken() {
        Assertions.assertThrows(ParseException.class, () -> ProxyStatusSupport.parse("p; error=\"connection_refused\""));
    }

    @Test
    void testDetailsMustBeString() {
        Assertions.assertThrows(ParseException.class, () -> ProxyStatusSupport.parse("p; details=plain"));
    }

    @Test
    void testNextProtocolMustBeTokenOrByteSequence() {
        Assertions.assertThrows(ParseException.class, () -> ProxyStatusSupport.parse("p; next-protocol=\"h2\""));
        Assertions.assertThrows(ParseException.class, () -> ProxyStatusSupport.parse("p; next-protocol=3"));
    }

    // Fix 3: next-protocol Byte Sequence values.

    @Test
    void testNextProtocolToken() throws Exception {
        Assertions.assertEquals("h2", ProxyStatusSupport.parse("p; next-protocol=h2").get(0).getNextProtocol());
    }

    @Test
    void testNextProtocolByteSequence() throws Exception {
        // ":AAEC:" is the base64 of the bytes {0, 1, 2}, which cannot be expressed as an ASCII Token.
        final byte[] expected = {0, 1, 2};
        final ProxyStatus member = ProxyStatusSupport.parse("p; next-protocol=:AAEC:").get(0);
        Assertions.assertNull(member.getNextProtocol());
        Assertions.assertArrayEquals(expected, (byte[]) member.getParameter("next-protocol"));
    }

    // Fix 4: received-status validated as an HTTP status code, no integer overflow.

    @Test
    void testReceivedStatusValid() throws Exception {
        Assertions.assertEquals(Integer.valueOf(200),
                ProxyStatusSupport.parse("p; received-status=200").get(0).getReceivedStatus());
    }

    @Test
    void testReceivedStatusBelowRangeRejected() {
        Assertions.assertThrows(ParseException.class, () -> ProxyStatusSupport.parse("p; received-status=99"));
    }

    @Test
    void testReceivedStatusAboveRangeRejected() {
        Assertions.assertThrows(ParseException.class, () -> ProxyStatusSupport.parse("p; received-status=600"));
    }

    @Test
    void testReceivedStatusOverflowRejected() {
        // 4294967596 truncates to 300 as an int; it must be rejected, not silently narrowed.
        Assertions.assertThrows(ParseException.class, () -> ProxyStatusSupport.parse("p; received-status=4294967596"));
    }

    // Fix 5: genuine immutability, including defensive handling of byte[] values.

    @Test
    void testByteSequenceValuesAreDefensivelyCopied() throws Exception {
        final ProxyStatus member = ProxyStatusSupport.parse("p; next-protocol=:AAEC:").get(0);
        final byte[] expected = {0, 1, 2};

        final byte[] fromGetParameter = (byte[]) member.getParameter("next-protocol");
        fromGetParameter[0] = 9;
        Assertions.assertArrayEquals(expected, (byte[]) member.getParameter("next-protocol"));

        final byte[] fromGetParameters = (byte[]) member.getParameters().get("next-protocol");
        fromGetParameters[0] = 9;
        Assertions.assertArrayEquals(expected, (byte[]) member.getParameter("next-protocol"));
    }

    @Test
    void testConstructorCopiesByteArray() {
        final Map<String, Object> params = new LinkedHashMap<>();
        final byte[] raw = {1, 2, 3};
        params.put("x-bin", raw);
        final ProxyStatus member = new ProxyStatus("p", params);
        raw[0] = 9;
        Assertions.assertArrayEquals(new byte[] {1, 2, 3}, (byte[]) member.getParameter("x-bin"));
    }

    @Test
    void testParametersMapIsUnmodifiable() throws Exception {
        final ProxyStatus member = ProxyStatusSupport.parse("p; x-a=1").get(0);
        Assertions.assertThrows(UnsupportedOperationException.class, () -> member.getParameters().put("x-b", "v"));
    }

    // Fix (this round) 1: RFC-defined error-specific parameter types are validated.

    @Test
    void testErrorSpecificParametersAccepted() throws Exception {
        final ProxyStatus member = ProxyStatusSupport.parse(
                "p; error=dns_error; rcode=\"NXDOMAIN\"; info-code=15").get(0);
        Assertions.assertEquals("NXDOMAIN", member.getParameter("rcode"));
        Assertions.assertEquals(Long.valueOf(15), member.getParameter("info-code"));
        Assertions.assertEquals(new ProxyStatus.Token("gzip"),
                ProxyStatusSupport.parse("p; coding=gzip").get(0).getParameter("coding"));
        // alert-message accepts either a Token or a String
        Assertions.assertEquals(new ProxyStatus.Token("close_notify"),
                ProxyStatusSupport.parse("p; alert-message=close_notify").get(0).getParameter("alert-message"));
        Assertions.assertEquals("close notify",
                ProxyStatusSupport.parse("p; alert-message=\"close notify\"").get(0).getParameter("alert-message"));
    }

    @Test
    void testErrorSpecificParameterTypesEnforced() {
        // rcode is a String; a Token is rejected
        Assertions.assertThrows(ParseException.class, () -> ProxyStatusSupport.parse("p; rcode=NXDOMAIN"));
        // info-code is an Integer; a String is rejected
        Assertions.assertThrows(ParseException.class, () -> ProxyStatusSupport.parse("p; info-code=\"15\""));
        // coding is a Token; a String is rejected
        Assertions.assertThrows(ParseException.class, () -> ProxyStatusSupport.parse("p; coding=\"gzip\""));
        // status-code is an Integer; a decimal is rejected
        Assertions.assertThrows(ParseException.class, () -> ProxyStatusSupport.parse("p; status-code=2.0"));
        // header-name is a String; a Token is rejected
        Assertions.assertThrows(ParseException.class, () -> ProxyStatusSupport.parse("p; header-name=Location"));
    }

    @Test
    void testUnrelatedExtensionParametersStillIgnored() throws Exception {
        // extension parameters are not type-checked, whatever their value type
        final ProxyStatus member = ProxyStatusSupport.parse("p; x-size=\"not-an-int\"; x-flag=maybe").get(0);
        Assertions.assertEquals("not-an-int", member.getParameter("x-size"));
        Assertions.assertEquals(new ProxyStatus.Token("maybe"), member.getParameter("x-flag"));
    }

    // Fix (this round) 2: only supported Structured Fields value types are accepted.

    @Test
    void testConstructorRejectsUnsupportedMutableValue() {
        final Map<String, Object> params = new LinkedHashMap<>();
        params.put("x-bad", new StringBuilder("mutable"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new ProxyStatus("p", params));
    }

    @Test
    void testConstructorRejectsIntegerInsteadOfLong() {
        final Map<String, Object> params = new LinkedHashMap<>();
        params.put("x-num", Integer.valueOf(3));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new ProxyStatus("p", params));
    }

    @Test
    void testConstructorAcceptsSupportedTypes() {
        final Map<String, Object> params = new LinkedHashMap<>();
        params.put("a", "s");
        params.put("b", new ProxyStatus.Token("t"));
        params.put("c", Long.valueOf(1));
        params.put("d", new BigDecimal("1.5"));
        params.put("e", Boolean.TRUE);
        params.put("f", new byte[] {1, 2});
        Assertions.assertEquals(6, new ProxyStatus("p", params).getParameters().size());
    }

    // Structured Fields grammar and error handling.

    @Test
    void testQuotedStringIdentityWithEscapes() throws Exception {
        final ProxyStatus member = ProxyStatusSupport.parse("\"a \\\"quoted\\\" proxy\"").get(0);
        Assertions.assertEquals("a \"quoted\" proxy", member.getName());
        Assertions.assertTrue(member.getParameters().isEmpty());
    }

    @Test
    void testBooleanParameterWithoutValue() throws Exception {
        final ProxyStatus member = ProxyStatusSupport.parse("p; cached").get(0);
        Assertions.assertEquals(Boolean.TRUE, member.getParameter("cached"));
    }

    @Test
    void testEmptyValueYieldsEmptyList() throws Exception {
        Assertions.assertTrue(ProxyStatusSupport.parse("").isEmpty());
        Assertions.assertTrue(ProxyStatusSupport.parse("   ").isEmpty());
    }

    @Test
    void testMalformedTrailingComma() {
        Assertions.assertThrows(ParseException.class, () -> ProxyStatusSupport.parse("p,"));
    }

    @Test
    void testMalformedUnterminatedString() {
        Assertions.assertThrows(ParseException.class, () -> ProxyStatusSupport.parse("p; details=\"open"));
    }

    @Test
    void testMalformedIdentityNotTokenOrString() {
        Assertions.assertThrows(ParseException.class, () -> ProxyStatusSupport.parse("504"));
    }

    @Test
    void testMalformedInvalidBoolean() {
        Assertions.assertThrows(ParseException.class, () -> ProxyStatusSupport.parse("p; flag=?2"));
    }

    @Test
    void testMalformedGarbageAfterMember() {
        Assertions.assertThrows(ParseException.class, () -> ProxyStatusSupport.parse("p q"));
    }

    @Test
    void testMalformedMissingParameterName() {
        Assertions.assertThrows(ParseException.class, () -> ProxyStatusSupport.parse("p; =bad"));
    }

    @Test
    void testNullValueRejected() {
        Assertions.assertThrows(NullPointerException.class, () -> ProxyStatusSupport.parse(null));
    }

}
