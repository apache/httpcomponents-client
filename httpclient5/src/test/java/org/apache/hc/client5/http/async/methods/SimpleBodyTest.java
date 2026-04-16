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
package org.apache.hc.client5.http.async.methods;

import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.ContentType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestSimpleBody {

    @Test
    void testGetBodyTextUsesUtf8ForJsonWithoutCharsetParameter() {
        final String message = "{\"msg\": \"Test emoji 👋\"}";
        final SimpleBody body = SimpleBody.create(
                message.getBytes(StandardCharsets.UTF_8),
                ContentType.parse("application/json"));

        Assertions.assertEquals(message, body.getBodyText());
    }

    @Test
    void testGetBodyBytesUsesUtf8ForJsonWithoutCharsetParameter() {
        final String message = "{\"msg\": \"Test emoji 👋\"}";
        final SimpleBody body = SimpleBody.create(
                message,
                ContentType.parse("application/json"));

        Assertions.assertArrayEquals(message.getBytes(StandardCharsets.UTF_8), body.getBodyBytes());
    }

    @Test
    void testGetBodyTextUsesUtf8ForProblemJsonWithoutCharsetParameter() {
        final String message = "{\"title\": \"Bad request 👋\"}";
        final SimpleBody body = SimpleBody.create(
                message.getBytes(StandardCharsets.UTF_8),
                ContentType.parse("application/problem+json"));

        Assertions.assertEquals(message, body.getBodyText());
    }

    @Test
    void testExplicitCharsetStillWins() {
        final String message = "{\"msg\": \"hi\"}";
        final byte[] utf16 = message.getBytes(StandardCharsets.UTF_16);
        final SimpleBody body = SimpleBody.create(
                utf16,
                ContentType.parse("application/json; charset=UTF-16"));

        Assertions.assertEquals(message, body.getBodyText());
    }
}