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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class RestInvocationHandlerTest {

    @Test
    void testEncodeSpace() {
        assertEquals("hello%20world", RestInvocationHandler.percentEncodeComponent("hello world"));
    }

    @Test
    void testEncodeSlash() {
        assertEquals("a%2Fb", RestInvocationHandler.percentEncodeComponent("a/b"));
    }

    @Test
    void testEncodeUnicode() {
        assertEquals("%C3%A9", RestInvocationHandler.percentEncodeComponent("\u00e9"));
    }

    @Test
    void testEncodeTildeIsUnreserved() {
        assertEquals("~user", RestInvocationHandler.percentEncodeComponent("~user"));
    }

    @Test
    void testEncodePlus() {
        assertEquals("a%2Bb", RestInvocationHandler.percentEncodeComponent("a+b"));
    }

    @Test
    void testEncodeHyphenDotUnderscore() {
        assertEquals("a-b.c_d",
                RestInvocationHandler.percentEncodeComponent("a-b.c_d"));
    }

    @Test
    void testEncodeAlphaNumericPassthrough() {
        assertEquals("AZaz09", RestInvocationHandler.percentEncodeComponent("AZaz09"));
    }

    @Test
    void testEncodeEmptyString() {
        assertEquals("", RestInvocationHandler.percentEncodeComponent(""));
    }

    @Test
    void testExpandSingleVariable() {
        final Map<String, String> vars = Collections.singletonMap("id", "42");
        assertEquals("/items/42", RestInvocationHandler.expandTemplate("/items/{id}", vars));
    }

    @Test
    void testExpandMultipleVariables() {
        final Map<String, String> vars = new LinkedHashMap<>();
        vars.put("group", "admin");
        vars.put("id", "7");
        assertEquals("/admin/users/7", RestInvocationHandler.expandTemplate("/{group}/users/{id}", vars));
    }

    @Test
    void testExpandEncodesValues() {
        final Map<String, String> vars = Collections.singletonMap("name", "hello world");
        assertEquals("/items/hello%20world", RestInvocationHandler.expandTemplate("/items/{name}", vars));
    }

    @Test
    void testExpandNoVariables() {
        assertEquals("/plain", RestInvocationHandler.expandTemplate("/plain", Collections.emptyMap()));
    }

}
