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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;

import org.apache.hc.client5.http.utils.CodingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class RFC5987CodecTest {

    @ParameterizedTest
    @MethodSource("params")
    public void testRfc5987EncodingDecoding(final String input, final String expected) throws CodingException {
        assertEquals(expected, RFC5987Codec.encode(input));
        assertEquals(input, RFC5987Codec.decode(expected));
    }

    static Stream<Object[]> params() {
        return Stream.of(
                new Object[]{"foo-ä-€.html", "foo-%C3%A4-%E2%82%AC.html"},
                new Object[]{"世界ーファイル 2.jpg", "%E4%B8%96%E7%95%8C%E3%83%BC%E3%83%95%E3%82%A1%E3%82%A4%E3%83%AB%202.jpg"},
                new Object[]{"foo.jpg", "foo.jpg"},
                new Object[]{"simple", "simple"},  // Unreserved characters
                new Object[]{"reserved/chars?", "reserved%2Fchars%3F"},  // Reserved characters
                new Object[]{"", ""},  // Empty string
                new Object[]{"space test", "space%20test"},  // String with space
                new Object[]{"ümlaut", "%C3%BCmlaut"}  // Non-ASCII characters
        );
    }

    @Test
    public void verifyRfc5987EncodingandDecoding() throws CodingException {
        final String s = "!\"$£%^&*()_-+={[}]:@~;'#,./<>?\\|✓éèæðŃœ";
        assertThat(RFC5987Codec.decode(RFC5987Codec.encode(s)), equalTo(s));
    }
}
