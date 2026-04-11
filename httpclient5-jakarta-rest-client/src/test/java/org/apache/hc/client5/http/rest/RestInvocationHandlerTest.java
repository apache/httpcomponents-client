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
package org.apache.hc.client5.http.rest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class RestInvocationHandlerTest {

    @Test
    void testExpandSingleVariable() {
        final Map<String, String> vars = Collections.singletonMap("id", "42");
        assertArrayEquals(
                new String[]{"items", "42"},
                RestInvocationHandler.expandPathSegments("/items/{id}", vars));
    }

    @Test
    void testExpandMultipleVariables() {
        final Map<String, String> vars = new LinkedHashMap<>();
        vars.put("group", "admin");
        vars.put("id", "7");
        assertArrayEquals(
                new String[]{"admin", "users", "7"},
                RestInvocationHandler.expandPathSegments("/{group}/users/{id}", vars));
    }

    @Test
    void testExpandPreservesRawValues() {
        final Map<String, String> vars = Collections.singletonMap("name", "hello world");
        assertArrayEquals(
                new String[]{"items", "hello world"},
                RestInvocationHandler.expandPathSegments("/items/{name}", vars));
    }

    @Test
    void testExpandNoVariables() {
        assertArrayEquals(
                new String[]{"plain"},
                RestInvocationHandler.expandPathSegments("/plain", Collections.emptyMap()));
    }

    @Test
    void testExpandEmptyTemplate() {
        assertArrayEquals(
                new String[0],
                RestInvocationHandler.expandPathSegments("/", Collections.emptyMap()));
    }

    @Test
    void testExpandSegmentNoVariable() {
        assertEquals("plain",
                RestInvocationHandler.expandSegment("plain", Collections.emptyMap()));
    }

    @Test
    void testExpandSegmentSingleVariable() {
        final Map<String, String> vars = Collections.singletonMap("id", "42");
        assertEquals("42",
                RestInvocationHandler.expandSegment("{id}", vars));
    }

    @Test
    void testExpandSegmentMixedContent() {
        final Map<String, String> vars = new LinkedHashMap<>();
        vars.put("group", "admin");
        vars.put("id", "7");
        assertEquals("admin-7",
                RestInvocationHandler.expandSegment("{group}-{id}", vars));
    }

    @Test
    void testExpandSegmentUnknownVariable() {
        assertEquals("{unknown}",
                RestInvocationHandler.expandSegment("{unknown}", Collections.emptyMap()));
    }

    @Test
    void testParamToStringBasicTypes() {
        assertEquals("42", RestInvocationHandler.paramToString(42));
        assertEquals("true", RestInvocationHandler.paramToString(true));
        assertEquals("hello", RestInvocationHandler.paramToString("hello"));
    }

    enum Color { RED, GREEN, BLUE }

    enum OverriddenToString {
        ALPHA;

        @Override
        public String toString() {
            return "custom-alpha";
        }
    }

    @Test
    void testParamToStringEnumUsesName() {
        assertEquals("RED", RestInvocationHandler.paramToString(Color.RED));
        assertEquals("GREEN", RestInvocationHandler.paramToString(Color.GREEN));
    }

    @Test
    void testParamToStringEnumIgnoresOverriddenToString() {
        assertEquals("ALPHA", RestInvocationHandler.paramToString(OverriddenToString.ALPHA));
    }

}
