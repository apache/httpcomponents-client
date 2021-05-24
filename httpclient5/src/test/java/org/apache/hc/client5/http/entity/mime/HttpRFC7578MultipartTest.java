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

import static org.junit.Assert.assertEquals;

import org.apache.commons.codec.DecoderException;
import org.junit.Assert;
import org.junit.Test;

public class HttpRFC7578MultipartTest {

    @Test
    public void testPercentDecodingWithTooShortMessage() throws Exception {
        Assert.assertThrows(DecoderException.class, () ->
                new HttpRFC7578Multipart.PercentCodec().decode("%".getBytes()));
    }

    @Test
    public void testPercentDecodingWithValidMessages() throws Exception {
        final HttpRFC7578Multipart.PercentCodec codec = new HttpRFC7578Multipart.PercentCodec();
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
            assertEquals(test[1], new String(codec.decode(test[0].getBytes())));
        }
    }

}