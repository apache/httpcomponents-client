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
package org.apache.hc.client5.http.impl;

import java.util.Arrays;
import java.util.Set;

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.impl.BasicEntityDetails;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ContentCodingSupportTest {

    static class MockEntityDetails implements EntityDetails {

        private final String contentEncoding;

        MockEntityDetails(final String contentEncoding) {
            this.contentEncoding = contentEncoding;
        }

        @Override
        public long getContentLength() {
            return -1;
        }

        @Override
        public String getContentType() {
            return null;
        }

        @Override
        public String getContentEncoding() {
            return contentEncoding;
        }

        @Override
        public boolean isChunked() {
            return false;
        }

        @Override
        public Set<String> getTrailerNames() {
            return null;
        }

    }

    @Test
    void testNoEntity() {
        Assertions.assertTrue(ContentCodingSupport.parseContentCodecs(null).isEmpty());
    }

    @Test
    void testNotEncoded() {
        Assertions.assertTrue(ContentCodingSupport.parseContentCodecs(
                new BasicEntityDetails(-1, null)).isEmpty());
    }

    @Test
    void testNotEncodedNoise() {
        Assertions.assertTrue(ContentCodingSupport.parseContentCodecs(
                new MockEntityDetails(", ,,   ,  ")).isEmpty());
    }

    @Test
    void testIdentityEncoded() {
        Assertions.assertTrue(ContentCodingSupport.parseContentCodecs(
                new MockEntityDetails("identity,,,identity")).isEmpty());
    }

    @Test
    void testEncodedMultipleCodes() {
        Assertions.assertEquals(Arrays.asList("this", "that", "\"this and that\""),
                ContentCodingSupport.parseContentCodecs(
                        new MockEntityDetails("This,,that,  \"This and That\"")));
    }

}
