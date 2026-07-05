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

package org.apache.hc.client5.http.entity.mime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.net.PercentCodec;
import org.junit.jupiter.api.Test;

class HttpRFC7578MultipartTest {

    @Test
    void testFieldNameLineBreaksAndQuotesAreNeutralised() throws Exception {
        final FormBodyPart part = FormBodyPartBuilder.create(
                "evil\"\r\nX-Injected: yes",
                new StringBody("value", ContentType.DEFAULT_TEXT),
                HttpMultipartMode.EXTENDED).build();
        final HttpRFC7578Multipart multipart = new HttpRFC7578Multipart(
                StandardCharsets.UTF_8, "BOUNDARY", Collections.singletonList(part),
                HttpMultipartMode.EXTENDED);

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        multipart.writeTo(out);
        final String s = out.toString("ISO-8859-1");

        assertFalse(s.contains("\r\nX-Injected: yes"), s);
        assertTrue(s.contains("name=\"evil\\\"  X-Injected: yes\""), s);
    }

    @Test
    void testPercentDecodingWithValidMessages() {
        final String[][] tests = new String[][] {
                {"test", "test"},
                {"%20", " "},
                {"a%20b", "a b"},
                {"https%3A%2F%2Fhc.apache.org%2Fhttpcomponents-client-5.0.x%2Findex.html",
                        "https://hc.apache.org/httpcomponents-client-5.0.x/index.html"},
                {"%00", "\00"},
                {"%0A", "\n"},

        };
        for (final String[] test : tests) {
            assertEquals(test[1], PercentCodec.RFC5987.decode(test[0]));
        }
    }

}