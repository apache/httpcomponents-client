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

package org.apache.hc.client5.http.classic.methods;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.hc.client5.http.entity.EntityBuilder;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestHttpRequestBase {

    private static final String HOT_URL = "http://host/path";

    @Test
    public void testBasicGetMethodProperties() throws Exception {
        final HttpGet httpget = new HttpGet(HOT_URL);
        Assertions.assertEquals("GET", httpget.getMethod());
        Assertions.assertEquals(new URI(HOT_URL), httpget.getUri());
    }

    @Test
    public void testBasicHttpPostMethodProperties() throws Exception {
        final HttpPost HttpPost = new HttpPost(HOT_URL);
        Assertions.assertEquals("POST", HttpPost.getMethod());
        Assertions.assertEquals(new URI(HOT_URL), HttpPost.getUri());
    }

    @Test
    public void testBasicHttpHeadMethodProperties() throws Exception {
        final HttpHead httpHead = new HttpHead(HOT_URL);
        Assertions.assertEquals("HEAD", httpHead.getMethod());
        Assertions.assertEquals(new URI(HOT_URL), httpHead.getUri());
    }

    @Test
    public void testBasicHttpOptionMethodProperties() throws Exception {
        final HttpOptions httpOption = new HttpOptions(HOT_URL);
        Assertions.assertEquals("OPTIONS", httpOption.getMethod());
        Assertions.assertEquals(new URI(HOT_URL), httpOption.getUri());
    }

    @Test
    public void testBasicHttpPatchMethodProperties() throws Exception {
        final HttpPatch httpPatch = new HttpPatch(HOT_URL);
        Assertions.assertEquals("PATCH", httpPatch.getMethod());
        Assertions.assertEquals(new URI(HOT_URL), httpPatch.getUri());
    }

    @Test
    public void testBasicHttpPutMethodProperties() throws Exception {
        final HttpPut httpPut = new HttpPut(HOT_URL);
        Assertions.assertEquals("PUT", httpPut.getMethod());
        Assertions.assertEquals(new URI(HOT_URL), httpPut.getUri());
    }

    @Test
    public void testBasicHttpTraceMethodProperties() throws Exception {
        final HttpTrace httpTrace = new HttpTrace(HOT_URL);
        Assertions.assertEquals("TRACE", httpTrace.getMethod());
        Assertions.assertEquals(new URI(HOT_URL), httpTrace.getUri());
    }


    @Test
    public void testBasicHttpDeleteMethodProperties() throws Exception {
        final HttpDelete httpDelete = new HttpDelete(HOT_URL);
        Assertions.assertEquals("DELETE", httpDelete.getMethod());
        Assertions.assertEquals(new URI(HOT_URL), httpDelete.getUri());
    }


    @Test
    public void testGetMethodEmptyURI() throws Exception {
        final HttpGet httpget = new HttpGet("");
        Assertions.assertEquals(new URI("/"), httpget.getUri());
    }

    @Test
    public void testPostMethodEmptyURI() throws Exception {
        final HttpPost HttpPost = new HttpPost("");
        Assertions.assertEquals(new URI("/"), HttpPost.getUri());
    }

    @Test
    public void testHeadMethodEmptyURI() throws Exception {
        final HttpHead httpHead = new HttpHead("");
        Assertions.assertEquals(new URI("/"), httpHead.getUri());
    }

    @Test
    public void testOptionMethodEmptyURI() throws Exception {
        final HttpOptions httpOption = new HttpOptions("");
        Assertions.assertEquals(new URI("/"), httpOption.getUri());
    }

    @Test
    public void testPatchMethodEmptyURI() throws Exception {
        final HttpPatch httpPatch = new HttpPatch("");
        Assertions.assertEquals(new URI("/"), httpPatch.getUri());
    }

    @Test
    public void testPutMethodEmptyURI() throws Exception {
        final HttpPut httpPut = new HttpPut("");
        Assertions.assertEquals(new URI("/"), httpPut.getUri());
    }

    @Test
    public void testTraceMethodEmptyURI() throws Exception {
        final HttpTrace httpTrace = new HttpTrace("");
        Assertions.assertEquals(new URI("/"), httpTrace.getUri());
    }


    @Test
    public void testDeleteMethodEmptyURI() throws Exception {
        final HttpDelete httpDelete = new HttpDelete("");
        Assertions.assertEquals(new URI("/"), httpDelete.getUri());
    }


    @Test
    public void testTraceMethodSetEntity() {
        final HttpTrace httpTrace = new HttpTrace(HOT_URL);
        final HttpEntity entity = EntityBuilder.create().setText("stuff").build();
        assertThrows(IllegalStateException.class, () -> httpTrace.setEntity(entity));
    }

    @Test
    public void testOptionMethodGetAllowedMethods() {
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_OK, "OK");
        response.addHeader("Allow", "GET, HEAD");
        response.addHeader("Allow", "DELETE");
        response.addHeader("Content-Length", "128");
        final HttpOptions httpOptions = new HttpOptions("");
        final Set<String> methods = httpOptions.getAllowedMethods(response);
        assertAll("Must all pass",
                () -> assertFalse(methods.isEmpty()),
                () -> assertEquals(methods.size(), 3),
                () -> assertTrue(methods.containsAll(Stream.of("HEAD", "DELETE", "GET")
                        .collect(Collectors.toCollection(HashSet::new))))
        );
    }

}
