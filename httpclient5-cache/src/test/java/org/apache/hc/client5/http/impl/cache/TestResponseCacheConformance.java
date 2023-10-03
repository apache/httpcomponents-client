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

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Instant;

import org.apache.hc.client5.http.utils.DateUtils;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.support.BasicResponseBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TestResponseCacheConformance {

    private ResponseCacheConformance impl;

    @BeforeEach
    public void setUp() {
        impl = ResponseCacheConformance.INSTANCE;
    }

    private void shouldStripEntityHeaderFromOrigin304ResponseToStrongValidation(
            final String entityHeader, final String entityHeaderValue) throws Exception {
        final HttpResponse response = BasicResponseBuilder.create(HttpStatus.SC_NOT_MODIFIED)
                .addHeader("Date", DateUtils.formatStandardDate(Instant.now()))
                .addHeader("Etag", "\"etag\"")
                .addHeader(entityHeader, entityHeaderValue)
                .build();
        impl.process(response, null, null);
        assertFalse(response.containsHeader(entityHeader));
    }

    @Test
    public void shouldStripContentEncodingFromOrigin304ResponseToStrongValidation() throws Exception {
        shouldStripEntityHeaderFromOrigin304ResponseToStrongValidation(
                "Content-Encoding", "gzip");
    }

    @Test
    public void shouldStripContentLanguageFromOrigin304ResponseToStrongValidation() throws Exception {
        shouldStripEntityHeaderFromOrigin304ResponseToStrongValidation(
                "Content-Language", "en");
    }

    @Test
    public void shouldStripContentLengthFromOrigin304ResponseToStrongValidation() throws Exception {
        shouldStripEntityHeaderFromOrigin304ResponseToStrongValidation(
                "Content-Length", "128");
    }

    @Test
    public void shouldStripContentMD5FromOrigin304ResponseToStrongValidation() throws Exception {
        shouldStripEntityHeaderFromOrigin304ResponseToStrongValidation(
                "Content-MD5", "Q2hlY2sgSW50ZWdyaXR5IQ==");
    }

    @Test
    public void shouldStripContentTypeFromOrigin304ResponseToStrongValidation() throws Exception {
        shouldStripEntityHeaderFromOrigin304ResponseToStrongValidation(
                "Content-Type", "text/html;charset=utf-8");
    }

}
