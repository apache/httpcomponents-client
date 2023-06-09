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
package org.apache.hc.client5.http.cache;

import java.util.Set;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.support.BasicResponseBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestCacheHeaderSupport {

    @Test
    public void testHopByHopHeaders() {
        Assertions.assertTrue(CacheHeaderSupport.isHopByHop("Connection"));
        Assertions.assertTrue(CacheHeaderSupport.isHopByHop("connection"));
        Assertions.assertTrue(CacheHeaderSupport.isHopByHop("coNNection"));
        Assertions.assertFalse(CacheHeaderSupport.isHopByHop("Content-Type"));
        Assertions.assertFalse(CacheHeaderSupport.isHopByHop("huh"));
    }

    @Test
    public void testHopByHopHeadersConnectionSpecific() {
        final HttpResponse response = BasicResponseBuilder.create(HttpStatus.SC_OK)
                .addHeader(HttpHeaders.CONNECTION, "blah, blah, this, that")
                .addHeader(HttpHeaders.CONTENT_TYPE, ContentType.TEXT_PLAIN.toString())
                .build();
        final Set<String> hopByHopConnectionSpecific = CacheHeaderSupport.hopByHopConnectionSpecific(response);
        Assertions.assertTrue(hopByHopConnectionSpecific.contains("Connection"));
        Assertions.assertTrue(hopByHopConnectionSpecific.contains("connection"));
        Assertions.assertTrue(hopByHopConnectionSpecific.contains("coNNection"));
        Assertions.assertFalse(hopByHopConnectionSpecific.contains("Content-Type"));
        Assertions.assertTrue(hopByHopConnectionSpecific.contains("blah"));
        Assertions.assertTrue(hopByHopConnectionSpecific.contains("Blah"));
        Assertions.assertTrue(hopByHopConnectionSpecific.contains("This"));
        Assertions.assertTrue(hopByHopConnectionSpecific.contains("That"));
    }

}
